package com.example.domain

import com.example.ai.AgentAdvisor
import com.example.ai.DeterministicAgentAdvisor
import com.example.data.TradingRepository
import com.example.model.AgentCycleSnapshot
import com.example.model.AgentCycleState
import com.example.model.AgentLogMessage
import com.example.model.AgentRole
import com.example.model.AgentStageState
import com.example.model.AgentStageStatus
import com.example.model.AgentVerdict
import com.example.model.DataMode
import com.example.model.DeskConfig
import com.example.model.EntryStatus
import com.example.model.PortfolioState
import com.example.model.RiskCheckItem
import com.example.model.RiskResult
import com.example.model.RiskVerdict
import com.example.model.ScanMode
import com.example.model.StopRuleType
import com.example.model.TargetRuleType
import com.example.model.TimeMachineSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Coordinates the 7 specialized day-trading agents in strict sequence,
 * passing and mutating a serializable AgentCycleState JSON payload.
 * Risk Agent holds unbypassable veto power: if Risk fails, execution stops immediately.
 */
class AgentOrchestratorEngine(
    private val repo: TradingRepository,
    private val advisor: AgentAdvisor
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun runFullCycle(
        config: DeskConfig,
        watchlist: List<String>,
        symbolOverride: String? = null,
        autoBookPaper: Boolean = true,
        useAi: Boolean = true,
        onStageUpdate: (List<AgentStageState>) -> Unit,
        onLogMessage: (AgentLogMessage) -> Unit
    ): AgentCycleSnapshot = withContext(Dispatchers.Default) {
        val activeAdvisor = if (useAi) advisor else DeterministicAgentAdvisor()
        val startWall = System.currentTimeMillis()
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val nowIst = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        val startedAtStr = IndianMarketUtils.formatIstDateTime(nowIst)
        val clockStatus = IndianMarketUtils.clockStatus(
            nowIst,
            live,
            repo.loadReferenceData(config).third
        )
        val cycleId = "CYC-${UUID.randomUUID().toString().take(6).uppercase()}"

        val stages = AgentRole.entries.map { AgentStageState(it, AgentStageStatus.PENDING, "Queued") }.toMutableList()
        val messages = mutableListOf<AgentLogMessage>()
        var aiCalls = 0
        var fallbackCalls = 0

        fun trackAi(source: String) {
            if (source.startsWith("Gemini")) aiCalls++ else fallbackCalls++
        }

        fun setStage(
            role: AgentRole,
            status: AgentStageStatus,
            note: String,
            activeSkill: String? = null,
            completedSkills: List<String>? = null
        ) {
            val idx = stages.indexOfFirst { it.role == role }
            if (idx >= 0) {
                val prev = stages[idx]
                stages[idx] = AgentStageState(
                    role = role,
                    status = status,
                    note = note,
                    activeSkill = activeSkill,
                    completedSkills = completedSkills ?: prev.completedSkills
                )
                onStageUpdate(stages.toList())
            }
        }

        fun emit(
            role: AgentRole,
            verdict: AgentVerdict,
            headline: String,
            reasoning: String,
            aiSource: String,
            symbol: String? = null,
            stateObj: AgentCycleState
        ) {
            val tNow = IndianMarketUtils.formatIstTime(IndianMarketUtils.nowIst(live, config.replayIsoIst))
            val msg = AgentLogMessage(
                id = "${cycleId}-${messages.size + 1}",
                cycleId = cycleId,
                agentRole = role,
                agentTitle = role.title,
                skillsUsed = role.skills,
                timestampIst = tNow,
                verdict = verdict,
                headline = headline,
                reasoning = reasoning,
                aiSource = aiSource,
                symbol = symbol,
                jsonPayload = json.encodeToString(stateObj)
            )
            messages += msg
            onLogMessage(msg)
        }

        onStageUpdate(stages.toList())

        var state = AgentCycleState(
            cycleId = cycleId,
            startedAtIst = startedAtStr,
            dataMode = config.dataMode,
            paperOnly = config.paper
        )

        // =========================================================================
        // 1. ORCHESTRATOR AGENT
        // =========================================================================
        setStage(AgentRole.ORCHESTRATOR, AgentStageStatus.RUNNING, "Booting cycle", activeSkill = "cycle-coord")
        val portfolio = if (config.paper) {
            repo.portfolioFlow.first()
        } else {
            val creds = repo.getCredentials(config.selectedBroker)
            if (!creds.hasUsableAuth()) {
                state = state.copy(
                    completedAtIst = IndianMarketUtils.formatIstTime(nowIst),
                    haltedByAgent = AgentRole.ORCHESTRATOR,
                    haltedReason = "Real Mode requires saved ${config.selectedBroker.displayName} API credentials in Broker Settings.",
                    durationMs = System.currentTimeMillis() - startWall
                )
                setStage(AgentRole.ORCHESTRATOR, AgentStageStatus.FAILED, "No broker auth", activeSkill = "cycle-coord")
                emit(
                    role = AgentRole.ORCHESTRATOR,
                    verdict = AgentVerdict.ERROR,
                    headline = "Missing ${config.selectedBroker.displayName} API Credentials",
                    reasoning = "Paper Mode is isolated and requires no broker keys, whereas Real Mode needs saved ${config.selectedBroker.displayName} API credentials in Broker Settings.",
                    aiSource = "Orchestrator Core",
                    stateObj = state
                )
                return@withContext finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
            }
            val fundsRes = repo.fetchRealFunds(config.selectedBroker)
            if (fundsRes.isFailure) {
                val errMsg = fundsRes.exceptionOrNull()?.message ?: "Unable to verify broker funds"
                state = state.copy(
                    completedAtIst = IndianMarketUtils.formatIstTime(nowIst),
                    haltedByAgent = AgentRole.ORCHESTRATOR,
                    haltedReason = errMsg,
                    durationMs = System.currentTimeMillis() - startWall
                )
                setStage(AgentRole.ORCHESTRATOR, AgentStageStatus.FAILED, "Broker API error", activeSkill = "cycle-coord")
                emit(
                    role = AgentRole.ORCHESTRATOR,
                    verdict = AgentVerdict.ERROR,
                    headline = "${config.selectedBroker.displayName} Funds Check Failed",
                    reasoning = errMsg,
                    aiSource = "Orchestrator Core",
                    stateObj = state
                )
                return@withContext finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
            }
            val livePos = repo.fetchRealPositions(config.selectedBroker).getOrElse { emptyList() }
            val todayStr = nowIst.toLocalDate().toString()
            val todayRealRecs = repo.brokerRecordsFlow.first().filter { !it.paper && it.date == todayStr }
            val realBuysToday = todayRealRecs.count { it.side.equals("BUY", ignoreCase = true) }
            PortfolioState(
                cash = fundsRes.getOrThrow().availableCash,
                realizedPnlToday = 0.0,
                tradesToday = realBuysToday,
                ordersToday = todayRealRecs.size,
                lastDay = todayStr,
                positions = livePos
            )
        }
        val modeDesc = if (live) "Live Public Feeds" else "Offline Replay (${config.replayIsoIst} IST)"
        val safetyNote = if (config.paper) {
            "Paper Mode Active (isolated default)."
        } else {
            "REAL MODE ACTIVE (${config.selectedBroker.displayName}) — REAL MONEY AT RISK."
        }
        setStage(
            AgentRole.ORCHESTRATOR,
            AgentStageStatus.PASSED,
            clockStatus.phaseLabel,
            activeSkill = null,
            completedSkills = listOf("cycle-coord")
        )
        emit(
            role = AgentRole.ORCHESTRATOR,
            verdict = AgentVerdict.INFO,
            headline = "Initiated $cycleId • ${clockStatus.istDate} ${clockStatus.istTime} IST",
            reasoning = "Coordinating 7-agent pipeline in $modeDesc. $safetyNote Available cash: ${IndianMarketUtils.inr(portfolio.cash)}, trades today: ${portfolio.tradesToday}/${config.maxTradesPerDay}.",
            aiSource = "Orchestrator Core",
            stateObj = state
        )

        // =========================================================================
        // 2. SCANNER AGENT (india-scanner)
        // =========================================================================
        setStage(
            AgentRole.SCANNER,
            AgentStageStatus.RUNNING,
            "Scanning ${config.exchange} ${config.orbDurationMinutes}m ORB",
            activeSkill = "india-scanner"
        )
        val orbHits = runCatching {
            repo.runScan(
                mode = ScanMode.ORB,
                setupFilter = "all",
                watchlist = watchlist,
                orMinutes = config.orbDurationMinutes,
                minVolumeX = config.orbMinVolumeX,
                includeT2t = false,
                config = config
            )
        }.getOrElse { emptyList() }
        val dailyHits = if (orbHits.isEmpty()) {
            runCatching {
                repo.runScan(
                    mode = ScanMode.DAILY,
                    setupFilter = "all",
                    watchlist = watchlist,
                    orMinutes = config.orbDurationMinutes,
                    minVolumeX = config.orbMinVolumeX,
                    includeT2t = false,
                    config = config
                )
            }.getOrElse { emptyList() }
        } else emptyList()
        val combinedHits = (orbHits + dailyHits).distinctBy { it.symbol }

        val candidatePool = if (!symbolOverride.isNullOrBlank()) {
            val clean = runCatching { IndianMarketUtils.parseSymbol(symbolOverride, config.exchange).first }
                .getOrElse { symbolOverride.uppercase() }
            val existing = combinedHits.firstOrNull { it.symbol == clean }
            val forced = existing ?: com.example.model.ScanHit(
                symbol = clean,
                exchange = config.exchange,
                setup = "manual_target",
                badge = "Manual Target",
                lastPrice = 0.0,
                prevClose = 0.0,
                changePct = 0.0,
                summary = "Targeted by operator for full multi-agent pipeline evaluation"
            )
            listOf(forced) + combinedHits.filter { it.symbol != clean }
        } else {
            combinedHits
        }

        if (candidatePool.isEmpty()) {
            state = state.copy(
                completedAtIst = IndianMarketUtils.formatIstTime(nowIst),
                haltedByAgent = AgentRole.SCANNER,
                haltedReason = "Scanner returned 0 candidates.",
                durationMs = System.currentTimeMillis() - startWall
            )
            setStage(AgentRole.SCANNER, AgentStageStatus.FAILED, "0 hits", activeSkill = "india-scanner")
            emit(
                AgentRole.SCANNER, AgentVerdict.ERROR,
                headline = "Scanner returned 0 qualifying setups",
                reasoning = "Neither ORB nor daily breakout scans matched any non-T2T NSE stock. Halting cycle cleanly.",
                aiSource = "Scanner Engine",
                stateObj = state
            )
            return@withContext finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
        }

        setStage(
            AgentRole.SCANNER,
            AgentStageStatus.RUNNING,
            if (useAi) "AI ranking ${candidatePool.size} hits" else "Ranking ${candidatePool.size} hits",
            activeSkill = "india-scanner"
        )
        val scanDecision = activeAdvisor.prioritizeCandidates(candidatePool, clockStatus)
        trackAi(scanDecision.aiSource)
        val rankedHits = scanDecision.rankedSymbols.mapNotNull { sym -> candidatePool.firstOrNull { it.symbol == sym } }
            .ifEmpty { candidatePool.take(3) }
        state = state.copy(watchlistCandidates = rankedHits)
        setStage(
            AgentRole.SCANNER,
            AgentStageStatus.PASSED,
            "${rankedHits.size} shortlisted",
            activeSkill = null,
            completedSkills = listOf("india-scanner")
        )
        emit(
            AgentRole.SCANNER, AgentVerdict.SELECTED,
            headline = "Shortlisted ${rankedHits.joinToString(", ") { it.symbol }}",
            reasoning = scanDecision.rationale,
            aiSource = scanDecision.aiSource,
            symbol = rankedHits.firstOrNull()?.symbol,
            stateObj = state
        )

        // =========================================================================
        // 3. NEWS & ANALYST AGENT (india-news, india-analyst, india-insiders)
        // =========================================================================
        setStage(AgentRole.NEWS_ANALYST, AgentStageStatus.RUNNING, "Auditing india-news", activeSkill = "india-news")
        var chosenSymbol: String? = null
        var chosenSummary = ""
        var chosenFlags: List<String> = emptyList()
        var chosenSource = "Deterministic Engine"

        for (hit in rankedHits) {
            setStage(AgentRole.NEWS_ANALYST, AgentStageStatus.RUNNING, "${hit.symbol}: india-news", activeSkill = "india-news")
            val news = runCatching { repo.fetchNews(hit.symbol, config) }.getOrNull()
            setStage(
                AgentRole.NEWS_ANALYST,
                AgentStageStatus.RUNNING,
                "${hit.symbol}: india-analyst",
                activeSkill = "india-analyst",
                completedSkills = listOf("india-news")
            )
            val analyst = repo.fetchAnalyst(hit.symbol, "consolidated", config).getOrNull()
            setStage(
                AgentRole.NEWS_ANALYST,
                AgentStageStatus.RUNNING,
                "${hit.symbol}: india-insiders",
                activeSkill = "india-insiders",
                completedSkills = listOf("india-news", "india-analyst")
            )
            val insiders = runCatching { repo.fetchInsiders(hit.symbol, config) }.getOrNull()
            val decision = activeAdvisor.evaluateNewsAndAnalyst(hit.symbol, news, analyst, insiders)
            trackAi(decision.aiSource)
            if (decision.proceed) {
                chosenSymbol = hit.symbol
                chosenSummary = decision.convictionNote
                chosenFlags = decision.riskFlags
                chosenSource = decision.aiSource
                break
            }
        }

        if (chosenSymbol == null) {
            state = state.copy(
                completedAtIst = IndianMarketUtils.formatIstTime(nowIst),
                haltedByAgent = AgentRole.NEWS_ANALYST,
                haltedReason = "All shortlisted candidates failed news/analyst event screening.",
                durationMs = System.currentTimeMillis() - startWall
            )
            setStage(AgentRole.NEWS_ANALYST, AgentStageStatus.VETOED, "All filtered out", activeSkill = "india-insiders")
            emit(
                AgentRole.NEWS_ANALYST, AgentVerdict.ERROR,
                headline = "News/Analyst filtered out all shortlisted stocks",
                reasoning = "Event risk or financial quality checks blocked every candidate in ${rankedHits.joinToString(", ") { it.symbol }}.",
                aiSource = chosenSource,
                stateObj = state
            )
            return@withContext finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
        }

        state = state.copy(
            selectedSymbol = chosenSymbol,
            newsAnalystSummary = chosenSummary,
            newsAnalystFlags = chosenFlags
        )
        setStage(
            AgentRole.NEWS_ANALYST,
            AgentStageStatus.PASSED,
            "Cleared: $chosenSymbol",
            activeSkill = null,
            completedSkills = listOf("india-news", "india-analyst", "india-insiders")
        )
        emit(
            AgentRole.NEWS_ANALYST, AgentVerdict.FILTERED,
            headline = "Cleared $chosenSymbol for intraday entry",
            reasoning = if (chosenFlags.isEmpty()) chosenSummary else "$chosenSummary Flags noted: ${chosenFlags.joinToString("; ")}.",
            aiSource = chosenSource,
            symbol = chosenSymbol,
            stateObj = state
        )

        // =========================================================================
        // 4. ENTRY AGENT (india-entry)
        // =========================================================================
        setStage(
            AgentRole.ENTRY,
            AgentStageStatus.RUNNING,
            "Timing ${config.orbDurationMinutes}m ORB entry",
            activeSkill = "india-entry"
        )
        val entryResult = runCatching {
            repo.evaluateEntry(
                rawSymbol = chosenSymbol,
                setup = "orb",
                levelPrice = null,
                orMinutes = config.orbDurationMinutes,
                minVolumeX = config.orbMinVolumeX,
                interval = "5m",
                config = config
            )
        }.getOrElse { err ->
            state = state.copy(
                completedAtIst = IndianMarketUtils.formatIstTime(nowIst),
                haltedByAgent = AgentRole.ENTRY,
                haltedReason = "Entry feed error for $chosenSymbol: ${err.message}",
                durationMs = System.currentTimeMillis() - startWall
            )
            setStage(AgentRole.ENTRY, AgentStageStatus.FAILED, "No intraday feed", activeSkill = "india-entry")
            emit(
                AgentRole.ENTRY, AgentVerdict.ERROR,
                headline = "Entry Agent failed to load $chosenSymbol 5m bars",
                reasoning = err.message ?: "Intraday bars missing.",
                aiSource = "Entry Engine",
                symbol = chosenSymbol,
                stateObj = state
            )
            return@withContext finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
        }

        state = state.copy(entryCheck = entryResult)
        if (entryResult.status == EntryStatus.NO_DATA || (entryResult.entryPrice == null && entryResult.lastClose == null)) {
            state = state.copy(
                completedAtIst = IndianMarketUtils.formatIstTime(nowIst),
                haltedByAgent = AgentRole.ENTRY,
                haltedReason = entryResult.message,
                durationMs = System.currentTimeMillis() - startWall
            )
            setStage(AgentRole.ENTRY, AgentStageStatus.FAILED, entryResult.status.name, activeSkill = "india-entry")
            emit(
                AgentRole.ENTRY, AgentVerdict.ERROR,
                headline = "Entry setup rejected for $chosenSymbol (${entryResult.status})",
                reasoning = entryResult.message,
                aiSource = "Entry Engine",
                symbol = chosenSymbol,
                stateObj = state
            )
            return@withContext finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
        }

        val effectiveEntry = entryResult.entryPrice ?: entryResult.lastClose!!
        setStage(
            AgentRole.ENTRY,
            AgentStageStatus.PASSED,
            "${IndianMarketUtils.inr(effectiveEntry)} @ ${entryResult.entryTime ?: entryResult.lastTime ?: "now"}",
            activeSkill = null,
            completedSkills = listOf("india-entry")
        )
        emit(
            AgentRole.ENTRY, AgentVerdict.PASS,
            headline = "Proposed long entry on $chosenSymbol @ ${IndianMarketUtils.inr(effectiveEntry)}",
            reasoning = entryResult.message,
            aiSource = "Entry Engine (india-entry)",
            symbol = chosenSymbol,
            stateObj = state
        )

        // =========================================================================
        // 5. EXIT & TIMEMACHINE AGENT (india-exit, india-timemachine)
        // =========================================================================
        setStage(AgentRole.EXIT_TIMEMACHINE, AgentStageStatus.RUNNING, "Building stop & target", activeSkill = "india-exit")
        val stopRule = if (entryResult.orHigh != null && entryResult.orLow != null) {
            if (config.orbStopRule == StopRuleType.OR_MID || config.orbStopRule == StopRuleType.OR_LOW) {
                config.orbStopRule
            } else {
                StopRuleType.OR_MID
            }
        } else {
            StopRuleType.PCT
        }
        val planResult = repo.buildTradePlan(
            entry = entryResult,
            stopRule = stopRule,
            stopParam = 1.0,
            targetRule = config.orbTargetRule,
            targetPriceParam = 0.0,
            config = config
        )
        val tradePlan = planResult.getOrElse { err ->
            state = state.copy(
                completedAtIst = IndianMarketUtils.formatIstTime(nowIst),
                haltedByAgent = AgentRole.EXIT_TIMEMACHINE,
                haltedReason = "Exit planner rejected setup: ${err.message}",
                durationMs = System.currentTimeMillis() - startWall
            )
            setStage(AgentRole.EXIT_TIMEMACHINE, AgentStageStatus.FAILED, "Invalid plan", activeSkill = "india-exit")
            emit(
                AgentRole.EXIT_TIMEMACHINE, AgentVerdict.ERROR,
                headline = "Exit Planner failed for $chosenSymbol",
                reasoning = err.message ?: "Could not compute valid stop/target on NSE tick grid.",
                aiSource = "Exit Engine",
                symbol = chosenSymbol,
                stateObj = state
            )
            return@withContext finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
        }

        setStage(
            AgentRole.EXIT_TIMEMACHINE,
            AgentStageStatus.RUNNING,
            "Running 10k Monte Carlo",
            activeSkill = "india-timemachine",
            completedSkills = listOf("india-exit")
        )
        val tmResult = repo.runTimeMachine(
            plan = tradePlan,
            config = config,
            replays = 10000,
            seed = 19,
            window = 2,
            qtyForNet = 30
        ).getOrNull()

        val tmSummary = tmResult?.let {
            TimeMachineSummaryDto(
                symbol = it.symbol,
                sessionsUsed = it.sessionsUsed,
                replays = it.replays,
                pTarget = it.pTarget,
                pStop = it.pStop,
                pNeither = it.pNeither,
                pGreen = it.pGreen,
                avgR = it.avgR,
                avgRNet = it.avgRNet,
                verdict = it.verdict
            )
        }
        state = state.copy(tradePlan = tradePlan, timeMachineSummary = tmSummary)
        val oddsNarrative = activeAdvisor.summarizeEntryAndOdds(entryResult, tradePlan, tmResult)
        trackAi(oddsNarrative.aiSource)
        setStage(
            AgentRole.EXIT_TIMEMACHINE,
            AgentStageStatus.PASSED,
            tmSummary?.verdict ?: "Plan Ready",
            activeSkill = null,
            completedSkills = listOf("india-exit", "india-timemachine")
        )
        emit(
            AgentRole.EXIT_TIMEMACHINE, AgentVerdict.PASS,
            headline = "Trade Plan (${"%.2f".format(tradePlan.rewardRisk)}R) + TimeMachine ${tmSummary?.verdict ?: "OK"}",
            reasoning = oddsNarrative.summary,
            aiSource = oddsNarrative.aiSource,
            symbol = chosenSymbol,
            stateObj = state
        )

        // =========================================================================
        // 6. RISK AGENT — HARD VETO GATE (india-budget, india-risk)
        // =========================================================================
        setStage(AgentRole.RISK, AgentStageStatus.RUNNING, "Sizing order budget", activeSkill = "india-budget")
        val sizeResult = repo.sizeOrder(tradePlan, config, portfolio, orderType = "MARKET", limitPrice = null, side = "BUY")
        val orderProposal = sizeResult.getOrNull()
        setStage(
            AgentRole.RISK,
            AgentStageStatus.RUNNING,
            "Enforcing 14-point gate",
            activeSkill = "india-risk",
            completedSkills = if (orderProposal != null) listOf("india-budget") else emptyList()
        )
        val riskVerdict = if (orderProposal != null) {
            repo.checkOrderRisk(orderProposal, config, portfolio)
        } else {
            RiskVerdict(
                date = tradePlan.date,
                checkedAtIst = IndianMarketUtils.formatIstDateTime(nowIst),
                allPassed = false,
                results = listOf(
                    RiskResult(
                        index = 0,
                        fingerprint = "SIZING_FAIL",
                        symbol = tradePlan.symbol,
                        exchange = tradePlan.exchange,
                        passed = false,
                        reasons = listOf(sizeResult.exceptionOrNull()?.message ?: "Sizing rejected order within budget caps"),
                        warnings = emptyList(),
                        items = listOf(
                            RiskCheckItem(
                                rule = "Position Sizing & Budget Caps",
                                passed = false,
                                detail = sizeResult.exceptionOrNull()?.message ?: "Zero affordable shares"
                            )
                        )
                    )
                )
            )
        }

        state = state.copy(orderProposal = orderProposal, riskVerdict = riskVerdict)
        val riskNarrative = activeAdvisor.explainRiskAndVeto(orderProposal, riskVerdict)
        trackAi(riskNarrative.aiSource)

        if (!riskVerdict.allPassed || orderProposal == null) {
            val vetoReason = riskVerdict.results.firstOrNull()?.reasons?.joinToString("; ")
                ?: "Risk Gate blocked execution"
            state = state.copy(
                completedAtIst = IndianMarketUtils.formatIstTime(nowIst),
                haltedByAgent = AgentRole.RISK,
                haltedReason = vetoReason,
                succeeded = false,
                durationMs = System.currentTimeMillis() - startWall
            )
            setStage(
                AgentRole.RISK,
                AgentStageStatus.VETOED,
                "HARD VETO",
                activeSkill = "india-risk",
                completedSkills = if (orderProposal != null) listOf("india-budget") else emptyList()
            )
            setStage(AgentRole.ALERTS_BROKER, AgentStageStatus.PENDING, "Aborted by Risk", activeSkill = null, completedSkills = emptyList())
            emit(
                AgentRole.RISK, AgentVerdict.VETO_BLOCK,
                headline = "RISK VETO: $chosenSymbol order BLOCKED",
                reasoning = riskNarrative.summary,
                aiSource = riskNarrative.aiSource,
                symbol = chosenSymbol,
                stateObj = state
            )
            return@withContext finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
        }

        setStage(
            AgentRole.RISK,
            AgentStageStatus.PASSED,
            "${orderProposal.quantity} qty • PASS",
            activeSkill = null,
            completedSkills = listOf("india-budget", "india-risk")
        )
        emit(
            AgentRole.RISK, AgentVerdict.PASS,
            headline = "Risk Gate APPROVED: ${orderProposal.quantity} × $chosenSymbol (FP ${orderProposal.fingerprint})",
            reasoning = "${riskNarrative.summary} (R:R ${"%.2f".format(tradePlan.rewardRisk)} ≥ min ${"%.1f".format(config.minRewardRisk)}R)",
            aiSource = riskNarrative.aiSource,
            symbol = chosenSymbol,
            stateObj = state
        )

        // =========================================================================
        // 7. ALERTS & EXECUTION AGENT (india-broker, india-alerts)
        // =========================================================================
        val brokerLabel = if (config.paper) "Paper Ledger" else config.selectedBroker.displayName
        setStage(
            AgentRole.ALERTS_BROKER,
            AgentStageStatus.RUNNING,
            if (autoBookPaper) {
                if (config.paper) "Booking paper fill" else "Sending ${config.selectedBroker.displayName} order"
            } else "Verifying ${config.selectedBroker.displayName} preview",
            activeSkill = "india-broker"
        )
        val fillSummary: String
        var alertsCount = 0

        if (autoBookPaper) {
            val confirmReply = "yes buy ${orderProposal.quantity} ${orderProposal.tradingsymbol}"
            val execRes = repo.confirmAndExecuteOrder(
                order = orderProposal,
                portfolio = portfolio,
                verdict = riskVerdict,
                reply = confirmReply,
                config = config
            )
            fillSummary = execRes.getOrElse { err ->
                state = state.copy(
                    completedAtIst = IndianMarketUtils.formatIstTime(nowIst),
                    haltedByAgent = AgentRole.ALERTS_BROKER,
                    haltedReason = err.message,
                    durationMs = System.currentTimeMillis() - startWall
                )
                setStage(AgentRole.ALERTS_BROKER, AgentStageStatus.FAILED, "$brokerLabel rejected", activeSkill = "india-broker")
                emit(
                    AgentRole.ALERTS_BROKER, AgentVerdict.ERROR,
                    headline = "$brokerLabel execution error",
                    reasoning = err.message ?: "Order execution failed.",
                    aiSource = "$brokerLabel Adapter",
                    symbol = chosenSymbol,
                    stateObj = state
                )
                return@withContext finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
            }
            setStage(
                AgentRole.ALERTS_BROKER,
                AgentStageStatus.RUNNING,
                "Arming alert rungs",
                activeSkill = "india-alerts",
                completedSkills = listOf("india-broker")
            )
            repo.addAlertsFromPlan(tradePlan)
            alertsCount = AlertsNewsAnalystInsidersEngine.rulesFromPlan(tradePlan).size
        } else {
            setStage(
                AgentRole.ALERTS_BROKER,
                AgentStageStatus.RUNNING,
                "Checking alert rungs",
                activeSkill = "india-alerts",
                completedSkills = listOf("india-broker")
            )
            fillSummary = "Queued ${orderProposal.quantity} × ${orderProposal.tradingsymbol} awaiting manual confirmation."
        }

        val endIst = IndianMarketUtils.formatIstTime(IndianMarketUtils.nowIst(live, config.replayIsoIst))
        state = state.copy(
            completedAtIst = endIst,
            alertsArmedCount = alertsCount,
            paperFillSummary = fillSummary,
            succeeded = true,
            durationMs = System.currentTimeMillis() - startWall
        )
        val wrapNarrative = activeAdvisor.summarizeCycleForAlerts(
            symbol = chosenSymbol,
            paperSummary = fillSummary,
            alertsArmedCount = alertsCount,
            verdictPassed = true
        )
        trackAi(wrapNarrative.aiSource)
        if (autoBookPaper) {
            val statusBadge = if (config.paper) "Paper filled" else "${config.selectedBroker.displayName} placed"
            setStage(
                AgentRole.ALERTS_BROKER,
                AgentStageStatus.BOOKED,
                "$statusBadge + $alertsCount alerts",
                activeSkill = null,
                completedSkills = listOf("india-broker", "india-alerts")
            )
            emit(
                AgentRole.ALERTS_BROKER, AgentVerdict.PAPER_FILLED,
                headline = if (config.paper) {
                    "Paper Fill Recorded & $alertsCount Protective Alert(s) Armed"
                } else {
                    "REAL Order Sent (${config.selectedBroker.displayName}) & $alertsCount Alert(s) Armed"
                },
                reasoning = "$fillSummary\n${wrapNarrative.summary}",
                aiSource = wrapNarrative.aiSource,
                symbol = chosenSymbol,
                stateObj = state
            )
        } else {
            setStage(
                AgentRole.ALERTS_BROKER,
                AgentStageStatus.PASSED,
                "Preview ready",
                activeSkill = null,
                completedSkills = listOf("india-broker", "india-alerts")
            )
            emit(
                AgentRole.ALERTS_BROKER, AgentVerdict.PASS,
                headline = "Preview Verified: ${orderProposal.quantity} × ${orderProposal.tradingsymbol} Ready",
                reasoning = wrapNarrative.summary,
                aiSource = wrapNarrative.aiSource,
                symbol = chosenSymbol,
                stateObj = state
            )
        }

        finishAndSave(state, messages, aiCalls, fallbackCalls, autoBookPaper, config)
    }

    private suspend fun finishAndSave(
        state: AgentCycleState,
        messages: List<AgentLogMessage>,
        aiCalls: Int,
        fallbackCalls: Int,
        autoBookPaper: Boolean,
        config: DeskConfig
    ): AgentCycleSnapshot {
        val outcome = when {
            state.succeeded && autoBookPaper && !config.paper ->
                "REAL_ORDER_PLACED (${config.selectedBroker.displayName}): ${state.selectedSymbol} (${state.orderProposal?.quantity ?: 0} qty)"
            state.succeeded && autoBookPaper ->
                "PAPER_BOOKED: ${state.selectedSymbol} (${state.orderProposal?.quantity ?: 0} qty)"
            state.succeeded ->
                "PREVIEW_READY: ${state.selectedSymbol} (${state.orderProposal?.quantity ?: 0} qty)"
            state.haltedByAgent == AgentRole.RISK -> "VETOED BY RISK: ${state.haltedReason}"
            else -> "HALTED (${state.haltedByAgent?.badge ?: "ERR"}): ${state.haltedReason ?: "Stopped"}"
        }
        val snapshot = AgentCycleSnapshot(
            cycleId = state.cycleId,
            startedAtIst = state.startedAtIst,
            selectedSymbol = state.selectedSymbol,
            outcome = outcome,
            succeeded = state.succeeded,
            aiCalls = aiCalls,
            fallbackCalls = fallbackCalls,
            messages = messages,
            finalStateJson = json.encodeToString(state)
        )
        runCatching { repo.saveAgentCycle(snapshot) }
        return snapshot
    }
}
