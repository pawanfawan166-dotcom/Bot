package com.example

import androidx.room.Room
import com.example.ai.AdvisorCandidateDecision
import com.example.ai.AdvisorIntelDecision
import com.example.ai.AdvisorNarrative
import com.example.ai.AgentAdvisor
import com.example.data.AppDatabase
import com.example.data.MarketDataSource
import com.example.data.SecureBrokerVault
import com.example.data.TradingRepository
import com.example.data.broker.BrokerAdapterRegistry
import com.example.domain.AgentOrchestratorEngine
import com.example.domain.AlertsNewsAnalystInsidersEngine
import com.example.domain.BudgetRiskBrokerEngine
import com.example.domain.IndianMarketUtils
import com.example.model.ALL_TRADING_SKILLS
import com.example.model.AgentRole
import com.example.model.AgentStageStatus
import com.example.model.AgentVisualState
import com.example.model.BrokerCredentials
import com.example.model.BrokerOrderRequest
import com.example.model.IndianBrokerId
import com.example.model.PortfolioPosition
import com.example.model.toVisualState
import com.example.model.AnalystReport
import com.example.model.DataMode
import com.example.model.DeskConfig
import com.example.model.EntryCheckResult
import com.example.model.EntryStatus
import com.example.model.FilingQuarter
import com.example.model.HeatmapIntensityMetric
import com.example.model.HeatmapUniverse
import com.example.model.InsidersReport
import com.example.model.MarketClockStatus
import com.example.model.NewsItem
import com.example.model.NewsReport
import com.example.model.OrderProposal
import com.example.model.PortfolioState
import com.example.model.RiskVerdict
import com.example.model.ScanHit
import com.example.model.ScanMode
import com.example.model.SentimentVerdict
import com.example.model.StopRuleType
import com.example.model.TargetRuleType
import com.example.model.TimeMachineResult
import com.example.model.TradePlan
import com.example.ui.MainViewModel
import android.os.Looper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDateTime

private class DeterministicFakeAdvisor : AgentAdvisor {
    override suspend fun prioritizeCandidates(
        hits: List<ScanHit>,
        clockStatus: MarketClockStatus
    ): AdvisorCandidateDecision {
        val ranked = listOfNotNull(hits.firstOrNull { it.symbol == "RELIANCE" }?.symbol) +
            hits.map { it.symbol }.filter { it != "RELIANCE" }
        return AdvisorCandidateDecision(
            rankedSymbols = ranked.take(3),
            rationale = "Test advisor selected ${ranked.firstOrNull()}",
            aiSource = "Deterministic Test Advisor"
        )
    }

    override suspend fun evaluateNewsAndAnalyst(
        symbol: String,
        newsReport: NewsReport?,
        analystReport: AnalystReport?,
        insidersReport: InsidersReport?
    ) = AdvisorIntelDecision(
        proceed = true,
        convictionNote = "$symbol cleared test intel gate.",
        riskFlags = emptyList(),
        aiSource = "Deterministic Test Advisor"
    )

    override suspend fun summarizeEntryAndOdds(
        entry: EntryCheckResult,
        plan: TradePlan,
        timeMachine: TimeMachineResult?
    ) = AdvisorNarrative("Plan ${plan.symbol} OK", "Deterministic Test Advisor")

    override suspend fun explainRiskAndVeto(
        order: OrderProposal?,
        verdict: RiskVerdict
    ) = AdvisorNarrative(
        if (verdict.allPassed) "Risk PASS" else "Risk VETO",
        "Deterministic Test Advisor"
    )

    override suspend fun summarizeCycleForAlerts(
        symbol: String,
        paperSummary: String,
        alertsArmedCount: Int,
        verdictPassed: Boolean
    ) = AdvisorNarrative("Booked $symbol with $alertsArmedCount alerts", "Deterministic Test Advisor")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TradeDeskUnitTest {

    @Test
    fun `nse tick grid, zerodha charges, and indian formatting`() {
        assertEquals(0.01, IndianMarketUtils.tickSize(249.99), 1e-6)
        assertEquals(0.05, IndianMarketUtils.tickSize(250.00), 1e-6)
        assertEquals(0.10, IndianMarketUtils.tickSize(1227.80), 1e-6)
        // Downward band crossing re-snaps to finer tick
        assertEquals(249.99, IndianMarketUtils.roundToTick(249.999, "down"), 1e-6)
        assertEquals("₹1,23,456.78", IndianMarketUtils.inr(123456.78))

        val mis = IndianMarketUtils.charges(36834.0, 36834.0, "MIS", "NSE")
        assertTrue(mis.brokerage > 0.0 && mis.total > 0.0)
        val cnc = IndianMarketUtils.charges(36834.0, 36834.0, "CNC", "NSE")
        assertEquals(0.0, cnc.brokerage, 1e-6)
        assertTrue(cnc.stt > mis.stt)

        val cas = IndianMarketUtils.sessionRules(true, "2026-10-19")
        assertEquals("15:15", cas.close)
        assertEquals("15:12", cas.squareOff)
        assertEquals(72, cas.bars5m)

        val nonCas = IndianMarketUtils.sessionRules(false, "2026-10-19")
        assertEquals("15:30", nonCas.close)
        assertEquals(75, nonCas.bars5m)
    }

    @Test
    fun `offline fixtures pipeline, time machine, risk veto, and broker yes-gate`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = TradingRepository(db.deskDao(), MarketDataSource(ctx))
            repo.ensureSeeded()
            val cfg = DeskConfig(dataMode = DataMode.OFFLINE_REPLAY, replayIsoIst = "2026-10-19 10:15")

            // 1. Scanner + Entry on RELIANCE
            val intraday = repo.dataSource.loadIntradaySpark(listOf("RELIANCE"), false)
            val scanHits = repo.runScan(ScanMode.ORB, "all", listOf("RELIANCE"), 15, 1.0, false, cfg)
            assertTrue("Expected offline ORB scan hits (intradayKeys=${intraday.keys.take(5)}, relianceBars=${intraday["RELIANCE"]?.bars?.size})", scanHits.isNotEmpty())

            val entry = repo.evaluateEntry("RELIANCE", "orb", null, 15, 1.0, "5m", cfg)
            assertEquals(EntryStatus.TRIGGERED, entry.status)
            assertEquals(1227.8, entry.entryPrice!!, 0.01)

            // 2. Exit plan & Time Machine determinism
            val plan = repo.buildTradePlan(entry, StopRuleType.OR_MID, 1.0, TargetRuleType.R1, 0.0, cfg).getOrThrow()
            assertEquals(1215.3, plan.stop, 0.05)
            assertEquals(1240.3, plan.target, 0.05)

            val tm1 = repo.runTimeMachine(plan, cfg, replays = 500, seed = 19, window = 2, qtyForNet = 30).getOrThrow()
            val tm2 = repo.runTimeMachine(plan, cfg, replays = 500, seed = 19, window = 2, qtyForNet = 30).getOrThrow()
            assertEquals(tm1.pTarget, tm2.pTarget, 1e-6)
            assertEquals(tm1.medianClose, tm2.medianClose, 1e-6)

            // 3. Budget sizing + Risk Pass vs Holiday Block
            val port = PortfolioState(cash = 50000.0)
            val order = repo.sizeOrder(plan, cfg, port, "MARKET", null).getOrThrow()
            assertTrue(order.quantity > 0)
            assertEquals(16, order.fingerprint.length)

            val passVerdict = repo.checkOrderRisk(order, cfg, port)
            assertTrue(passVerdict.allPassed)

            val holidayCfg = cfg.copy(replayIsoIst = "2026-10-20 10:15") // Dussehra
            val holidayVerdict = repo.checkOrderRisk(order, holidayCfg, port)
            assertFalse("Holiday must veto order", holidayVerdict.allPassed)

            // 4. Broker yes-gate & FIFO accounting
            assertFalse(BudgetRiskBrokerEngine.parseYes("ok", order).first)
            assertFalse(BudgetRiskBrokerEngine.parseYes("all", order).first)
            assertTrue(BudgetRiskBrokerEngine.parseYes("yes buy ${order.quantity} ${order.tradingsymbol}", order).first)

            val fillMsg = repo.confirmPaperOrder(
                order,
                port,
                passVerdict,
                "yes buy ${order.quantity} ${order.tradingsymbol}",
                cfg
            ).getOrThrow()
            assertTrue(fillMsg.contains("RELIANCE"))
            val updatedPort = repo.portfolioFlow.first()
            assertEquals(1, updatedPort.positions.size)
            assertEquals(order.quantity, updatedPort.positions.first().shares)
        } finally {
            db.close()
        }
    }

    @Test
    fun `xbrl Ind-AS and banking parsers plus SEBI PIT lag and bulk-block churn`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = TradingRepository(db.deskDao(), MarketDataSource(ctx))
            repo.ensureSeeded()
            val cfg = DeskConfig(dataMode = DataMode.OFFLINE_REPLAY)

            val rel = repo.fetchAnalyst("RELIANCE", "consolidated", cfg).getOrThrow()
            assertEquals("ind-as", rel.format)
            assertTrue(rel.quarters.isNotEmpty())

            val hdfc = repo.fetchAnalyst("HDFCBANK", "standalone", cfg).getOrThrow()
            assertEquals("bank", hdfc.format)
            assertTrue(hdfc.quarters.isNotEmpty())
            assertNotNull(hdfc.quarters.first().niiCr)

            val insiders = repo.fetchInsiders(null, cfg)
            assertTrue(insiders.pitFilings.any { it.isLate })
            assertTrue(insiders.deals.any { it.sameDayBothSides })

            val alertSummary = repo.checkAlertsNow(cfg)
            assertTrue(alertSummary.alerts.isNotEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `7-agent orchestrator approves valid cycle and halts on Risk Agent hard veto`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = TradingRepository(db.deskDao(), MarketDataSource(ctx))
            repo.ensureSeeded()
            val orchestrator = AgentOrchestratorEngine(repo, DeterministicFakeAdvisor())

            // A) Happy path on valid trading day 2026-10-19 10:15 IST -> books paper fill + alerts
            val validCfg = DeskConfig(dataMode = DataMode.OFFLINE_REPLAY, replayIsoIst = "2026-10-19 10:15")
            var lastStages: List<com.example.model.AgentStageState> = emptyList()
            val snapPass = orchestrator.runFullCycle(
                config = validCfg,
                watchlist = listOf("RELIANCE"),
                symbolOverride = "RELIANCE",
                autoBookPaper = true,
                onStageUpdate = { lastStages = it },
                onLogMessage = {}
            )
            assertTrue("Expected approved multi-agent cycle but got: ${snapPass.outcome}", snapPass.succeeded)
            assertEquals("RELIANCE", snapPass.selectedSymbol)
            assertEquals(AgentStageStatus.BOOKED, lastStages.last().status)
            assertEquals(1, repo.portfolioFlow.first().positions.size)

            // B) Hard Risk Veto on NSE Holiday (2026-10-20 Dussehra) -> stops at Risk, never reaches Agent 7
            val holidayCfg = DeskConfig(dataMode = DataMode.OFFLINE_REPLAY, replayIsoIst = "2026-10-20 10:15")
            var vetoStages: List<com.example.model.AgentStageState> = emptyList()
            val snapVeto = orchestrator.runFullCycle(
                config = holidayCfg,
                watchlist = listOf("RELIANCE"),
                symbolOverride = "RELIANCE",
                autoBookPaper = true,
                onStageUpdate = { vetoStages = it },
                onLogMessage = {}
            )
            assertFalse("Risk veto must halt pipeline", snapVeto.succeeded)
            assertTrue(snapVeto.outcome.contains("VETOED BY RISK"))
            assertEquals(AgentStageStatus.VETOED, vetoStages.first { it.role == AgentRole.RISK }.status)
            assertEquals(AgentStageStatus.PENDING, vetoStages.first { it.role == AgentRole.ALERTS_BROKER }.status)
            // Confirm no second trade was added on veto
            assertEquals(1, repo.portfolioFlow.first().tradesToday)

            // C) Verify non-mutating preview cycle (used on first launch) leaves cash & trades unchanged
            repo.resetPaperAccount(50000.0)
            val previewCfg = DeskConfig(dataMode = DataMode.OFFLINE_REPLAY, replayIsoIst = "2026-10-19 10:15")
            var previewStages: List<com.example.model.AgentStageState> = emptyList()
            val cashBeforePreview = repo.portfolioFlow.first().cash
            val tradesBeforePreview = repo.portfolioFlow.first().tradesToday
            val snapPreview = orchestrator.runFullCycle(
                config = previewCfg,
                watchlist = listOf("RELIANCE"),
                symbolOverride = "RELIANCE",
                autoBookPaper = false,
                onStageUpdate = { previewStages = it },
                onLogMessage = {}
            )
            assertTrue("Expected preview cycle to succeed: ${snapPreview.outcome}", snapPreview.succeeded)
            assertTrue("Expected PREVIEW_READY outcome but got: ${snapPreview.outcome}", snapPreview.outcome.startsWith("PREVIEW_READY"))
            assertEquals(AgentStageStatus.PASSED, previewStages.last().status)
            assertEquals(cashBeforePreview, repo.portfolioFlow.first().cash, 1e-6)
            assertEquals(tradesBeforePreview, repo.portfolioFlow.first().tradesToday)

            // D) Verify 4-state visual mapping and all 11 skills tracked in completedSkills
            assertEquals(7, AgentRole.entries.size)
            assertEquals(11, ALL_TRADING_SKILLS.size)
            val completed11 = previewStages.flatMap { it.completedSkills }.filter { it in ALL_TRADING_SKILLS }.toSet()
            assertEquals("Expected all 11 skills completed in previewStages", ALL_TRADING_SKILLS.toSet(), completed11)

            // Also verify useAi = false runs cleanly without touching Gemini network advisor
            val coldBootCfg = DeskConfig(dataMode = DataMode.OFFLINE_REPLAY, replayIsoIst = "2026-10-19 10:15")
            var coldStages: List<com.example.model.AgentStageState> = emptyList()
            val coldSnap = orchestrator.runFullCycle(
                config = coldBootCfg,
                watchlist = listOf("RELIANCE"),
                symbolOverride = "RELIANCE",
                autoBookPaper = false,
                useAi = false,
                onStageUpdate = { coldStages = it },
                onLogMessage = {}
            )
            assertTrue("Expected cold-boot deterministic cycle to succeed", coldSnap.succeeded)
            assertEquals(0, coldSnap.aiCalls)
            assertEquals(
                ALL_TRADING_SKILLS.toSet(),
                coldStages.flatMap { it.completedSkills }.filter { it in ALL_TRADING_SKILLS }.toSet()
            )
            assertEquals(com.example.model.AgentVisualState.IDLE, AgentStageStatus.PENDING.toVisualState())
            assertEquals(com.example.model.AgentVisualState.PROCESSING, AgentStageStatus.RUNNING.toVisualState())
            assertEquals(com.example.model.AgentVisualState.SUCCESS, AgentStageStatus.PASSED.toVisualState())
            assertEquals(com.example.model.AgentVisualState.SUCCESS, AgentStageStatus.FILTERED.toVisualState())
            assertEquals(com.example.model.AgentVisualState.SUCCESS, AgentStageStatus.BOOKED.toVisualState())
            assertEquals(com.example.model.AgentVisualState.FAILED, AgentStageStatus.VETOED.toVisualState())
            assertEquals(com.example.model.AgentVisualState.FAILED, AgentStageStatus.FAILED.toVisualState())
        } finally {
            db.close()
        }
    }

    @Test
    fun `watchlist sentiment gauge scores india-news and india-analyst signals into bullish or bearish range`() = runBlocking {
        // 1. Bullish synthetic news + expanding Ind-AS XBRL results
        val bullNews = NewsReport(
            symbol = "RELIANCE",
            lastPrice = 1245.0,
            prevClose = 1210.0,
            changePct = 2.89,
            movingFlag = false,
            items = listOf(
                NewsItem(
                    id = "n1",
                    source = "NSE Filing",
                    symbol = "RELIANCE",
                    timestamp = "2026-10-19",
                    title = "Strong profit jump and order win award with dividend",
                    summary = "Record quarter profit growth and expansion beat estimates",
                    tags = listOf("RESULTS", "DIVIDEND", "ORDER/CONTRACT"),
                    important = true,
                    routine = false
                )
            )
        )
        val bullAnalyst = AnalystReport(
            symbol = "RELIANCE",
            entityName = "Reliance Industries Ltd",
            basis = "consolidated",
            basisNote = null,
            format = "ind-as",
            quarters = listOf(
                FilingQuarter(
                    end = "2026-06-30", fiscal = "Q1 FY27", basis = "consolidated", format = "ind-as",
                    audited = "Unaudited", filed = "2026-07-17",
                    revenueCr = 250000.0, otherIncomeCr = 1200.0, ebitdaCr = 48000.0, operatingProfitCr = 38000.0,
                    niiCr = null, provisionsCr = null, pbtCr = 28000.0, netProfitCr = 21000.0, exceptionalCr = null,
                    epsDiluted = 30.0, epsAdjustedTodayShares = 30.0, epsAdjusted = false,
                    ebitdaMarginPct = 19.2, operatingMarginPct = 15.2, netMarginPct = 11.4,
                    revenueYoyPct = 18.0, netProfitYoyPct = 24.0, niiYoyPct = null, epsYoyPct = 23.0
                )
            ),
            ttmRevenueCr = null, ttmProfitCr = null, ttmNiiCr = null, ttmEps = null,
            whatChanged = emptyList(), upcomingEvent = null
        )
        val bullScore = AlertsNewsAnalystInsidersEngine.computeSymbolSentiment("RELIANCE", bullNews, bullAnalyst)
        assertTrue("Expected bullish score >= 62 but got ${bullScore.score}", bullScore.score >= 62)
        assertEquals(SentimentVerdict.BULLISH, bullScore.verdict)
        assertNotNull(bullScore.analystScore)

        // 2. Bearish regulatory penalty news + contracting loss XBRL quarter
        val bearNews = NewsReport(
            symbol = "XYZ",
            lastPrice = 94.0,
            prevClose = 100.0,
            changePct = -6.0,
            movingFlag = true,
            items = listOf(
                NewsItem(
                    id = "b1",
                    source = "NSE Filing",
                    symbol = "XYZ",
                    timestamp = "2026-10-19",
                    title = "SEBI show cause notice and penalty inquiry; CFO resign",
                    summary = "Weak demand, debt default warning, and profit slump",
                    tags = listOf("REGULATORY", "MANAGEMENT"),
                    important = true,
                    routine = false
                )
            )
        )
        val bearAnalyst = AnalystReport(
            symbol = "XYZ",
            entityName = "XYZ Corp",
            basis = "consolidated",
            basisNote = null,
            format = "ind-as",
            quarters = listOf(
                FilingQuarter(
                    end = "2026-06-30", fiscal = "Q1 FY27", basis = "consolidated", format = "ind-as",
                    audited = "Unaudited", filed = "2026-07-17",
                    revenueCr = 800.0, otherIncomeCr = 10.0, ebitdaCr = -40.0, operatingProfitCr = -70.0,
                    niiCr = null, provisionsCr = null, pbtCr = -120.0, netProfitCr = -95.0, exceptionalCr = -30.0,
                    epsDiluted = -4.2, epsAdjustedTodayShares = -4.2, epsAdjusted = false,
                    ebitdaMarginPct = -5.0, operatingMarginPct = -8.7, netMarginPct = -11.9,
                    revenueYoyPct = -22.0, netProfitYoyPct = -65.0, niiYoyPct = null, epsYoyPct = -65.0
                )
            ),
            ttmRevenueCr = null, ttmProfitCr = null, ttmNiiCr = null, ttmEps = null,
            whatChanged = emptyList(), upcomingEvent = null
        )
        val bearScore = AlertsNewsAnalystInsidersEngine.computeSymbolSentiment("XYZ", bearNews, bearAnalyst)
        assertTrue("Expected bearish score <= 37 but got ${bearScore.score}", bearScore.score <= 37)
        assertEquals(SentimentVerdict.BEARISH, bearScore.verdict)

        // 3. Full offline watchlist evaluation through TradingRepository
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = TradingRepository(db.deskDao(), MarketDataSource(ctx))
            repo.ensureSeeded()
            val cfg = DeskConfig(dataMode = DataMode.OFFLINE_REPLAY, replayIsoIst = "2026-10-19 10:15")
            val wl = listOf("RELIANCE", "HDFCBANK", "TCS", "ICICIBANK", "M&M")
            val report = repo.fetchWatchlistSentiment(wl, cfg)
            assertEquals(5, report.symbols.size)
            assertTrue("Expected XBRL coverage for RELIANCE & HDFCBANK", report.xbrlCoveredCount >= 2)
            assertTrue(report.overallScore in 0..100)
            assertTrue(report.newsAvgScore in 0..100)
            assertNotNull(report.analystAvgScore)
        } finally {
            db.close()
        }
    }

    @Test
    fun `live nse and bse top-5 orb scanner ranks strictly by relative volume`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = TradingRepository(db.deskDao(), MarketDataSource(ctx))
            repo.ensureSeeded()
            val cfg = DeskConfig(dataMode = DataMode.OFFLINE_REPLAY, replayIsoIst = "2026-10-19 10:15")

            val nseTop5 = repo.fetchTopOrbByRelativeVolume(
                exchange = "NSE",
                orMinutes = 15,
                includeT2t = false,
                config = cfg,
                limit = 5
            )
            assertEquals(5, nseTop5.size)
            assertEquals(listOf(1, 2, 3, 4, 5), nseTop5.map { it.rank })
            assertTrue(nseTop5.all { it.exchange == "NSE" })
            assertTrue(nseTop5.all { it.orHigh >= it.orLow && it.relativeVolumeX > 0.0 })
            // Verify strict descending sort by relativeVolumeX
            for (i in 0 until nseTop5.size - 1) {
                assertTrue(
                    "Expected rank ${i + 1} vol (${nseTop5[i].relativeVolumeX}) >= rank ${i + 2} vol (${nseTop5[i + 1].relativeVolumeX})",
                    nseTop5[i].relativeVolumeX >= nseTop5[i + 1].relativeVolumeX
                )
            }

            val bseTop5 = repo.fetchTopOrbByRelativeVolume(
                exchange = "BSE",
                orMinutes = 30,
                includeT2t = false,
                config = cfg,
                limit = 5
            )
            assertEquals(5, bseTop5.size)
            assertTrue(bseTop5.all { it.exchange == "BSE" })
            assertEquals(nseTop5.map { it.symbol }.toSet().size, 5)
        } finally {
            db.close()
        }
    }

    @Test
    fun `market heatmap builds volatility and relative volume intensity for nifty 50 and watchlist`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = TradingRepository(db.deskDao(), MarketDataSource(ctx))
            repo.ensureSeeded()
            val cfg = DeskConfig(dataMode = DataMode.OFFLINE_REPLAY, replayIsoIst = "2026-10-19 10:15")
            val wl = listOf("RELIANCE", "HDFCBANK", "TCS", "ICICIBANK", "M&M")

            val niftyCombined = repo.fetchMarketHeatmap(
                universe = HeatmapUniverse.NIFTY_50,
                metric = HeatmapIntensityMetric.COMBINED,
                watchlist = wl,
                exchange = "NSE",
                orMinutes = 15,
                config = cfg
            )
            assertTrue("Expected Nifty 50 heatmap cells", niftyCombined.size >= 10)
            assertTrue(niftyCombined.all { it.intensity in 0.18f..1.0f })
            assertTrue(niftyCombined.any { it.volatilityPct > 0.0 })
            assertTrue(niftyCombined.any { it.relativeVolumeX > 0.0 })

            val wlVolOnly = repo.fetchMarketHeatmap(
                universe = HeatmapUniverse.WATCHLIST,
                metric = HeatmapIntensityMetric.VOLATILITY,
                watchlist = wl,
                exchange = "NSE",
                orMinutes = 5,
                config = cfg
            )
            assertEquals(wl.size, wlVolOnly.size)
            assertEquals(wl.toSet(), wlVolOnly.map { it.symbol }.toSet())
            for (i in 0 until wlVolOnly.size - 1) {
                assertTrue(
                    "Expected descending volatility sort",
                    wlVolOnly[i].volatilityPct >= wlVolOnly[i + 1].volatilityPct
                )
            }

            val wlRelVol = repo.fetchMarketHeatmap(
                universe = HeatmapUniverse.WATCHLIST,
                metric = HeatmapIntensityMetric.RELATIVE_VOLUME,
                watchlist = wl,
                exchange = "NSE",
                orMinutes = 5,
                config = cfg
            )
            for (i in 0 until wlRelVol.size - 1) {
                assertTrue(
                    "Expected descending relative volume sort",
                    wlRelVol[i].relativeVolumeX >= wlRelVol[i + 1].relativeVolumeX
                )
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `configurable ORB 5m vs 15m parameters and Risk Agent R_R veto threshold`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = TradingRepository(db.deskDao(), MarketDataSource(ctx))
            repo.ensureSeeded()
            val orchestrator = AgentOrchestratorEngine(repo, DeterministicFakeAdvisor())

            // 1. 5m ORB with R1 (1.0R) target while Risk Agent requires minRewardRisk = 2.0 -> MUST VETO
            val vetoCfg = DeskConfig(
                dataMode = DataMode.OFFLINE_REPLAY,
                replayIsoIst = "2026-10-19 10:15",
                orbDurationMinutes = 5,
                orbMinVolumeX = 1.0,
                orbStopRule = StopRuleType.OR_MID,
                orbTargetRule = TargetRuleType.R1,
                minRewardRisk = 2.0,
                noEntriesBefore = "09:20"
            )
            var vetoStages: List<com.example.model.AgentStageState> = emptyList()
            val snapVeto = orchestrator.runFullCycle(
                config = vetoCfg,
                watchlist = listOf("RELIANCE"),
                symbolOverride = "RELIANCE",
                autoBookPaper = true,
                useAi = false,
                onStageUpdate = { vetoStages = it },
                onLogMessage = {}
            )
            assertFalse("Risk Agent must veto 1.0R target when minRewardRisk=2.0", snapVeto.succeeded)
            assertTrue("Expected VETOED BY RISK outcome but got: ${snapVeto.outcome}", snapVeto.outcome.contains("VETOED BY RISK"))
            assertEquals(AgentStageStatus.VETOED, vetoStages.first { it.role == AgentRole.RISK }.status)

            // 2. Toggle Target to R2 (2.0R) on 5m ORB with minRewardRisk = 2.0 -> MUST PASS
            val passCfg = vetoCfg.copy(
                orbTargetRule = TargetRuleType.R2,
                orbStopRule = StopRuleType.OR_LOW
            )
            var passStages: List<com.example.model.AgentStageState> = emptyList()
            val snapPass = orchestrator.runFullCycle(
                config = passCfg,
                watchlist = listOf("RELIANCE"),
                symbolOverride = "RELIANCE",
                autoBookPaper = false,
                useAi = false,
                onStageUpdate = { passStages = it },
                onLogMessage = {}
            )
            assertTrue("Expected 2.0R 5m ORB to pass Risk Agent (outcome=${snapPass.outcome})", snapPass.succeeded)
            assertEquals(AgentStageStatus.PASSED, passStages.first { it.role == AgentRole.RISK }.status)
            assertTrue(snapPass.finalStateJson.contains("\"minutes\": 5") || snapPass.finalStateJson.contains("\"orMinutes\": 5"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `all 6 indian broker adapters handle buy, sell, positions, orders, and funds uniformly`() = runBlocking {
        data class RecordedCall(
            val method: String,
            val url: String,
            val headers: Map<String, String>,
            val body: String
        )

        val calls = mutableListOf<RecordedCall>()
        val jsonMedia = "application/json; charset=utf-8".toMediaType()

        val mockInterceptor = Interceptor { chain ->
            val req = chain.request()
            val buf = Buffer()
            req.body?.writeTo(buf)
            val bodyStr = buf.readUtf8()
            val headerMap = req.headers.names().associateWith { req.header(it).orEmpty() }
            val url = req.url.toString()
            calls.add(RecordedCall(req.method, url, headerMap, bodyStr))

            val jsonBody = when {
                // 1. Zerodha Kite
                url.contains("api.kite.trade") && url.endsWith("/orders/regular") ->
                    """{"status":"success","data":{"order_id":"KITE-101"}}"""
                url.contains("api.kite.trade") && url.endsWith("/portfolio/positions") ->
                    """{"status":"success","data":{"net":[{"tradingsymbol":"RELIANCE","exchange":"NSE","quantity":10,"average_price":1220.0,"last_price":1240.0,"product":"MIS"}]}}"""
                url.contains("api.kite.trade") && url.endsWith("/orders") ->
                    """{"status":"success","data":[{"order_id":"KITE-101","tradingsymbol":"RELIANCE","exchange":"NSE","transaction_type":"BUY","quantity":10,"product":"MIS","order_type":"MARKET","average_price":1220.0,"status":"COMPLETE"}]}"""
                url.contains("api.kite.trade") && url.endsWith("/user/margins/equity") ->
                    """{"status":"success","data":{"net":85000.0,"available":{"live_balance":75000.0,"collateral":5000.0},"utilised":{"debits":10000.0}}}"""

                // 2. Upstox
                url.contains("api.upstox.com") && url.endsWith("/order/place") ->
                    """{"status":"success","data":{"order_id":"UPX-202"}}"""
                url.contains("api.upstox.com") && url.endsWith("/portfolio/short-term-positions") ->
                    """{"status":"success","data":[{"tradingsymbol":"TCS","exchange":"NSE","quantity":5,"average_price":4100.0,"last_price":4140.0,"product":"MIS"}]}"""
                url.contains("api.upstox.com") && url.endsWith("/order/retrieve-all") ->
                    """{"status":"success","data":[{"order_id":"UPX-202","tradingsymbol":"TCS","exchange":"NSE","transaction_type":"SELL","quantity":5,"product":"MIS","order_type":"LIMIT","price":4140.0,"status":"COMPLETE"}]}"""
                url.contains("api.upstox.com") && url.contains("/user/get-funds-and-margin") ->
                    """{"status":"success","data":{"equity":{"available_margin":62000.0,"used_margin":8000.0}}}"""

                // 3. Angel One SmartAPI
                url.contains("apiconnect.angelone.in") && url.endsWith("/placeOrder") ->
                    """{"status":true,"message":"SUCCESS","data":{"orderid":"AONE-303"}}"""
                url.contains("apiconnect.angelone.in") && url.endsWith("/getPosition") ->
                    """{"status":true,"data":[{"tradingsymbol":"HDFCBANK-EQ","exchange":"NSE","netqty":"12","avgnetprice":"1650.0","ltp":"1675.0","producttype":"INTRADAY"}]}"""
                url.contains("apiconnect.angelone.in") && url.endsWith("/getOrderBook") ->
                    """{"status":true,"data":[{"orderid":"AONE-303","tradingsymbol":"HDFCBANK-EQ","exchange":"NSE","transactiontype":"BUY","quantity":"12","producttype":"INTRADAY","ordertype":"MARKET","price":"1650.0","status":"complete"}]}"""
                url.contains("apiconnect.angelone.in") && url.endsWith("/getRMS") ->
                    """{"status":true,"data":{"availablecash":"90000.0","net":"95000.0","collateral":"5000.0"}}"""

                // 4. Fyers
                url.contains("api-t1.fyers.in") && url.endsWith("/orders/sync") ->
                    """{"s":"ok","code":200,"id":"FYR-404","message":"Order placed"}"""
                url.contains("api-t1.fyers.in") && url.endsWith("/positions") ->
                    """{"s":"ok","netPositions":[{"symbol":"NSE:ICICIBANK-EQ","netQty":8,"netAvg":1260.0,"ltp":1285.0,"productType":"INTRADAY"}]}"""
                url.contains("api-t1.fyers.in") && url.endsWith("/orders") ->
                    """{"s":"ok","orderBook":[{"id":"FYR-404","symbol":"NSE:ICICIBANK-EQ","side":-1,"qty":8,"productType":"INTRADAY","type":2,"tradedPrice":1285.0,"status":"TRADED"}]}"""
                url.contains("api-t1.fyers.in") && url.endsWith("/funds") ->
                    """{"s":"ok","fund_limit":[{"title":"Available Balance","equityAmount":55000.0},{"title":"Utilized Amount","equityAmount":5000.0}]}"""

                // 5. Dhan
                url.contains("api.dhan.co") && req.method == "POST" && url.endsWith("/orders") ->
                    """{"orderId":"DHAN-505","orderStatus":"PENDING"}"""
                url.contains("api.dhan.co") && url.endsWith("/positions") ->
                    """[{"tradingSymbol":"M&M","exchange":"NSE","netQty":6,"buyAvg":2900.0,"ltp":2950.0,"productType":"INTRADAY"}]"""
                url.contains("api.dhan.co") && req.method == "GET" && url.endsWith("/orders") ->
                    """[{"orderId":"DHAN-505","tradingSymbol":"M&M","exchange":"NSE","transactionType":"BUY","quantity":6,"productType":"INTRADAY","orderType":"MARKET","price":2900.0,"orderStatus":"TRADED"}]"""
                url.contains("api.dhan.co") && url.endsWith("/fundlimit") ->
                    """{"availabelBalance":42000.0,"utilizedAmount":3000.0,"collateralAmount":2000.0}"""

                // 6. Generic / Custom Broker
                url.contains("custom-broker.example.in") && req.method == "POST" && url.endsWith("/orders") ->
                    """{"data":{"order_id":"GEN-606","status":"PLACED"},"message":"Custom OK"}"""
                url.contains("custom-broker.example.in") && url.endsWith("/positions") ->
                    """{"positions":[{"symbol":"INFY","exchange":"NSE","quantity":4,"average_price":1800.0,"last_price":1830.0,"product":"CNC"}]}"""
                url.contains("custom-broker.example.in") && req.method == "GET" && url.endsWith("/orders") ->
                    """{"orders":[{"order_id":"GEN-606","symbol":"INFY","exchange":"NSE","side":"SELL","quantity":4,"product":"CNC","order_type":"MARKET","price":1830.0,"status":"COMPLETE"}]}"""
                url.contains("custom-broker.example.in") && url.endsWith("/funds") ->
                    """{"available_cash":110000.0,"used_margin":12000.0,"collateral":8000.0}"""

                else -> """{"error":"unexpected route $url"}"""
            }

            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(jsonBody.toResponseBody(jsonMedia))
                .build()
        }

        val okHttp = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val registry = BrokerAdapterRegistry(okHttp)

        val cases = listOf(
            Triple(
                IndianBrokerId.ZERODHA,
                BrokerCredentials(IndianBrokerId.ZERODHA, apiKey = "zk", apiSecret = "zs", accessToken = "zt"),
                "KITE-101"
            ),
            Triple(
                IndianBrokerId.UPSTOX,
                BrokerCredentials(IndianBrokerId.UPSTOX, accessToken = "ut"),
                "UPX-202"
            ),
            Triple(
                IndianBrokerId.ANGEL_ONE,
                BrokerCredentials(IndianBrokerId.ANGEL_ONE, apiKey = "ak", clientId = "AC1", accessToken = "ajwt"),
                "AONE-303"
            ),
            Triple(
                IndianBrokerId.FYERS,
                BrokerCredentials(IndianBrokerId.FYERS, apiKey = "FAPP", accessToken = "ftok"),
                "FYR-404"
            ),
            Triple(
                IndianBrokerId.DHAN,
                BrokerCredentials(IndianBrokerId.DHAN, clientId = "DC1", accessToken = "dtok"),
                "DHAN-505"
            ),
            Triple(
                IndianBrokerId.GENERIC,
                BrokerCredentials(
                    brokerId = IndianBrokerId.GENERIC,
                    apiKey = "gk",
                    apiSecret = "gs",
                    accessToken = "gt",
                    clientId = "gc",
                    baseUrl = "https://custom-broker.example.in/v1"
                ),
                "GEN-606"
            )
        )

        for ((brokerId, creds, expectedOrderId) in cases) {
            val adapter = registry.adapterFor(brokerId)

            val buyRes = adapter.placeOrder(
                creds,
                BrokerOrderRequest("RELIANCE", "NSE", "BUY", 10, "MIS", "MARKET", 1227.8)
            ).getOrThrow()
            assertEquals(brokerId, buyRes.brokerId)
            assertEquals(expectedOrderId, buyRes.brokerOrderId)

            val sellRes = adapter.placeOrder(
                creds,
                BrokerOrderRequest("RELIANCE", "NSE", "SELL", 5, "MIS", "LIMIT", 1245.0)
            ).getOrThrow()
            assertEquals(expectedOrderId, sellRes.brokerOrderId)

            val positions = adapter.getPositions(creds).getOrThrow()
            assertEquals(1, positions.size)
            assertTrue(positions.first().shares > 0)

            val orders = adapter.getOrders(creds).getOrThrow()
            assertEquals(1, orders.size)
            assertEquals(expectedOrderId, orders.first().orderId)

            val funds = adapter.getFunds(creds).getOrThrow()
            assertEquals(brokerId, funds.brokerId)
            assertTrue(funds.availableCash > 0.0)
        }

        // Verify distinct broker auth headers were actually transmitted
        assertTrue(calls.any { it.headers["Authorization"] == "token zk:zt" && it.headers["X-Kite-Version"] == "3" })
        assertTrue(calls.any { it.headers["Authorization"] == "Bearer ut" && it.headers["Api-Version"] == "2.0" })
        assertTrue(calls.any { it.headers["X-PrivateKey"] == "ak" && it.headers["Authorization"] == "Bearer ajwt" })
        assertTrue(calls.any { it.headers["Authorization"] == "FAPP:ftok" })
        assertTrue(calls.any { it.headers["access-token"] == "dtok" && it.headers["client-id"] == "DC1" })
        assertTrue(calls.any { it.headers["X-Api-Key"] == "gk" && it.headers["X-Api-Secret"] == "gs" && it.url.startsWith("https://custom-broker.example.in/v1") })
    }

    @Test
    fun `paper mode defaults separate without credentials, sell computes live pnl, and risk agent blocks both modes`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val mockHttp = OkHttpClient.Builder().addInterceptor { chain ->
                val req = chain.request()
                val media = "application/json; charset=utf-8".toMediaType()
                val body = when {
                    req.url.encodedPath.endsWith("/orders") && req.method == "POST" ->
                        """{"orderId":"DHAN-LIVE-999","orderStatus":"PLACED"}"""
                    req.url.encodedPath.endsWith("/fundlimit") ->
                        """{"availabelBalance":75000.0,"utilizedAmount":5000.0,"collateralAmount":0.0}"""
                    req.url.encodedPath.endsWith("/positions") ->
                        """[]"""
                    else -> """[]"""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody(media))
                    .build()
            }.build()

            val vault = SecureBrokerVault(ctx)
            // Clear any pre-existing test vault entries
            IndianBrokerId.entries.forEach { vault.clearCredentials(it) }

            val repo = TradingRepository(
                dao = db.deskDao(),
                dataSource = MarketDataSource(ctx),
                vault = vault,
                brokerRegistry = BrokerAdapterRegistry(mockHttp)
            )
            repo.ensureSeeded()

            // 1. Default config is always Paper Trading, works with ZERO broker credentials
            val defaultCfg = repo.configFlow.first()
            assertTrue("Default mode must always be Paper Trading", defaultCfg.paper)
            assertTrue(vault.getAllCredentials().isEmpty())

            val paperCfg = defaultCfg.copy(dataMode = DataMode.OFFLINE_REPLAY, replayIsoIst = "2026-10-19 10:15", paper = true)
            val entry = repo.evaluateEntry("RELIANCE", "orb", null, 15, 1.0, "5m", paperCfg)
            val plan = repo.buildTradePlan(entry, StopRuleType.OR_MID, 1.0, TargetRuleType.R2, 0.0, paperCfg).getOrThrow()

            // Paper BUY
            val initialPort = repo.portfolioFlow.first()
            val buyOrder = repo.sizeOrder(plan, paperCfg, initialPort, "MARKET", null, side = "BUY").getOrThrow()
            val buyRisk = repo.checkOrderRisk(buyOrder, paperCfg, initialPort)
            assertTrue(buyRisk.allPassed)
            repo.confirmAndExecuteOrder(
                buyOrder,
                initialPort,
                buyRisk,
                "yes buy ${buyOrder.quantity} ${buyOrder.tradingsymbol}",
                paperCfg
            ).getOrThrow()

            val afterBuyPort = repo.portfolioFlow.first()
            assertEquals(1, afterBuyPort.positions.size)

            // Mark-to-market + partial Paper SELL computes live open + realized + day P&L
            val liftedPort = afterBuyPort.copy(
                positions = afterBuyPort.positions.map { it.copy(lastPrice = it.avgPrice + 25.0) }
            )
            val liveOpenPnl = BudgetRiskBrokerEngine.computeLivePnlSummary(
                portfolio = liftedPort,
                isRealMode = false,
                modeLabel = "Paper Simulation",
                nowIst = LocalDateTime.of(2026, 10, 19, 10, 30)
            )
            assertTrue(liveOpenPnl.openUnrealizedPnl > 0.0)
            assertEquals(liveOpenPnl.openUnrealizedPnl + liveOpenPnl.realizedPnlToday, liveOpenPnl.dayPnl, 1e-6)

            val sellOrder = repo.sizeOrder(
                plan.copy(entry = liftedPort.positions.first().lastPrice),
                paperCfg,
                liftedPort,
                "MARKET",
                null,
                side = "SELL"
            ).getOrThrow()
            val sellRisk = repo.checkOrderRisk(sellOrder, paperCfg, liftedPort)
            assertTrue("Closing SELL on held shares must pass Risk Agent", sellRisk.allPassed)
            repo.confirmAndExecuteOrder(
                sellOrder,
                liftedPort,
                sellRisk,
                "yes sell ${sellOrder.quantity} ${sellOrder.tradingsymbol}",
                paperCfg
            ).getOrThrow()

            val afterSellPort = repo.portfolioFlow.first()
            assertTrue("Expected positive realized P&L after closing SELL at higher price", afterSellPort.realizedPnlToday > 0.0)

            // 2. Risk Agent blocks unsafe naked SELL when no shares are held (in both Paper and Real modes)
            val emptyPort = PortfolioState(cash = 75000.0)
            val unsafeSell = repo.sizeOrder(plan, paperCfg, emptyPort, "MARKET", null, side = "SELL").getOrThrow()
            val unsafePaperVerdict = repo.checkOrderRisk(unsafeSell, paperCfg, emptyPort)
            assertFalse("Paper Risk Agent must block naked short sell", unsafePaperVerdict.allPassed)

            // 3. Switch to Real Mode (Dhan): fails cleanly when credentials missing, succeeds after saving to SecureBrokerVault
            val realDhanCfg = paperCfg.copy(paper = false, selectedBroker = IndianBrokerId.DHAN)
            val realBuyOrder = repo.sizeOrder(plan, realDhanCfg, emptyPort, "MARKET", null, side = "BUY").getOrThrow()
            val realBuyRisk = repo.checkOrderRisk(realBuyOrder, realDhanCfg, emptyPort)
            assertTrue(realBuyRisk.allPassed)

            val missingCredsAttempt = repo.confirmAndExecuteOrder(
                realBuyOrder,
                emptyPort,
                realBuyRisk,
                "yes buy ${realBuyOrder.quantity} ${realBuyOrder.tradingsymbol}",
                realDhanCfg
            )
            assertTrue("Real mode without credentials must fail cleanly", missingCredsAttempt.isFailure)

            // Save Dhan credentials securely & verify vault round-trip
            repo.saveCredentials(
                BrokerCredentials(
                    brokerId = IndianBrokerId.DHAN,
                    clientId = "DHAN-UID-7",
                    accessToken = "jwt-dhan-secret"
                )
            )
            assertEquals("DHAN-UID-7", repo.getCredentials(IndianBrokerId.DHAN).clientId)

            // Unsafe holiday trade MUST still be blocked by Risk Agent in Real Mode!
            val realHolidayCfg = realDhanCfg.copy(replayIsoIst = "2026-10-20 10:15") // Dussehra holiday
            val holidayVerdict = repo.checkOrderRisk(realBuyOrder, realHolidayCfg, emptyPort)
            assertFalse("Risk Agent must block unsafe holiday order even in Real mode", holidayVerdict.allPassed)
            val blockedRealExec = repo.confirmAndExecuteOrder(
                realBuyOrder,
                emptyPort,
                holidayVerdict,
                "yes buy ${realBuyOrder.quantity} ${realBuyOrder.tradingsymbol}",
                realHolidayCfg
            )
            assertTrue("confirmAndExecuteOrder must refuse execution when Risk Agent vetoes", blockedRealExec.isFailure)

            // Valid Real Mode cycle executes via Dhan adapter and keeps Paper Portfolio untouched
            val paperCashBeforeReal = repo.portfolioFlow.first().cash
            val realResult = repo.confirmAndExecuteOrder(
                realBuyOrder,
                emptyPort,
                realBuyRisk,
                "yes buy ${realBuyOrder.quantity} ${realBuyOrder.tradingsymbol}",
                realDhanCfg
            ).getOrThrow()
            assertTrue("Expected Real Dhan confirmation but got: $realResult", realResult.contains("DHAN-LIVE-999"))
            assertEquals("Paper portfolio cash must remain isolated during Real execution", paperCashBeforeReal, repo.portfolioFlow.first().cash, 1e-6)
            assertTrue(repo.brokerRecordsFlow.first().any { !it.paper && it.plainSummary.contains("DHAN-LIVE-999") })

            // Verify 7-Agent Orchestrator also routes Real Mode through the selected broker & halts on Risk Veto
            val orchestrator = AgentOrchestratorEngine(repo, DeterministicFakeAdvisor())
            val realCycleSnap = orchestrator.runFullCycle(
                config = realDhanCfg,
                watchlist = listOf("RELIANCE"),
                symbolOverride = "RELIANCE",
                autoBookPaper = true,
                useAi = false,
                onStageUpdate = {},
                onLogMessage = {}
            )
            assertTrue("Expected Real mode orchestrator cycle to book through Dhan: ${realCycleSnap.outcome}", realCycleSnap.succeeded)
            assertTrue("Expected REAL_ORDER_PLACED in outcome but got: ${realCycleSnap.outcome}", realCycleSnap.outcome.contains("REAL_ORDER_PLACED") && realCycleSnap.outcome.contains("Dhan"))
        } finally {
            db.close()
        }
    }

    private suspend fun waitUntilMain(
        timeoutMs: Long = 8000L,
        predicate: suspend () -> Boolean
    ) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(15)
            }
        }
    }

    @Test
    fun `auto mode defaults OFF, supports interval controls, executes paper cycles cleanly, and stops on emergency stop`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val vault = SecureBrokerVault(ctx)
        IndianBrokerId.entries.forEach { vault.clearCredentials(it) }
        val repo = TradingRepository(db.deskDao(), MarketDataSource(ctx), vault = vault)
        repo.ensureSeeded()
        repo.saveConfig(
            DeskConfig(
                dataMode = DataMode.OFFLINE_REPLAY,
                replayIsoIst = "2026-10-19 10:15",
                paper = true,
                maxDailyLossInr = 5000.0
            )
        )

        val vm = MainViewModel(repo, DeterministicFakeAdvisor(), autoTickDelayMs = 1L)
        val statusCollector = launch { vm.autoModeStatus.collect {} }
        val tradesCollector = launch { vm.tradesTodayCount.collect {} }
        try {
            // 1. Default is always Auto OFF and Paper ON
            waitUntilMain { vm.lastCycleSnapshot.value != null && !vm.cycleRunning.value }
            assertFalse("Auto Mode must default to OFF", vm.autoModeEnabled.value)
            assertFalse("AutoModeStatus must default to OFF", vm.autoModeStatus.value.enabled)
            assertEquals(5, vm.autoModeStatus.value.intervalMinutes)
            assertEquals(300, vm.autoModeStatus.value.countdownSec)

            // 2. Change cycle interval (5m -> 15m -> 30m -> 1h)
            vm.setAutoIntervalMinutes(15)
            waitUntilMain { vm.autoModeStatus.value.intervalMinutes == 15 }
            assertEquals(900, vm.autoModeStatus.value.countdownSec)

            vm.setAutoIntervalMinutes(60)
            waitUntilMain { vm.autoModeStatus.value.intervalMinutes == 60 }
            assertEquals(3600, vm.autoModeStatus.value.countdownSec)

            vm.setAutoIntervalMinutes(5)
            waitUntilMain { vm.autoModeStatus.value.intervalMinutes == 5 }

            // 3. Start Auto Mode in Paper Mode -> runs full 7-agent cycle on schedule without needing broker credentials
            val tradesBeforeAuto = vm.tradesTodayCount.value
            vm.startAutoMode(symbolOverride = "RELIANCE", useAi = false)
            waitUntilMain {
                vm.autoModeStatus.value.enabled &&
                    vm.autoModeStatus.value.cyclesCompleted >= 1 &&
                    !vm.cycleRunning.value
            }
            val activeStatus = vm.autoModeStatus.value
            assertTrue("Expected nextRunIst to be populated while running", activeStatus.nextRunIst != "--")
            assertNotNull("Expected lastRunIst to be populated after cycle", activeStatus.lastRunIst)
            assertTrue("Expected live activity log entries", vm.autoActivityLog.value.isNotEmpty())
            assertTrue("Expected Paper Auto cycle to book at least 1 trade", vm.tradesTodayCount.value > tradesBeforeAuto)

            // 4. Emergency Stop halts Auto Mode immediately and logs alert
            vm.emergencyStopAuto()
            waitUntilMain { !vm.autoModeEnabled.value && !vm.autoModeStatus.value.enabled }
            assertEquals("--", vm.autoModeStatus.value.nextRunIst)
            assertTrue(
                "Expected EMERGENCY STOP entry in activity log",
                vm.autoActivityLog.value.any { it.title.contains("EMERGENCY STOP") }
            )
        } finally {
            statusCollector.cancel()
            tradesCollector.cancel()
            vm.stopAutoMode()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            db.close()
        }
    }

    @Test
    fun `auto mode enforces Risk veto gate, daily loss auto-stop, and Real+Auto safety guards`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val vault = SecureBrokerVault(ctx)
        IndianBrokerId.entries.forEach { vault.clearCredentials(it) }
        val repo = TradingRepository(db.deskDao(), MarketDataSource(ctx), vault = vault)
        repo.ensureSeeded()
        // Holiday session: Risk Agent MUST block every cycle even in Auto Mode
        repo.saveConfig(
            DeskConfig(
                dataMode = DataMode.OFFLINE_REPLAY,
                replayIsoIst = "2026-10-20 10:15",
                paper = true,
                maxTradesPerDay = 2,
                maxDailyLossInr = 500.0
            )
        )

        val vm = MainViewModel(repo, DeterministicFakeAdvisor(), autoTickDelayMs = 1L)
        val statusCollector = launch { vm.autoModeStatus.collect {} }
        val tradesCollector = launch { vm.tradesTodayCount.collect {} }
        try {
            waitUntilMain { vm.lastCycleSnapshot.value != null && !vm.cycleRunning.value }

            // 1. Risk Agent hard gate prevents any order placement during Auto Mode on holiday
            vm.startAutoMode(symbolOverride = "RELIANCE", useAi = false)
            waitUntilMain { vm.autoModeStatus.value.cyclesCompleted >= 1 && !vm.cycleRunning.value }
            assertEquals(0, repo.portfolioFlow.first().tradesToday)
            assertEquals(0, repo.portfolioFlow.first().positions.size)
            assertTrue(
                "Last cycle outcome should indicate Risk veto",
                vm.lastCycleSnapshot.value?.outcome?.contains("VETOED", ignoreCase = true) == true
            )

            // 2. Switching Paper -> Real while Auto Mode is ON safely pauses Auto Mode
            vm.setTradingMode(paper = false)
            waitUntilMain { !vm.autoModeEnabled.value && !repo.configFlow.first().paper }
            assertTrue(
                "Expected auto-pause warning log on Paper->Real switch",
                vm.autoActivityLog.value.any { it.detail.contains("Real Trading", ignoreCase = true) }
            )

            // 3. Starting Auto Mode in Real Mode without saved broker credentials is blocked cleanly
            vm.startAutoMode(symbolOverride = "RELIANCE", useAi = false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(50)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse("Real Auto Mode must not start without broker credentials", vm.autoModeEnabled.value)
            assertTrue(
                vm.autoActivityLog.value.any { it.title.contains("Auto Start Blocked") }
            )

            // 4. Max Daily Loss Auto-Stop halts active Auto Mode reactively and blocks re-starting while breached
            vm.setTradingMode(paper = true)
            waitUntilMain { repo.configFlow.first().paper }
            val cleanPort = repo.portfolioFlow.first()
            vm.updateAutoSafetyLimits(maxTradesPerDay = 2, maxDailyLossInr = 500.0)
            waitUntilMain { repo.configFlow.first().maxDailyLossInr == 500.0 }

            vm.startAutoMode(symbolOverride = "RELIANCE", useAi = false)
            waitUntilMain { vm.autoModeEnabled.value }

            // Inject realized loss breaching 500 INR while Auto Mode is active -> must auto-stop immediately
            repo.savePortfolio(cleanPort.copy(realizedPnlToday = -650.0, tradesToday = 1, ordersToday = 1))
            waitUntilMain {
                !vm.autoModeEnabled.value &&
                    vm.autoModeStatus.value.autoStopReason?.contains("Daily loss", ignoreCase = true) == true
            }
            assertTrue(
                "Expected Max Daily Loss Auto-Stop log entry",
                vm.autoActivityLog.value.any { it.title.contains("Max Daily Loss Auto-Stop") }
            )

            // Attempting to re-start Auto Mode while still breached must refuse
            vm.startAutoMode(symbolOverride = "RELIANCE", useAi = false)
            delay(60)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse("Auto Mode must refuse start when daily loss cap already breached", vm.autoModeEnabled.value)
        } finally {
            statusCollector.cancel()
            tradesCollector.cancel()
            vm.stopAutoMode()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            db.close()
        }
    }

    @Test
    fun `gemini advisor falls back cleanly when network unavailable without timeout`() = runBlocking {
        val advisor = com.example.ai.GeminiAgentAdvisor(hasNetwork = { false })
        val hits = listOf(
            ScanHit(
                symbol = "RELIANCE",
                exchange = "NSE",
                setup = "orb",
                badge = "ORB",
                lastPrice = 1227.8,
                prevClose = 1210.0,
                changePct = 1.47,
                volumeX = 1.8,
                rsi14 = 61.0,
                summary = "Reliance ORB"
            )
        )
        val clock = IndianMarketUtils.clockStatus(LocalDateTime.of(2026, 10, 19, 10, 15), isLiveMode = false)
        val decision = advisor.prioritizeCandidates(hits, clock)
        assertEquals(listOf("RELIANCE"), decision.rankedSymbols)
        assertTrue(decision.aiSource.contains("Offline"))
    }
}
