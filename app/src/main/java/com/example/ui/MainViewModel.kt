package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AgentAdvisor
import com.example.ai.GeminiAgentAdvisor
import com.example.data.TradingRepository
import com.example.domain.AgentOrchestratorEngine
import com.example.domain.BudgetRiskBrokerEngine
import com.example.domain.IndianMarketUtils
import com.example.model.AgentCycleSnapshot
import com.example.model.AgentLogMessage
import com.example.model.AgentRole
import com.example.model.AgentStageState
import com.example.model.AgentStageStatus
import com.example.model.AgentVerdict
import com.example.model.AlertCheckSummary
import com.example.model.AlertRule
import com.example.model.AlertType
import com.example.model.AllocationRow
import com.example.model.AnalystReport
import com.example.model.AutoActivityEvent
import com.example.model.AutoIntervalOption
import com.example.model.AutoModeStatus
import com.example.model.BrokerCredentials
import com.example.model.BrokerFundsSnapshot
import com.example.model.BrokerRecord
import com.example.model.BrokerRemoteOrder
import com.example.model.DataMode
import com.example.model.DeskConfig
import com.example.model.EntryCheckResult
import com.example.model.HeatmapIntensityMetric
import com.example.model.HeatmapUniverse
import com.example.model.IndianBrokerId
import com.example.model.InsidersReport
import com.example.model.LivePnlSummary
import com.example.model.MarketClockStatus
import com.example.model.MarketHeatmapCell
import com.example.model.NewsReport
import com.example.model.OrbQuickRow
import com.example.model.OrderProposal
import com.example.model.PortfolioPosition
import com.example.model.PortfolioState
import com.example.model.RiskVerdict
import com.example.model.ScanHit
import com.example.model.ScanMode
import com.example.model.StopRuleType
import com.example.model.TargetRuleType
import com.example.model.TimeMachineResult
import com.example.model.TradePlan
import com.example.model.WatchlistSentimentReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val repo: TradingRepository,
    advisor: AgentAdvisor = GeminiAgentAdvisor(context = repo.dataSource.appContext),
    private val autoTickDelayMs: Long = 1000L
) : ViewModel() {

    private val orchestrator = AgentOrchestratorEngine(repo, advisor)

    val config: StateFlow<DeskConfig> = repo.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeskConfig())

    val portfolio: StateFlow<PortfolioState> = repo.portfolioFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PortfolioState())

    val brokerRecords: StateFlow<List<BrokerRecord>> = repo.brokerRecordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alerts: StateFlow<List<AlertRule>> = repo.alertsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedInvestors: StateFlow<List<String>> = repo.followedInvestorsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val brokerCredentials: StateFlow<Map<IndianBrokerId, BrokerCredentials>> = repo.credentialsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _realFunds = MutableStateFlow<BrokerFundsSnapshot?>(null)
    val realFunds: StateFlow<BrokerFundsSnapshot?> = _realFunds.asStateFlow()

    private val _realPositions = MutableStateFlow<List<PortfolioPosition>>(emptyList())
    val realPositions: StateFlow<List<PortfolioPosition>> = _realPositions.asStateFlow()

    private val _realOrders = MutableStateFlow<List<BrokerRemoteOrder>>(emptyList())
    val realOrders: StateFlow<List<BrokerRemoteOrder>> = _realOrders.asStateFlow()

    private val _realBrokerLoading = MutableStateFlow(false)
    val realBrokerLoading: StateFlow<Boolean> = _realBrokerLoading.asStateFlow()

    private val _brokerTestMessage = MutableStateFlow<String?>(null)
    val brokerTestMessage: StateFlow<String?> = _brokerTestMessage.asStateFlow()

    private val _holidays = MutableStateFlow<Map<String, String>>(IndianMarketUtils.NSE_HOLIDAYS_2026)
    val holidays: StateFlow<Map<String, String>> = _holidays.asStateFlow()

    val clockStatus: StateFlow<MarketClockStatus> = combine(config, holidays) { cfg, hol ->
        val live = cfg.dataMode == DataMode.LIVE_PUBLIC
        val now = IndianMarketUtils.nowIst(live, cfg.replayIsoIst)
        IndianMarketUtils.clockStatus(now, live, hol)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        IndianMarketUtils.clockStatus(IndianMarketUtils.nowIst(false, "2026-10-19 10:15"), false)
    )

    val livePnlSummary: StateFlow<LivePnlSummary> = combine(
        config,
        portfolio,
        realFunds,
        realPositions,
        clockStatus
    ) { cfg, paperPort, rFunds, rPos, _ ->
        val now = IndianMarketUtils.nowIst(cfg.dataMode == DataMode.LIVE_PUBLIC, cfg.replayIsoIst)
        if (cfg.paper) {
            BudgetRiskBrokerEngine.computeLivePnlSummary(
                portfolio = paperPort,
                isRealMode = false,
                modeLabel = "Paper Simulation",
                nowIst = now
            )
        } else {
            val realPort = PortfolioState(
                cash = rFunds?.availableCash ?: 0.0,
                realizedPnlToday = 0.0,
                positions = rPos,
                lastDay = now.toLocalDate().toString()
            )
            BudgetRiskBrokerEngine.computeLivePnlSummary(
                portfolio = realPort,
                isRealMode = true,
                modeLabel = "Real • ${cfg.selectedBroker.displayName}",
                nowIst = now
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LivePnlSummary(
            isRealMode = false,
            modeLabel = "Paper Simulation",
            equity = 50000.0,
            availableCash = 50000.0,
            investedValue = 0.0,
            openUnrealizedPnl = 0.0,
            realizedPnlToday = 0.0,
            dayPnl = 0.0,
            openPositionsCount = 0,
            lastUpdatedIst = "10:15"
        )
    )

    // Scanner state
    private val _scanMode = MutableStateFlow(ScanMode.ORB)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _setupFilter = MutableStateFlow("all")
    val setupFilter: StateFlow<String> = _setupFilter.asStateFlow()

    private val _watchlist = MutableStateFlow(listOf("RELIANCE", "HDFCBANK", "TCS", "ICICIBANK", "M&M"))
    val watchlist: StateFlow<List<String>> = _watchlist.asStateFlow()

    private val _orMinutes = MutableStateFlow(15)
    val orMinutes: StateFlow<Int> = _orMinutes.asStateFlow()

    private val _includeT2t = MutableStateFlow(false)
    val includeT2t: StateFlow<Boolean> = _includeT2t.asStateFlow()

    private val _scanHits = MutableStateFlow<List<ScanHit>>(emptyList())
    val scanHits: StateFlow<List<ScanHit>> = _scanHits.asStateFlow()

    private val _scanLoading = MutableStateFlow(false)
    val scanLoading: StateFlow<Boolean> = _scanLoading.asStateFlow()

    // Live NSE/BSE Top-5 Relative Volume ORB Quick Scanner
    private val _orbExchange = MutableStateFlow("NSE")
    val orbExchange: StateFlow<String> = _orbExchange.asStateFlow()

    private val _orbQuickMinutes = MutableStateFlow(15)
    val orbQuickMinutes: StateFlow<Int> = _orbQuickMinutes.asStateFlow()

    private val _topOrbRows = MutableStateFlow<List<OrbQuickRow>>(emptyList())
    val topOrbRows: StateFlow<List<OrbQuickRow>> = _topOrbRows.asStateFlow()

    private val _topOrbLoading = MutableStateFlow(false)
    val topOrbLoading: StateFlow<Boolean> = _topOrbLoading.asStateFlow()

    // Market Volatility & Relative Volume Heatmap (Nifty 50 or Watchlist)
    private val _heatmapUniverse = MutableStateFlow(HeatmapUniverse.NIFTY_50)
    val heatmapUniverse: StateFlow<HeatmapUniverse> = _heatmapUniverse.asStateFlow()

    private val _heatmapMetric = MutableStateFlow(HeatmapIntensityMetric.COMBINED)
    val heatmapMetric: StateFlow<HeatmapIntensityMetric> = _heatmapMetric.asStateFlow()

    private val _heatmapCells = MutableStateFlow<List<MarketHeatmapCell>>(emptyList())
    val heatmapCells: StateFlow<List<MarketHeatmapCell>> = _heatmapCells.asStateFlow()

    private val _heatmapLoading = MutableStateFlow(false)
    val heatmapLoading: StateFlow<Boolean> = _heatmapLoading.asStateFlow()

    // Trade Flow (Steps 1..6)
    private val _tradeSymbol = MutableStateFlow("RELIANCE")
    val tradeSymbol: StateFlow<String> = _tradeSymbol.asStateFlow()

    private val _tradeSide = MutableStateFlow("BUY")
    val tradeSide: StateFlow<String> = _tradeSide.asStateFlow()

    private val _activeStep = MutableStateFlow(0)
    val activeStep: StateFlow<Int> = _activeStep.asStateFlow()

    private val _entryResult = MutableStateFlow<EntryCheckResult?>(null)
    val entryResult: StateFlow<EntryCheckResult?> = _entryResult.asStateFlow()

    private val _stopRule = MutableStateFlow(StopRuleType.OR_MID)
    val stopRule: StateFlow<StopRuleType> = _stopRule.asStateFlow()

    private val _stopParam = MutableStateFlow(1.0)
    val stopParam: StateFlow<Double> = _stopParam.asStateFlow()

    private val _targetRule = MutableStateFlow(TargetRuleType.R1)
    val targetRule: StateFlow<TargetRuleType> = _targetRule.asStateFlow()

    private val _targetParam = MutableStateFlow(1250.0)
    val targetParam: StateFlow<Double> = _targetParam.asStateFlow()

    private val _tradePlan = MutableStateFlow<TradePlan?>(null)
    val tradePlan: StateFlow<TradePlan?> = _tradePlan.asStateFlow()

    private val _timeMachineResult = MutableStateFlow<TimeMachineResult?>(null)
    val timeMachineResult: StateFlow<TimeMachineResult?> = _timeMachineResult.asStateFlow()

    private val _orderType = MutableStateFlow("MARKET")
    val orderType: StateFlow<String> = _orderType.asStateFlow()

    private val _orderProposal = MutableStateFlow<OrderProposal?>(null)
    val orderProposal: StateFlow<OrderProposal?> = _orderProposal.asStateFlow()

    private val _riskVerdict = MutableStateFlow<RiskVerdict?>(null)
    val riskVerdict: StateFlow<RiskVerdict?> = _riskVerdict.asStateFlow()

    private val _tradeFlowLoading = MutableStateFlow(false)
    val tradeFlowLoading: StateFlow<Boolean> = _tradeFlowLoading.asStateFlow()

    // Weights allocator
    private val _allocations = MutableStateFlow<List<AllocationRow>>(emptyList())
    val allocations: StateFlow<List<AllocationRow>> = _allocations.asStateFlow()

    // Alerts check summary
    private val _alertSummary = MutableStateFlow<AlertCheckSummary?>(null)
    val alertSummary: StateFlow<AlertCheckSummary?> = _alertSummary.asStateFlow()

    // Intel state (News, Analyst, Insiders)
    private val _intelSymbol = MutableStateFlow("RELIANCE")
    val intelSymbol: StateFlow<String> = _intelSymbol.asStateFlow()

    private val _newsReport = MutableStateFlow<NewsReport?>(null)
    val newsReport: StateFlow<NewsReport?> = _newsReport.asStateFlow()

    private val _analystReport = MutableStateFlow<AnalystReport?>(null)
    val analystReport: StateFlow<AnalystReport?> = _analystReport.asStateFlow()

    private val _analystBasis = MutableStateFlow("consolidated")
    val analystBasis: StateFlow<String> = _analystBasis.asStateFlow()

    private val _insidersReport = MutableStateFlow<InsidersReport?>(null)
    val insidersReport: StateFlow<InsidersReport?> = _insidersReport.asStateFlow()

    private val _intelLoading = MutableStateFlow(false)
    val intelLoading: StateFlow<Boolean> = _intelLoading.asStateFlow()

    // Watchlist Bullish/Bearish Sentiment (india-news + india-analyst)
    private val _watchlistSentiment = MutableStateFlow<WatchlistSentimentReport?>(null)
    val watchlistSentiment: StateFlow<WatchlistSentimentReport?> = _watchlistSentiment.asStateFlow()

    private val _sentimentLoading = MutableStateFlow(false)
    val sentimentLoading: StateFlow<Boolean> = _sentimentLoading.asStateFlow()

    // Global banner feedback
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Multi-Agent Autonomous Cycle State
    private val _agentStages = MutableStateFlow(AgentRole.entries.map { AgentStageState(it) })
    val agentStages: StateFlow<List<AgentStageState>> = _agentStages.asStateFlow()

    private val _agentLogs = MutableStateFlow<List<AgentLogMessage>>(emptyList())
    val agentLogs: StateFlow<List<AgentLogMessage>> = _agentLogs.asStateFlow()

    val agentCycles: StateFlow<List<AgentCycleSnapshot>> = repo.agentCyclesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastCycleSnapshot = MutableStateFlow<AgentCycleSnapshot?>(null)
    val lastCycleSnapshot: StateFlow<AgentCycleSnapshot?> = _lastCycleSnapshot.asStateFlow()

    private val _cycleRunning = MutableStateFlow(false)
    val cycleRunning: StateFlow<Boolean> = _cycleRunning.asStateFlow()

    private val _autoModeEnabled = MutableStateFlow(false)
    val autoModeEnabled: StateFlow<Boolean> = _autoModeEnabled.asStateFlow()

    private val _autoCountdownSec = MutableStateFlow(300)
    val autoCountdownSec: StateFlow<Int> = _autoCountdownSec.asStateFlow()

    private val _autoNextRunIst = MutableStateFlow("--")
    private val _autoLastRunIst = MutableStateFlow<String?>(null)
    private val _autoCyclesCompleted = MutableStateFlow(0)
    private val _autoStopReason = MutableStateFlow<String?>(null)

    private val _autoActivityLog = MutableStateFlow<List<AutoActivityEvent>>(emptyList())
    val autoActivityLog: StateFlow<List<AutoActivityEvent>> = _autoActivityLog.asStateFlow()

    val autoModeStatus: StateFlow<AutoModeStatus> = combine(
        _autoModeEnabled,
        _cycleRunning,
        config,
        _autoCountdownSec,
        combine(_autoNextRunIst, _autoLastRunIst, _autoCyclesCompleted, _autoStopReason) { next, last, done, reason ->
            listOf(next, last, done, reason)
        }
    ) { enabled, running, cfg, remSec, meta ->
        val intervalMin = cfg.autoIntervalMinutes.coerceAtLeast(1)
        AutoModeStatus(
            enabled = enabled,
            cycleRunning = running,
            intervalMinutes = intervalMin,
            countdownSec = remSec,
            nextRunIst = if (enabled) meta[0] as String else "--",
            lastRunIst = meta[1] as String?,
            cyclesCompleted = meta[2] as Int,
            autoStopReason = meta[3] as String?
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AutoModeStatus()
    )

    val tradesTodayCount: StateFlow<Int> = combine(config, portfolio, brokerRecords) { cfg, paperPort, recs ->
        countTodayTrades(cfg, paperPort, recs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var autoLoopJob: Job? = null
    private var cycleJob: Job? = null
    private var isBootPreviewCycle = false
    private var autoCycleSymbolOverride: String? = null

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
            val cfg = repo.configFlow.first()
            _orMinutes.value = cfg.orbDurationMinutes
            _orbQuickMinutes.value = cfg.orbDurationMinutes
            _stopRule.value = cfg.orbStopRule
            _targetRule.value = cfg.orbTargetRule
            _autoCountdownSec.value = cfg.autoIntervalMinutes.coerceAtLeast(1) * 60
            val (_, _, hol) = repo.loadReferenceData(cfg)
            _holidays.value = hol

            // Reactive max daily loss monitor while Auto Mode is running
            launch {
                combine(config, portfolio, livePnlSummary, _autoModeEnabled) { c, p, pnl, autoOn ->
                    Triple(c, p, pnl) to autoOn
                }.collect { (triple, autoOn) ->
                    if (autoOn) {
                        val (c, p, pnl) = triple
                        val lossBreach = checkDailyLossBreach(c, p, pnl)
                        if (lossBreach != null) {
                            triggerDailyLossAutoStop(c, lossBreach)
                        }
                    }
                }
            }

            // Launch independent feeds & computations concurrently off Main thread
            launch { refreshWatchlistSentimentInternal(cfg) }
            launch { runScanInternal(cfg) }
            launch { refreshTopOrbInternal(cfg) }
            launch { refreshMarketHeatmapInternal(cfg) }
            launch { loadIntel("RELIANCE", cfg) }
            launch { runFullPipelineForSymbol("RELIANCE", cfg, advanceToStep = 0) }
            if (cfg.paper) {
                launch { repo.refreshPaperQuotes(repo.portfolioFlow.first(), cfg) }
            } else {
                launch { refreshRealBrokerInternal(cfg) }
            }

            val savedCycles = repo.agentCyclesFlow.first()
            if (savedCycles.isNotEmpty()) {
                val latest = savedCycles.first()
                _lastCycleSnapshot.value = latest
                _agentLogs.value = latest.messages
                _agentStages.value = stagesFromSnapshot(latest)
            } else {
                // First launch: run a fast deterministic non-mutating preview cycle so dashboard & logs populate cleanly
                launchCycle(symbolOverride = null, autoBookPaper = false, isPreviewBoot = true)
            }
        }
    }

    private fun stagesFromSnapshot(snapshot: AgentCycleSnapshot): List<AgentStageState> {
        val byRole = snapshot.messages.associateBy { it.agentRole }
        return AgentRole.entries.map { role ->
            val msg = byRole[role]
            if (msg == null) {
                AgentStageState(role, AgentStageStatus.PENDING, "Queued")
            } else {
                val status = when (msg.verdict) {
                    AgentVerdict.INFO, AgentVerdict.PASS -> AgentStageStatus.PASSED
                    AgentVerdict.SELECTED -> AgentStageStatus.PASSED
                    AgentVerdict.FILTERED -> AgentStageStatus.FILTERED
                    AgentVerdict.PAPER_FILLED -> AgentStageStatus.BOOKED
                    AgentVerdict.VETO_BLOCK -> AgentStageStatus.VETOED
                    AgentVerdict.ERROR -> AgentStageStatus.FAILED
                    AgentVerdict.AI_FALLBACK -> AgentStageStatus.PASSED
                }
                val doneSkills = if (status == AgentStageStatus.PASSED ||
                    status == AgentStageStatus.FILTERED ||
                    status == AgentStageStatus.BOOKED
                ) {
                    role.skills
                } else {
                    emptyList()
                }
                AgentStageState(
                    role = role,
                    status = status,
                    note = msg.symbol ?: msg.verdict.label,
                    activeSkill = null,
                    completedSkills = doneSkills
                )
            }
        }
    }

    fun runAgentCycle(symbolOverride: String? = null, autoBookPaper: Boolean = true) {
        if (_cycleRunning.value && !isBootPreviewCycle) return
        if (isBootPreviewCycle) {
            cycleJob?.cancel()
            isBootPreviewCycle = false
            _cycleRunning.value = false
        }
        launchCycle(symbolOverride = symbolOverride, autoBookPaper = autoBookPaper, isPreviewBoot = false)
    }

    private fun launchCycle(
        symbolOverride: String?,
        autoBookPaper: Boolean,
        isPreviewBoot: Boolean
    ) {
        cycleJob?.cancel()
        isBootPreviewCycle = isPreviewBoot
        cycleJob = viewModelScope.launch {
            executeCycleInternal(
                symbolOverride = symbolOverride,
                autoBookPaper = autoBookPaper,
                isPreviewBoot = isPreviewBoot,
                isAutoScheduled = false,
                useAi = !isPreviewBoot
            )
        }
    }

    private suspend fun executeCycleInternal(
        symbolOverride: String?,
        autoBookPaper: Boolean,
        isPreviewBoot: Boolean,
        isAutoScheduled: Boolean,
        useAi: Boolean
    ): AgentCycleSnapshot? {
        _cycleRunning.value = true
        _agentLogs.value = emptyList()
        val cfg = repo.configFlow.first()
        var completedSnap: AgentCycleSnapshot? = null
        try {
            val res = runCatching {
                orchestrator.runFullCycle(
                    config = cfg,
                    watchlist = _watchlist.value,
                    symbolOverride = symbolOverride,
                    autoBookPaper = autoBookPaper,
                    useAi = useAi,
                    onStageUpdate = { _agentStages.value = it },
                    onLogMessage = { msg -> _agentLogs.update { it + msg } }
                )
            }
            res.onSuccess { snap ->
                completedSnap = snap
                _lastCycleSnapshot.value = snap
                val nowStr = IndianMarketUtils.formatIstTime(
                    IndianMarketUtils.nowIst(cfg.dataMode == DataMode.LIVE_PUBLIC, cfg.replayIsoIst)
                )
                if (!isPreviewBoot) {
                    _autoLastRunIst.value = "$nowStr IST"
                    _statusMessage.value = snap.outcome
                    if (isAutoScheduled) {
                        _autoCyclesCompleted.update { it + 1 }
                    }
                    val isVeto = snap.outcome.contains("VETOED", ignoreCase = true)
                    appendAutoLog(
                        cfg = cfg,
                        title = if (isAutoScheduled) "Auto Cycle #${_autoCyclesCompleted.value} Complete" else "Manual Cycle Complete",
                        detail = snap.outcome,
                        isWarning = isVeto || !snap.succeeded
                    )
                }
                if (cfg.paper) {
                    repo.refreshPaperQuotes(repo.portfolioFlow.first(), cfg)
                } else {
                    refreshRealBrokerInternal(cfg)
                }
                snap.selectedSymbol?.let { sym ->
                    runFullPipelineForSymbol(sym, cfg, advanceToStep = 0)
                }
            }.onFailure { err ->
                if (err !is kotlinx.coroutines.CancellationException) {
                    _statusMessage.value = "Agent cycle error: ${err.message}"
                    appendAutoLog(
                        cfg = cfg,
                        title = "Agent Cycle Error",
                        detail = err.message ?: "Unexpected failure",
                        isError = true
                    )
                }
            }
        } finally {
            isBootPreviewCycle = false
            _cycleRunning.value = false
        }
        return completedSnap
    }

    private fun checkDailyLossBreach(
        cfg: DeskConfig,
        paperPort: PortfolioState,
        pnl: LivePnlSummary
    ): String? {
        if (cfg.maxDailyLossInr <= 0.0) return null
        val lossInr = if (cfg.paper) {
            val openPaperUnrealized = paperPort.positions.sumOf { (it.lastPrice - it.avgPrice) * it.shares }
            val computedDayPnl = paperPort.realizedPnlToday + openPaperUnrealized
            (-paperPort.realizedPnlToday).coerceAtLeast(-computedDayPnl).coerceAtLeast(-pnl.dayPnl).coerceAtLeast(0.0)
        } else {
            val openRealUnrealized = _realPositions.value.sumOf { (it.lastPrice - it.avgPrice) * it.shares }
            (-pnl.dayPnl).coerceAtLeast(-openRealUnrealized).coerceAtLeast(0.0)
        }
        return if (lossInr >= cfg.maxDailyLossInr - 0.01) {
            "Daily loss ${IndianMarketUtils.inr(lossInr)} reached cap ${IndianMarketUtils.inr(cfg.maxDailyLossInr)}"
        } else {
            null
        }
    }

    private fun countTodayTrades(cfg: DeskConfig, paperPort: PortfolioState, records: List<BrokerRecord>): Int {
        if (cfg.paper) return paperPort.tradesToday
        val today = IndianMarketUtils.nowIst(cfg.dataMode == DataMode.LIVE_PUBLIC, cfg.replayIsoIst)
            .toLocalDate()
            .toString()
        return records.count { !it.paper && it.date == today && it.side.equals("BUY", ignoreCase = true) }
    }

    private fun computeNextRunIstLabel(cfg: DeskConfig, delaySec: Int): String {
        val now = IndianMarketUtils.nowIst(cfg.dataMode == DataMode.LIVE_PUBLIC, cfg.replayIsoIst)
        val next = now.plusSeconds(delaySec.toLong())
        return "${"%02d:%02d".format(next.hour, next.minute)} IST"
    }

    private fun appendAutoLog(
        cfg: DeskConfig,
        title: String,
        detail: String,
        isWarning: Boolean = false,
        isError: Boolean = false
    ) {
        val now = IndianMarketUtils.nowIst(cfg.dataMode == DataMode.LIVE_PUBLIC, cfg.replayIsoIst)
        val ts = "%02d:%02d:%02d".format(now.hour, now.minute, now.second)
        val event = AutoActivityEvent(
            id = "AUTO-${System.nanoTime()}",
            timestampIst = ts,
            title = title,
            detail = detail,
            isWarning = isWarning,
            isError = isError
        )
        _autoActivityLog.update { (listOf(event) + it).take(40) }
    }

    private fun triggerDailyLossAutoStop(cfg: DeskConfig, reason: String) {
        autoLoopJob?.cancel()
        cycleJob?.cancel()
        _cycleRunning.value = false
        _autoModeEnabled.value = false
        _autoNextRunIst.value = "--"
        _autoCountdownSec.value = cfg.autoIntervalMinutes.coerceAtLeast(1) * 60
        val msg = "AUTO-STOPPED: $reason"
        _autoStopReason.value = msg
        _statusMessage.value = msg
        appendAutoLog(cfg, title = "Max Daily Loss Auto-Stop", detail = msg, isError = true)
    }

    fun startAutoMode(symbolOverride: String? = null, useAi: Boolean = true) {
        if (_autoModeEnabled.value) return
        autoCycleSymbolOverride = symbolOverride
        viewModelScope.launch {
            val cfg = repo.configFlow.first()
            if (!cfg.paper && !repo.getCredentials(cfg.selectedBroker).hasUsableAuth()) {
                val warn = "Save ${cfg.selectedBroker.displayName} credentials in Broker Settings before enabling Real Auto Mode."
                _autoStopReason.value = warn
                _statusMessage.value = warn
                appendAutoLog(cfg, title = "Auto Start Blocked", detail = warn, isError = true)
                return@launch
            }
            val paperPort = repo.portfolioFlow.first()
            val lossBreach = checkDailyLossBreach(cfg, paperPort, livePnlSummary.value)
            if (lossBreach != null) {
                val warn = "Cannot start Auto Mode: $lossBreach"
                _autoStopReason.value = warn
                _statusMessage.value = warn
                appendAutoLog(cfg, title = "Auto Start Blocked (Loss Limit)", detail = warn, isError = true)
                return@launch
            }

            if (isBootPreviewCycle) {
                cycleJob?.cancel()
                isBootPreviewCycle = false
                _cycleRunning.value = false
            }

            _autoStopReason.value = null
            _autoModeEnabled.value = true
            val intervalMin = cfg.autoIntervalMinutes.coerceAtLeast(1)
            val intervalSec = intervalMin * 60
            _autoCountdownSec.value = intervalSec
            _autoNextRunIst.value = computeNextRunIstLabel(cfg, intervalSec)

            val modeLabel = if (cfg.paper) "PAPER MODE" else "REAL MODE • ${cfg.selectedBroker.displayName}"
            appendAutoLog(
                cfg = cfg,
                title = "Auto Mode STARTED ($modeLabel)",
                detail = "Schedule: every $intervalMin min • Max ${cfg.maxTradesPerDay} trades/day • Auto-stop loss ${IndianMarketUtils.inr(cfg.maxDailyLossInr)}",
                isWarning = !cfg.paper
            )
            _statusMessage.value = "Auto Mode ON ($modeLabel): running every $intervalMin min"

            autoLoopJob?.cancel()
            autoLoopJob = viewModelScope.launch {
                while (isActive && _autoModeEnabled.value) {
                    val loopCfg = repo.configFlow.first()
                    val loopPort = repo.portfolioFlow.first()
                    val preBreach = checkDailyLossBreach(loopCfg, loopPort, livePnlSummary.value)
                    if (preBreach != null) {
                        triggerDailyLossAutoStop(loopCfg, preBreach)
                        return@launch
                    }

                    // Run one full 7-agent pipeline cycle (Scanner -> News/Analyst -> Entry -> TimeMachine -> Risk -> Broker -> Alerts)
                    if (!_cycleRunning.value) {
                        executeCycleInternal(
                            symbolOverride = autoCycleSymbolOverride,
                            autoBookPaper = true,
                            isPreviewBoot = false,
                            isAutoScheduled = true,
                            useAi = useAi
                        )
                    }

                    // Re-check max daily loss immediately after cycle completes
                    val postCfg = repo.configFlow.first()
                    val postPort = repo.portfolioFlow.first()
                    val postRecords = repo.brokerRecordsFlow.first()
                    val livePostPnl = if (postCfg.paper) {
                        BudgetRiskBrokerEngine.computeLivePnlSummary(
                            portfolio = postPort,
                            isRealMode = false,
                            modeLabel = "Paper Simulation",
                            nowIst = IndianMarketUtils.nowIst(postCfg.dataMode == DataMode.LIVE_PUBLIC, postCfg.replayIsoIst)
                        )
                    } else {
                        livePnlSummary.value
                    }
                    val postBreach = checkDailyLossBreach(postCfg, postPort, livePostPnl)
                    if (postBreach != null) {
                        triggerDailyLossAutoStop(postCfg, postBreach)
                        return@launch
                    }
                    val tradesDone = countTodayTrades(postCfg, postPort, postRecords)
                    if (tradesDone >= postCfg.maxTradesPerDay) {
                        appendAutoLog(
                            cfg = postCfg,
                            title = "Daily Trade Cap Reached ($tradesDone/${postCfg.maxTradesPerDay})",
                            detail = "Risk Agent will block new BUY entries until next session.",
                            isWarning = true
                        )
                    }

                    val totalSec = postCfg.autoIntervalMinutes.coerceAtLeast(1) * 60
                    _autoNextRunIst.value = computeNextRunIstLabel(postCfg, totalSec)
                    for (rem in totalSec downTo 1) {
                        if (!isActive || !_autoModeEnabled.value) return@launch
                        _autoCountdownSec.value = rem
                        delay(autoTickDelayMs)
                    }
                }
            }
        }
    }

    fun stopAutoMode(reason: String? = null) {
        val wasOn = _autoModeEnabled.value
        autoLoopJob?.cancel()
        _autoModeEnabled.value = false
        _autoNextRunIst.value = "--"
        viewModelScope.launch {
            val cfg = repo.configFlow.first()
            _autoCountdownSec.value = cfg.autoIntervalMinutes.coerceAtLeast(1) * 60
            if (!reason.isNullOrBlank()) {
                _autoStopReason.value = reason
                _statusMessage.value = reason
                appendAutoLog(cfg, title = "Auto Mode Stopped", detail = reason, isWarning = true)
            } else if (wasOn) {
                _statusMessage.value = "Auto Mode STOPPED (Manual Mode active)"
                appendAutoLog(cfg, title = "Auto Mode STOPPED", detail = "Switched back to Manual Mode.")
            }
        }
    }

    fun emergencyStopAuto() {
        autoLoopJob?.cancel()
        cycleJob?.cancel()
        isBootPreviewCycle = false
        _cycleRunning.value = false
        _autoModeEnabled.value = false
        _autoNextRunIst.value = "--"
        _agentStages.update { list ->
            list.map { st ->
                if (st.status == AgentStageStatus.RUNNING) {
                    st.copy(status = AgentStageStatus.FAILED, note = "Emergency stopped", activeSkill = null)
                } else st
            }
        }
        val cfg = config.value
        _autoCountdownSec.value = cfg.autoIntervalMinutes.coerceAtLeast(1) * 60
        val msg = "EMERGENCY STOP activated — Auto Mode & active cycle halted."
        _autoStopReason.value = msg
        _statusMessage.value = msg
        appendAutoLog(cfg, title = "EMERGENCY STOP", detail = msg, isError = true)
    }

    fun toggleAutoMode(enabled: Boolean, symbolOverride: String? = null) {
        if (enabled) startAutoMode(symbolOverride) else stopAutoMode()
    }

    fun setAutoIntervalMinutes(minutes: Int) {
        val clean = minutes.coerceIn(5, 60)
        viewModelScope.launch {
            val cur = repo.configFlow.first()
            val updated = cur.copy(autoIntervalMinutes = clean)
            repo.saveConfig(updated)
            val newSec = clean * 60
            _autoCountdownSec.value = newSec
            if (_autoModeEnabled.value) {
                _autoNextRunIst.value = computeNextRunIstLabel(updated, newSec)
                // Restart timer cleanly with updated interval without losing enabled state
                val sym = autoCycleSymbolOverride
                autoLoopJob?.cancel()
                autoLoopJob = viewModelScope.launch {
                    while (isActive && _autoModeEnabled.value) {
                        for (rem in newSec downTo 1) {
                            if (!isActive || !_autoModeEnabled.value) return@launch
                            _autoCountdownSec.value = rem
                            delay(autoTickDelayMs)
                        }
                        if (!_cycleRunning.value) {
                            executeCycleInternal(
                                symbolOverride = sym,
                                autoBookPaper = true,
                                isPreviewBoot = false,
                                isAutoScheduled = true,
                                useAi = true
                            )
                        }
                        val postCfg = repo.configFlow.first()
                        val breach = checkDailyLossBreach(postCfg, repo.portfolioFlow.first(), livePnlSummary.value)
                        if (breach != null) {
                            triggerDailyLossAutoStop(postCfg, breach)
                            return@launch
                        }
                        _autoNextRunIst.value = computeNextRunIstLabel(postCfg, postCfg.autoIntervalMinutes * 60)
                    }
                }
            }
            val label = AutoIntervalOption.fromMinutes(clean).label
            appendAutoLog(updated, title = "Auto Interval Updated", detail = "Cycle interval set to $label")
            _statusMessage.value = "Auto cycle interval set to $label"
        }
    }

    fun updateAutoSafetyLimits(maxTradesPerDay: Int, maxDailyLossInr: Double) {
        val cleanTrades = maxTradesPerDay.coerceIn(1, 50)
        val cleanLoss = maxDailyLossInr.coerceAtLeast(100.0)
        viewModelScope.launch {
            val cur = repo.configFlow.first()
            val updated = cur.copy(
                maxTradesPerDay = cleanTrades,
                maxDailyLossInr = cleanLoss
            )
            repo.saveConfig(updated)
            appendAutoLog(
                updated,
                title = "Auto Safety Limits Updated",
                detail = "Max $cleanTrades trades/day · Auto-stop loss ${IndianMarketUtils.inr(cleanLoss)}"
            )
            _statusMessage.value = "Updated Auto Limits: max $cleanTrades trades/day, stop loss ${IndianMarketUtils.inr(cleanLoss)}"
            if (_autoModeEnabled.value) {
                val breach = checkDailyLossBreach(updated, repo.portfolioFlow.first(), livePnlSummary.value)
                if (breach != null) {
                    triggerDailyLossAutoStop(updated, breach)
                }
            }
        }
    }

    fun clearAgentLogs() {
        viewModelScope.launch {
            repo.clearAgentCycles()
            _agentLogs.value = emptyList()
            _autoActivityLog.value = emptyList()
            _lastCycleSnapshot.value = null
            _agentStages.value = AgentRole.entries.map { AgentStageState(it) }
            _statusMessage.value = "Agent cycle history cleared"
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
        runScan()
    }

    fun setSetupFilter(filter: String) {
        _setupFilter.value = filter
        runScan()
    }

    fun setOrMinutes(minutes: Int) {
        _orMinutes.value = minutes
        runScan()
    }

    fun setIncludeT2t(include: Boolean) {
        _includeT2t.value = include
        runScan()
    }

    fun addWatchlistSymbol(raw: String) {
        runCatching { IndianMarketUtils.parseSymbol(raw).first }.onSuccess { sym ->
            if (sym !in _watchlist.value) {
                _watchlist.value = _watchlist.value + sym
                if (_scanMode.value == ScanMode.WATCHLIST) runScan()
                refreshWatchlistSentiment()
                if (_heatmapUniverse.value == HeatmapUniverse.WATCHLIST) refreshMarketHeatmap()
            }
        }.onFailure { _statusMessage.value = it.message }
    }

    fun removeWatchlistSymbol(sym: String) {
        _watchlist.value = _watchlist.value - sym
        if (_scanMode.value == ScanMode.WATCHLIST) runScan()
        refreshWatchlistSentiment()
        if (_heatmapUniverse.value == HeatmapUniverse.WATCHLIST) refreshMarketHeatmap()
    }

    fun refreshWatchlistSentiment() {
        viewModelScope.launch {
            refreshWatchlistSentimentInternal(repo.configFlow.first())
        }
    }

    private suspend fun refreshWatchlistSentimentInternal(cfg: DeskConfig) {
        _sentimentLoading.value = true
        runCatching { repo.fetchWatchlistSentiment(_watchlist.value, cfg) }
            .onSuccess { _watchlistSentiment.value = it }
            .onFailure { _statusMessage.value = "Sentiment refresh error: ${it.message}" }
        _sentimentLoading.value = false
    }

    fun runScan() {
        viewModelScope.launch {
            runScanInternal(repo.configFlow.first())
        }
    }

    private suspend fun runScanInternal(cfg: DeskConfig) {
        _scanLoading.value = true
        runCatching {
            repo.runScan(
                mode = _scanMode.value,
                setupFilter = _setupFilter.value,
                watchlist = _watchlist.value,
                orMinutes = _orMinutes.value,
                minVolumeX = cfg.orbMinVolumeX,
                includeT2t = _includeT2t.value,
                config = cfg
            )
        }.onSuccess { hits ->
            _scanHits.value = hits
        }.onFailure { err ->
            _statusMessage.value = "Scan error: ${err.message}"
        }
        _scanLoading.value = false
    }

    fun setOrbExchange(exchange: String) {
        val clean = if (exchange.uppercase() == "BSE") "BSE" else "NSE"
        if (_orbExchange.value != clean) {
            _orbExchange.value = clean
            refreshTopOrbScanner()
            refreshMarketHeatmap()
        }
    }

    fun setOrbQuickMinutes(minutes: Int) {
        val clean = if (minutes in listOf(5, 10, 15, 30)) minutes else 15
        if (_orbQuickMinutes.value != clean) {
            _orbQuickMinutes.value = clean
            refreshTopOrbScanner()
            refreshMarketHeatmap()
        }
    }

    fun refreshTopOrbScanner() {
        viewModelScope.launch {
            refreshTopOrbInternal(repo.configFlow.first())
        }
    }

    private suspend fun refreshTopOrbInternal(cfg: DeskConfig) {
        _topOrbLoading.value = true
        runCatching {
            repo.fetchTopOrbByRelativeVolume(
                exchange = _orbExchange.value,
                orMinutes = _orbQuickMinutes.value,
                includeT2t = _includeT2t.value,
                config = cfg,
                limit = 5
            )
        }.onSuccess { rows ->
            _topOrbRows.value = rows
        }.onFailure { err ->
            _statusMessage.value = "ORB Top-5 error: ${err.message}"
        }
        _topOrbLoading.value = false
    }

    fun setHeatmapUniverse(universe: HeatmapUniverse) {
        if (_heatmapUniverse.value != universe) {
            _heatmapUniverse.value = universe
            refreshMarketHeatmap()
        }
    }

    fun setHeatmapMetric(metric: HeatmapIntensityMetric) {
        if (_heatmapMetric.value != metric) {
            _heatmapMetric.value = metric
            refreshMarketHeatmap()
        }
    }

    fun refreshMarketHeatmap() {
        viewModelScope.launch {
            refreshMarketHeatmapInternal(repo.configFlow.first())
        }
    }

    private suspend fun refreshMarketHeatmapInternal(cfg: DeskConfig) {
        _heatmapLoading.value = true
        runCatching {
            repo.fetchMarketHeatmap(
                universe = _heatmapUniverse.value,
                metric = _heatmapMetric.value,
                watchlist = _watchlist.value,
                exchange = _orbExchange.value,
                orMinutes = cfg.orbDurationMinutes,
                includeT2t = _includeT2t.value,
                config = cfg
            )
        }.onSuccess { cells ->
            _heatmapCells.value = cells
        }.onFailure { err ->
            _statusMessage.value = "Heatmap error: ${err.message}"
        }
        _heatmapLoading.value = false
    }

    fun updateOrbStrategyConfig(
        orbDurationMinutes: Int,
        orbMinVolumeX: Double,
        orbStopRule: StopRuleType,
        orbTargetRule: TargetRuleType,
        minRewardRisk: Double,
        noEntriesBefore: String
    ) {
        viewModelScope.launch {
            val cur = repo.configFlow.first()
            val cleanDuration = if (orbDurationMinutes in listOf(5, 10, 15, 30)) orbDurationMinutes else cur.orbDurationMinutes
            val updated = cur.copy(
                orbDurationMinutes = cleanDuration,
                orbMinVolumeX = orbMinVolumeX.coerceIn(0.5, 5.0),
                orbStopRule = orbStopRule,
                orbTargetRule = orbTargetRule,
                minRewardRisk = minRewardRisk.coerceIn(0.5, 5.0),
                noEntriesBefore = noEntriesBefore.trim().ifBlank { cur.noEntriesBefore }
            )
            repo.saveConfig(updated)
            _orMinutes.value = updated.orbDurationMinutes
            _orbQuickMinutes.value = updated.orbDurationMinutes
            _stopRule.value = updated.orbStopRule
            _targetRule.value = updated.orbTargetRule

            launch { runScanInternal(updated) }
            launch { refreshTopOrbInternal(updated) }
            launch { refreshMarketHeatmapInternal(updated) }
            launch { runFullPipelineForSymbol(_tradeSymbol.value, updated, advanceToStep = _activeStep.value) }

            _statusMessage.value =
                "ORB ${updated.orbDurationMinutes}m • Vol ≥${updated.orbMinVolumeX}x • ${updated.orbStopRule.label} • Target ${updated.orbTargetRule.label} (Risk Min ${updated.minRewardRisk}R)"
        }
    }

    // ---------------------------------------------------------------- Trade Flow Pipeline

    fun setTradeSide(side: String) {
        val clean = if (side.equals("SELL", true)) "SELL" else "BUY"
        if (_tradeSide.value != clean) {
            _tradeSide.value = clean
            viewModelScope.launch {
                val plan = _tradePlan.value ?: return@launch
                runSizingAndRisk(plan, repo.configFlow.first(), null)
            }
        }
    }

    fun buildBrokerPreviewPayload(brokerId: IndianBrokerId, order: OrderProposal): String =
        repo.buildBrokerPreviewPayload(brokerId, order)

    fun startTradeForSymbol(symbol: String, side: String = "BUY", advanceToStep: Int = 0) {
        _tradeSymbol.value = symbol.uppercase()
        _tradeSide.value = if (side.equals("SELL", true)) "SELL" else "BUY"
        viewModelScope.launch {
            runFullPipelineForSymbol(symbol, repo.configFlow.first(), advanceToStep = advanceToStep)
        }
    }

    fun setTradeStep(step: Int) {
        _activeStep.value = step.coerceIn(0, 5)
    }

    fun evaluateEntry(
        rawSymbol: String,
        setup: String = "orb",
        levelPrice: Double? = null,
        orMinutes: Int = 15,
        minVolumeX: Double = 1.0,
        interval: String = "5m"
    ) {
        viewModelScope.launch {
            _tradeFlowLoading.value = true
            val cfg = repo.configFlow.first()
            runCatching {
                repo.evaluateEntry(rawSymbol, setup, levelPrice, orMinutes, minVolumeX, interval, cfg)
            }.onSuccess { res ->
                _tradeSymbol.value = res.symbol
                _entryResult.value = res
                _tradePlan.value = null
                _timeMachineResult.value = null
                _orderProposal.value = null
                _riskVerdict.value = null
                rebuildExitAndSizing(cfg)
            }.onFailure { _statusMessage.value = it.message }
            _tradeFlowLoading.value = false
        }
    }

    fun updateExitParams(
        stopRule: StopRuleType = _stopRule.value,
        stopParam: Double = _stopParam.value,
        targetRule: TargetRuleType = _targetRule.value,
        targetParam: Double = _targetParam.value
    ) {
        _stopRule.value = stopRule
        _stopParam.value = stopParam
        _targetRule.value = targetRule
        _targetParam.value = targetParam
        viewModelScope.launch {
            rebuildExitAndSizing(repo.configFlow.first())
        }
    }

    private suspend fun rebuildExitAndSizing(cfg: DeskConfig) {
        val entry = _entryResult.value ?: return
        val planRes = repo.buildTradePlan(
            entry = entry,
            stopRule = _stopRule.value,
            stopParam = _stopParam.value,
            targetRule = _targetRule.value,
            targetPriceParam = _targetParam.value,
            config = cfg
        )
        planRes.onSuccess { plan ->
            _tradePlan.value = plan
            runSizingAndRisk(plan, cfg)
        }.onFailure {
            _tradePlan.value = null
            _orderProposal.value = null
            _riskVerdict.value = null
            _statusMessage.value = it.message
        }
    }

    fun runTimeMachine(replays: Int = 10000, seed: Int = 19, window: Int = 2) {
        val plan = _tradePlan.value ?: return
        viewModelScope.launch {
            _tradeFlowLoading.value = true
            val cfg = repo.configFlow.first()
            val qty = _orderProposal.value?.quantity ?: 30
            repo.runTimeMachine(plan, cfg, replays = replays, seed = seed, window = window, qtyForNet = qty)
                .onSuccess { _timeMachineResult.value = it }
                .onFailure { _statusMessage.value = it.message }
            _tradeFlowLoading.value = false
        }
    }

    fun setOrderType(type: String, limitPrice: Double? = null) {
        _orderType.value = type
        viewModelScope.launch {
            val plan = _tradePlan.value ?: return@launch
            runSizingAndRisk(plan, repo.configFlow.first(), limitPrice)
        }
    }

    private suspend fun activePortfolioForConfig(cfg: DeskConfig): PortfolioState {
        if (cfg.paper) return repo.portfolioFlow.first()
        val now = IndianMarketUtils.nowIst(cfg.dataMode == DataMode.LIVE_PUBLIC, cfg.replayIsoIst)
        if (_realFunds.value == null && repo.getCredentials(cfg.selectedBroker).hasUsableAuth()) {
            refreshRealBrokerInternal(cfg)
        }
        val todayStr = now.toLocalDate().toString()
        val todayReal = repo.brokerRecordsFlow.first().filter { !it.paper && it.date == todayStr }
        return PortfolioState(
            cash = _realFunds.value?.availableCash ?: 0.0,
            realizedPnlToday = 0.0,
            tradesToday = todayReal.count { it.side.equals("BUY", ignoreCase = true) },
            ordersToday = todayReal.size,
            lastDay = todayStr,
            positions = _realPositions.value
        )
    }

    private suspend fun runSizingAndRisk(plan: TradePlan, cfg: DeskConfig, limitPrice: Double? = null) {
        val port = activePortfolioForConfig(cfg)
        repo.sizeOrder(plan, cfg, port, _orderType.value, limitPrice, side = _tradeSide.value)
            .onSuccess { prop ->
                _orderProposal.value = prop
                _riskVerdict.value = repo.checkOrderRisk(prop, cfg, port)
            }
            .onFailure {
                _orderProposal.value = null
                _riskVerdict.value = null
                _statusMessage.value = it.message
            }
    }

    fun recheckRisk() {
        val prop = _orderProposal.value ?: return
        viewModelScope.launch {
            val cfg = repo.configFlow.first()
            val port = activePortfolioForConfig(cfg)
            _riskVerdict.value = repo.checkOrderRisk(prop, cfg, port)
        }
    }

    fun confirmBrokerTicket(reply: String) {
        val prop = _orderProposal.value ?: return
        val verdict = _riskVerdict.value ?: return
        viewModelScope.launch {
            val cfg = repo.configFlow.first()
            val port = activePortfolioForConfig(cfg)
            repo.confirmAndExecuteOrder(prop, port, verdict, reply, cfg)
                .onSuccess { msg ->
                    _statusMessage.value = if (cfg.paper) "PAPER FILL RECORDED: $msg" else msg
                    refreshLivePortfolioAndPnl()
                    recheckRisk()
                }
                .onFailure { err ->
                    _statusMessage.value = "Execution rejected: ${err.message}"
                }
        }
    }

    private suspend fun runFullPipelineForSymbol(symbol: String, cfg: DeskConfig, advanceToStep: Int) {
        _tradeFlowLoading.value = true
        runCatching {
            val entry = repo.evaluateEntry(
                rawSymbol = symbol,
                setup = "orb",
                levelPrice = null,
                orMinutes = cfg.orbDurationMinutes,
                minVolumeX = cfg.orbMinVolumeX,
                interval = "5m",
                config = cfg
            )
            _entryResult.value = entry
            val plan = repo.buildTradePlan(entry, _stopRule.value, _stopParam.value, _targetRule.value, _targetParam.value, cfg).getOrNull()
            _tradePlan.value = plan
            if (plan != null) {
                val port = activePortfolioForConfig(cfg)
                val order = repo.sizeOrder(plan, cfg, port, _orderType.value, null, side = _tradeSide.value).getOrNull()
                _orderProposal.value = order
                if (order != null) {
                    _riskVerdict.value = repo.checkOrderRisk(order, cfg, port)
                }
                val tm = repo.runTimeMachine(plan, cfg, replays = 10000, seed = 19, window = 2, qtyForNet = order?.quantity ?: 30).getOrNull()
                _timeMachineResult.value = tm
            }
        }
        _activeStep.value = advanceToStep
        _tradeFlowLoading.value = false
    }

    // ---------------------------------------------------------------- Portfolio, Multi-Broker & Alerts

    fun setTradingMode(paper: Boolean) {
        viewModelScope.launch {
            val cur = repo.configFlow.first()
            if (cur.paper == paper) return@launch
            if (!paper && _autoModeEnabled.value) {
                autoLoopJob?.cancel()
                _autoModeEnabled.value = false
                _autoNextRunIst.value = "--"
                val pauseMsg = "Auto Mode paused automatically when switching to Real Trading. Confirm Real+Auto explicitly if intended."
                _autoStopReason.value = pauseMsg
                appendAutoLog(cur, title = "Auto Mode Paused on Real Switch", detail = pauseMsg, isWarning = true)
            }
            val updated = cur.copy(paper = paper)
            repo.saveConfig(updated)
            if (paper) {
                launch { repo.refreshPaperQuotes(repo.portfolioFlow.first(), updated) }
                _statusMessage.value = "Paper Trading Mode ON (Default isolated simulation — no broker keys needed)"
            } else {
                refreshRealBrokerInternal(updated)
                _statusMessage.value = "WARNING: REAL TRADING MODE ON (${updated.selectedBroker.displayName}) — Real money at risk!"
            }
            launch { runFullPipelineForSymbol(_tradeSymbol.value, updated, advanceToStep = _activeStep.value) }
        }
    }

    fun selectBroker(brokerId: IndianBrokerId) {
        viewModelScope.launch {
            val cur = repo.configFlow.first()
            val updated = cur.copy(selectedBroker = brokerId, broker = brokerId.code)
            repo.saveConfig(updated)
            _brokerTestMessage.value = null
            _realFunds.value = null
            _realPositions.value = emptyList()
            _realOrders.value = emptyList()
            if (!updated.paper) {
                refreshRealBrokerInternal(updated)
            }
            _statusMessage.value = "Selected broker: ${brokerId.displayName}"
        }
    }

    fun saveBrokerCredentials(creds: BrokerCredentials) {
        viewModelScope.launch {
            repo.saveCredentials(creds)
            _brokerTestMessage.value = null
            _statusMessage.value = "${creds.brokerId.displayName} credentials saved securely"
            val cfg = repo.configFlow.first()
            if (!cfg.paper && cfg.selectedBroker == creds.brokerId) {
                refreshRealBrokerInternal(cfg)
            }
        }
    }

    fun clearBrokerCredentials(brokerId: IndianBrokerId) {
        viewModelScope.launch {
            repo.clearCredentials(brokerId)
            val cfg = repo.configFlow.first()
            if (cfg.selectedBroker == brokerId) {
                _realFunds.value = null
                _realPositions.value = emptyList()
                _realOrders.value = emptyList()
            }
            _brokerTestMessage.value = "Cleared saved credentials for ${brokerId.displayName}"
            _statusMessage.value = "Cleared ${brokerId.displayName} credentials"
        }
    }

    fun testBrokerConnection(creds: BrokerCredentials) {
        viewModelScope.launch {
            _realBrokerLoading.value = true
            _brokerTestMessage.value = null
            repo.testBrokerConnection(creds)
                .onSuccess { funds ->
                    _realFunds.value = funds
                    _brokerTestMessage.value =
                        "Connected to ${creds.brokerId.displayName}: Available Cash ${IndianMarketUtils.inr(funds.availableCash)} · Net ${IndianMarketUtils.inr(funds.netBalance)}"
                }
                .onFailure { err ->
                    _brokerTestMessage.value = "Connection check failed: ${err.message}"
                }
            _realBrokerLoading.value = false
        }
    }

    fun refreshLivePortfolioAndPnl() {
        viewModelScope.launch {
            val cfg = repo.configFlow.first()
            if (cfg.paper) {
                repo.refreshPaperQuotes(repo.portfolioFlow.first(), cfg)
                _statusMessage.value = "Refreshed Paper portfolio quotes & live P&L"
            } else {
                refreshRealBrokerInternal(cfg)
            }
        }
    }

    private suspend fun refreshRealBrokerInternal(cfg: DeskConfig) {
        val brokerId = cfg.selectedBroker
        val creds = repo.getCredentials(brokerId)
        if (!creds.hasUsableAuth()) {
            _realFunds.value = null
            _realPositions.value = emptyList()
            _realOrders.value = emptyList()
            return
        }
        _realBrokerLoading.value = true
        withContext(Dispatchers.IO) {
            val fundsDef = async { repo.fetchRealFunds(brokerId) }
            val posDef = async { repo.fetchRealPositions(brokerId) }
            val ordDef = async { repo.fetchRealOrders(brokerId) }
            fundsDef.await().onSuccess { _realFunds.value = it }
            posDef.await().onSuccess { _realPositions.value = it }
            ordDef.await().onSuccess { _realOrders.value = it }
        }
        _realBrokerLoading.value = false
    }

    fun flattenPosition(pos: PortfolioPosition) {
        viewModelScope.launch {
            val cfg = repo.configFlow.first()
            val port = activePortfolioForConfig(cfg)
            runCatching { repo.flattenPosition(pos, port, cfg) }
                .onSuccess { rec ->
                    _statusMessage.value = "Closed position: ${rec.plainSummary}"
                    refreshLivePortfolioAndPnl()
                }
                .onFailure { err ->
                    _statusMessage.value = err.message ?: "Unable to close position"
                }
        }
    }

    fun computeBasketAllocation(items: List<Pair<String, Double>>) {
        viewModelScope.launch {
            val cfg = repo.configFlow.first()
            val port = repo.portfolioFlow.first()
            val live = cfg.dataMode == DataMode.LIVE_PUBLIC
            val rows = withContext(Dispatchers.Default) {
                val priced = items.mapNotNull { (sym, w) ->
                    val clean = runCatching { IndianMarketUtils.parseSymbol(sym).first }.getOrNull() ?: return@mapNotNull null
                    val q = repo.dataSource.loadQuotePair(clean, cfg.exchange, live)?.first ?: return@mapNotNull null
                    Triple(clean, w, q)
                }
                BudgetRiskBrokerEngine.allocateWeights(priced, cfg, port)
            }
            _allocations.value = rows
        }
    }

    fun addAlertsFromCurrentPlan() {
        val plan = _tradePlan.value ?: run {
            _statusMessage.value = "Create a trade plan in Trade Flow first"
            return
        }
        viewModelScope.launch {
            repo.addAlertsFromPlan(plan)
            _statusMessage.value = "Added target, stop, flat-by, and square-off warning alerts for ${plan.symbol}"
        }
    }

    fun addCustomAlert(symbol: String, type: AlertType, value: Double?, timeHm: String?, label: String) {
        viewModelScope.launch {
            runCatching { repo.addCustomAlert(symbol, type, value, timeHm, label) }
                .onSuccess { _statusMessage.value = "Alert added for ${symbol.uppercase()}" }
                .onFailure { _statusMessage.value = it.message }
        }
    }

    fun deleteAlert(id: String) {
        viewModelScope.launch { repo.deleteAlert(id) }
    }

    fun checkAlertsNow() {
        viewModelScope.launch {
            val summary = repo.checkAlertsNow(repo.configFlow.first())
            _alertSummary.value = summary
            _statusMessage.value = if (summary.triggeredCount > 0) {
                "${summary.triggeredCount} alert(s) triggered!"
            } else {
                "Checked ${summary.alerts.size} alert(s) at ${summary.checkedAtIst}: none triggered"
            }
        }
    }

    fun rearmAlerts() {
        viewModelScope.launch {
            repo.rearmAllAlerts()
            _statusMessage.value = "All alert rungs re-armed"
        }
    }

    // ---------------------------------------------------------------- Intel (News, Analyst, Insiders)

    fun loadIntelForSymbol(rawSymbol: String) {
        viewModelScope.launch {
            loadIntel(rawSymbol, repo.configFlow.first())
        }
    }

    fun setAnalystBasis(basis: String) {
        _analystBasis.value = basis
        loadIntelForSymbol(_intelSymbol.value)
    }

    private suspend fun loadIntel(rawSymbol: String, cfg: DeskConfig) {
        _intelLoading.value = true
        val clean = runCatching { IndianMarketUtils.parseSymbol(rawSymbol).first }.getOrElse { rawSymbol.uppercase() }
        _intelSymbol.value = clean

        runCatching { repo.fetchNews(clean, cfg) }
            .onSuccess { _newsReport.value = it }
            .onFailure { _statusMessage.value = "News error: ${it.message}" }

        repo.fetchAnalyst(clean, _analystBasis.value, cfg)
            .onSuccess { _analystReport.value = it }
            .onFailure { _analystReport.value = null }

        runCatching { repo.fetchInsiders(clean, cfg) }
            .onSuccess { _insidersReport.value = it }

        _intelLoading.value = false
    }

    fun loadAllBulkBlockDeals() {
        viewModelScope.launch {
            _intelLoading.value = true
            runCatching { repo.fetchInsiders(null, repo.configFlow.first()) }
                .onSuccess { _insidersReport.value = it }
            _intelLoading.value = false
        }
    }

    fun addFollowedInvestor(name: String) {
        viewModelScope.launch {
            repo.addInvestor(name)
            loadAllBulkBlockDeals()
        }
    }

    fun removeFollowedInvestor(name: String) {
        viewModelScope.launch {
            repo.removeInvestor(name)
            loadAllBulkBlockDeals()
        }
    }

    // ---------------------------------------------------------------- Config & Settings

    fun saveConfig(updated: DeskConfig) {
        viewModelScope.launch {
            repo.saveConfig(updated)
            _orMinutes.value = updated.orbDurationMinutes
            _orbQuickMinutes.value = updated.orbDurationMinutes
            _stopRule.value = updated.orbStopRule
            _targetRule.value = updated.orbTargetRule
            val (_, _, hol) = repo.loadReferenceData(updated)
            _holidays.value = hol
            launch { refreshWatchlistSentimentInternal(updated) }
            launch { runScanInternal(updated) }
            launch { refreshTopOrbInternal(updated) }
            launch { refreshMarketHeatmapInternal(updated) }
            launch { runFullPipelineForSymbol(_tradeSymbol.value, updated, advanceToStep = _activeStep.value) }
            _statusMessage.value = "Configuration saved (${updated.dataMode.name.replace('_', ' ')})"
        }
    }

    fun toggleDataMode() {
        viewModelScope.launch {
            val cur = repo.configFlow.first()
            val nextMode = if (cur.dataMode == DataMode.OFFLINE_REPLAY) DataMode.LIVE_PUBLIC else DataMode.OFFLINE_REPLAY
            val next = cur.copy(dataMode = nextMode)
            repo.saveConfig(next)
            val (_, _, hol) = repo.loadReferenceData(next)
            _holidays.value = hol
            launch { runScanInternal(next) }
            launch { refreshTopOrbInternal(next) }
            launch { refreshMarketHeatmapInternal(next) }
            launch { refreshWatchlistSentimentInternal(next) }
            _statusMessage.value = if (nextMode == DataMode.LIVE_PUBLIC) {
                "Switched to Live Public Feeds (Yahoo Finance + NSE + Google News)"
            } else {
                "Switched to Offline Replay (Oct 19–23, 2026 NSE Fixtures)"
            }
        }
    }

    fun resetPaperAccount() {
        viewModelScope.launch {
            val cfg = repo.configFlow.first()
            repo.resetPaperAccount(cfg.budget)
            recheckRisk()
            _statusMessage.value = "Paper account reset to ${IndianMarketUtils.inr(cfg.budget)}"
        }
    }
}

class MainViewModelFactory(private val repo: TradingRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(repo) as T
    }
}
