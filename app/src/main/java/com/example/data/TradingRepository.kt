package com.example.data

import com.example.data.broker.BrokerAdapterRegistry
import com.example.domain.AlertsNewsAnalystInsidersEngine
import com.example.domain.BudgetRiskBrokerEngine
import com.example.domain.ExitAndTimeMachineEngine
import com.example.domain.IndianMarketUtils
import com.example.domain.ScannerAndEntryEngine
import com.example.model.AgentCycleSnapshot
import com.example.model.AlertCheckSummary
import com.example.model.AlertRule
import com.example.model.AlertType
import com.example.model.AnalystReport
import com.example.model.BrokerCredentials
import com.example.model.BrokerFundsSnapshot
import com.example.model.BrokerOrderRequest
import com.example.model.BrokerRecord
import com.example.model.BrokerRemoteOrder
import com.example.model.DataMode
import com.example.model.DeskConfig
import com.example.model.EntryCheckResult
import com.example.model.HeatmapIntensityMetric
import com.example.model.HeatmapUniverse
import com.example.model.IndianBrokerId
import com.example.model.InsidersReport
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
import com.example.model.UniverseSecurity
import com.example.model.WatchlistSentimentReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class TradingRepository(
    private val dao: DeskDao,
    val dataSource: MarketDataSource,
    private val vault: SecureBrokerVault = SecureBrokerVault(dataSource.appContext),
    val brokerRegistry: BrokerAdapterRegistry = BrokerAdapterRegistry()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val KEY_CONFIG = "desk_config"
        private const val KEY_PORTFOLIO = "portfolio_state"
    }

    val configFlow: Flow<DeskConfig> = dao.observeState(KEY_CONFIG).map { ent ->
        ent?.let { runCatching { json.decodeFromString<DeskConfig>(it.jsonValue) }.getOrNull() }
            ?: DeskConfig()
    }

    val portfolioFlow: Flow<PortfolioState> = dao.observeState(KEY_PORTFOLIO).map { ent ->
        ent?.let { runCatching { json.decodeFromString<PortfolioState>(it.jsonValue) }.getOrNull() }
            ?: PortfolioState()
    }

    val brokerRecordsFlow: Flow<List<BrokerRecord>> = dao.observeBrokerRecords().map { rows ->
        rows.map { r ->
            BrokerRecord(
                id = r.id,
                timestampIst = r.timestampIst,
                date = r.date,
                fingerprint = r.fingerprint,
                symbol = r.symbol,
                exchange = r.exchange,
                side = r.side,
                quantity = r.quantity,
                product = r.product,
                price = r.price,
                stopPrice = r.stopPrice,
                targetPrice = r.targetPrice,
                chargesInr = r.chargesInr,
                paper = r.paper,
                status = r.status,
                confirmationReply = r.confirmationReply,
                plainSummary = r.plainSummary
            )
        }
    }

    val alertsFlow: Flow<List<AlertRule>> = dao.observeAlerts().map { list ->
        list.map { e ->
            AlertRule(
                id = e.id,
                symbol = e.symbol,
                exchange = e.exchange,
                type = AlertType.entries.firstOrNull { it.code == e.typeCode } ?: AlertType.ABOVE,
                value = e.value,
                timeHm = e.timeHm,
                label = e.label,
                armed = e.armed,
                lastTriggeredDate = e.lastTriggeredDate,
                lastTriggeredInfo = e.lastTriggeredInfo
            )
        }
    }

    val followedInvestorsFlow: Flow<List<String>> = dao.observeFollowedInvestors().map { rows ->
        rows.map { it.name }
    }

    val agentCyclesFlow: Flow<List<AgentCycleSnapshot>> = dao.observeAgentCycles().map { rows ->
        rows.mapNotNull { row ->
            runCatching { json.decodeFromString<AgentCycleSnapshot>(row.snapshotJson) }.getOrNull()
        }
    }

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.getState(KEY_CONFIG) == null) {
            saveConfig(DeskConfig())
        }
        if (dao.getState(KEY_PORTFOLIO) == null) {
            savePortfolio(PortfolioState(cash = 50000.0))
        }
        if (dao.getFollowedInvestorsSnapshot().isEmpty()) {
            listOf("Ashish Kacholia", "Vijay Kedia", "HRTI PRIVATE LIMITED", "FINAVENUE GROWTH FUND")
                .forEach { dao.insertInvestor(FollowedInvestorEntity(it)) }
        }
        if (dao.getAlertsSnapshot().isEmpty()) {
            saveAlerts(
                listOf(
                    AlertRule(
                        id = "default_open_bell",
                        symbol = "NIFTY",
                        exchange = "NSE",
                        type = AlertType.OPENING_BELL,
                        label = "NSE Opening Bell (09:15 IST)"
                    ),
                    AlertRule(
                        id = "default_reliance_pct",
                        symbol = "RELIANCE",
                        exchange = "NSE",
                        type = AlertType.PCT_MOVE,
                        value = 3.0,
                        label = "RELIANCE ±3.0% Intraday Move"
                    )
                )
            )
        }
    }

    suspend fun saveConfig(config: DeskConfig) {
        dao.upsertState(DeskStateEntity(KEY_CONFIG, json.encodeToString(config)))
    }

    suspend fun savePortfolio(portfolio: PortfolioState) {
        dao.upsertState(DeskStateEntity(KEY_PORTFOLIO, json.encodeToString(portfolio)))
    }

    suspend fun resetPaperAccount(startingCash: Double = 50000.0) {
        savePortfolio(PortfolioState(cash = startingCash))
        dao.clearBrokerRecords()
    }

    suspend fun saveAlerts(alerts: List<AlertRule>) {
        dao.upsertAlerts(alerts.map { it.toEntity() })
    }

    suspend fun deleteAlert(id: String) = dao.deleteAlert(id)

    suspend fun addInvestor(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) dao.insertInvestor(FollowedInvestorEntity(clean))
    }

    suspend fun removeInvestor(name: String) = dao.deleteInvestor(name)

    suspend fun saveAgentCycle(snapshot: AgentCycleSnapshot) {
        dao.insertAgentCycle(
            AgentCycleEntity(
                cycleId = snapshot.cycleId,
                startedAtIst = snapshot.startedAtIst,
                selectedSymbol = snapshot.selectedSymbol,
                outcome = snapshot.outcome,
                succeeded = snapshot.succeeded,
                snapshotJson = json.encodeToString(snapshot)
            )
        )
    }

    suspend fun clearAgentCycles() = dao.clearAgentCycles()

    // ---------------------------------------------------------------- Broker Credentials & Adapters

    val credentialsFlow: Flow<Map<IndianBrokerId, BrokerCredentials>> = vault.credentialsFlow

    fun getCredentials(brokerId: IndianBrokerId): BrokerCredentials = vault.getCredentials(brokerId)

    fun saveCredentials(creds: BrokerCredentials) = vault.saveCredentials(creds)

    fun clearCredentials(brokerId: IndianBrokerId) = vault.clearCredentials(brokerId)

    fun buildBrokerPreviewPayload(brokerId: IndianBrokerId, order: OrderProposal): String {
        val req = BrokerOrderRequest(
            symbol = order.tradingsymbol,
            exchange = order.exchange,
            side = order.transactionType,
            quantity = order.quantity,
            product = order.product,
            orderType = order.orderType,
            price = order.price ?: order.entryPrice,
            stopPrice = order.stopPrice,
            targetPrice = order.targetPrice,
            fingerprint = order.fingerprint
        )
        return brokerRegistry.adapterFor(brokerId).buildPreviewPayload(req)
    }

    suspend fun testBrokerConnection(creds: BrokerCredentials): Result<BrokerFundsSnapshot> =
        brokerRegistry.adapterFor(creds.brokerId).getFunds(creds)

    suspend fun fetchRealFunds(brokerId: IndianBrokerId): Result<BrokerFundsSnapshot> {
        val creds = vault.getCredentials(brokerId)
        if (!creds.hasUsableAuth()) {
            return Result.failure(IllegalStateException("Save ${brokerId.displayName} API credentials in Broker Settings first."))
        }
        return brokerRegistry.adapterFor(brokerId).getFunds(creds)
    }

    suspend fun fetchRealPositions(brokerId: IndianBrokerId): Result<List<PortfolioPosition>> {
        val creds = vault.getCredentials(brokerId)
        if (!creds.hasUsableAuth()) {
            return Result.failure(IllegalStateException("Save ${brokerId.displayName} API credentials in Broker Settings first."))
        }
        return brokerRegistry.adapterFor(brokerId).getPositions(creds)
    }

    suspend fun fetchRealOrders(brokerId: IndianBrokerId): Result<List<BrokerRemoteOrder>> {
        val creds = vault.getCredentials(brokerId)
        if (!creds.hasUsableAuth()) {
            return Result.failure(IllegalStateException("Save ${brokerId.displayName} API credentials in Broker Settings first."))
        }
        return brokerRegistry.adapterFor(brokerId).getOrders(creds)
    }

    // ---------------------------------------------------------------- Market & Reference

    suspend fun loadReferenceData(config: DeskConfig): Triple<Set<String>, Map<String, UniverseSecurity>, Map<String, String>> =
        withContext(Dispatchers.Default) {
            val live = config.dataMode == DataMode.LIVE_PUBLIC
            val fo = dataSource.loadFoSymbols()
            val sec = dataSource.loadSecList(live)
            val hol = dataSource.loadHolidays(live)
            Triple(fo, sec, hol)
        }

    // ---------------------------------------------------------------- Scanner & Entry

    suspend fun runScan(
        mode: ScanMode,
        setupFilter: String,
        watchlist: List<String>,
        orMinutes: Int,
        minVolumeX: Double,
        includeT2t: Boolean,
        config: DeskConfig
    ): List<ScanHit> = withContext(Dispatchers.Default) {
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val fo = dataSource.loadFoSymbols()
        val sec = dataSource.loadSecList(live)
        val universe = if (mode == ScanMode.WATCHLIST && watchlist.isNotEmpty()) {
            watchlist
        } else {
            dataSource.loadNifty50Symbols(live)
        }
        val asOf = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        when (mode) {
            ScanMode.ORB -> {
                val intraday = dataSource.loadIntradaySpark(universe, live)
                ScannerAndEntryEngine.scanOrb(intraday, sec, fo, asOf, orMinutes, minVolumeX, includeT2t)
            }
            ScanMode.DAILY, ScanMode.WATCHLIST -> {
                val daily = dataSource.loadDailySpark(universe, live)
                val dailyHits = ScannerAndEntryEngine.scanDaily(daily, sec, fo, setupFilter, includeT2t)
                if (mode == ScanMode.WATCHLIST && watchlist.isNotEmpty()) {
                    val wlSet = watchlist.map { it.uppercase() }.toSet()
                    dailyHits.filter { it.symbol in wlSet }.ifEmpty {
                        // Even if no technical setup fired on watchlist symbol, show its quote summary
                        wlSet.mapNotNull { sym ->
                            val s = daily[sym] ?: return@mapNotNull null
                            val closes = s.bars.map { it.close }
                            val last = closes.lastOrNull() ?: s.regularMarketPrice ?: return@mapNotNull null
                            val prev = closes.dropLast(1).lastOrNull() ?: s.prevClose ?: last
                            val chg = if (prev > 0) (last - prev) / prev * 100.0 else 0.0
                            ScanHit(
                                symbol = sym,
                                exchange = "NSE",
                                setup = "watchlist",
                                badge = "Watchlist",
                                lastPrice = IndianMarketUtils.round2(last),
                                prevClose = IndianMarketUtils.round2(prev),
                                changePct = IndianMarketUtils.round2(chg),
                                rsi14 = ScannerAndEntryEngine.wilderRsi(closes),
                                series = sec[sym]?.series ?: "EQ",
                                bandPct = sec[sym]?.bandPct,
                                isCas = fo.contains(sym),
                                summary = "Last ${IndianMarketUtils.inr(last)} (${if (chg >= 0) "+" else ""}${"%.2f".format(chg)}%)"
                            )
                        }
                    }
                } else {
                    dailyHits
                }
            }
        }
    }

    suspend fun fetchTopOrbByRelativeVolume(
        exchange: String = "NSE",
        orMinutes: Int = 15,
        includeT2t: Boolean = false,
        config: DeskConfig,
        limit: Int = 5
    ): List<OrbQuickRow> = withContext(Dispatchers.Default) {
        val cleanEx = if (exchange.uppercase() == "BSE") "BSE" else "NSE"
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val fo = dataSource.loadFoSymbols()
        val sec = dataSource.loadSecList(live)
        val universe = dataSource.loadNifty50Symbols(live)
        val intraday = dataSource.loadIntradaySpark(universe, live, cleanEx)
        val asOf = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        ScannerAndEntryEngine.topOrbByRelativeVolume(
            intradayMap = intraday,
            secList = sec,
            foSet = fo,
            exchange = cleanEx,
            asOfIst = asOf,
            orMinutes = orMinutes,
            includeT2t = includeT2t,
            limit = limit
        )
    }

    suspend fun fetchMarketHeatmap(
        universe: HeatmapUniverse,
        metric: HeatmapIntensityMetric,
        watchlist: List<String>,
        exchange: String = "NSE",
        orMinutes: Int = 15,
        includeT2t: Boolean = false,
        config: DeskConfig
    ): List<MarketHeatmapCell> = withContext(Dispatchers.Default) {
        val cleanEx = if (exchange.uppercase() == "BSE") "BSE" else "NSE"
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val symbols = if (universe == HeatmapUniverse.WATCHLIST && watchlist.isNotEmpty()) {
            watchlist.mapNotNull { runCatching { IndianMarketUtils.parseSymbol(it, cleanEx).first }.getOrNull() }.distinct()
        } else {
            dataSource.loadNifty50Symbols(live)
        }
        val asOf = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        coroutineScope {
            val secDeferred = async { dataSource.loadSecList(live) }
            val intraDeferred = async { dataSource.loadIntradaySpark(symbols, live, cleanEx) }
            val dailyDeferred = async { dataSource.loadDailySpark(symbols, live) }
            ScannerAndEntryEngine.buildMarketHeatmap(
                intradayMap = intraDeferred.await(),
                dailyMap = dailyDeferred.await(),
                symbols = symbols,
                secList = secDeferred.await(),
                exchange = cleanEx,
                asOfIst = asOf,
                orMinutes = orMinutes,
                metric = metric,
                includeT2t = includeT2t
            )
        }
    }

    suspend fun evaluateEntry(
        rawSymbol: String,
        setup: String,
        levelPrice: Double?,
        orMinutes: Int,
        minVolumeX: Double,
        interval: String,
        config: DeskConfig
    ): EntryCheckResult = withContext(Dispatchers.Default) {
        val (sym, exch) = IndianMarketUtils.parseSymbol(rawSymbol, config.exchange)
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val chart = dataSource.loadSymbolIntradayChart(sym, exch, interval = interval, range = "5d", live = live)
        val asOf = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        ScannerAndEntryEngine.evaluateEntry(
            symbol = sym,
            exchange = exch,
            chart = chart,
            setup = setup,
            levelPrice = levelPrice,
            orMinutes = orMinutes,
            minVolumeX = minVolumeX,
            interval = interval,
            config = config,
            asOfIst = asOf
        )
    }

    // ---------------------------------------------------------------- Exit & Time Machine

    suspend fun buildTradePlan(
        entry: EntryCheckResult,
        stopRule: StopRuleType,
        stopParam: Double,
        targetRule: TargetRuleType,
        targetPriceParam: Double,
        config: DeskConfig
    ): Result<TradePlan> = withContext(Dispatchers.Default) {
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val fo = dataSource.loadFoSymbols()
        val sec = dataSource.loadSecList(live)
        val quote = dataSource.loadQuotePair(entry.symbol, entry.exchange, live)
        ExitAndTimeMachineEngine.buildTradePlan(
            entryResult = entry,
            stopRule = stopRule,
            stopParam = stopParam,
            targetRule = targetRule,
            targetPriceParam = targetPriceParam,
            config = config,
            foSymbols = fo,
            secBandPct = sec[entry.symbol]?.bandPct,
            prevClose = quote?.second
        )
    }

    suspend fun runTimeMachine(
        plan: TradePlan,
        config: DeskConfig,
        replays: Int = 10000,
        seed: Int = 19,
        window: Int = 2,
        qtyForNet: Int = 30
    ): Result<TimeMachineResult> = withContext(Dispatchers.Default) {
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val fo = dataSource.loadFoSymbols()
        val series60d = dataSource.load60d5mChart(plan.symbol, plan.exchange, live)
            ?: return@withContext Result.failure(
                IllegalArgumentException("60-day 5m history unavailable for ${plan.symbol} in offline mode (bundled 60d5m history is included for RELIANCE, or switch to Live Public Feed).")
            )
        ExitAndTimeMachineEngine.runTimeMachine(
            historySeries = series60d,
            plan = plan,
            foSymbols = fo,
            replays = replays,
            seed = seed,
            window = window,
            qtyForNet = qtyForNet
        )
    }

    // ---------------------------------------------------------------- Budget, Risk & Broker

    suspend fun sizeOrder(
        plan: TradePlan,
        config: DeskConfig,
        portfolio: PortfolioState,
        orderType: String,
        limitPrice: Double?,
        side: String = "BUY"
    ): Result<OrderProposal> = withContext(Dispatchers.Default) {
        val fo = dataSource.loadFoSymbols()
        BudgetRiskBrokerEngine.sizeOrder(plan, config, portfolio, fo, orderType, limitPrice, side = side)
    }

    suspend fun checkOrderRisk(
        order: OrderProposal,
        config: DeskConfig,
        portfolio: PortfolioState
    ): RiskVerdict = withContext(Dispatchers.Default) {
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val sec = dataSource.loadSecList(live)
        val holidays = dataSource.loadHolidays(live)
        val quote = dataSource.loadQuotePair(order.tradingsymbol, order.exchange, live)
        val nowIst = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        BudgetRiskBrokerEngine.checkOrderRisk(order, config, portfolio, sec, quote?.second, nowIst, holidays)
    }

    suspend fun confirmPaperOrder(
        order: OrderProposal,
        portfolio: PortfolioState,
        verdict: RiskVerdict,
        reply: String,
        config: DeskConfig
    ): Result<String> = withContext(Dispatchers.Default) {
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val nowIst = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        val res = if (order.transactionType.equals("SELL", ignoreCase = true)) {
            BudgetRiskBrokerEngine.applyPaperSellFill(order, portfolio, verdict, reply, nowIst)
        } else {
            BudgetRiskBrokerEngine.applyPaperBuyFill(order, portfolio, verdict, reply, nowIst)
        }
        res.map { (newPortfolio, record, msg) ->
            savePortfolio(newPortfolio)
            dao.insertBrokerRecord(record.toEntity())
            msg
        }
    }

    suspend fun confirmAndExecuteOrder(
        order: OrderProposal,
        portfolio: PortfolioState,
        verdict: RiskVerdict,
        reply: String,
        config: DeskConfig
    ): Result<String> = withContext(Dispatchers.Default) {
        val rv = verdict.results.firstOrNull { it.fingerprint == order.fingerprint }
            ?: return@withContext Result.failure(
                IllegalArgumentException("Order fingerprint does not match the latest Risk verdict. Re-run Risk Check.")
            )
        if (!rv.passed) {
            return@withContext Result.failure(
                IllegalArgumentException("Risk Check BLOCKED this order: ${rv.reasons.joinToString("; ")}")
            )
        }
        val (yesOk, yesMsg) = BudgetRiskBrokerEngine.parseYes(reply, order)
        if (!yesOk) {
            return@withContext Result.failure(IllegalArgumentException(yesMsg))
        }

        if (config.paper) {
            return@withContext confirmPaperOrder(order, portfolio, verdict, reply, config)
        }

        // REAL MODE: completely separate from Paper Mode — routes through selected broker adapter
        val brokerId = config.selectedBroker
        val creds = vault.getCredentials(brokerId)
        if (!creds.hasUsableAuth()) {
            return@withContext Result.failure(
                IllegalStateException("Real Mode requires saved ${brokerId.displayName} API credentials in Broker Settings.")
            )
        }
        val req = BrokerOrderRequest(
            symbol = order.tradingsymbol,
            exchange = order.exchange,
            side = order.transactionType.uppercase(),
            quantity = order.quantity,
            product = order.product,
            orderType = order.orderType,
            price = order.price ?: order.entryPrice,
            stopPrice = order.stopPrice,
            targetPrice = order.targetPrice,
            fingerprint = order.fingerprint
        )
        val execRes = brokerRegistry.adapterFor(brokerId).placeOrder(creds, req)
        execRes.map { exec ->
            val live = config.dataMode == DataMode.LIVE_PUBLIC
            val nowIst = IndianMarketUtils.nowIst(live, config.replayIsoIst)
            val record = BrokerRecord(
                timestampIst = nowIst.toString().replace("T", " "),
                date = nowIst.toLocalDate().toString(),
                fingerprint = order.fingerprint,
                symbol = order.tradingsymbol,
                exchange = order.exchange,
                side = order.transactionType.uppercase(),
                quantity = order.quantity,
                product = order.product,
                price = exec.executedPrice ?: order.entryPrice,
                stopPrice = order.stopPrice,
                targetPrice = order.targetPrice,
                chargesInr = order.charges.total,
                paper = false,
                status = "REAL_${exec.status}",
                confirmationReply = reply.trim(),
                plainSummary = "[${brokerId.displayName}] ${order.transactionType.uppercase()} ${order.quantity} ${order.exchange}:${order.tradingsymbol} @ ${IndianMarketUtils.inr(exec.executedPrice ?: order.entryPrice)} (#${exec.brokerOrderId})"
            )
            dao.insertBrokerRecord(record.toEntity())
            "REAL ORDER PLACED (${brokerId.displayName} #${exec.brokerOrderId}): ${exec.message}"
        }
    }

    suspend fun flattenPosition(
        position: PortfolioPosition,
        portfolio: PortfolioState,
        config: DeskConfig
    ): BrokerRecord = withContext(Dispatchers.Default) {
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val quote = dataSource.loadQuotePair(position.symbol, position.exchange, live)
        val price = IndianMarketUtils.roundToTick((quote?.first ?: position.lastPrice).coerceAtLeast(1.0), "nearest")
        val nowIst = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        val fo = dataSource.loadFoSymbols()
        val (cas, _) = IndianMarketUtils.casStatus(position.symbol, null, foSymbols = fo)
        val rules = IndianMarketUtils.sessionRules(cas, nowIst.toLocalDate().toString())
        val stopPx = position.stopPrice ?: IndianMarketUtils.roundToTick((price * 0.98).coerceAtLeast(0.5), "down")
        val targetPx = position.targetPrice ?: IndianMarketUtils.roundToTick(price * 1.04, "up")
        val synthPlan = TradePlan(
            symbol = position.symbol,
            exchange = position.exchange,
            tick = IndianMarketUtils.tickSize(price),
            side = "sell",
            product = position.product,
            date = nowIst.toLocalDate().toString(),
            setup = "exit",
            entry = price,
            entryTime = IndianMarketUtils.formatIstTime(nowIst),
            entryBar = 0,
            stop = stopPx,
            stopRule = "price",
            stopLabel = "Exit stop",
            target = targetPx,
            targetR = 1.0,
            targetLabel = "1.0R",
            riskPerShare = IndianMarketUtils.round2((price - stopPx).coerceAtLeast(0.1)),
            rewardRisk = 1.0,
            flatBy = rules.flatByDefault,
            cas = cas,
            warnings = emptyList()
        )
        val sellOrder = BudgetRiskBrokerEngine.sizeOrder(
            plan = synthPlan,
            config = config,
            portfolio = portfolio,
            foSymbols = fo,
            orderType = "MARKET",
            limitPrice = null,
            side = "SELL"
        ).getOrThrow()
        val verdict = checkOrderRisk(sellOrder, config, portfolio)
        if (!verdict.allPassed) {
            val why = verdict.results.firstOrNull()?.reasons?.joinToString("; ") ?: "Risk Agent vetoed exit"
            throw IllegalStateException("Risk Agent BLOCKED sell: $why")
        }

        if (config.paper) {
            val (newPort, rec) = BudgetRiskBrokerEngine.applyPaperFlatten(position, price, portfolio, nowIst)
            savePortfolio(newPort)
            dao.insertBrokerRecord(rec.toEntity())
            rec
        } else {
            val brokerId = config.selectedBroker
            val creds = vault.getCredentials(brokerId)
            if (!creds.hasUsableAuth()) {
                throw IllegalStateException("Real Mode requires saved ${brokerId.displayName} API credentials in Broker Settings.")
            }
            val exec = brokerRegistry.adapterFor(brokerId).placeOrder(
                creds,
                BrokerOrderRequest(
                    symbol = position.symbol,
                    exchange = position.exchange,
                    side = "SELL",
                    quantity = position.shares,
                    product = position.product,
                    orderType = "MARKET",
                    price = price,
                    fingerprint = sellOrder.fingerprint
                )
            ).getOrThrow()
            val charges = IndianMarketUtils.charges(0.0, position.shares * price, position.product, position.exchange).total
            val rec = BrokerRecord(
                timestampIst = nowIst.toString().replace("T", " "),
                date = nowIst.toLocalDate().toString(),
                fingerprint = sellOrder.fingerprint,
                symbol = position.symbol,
                exchange = position.exchange,
                side = "SELL",
                quantity = position.shares,
                product = position.product,
                price = exec.executedPrice ?: price,
                chargesInr = charges,
                paper = false,
                status = "REAL_${exec.status}",
                confirmationReply = "yes flatten ${position.symbol}",
                plainSummary = "[${brokerId.displayName}] SELL ${position.shares} ${position.exchange}:${position.symbol} @ ${IndianMarketUtils.inr(price)} (#${exec.brokerOrderId})"
            )
            dao.insertBrokerRecord(rec.toEntity())
            rec
        }
    }

    suspend fun refreshPaperQuotes(
        portfolio: PortfolioState,
        config: DeskConfig
    ): PortfolioState = withContext(Dispatchers.Default) {
        if (portfolio.positions.isEmpty()) return@withContext portfolio
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val updatedPositions = portfolio.positions.map { pos ->
            async {
                val quote = runCatching { dataSource.loadQuotePair(pos.symbol, pos.exchange, live) }.getOrNull()
                if (quote != null && quote.first > 0.0) {
                    pos.copy(lastPrice = IndianMarketUtils.round2(quote.first))
                } else {
                    pos
                }
            }
        }.awaitAll()
        val updated = portfolio.copy(positions = updatedPositions)
        savePortfolio(updated)
        updated
    }

    // ---------------------------------------------------------------- Alerts

    suspend fun addAlertsFromPlan(plan: TradePlan) {
        val existing = alertsFlow.first().associateBy { it.id }.toMutableMap()
        for (r in AlertsNewsAnalystInsidersEngine.rulesFromPlan(plan)) {
            existing[r.id] = r
        }
        saveAlerts(existing.values.toList())
    }

    suspend fun addCustomAlert(symbol: String, type: AlertType, value: Double?, timeHm: String?, label: String) {
        val (sym, exch) = IndianMarketUtils.parseSymbol(symbol, "NSE")
        val rule = AlertRule(
            id = "custom_${sym}_${UUID.randomUUID().toString().take(8)}",
            symbol = sym,
            exchange = exch,
            type = type,
            value = value,
            timeHm = timeHm,
            label = label.ifBlank { "$sym ${type.label}" }
        )
        dao.upsertAlerts(listOf(rule.toEntity()))
    }

    suspend fun checkAlertsNow(config: DeskConfig): AlertCheckSummary = withContext(Dispatchers.Default) {
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val currentAlerts = alertsFlow.first()
        val sec = dataSource.loadSecList(live)
        val quotes = mutableMapOf<String, Pair<Double, Double>>()
        for (sym in currentAlerts.map { it.symbol }.distinct()) {
            if (sym == "NIFTY") continue
            dataSource.loadQuotePair(sym, "NSE", live)?.let { quotes[sym] = it }
        }
        val nowIst = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        val summary = AlertsNewsAnalystInsidersEngine.evaluateAlerts(currentAlerts, quotes, sec, nowIst)
        saveAlerts(summary.alerts)
        summary
    }

    suspend fun rearmAllAlerts() = withContext(Dispatchers.IO) {
        val current = alertsFlow.first().map {
            it.copy(armed = true, lastTriggeredDate = null, lastTriggeredInfo = null)
        }
        saveAlerts(current)
    }

    // ---------------------------------------------------------------- News, Analyst, Insiders

    suspend fun fetchNews(rawSymbol: String, config: DeskConfig): NewsReport = withContext(Dispatchers.Default) {
        val (sym, exch) = IndianMarketUtils.parseSymbol(rawSymbol, config.exchange)
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val quote = dataSource.loadQuotePair(sym, exch, live)
        val (ann, rss, gnews) = dataSource.loadNewsRaw(sym, live)
        AlertsNewsAnalystInsidersEngine.buildNewsReport(
            symbol = sym,
            lastPrice = quote?.first,
            prevClose = quote?.second,
            nseAnnJson = ann,
            nseRssXml = rss,
            gnewsXml = gnews
        )
    }

    suspend fun fetchAnalyst(rawSymbol: String, basis: String, config: DeskConfig): Result<AnalystReport> =
        withContext(Dispatchers.Default) {
            val (sym, _) = IndianMarketUtils.parseSymbol(rawSymbol, config.exchange)
            val live = config.dataMode == DataMode.LIVE_PUBLIC
            val bundle = dataSource.loadAnalystRaw(sym, live)
            val allFilings = AlertsNewsAnalystInsidersEngine.extractFilingsList(bundle.integratedJson, bundle.legacyJson)
            if (allFilings.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No NSE quarterly financial filings found for $sym (bundled fixtures include RELIANCE & HDFCBANK; switch to Live Public Feed for others)."))
            }
            val matching = allFilings.filter { it.basis == basis }.ifEmpty { allFilings }
            var chosenBasis = matching.first().basis
            val parsed = mutableListOf<AlertsNewsAnalystInsidersEngine.ParsedXbrlQuarter>()
            for (meta in matching.take(8)) {
                val bytes = bundle.fetchXbrl(meta.xbrlFileName) ?: continue
                runCatching { AlertsNewsAnalystInsidersEngine.parseXbrlQuarter(bytes, meta) }
                    .getOrNull()?.let { parsed.add(it) }
            }
            if (parsed.isEmpty() && matching.size < allFilings.size) {
                val altFilings = allFilings.filter { it.basis != chosenBasis }
                if (altFilings.isNotEmpty()) {
                    chosenBasis = altFilings.first().basis
                    for (meta in altFilings.take(8)) {
                        val bytes = bundle.fetchXbrl(meta.xbrlFileName) ?: continue
                        runCatching { AlertsNewsAnalystInsidersEngine.parseXbrlQuarter(bytes, meta) }
                            .getOrNull()?.let { parsed.add(it) }
                    }
                }
            }
            if (parsed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Could not read XBRL files for $sym"))
            }
            val report = AlertsNewsAnalystInsidersEngine.buildAnalystReport(sym, parsed, bundle.eventsJson, chosenBasis)
            val basisNote = if (chosenBasis != basis) "No $basis results filed; showing $chosenBasis instead." else null
            Result.success(report.copy(basisNote = basisNote))
        }

    suspend fun fetchInsiders(rawSymbol: String?, config: DeskConfig): InsidersReport =
        withContext(Dispatchers.Default) {
            val sym = rawSymbol?.trim()?.takeIf { it.isNotEmpty() }?.let {
                runCatching { IndianMarketUtils.parseSymbol(it, config.exchange).first }.getOrNull()
            }
            val live = config.dataMode == DataMode.LIVE_PUBLIC
            val followed = dao.getFollowedInvestorsSnapshot().map { it.name }
            val bundle = dataSource.loadInsidersRaw(sym, live)
            val pit = bundle.pitJson?.let { AlertsNewsAnalystInsidersEngine.parsePitJson(it) }.orEmpty()
            val deals = mutableListOf<com.example.model.BulkBlockDeal>()
            bundle.bulkCsv?.let { deals.addAll(AlertsNewsAnalystInsidersEngine.parseBulkBlockCsv(it, "BULK", followed)) }
            bundle.blockCsv?.let { deals.addAll(AlertsNewsAnalystInsidersEngine.parseBulkBlockCsv(it, "BLOCK", followed)) }
            bundle.histDealsJson?.let { deals.addAll(AlertsNewsAnalystInsidersEngine.parseHistoricalDealsJson(it, "BLOCK", followed)) }
            AlertsNewsAnalystInsidersEngine.buildInsidersReport(sym, pit, deals)
        }

    suspend fun fetchWatchlistSentiment(
        watchlist: List<String>,
        config: DeskConfig
    ): WatchlistSentimentReport = withContext(Dispatchers.Default) {
        val cleanSymbols = watchlist
            .mapNotNull { runCatching { IndianMarketUtils.parseSymbol(it, config.exchange).first }.getOrNull() }
            .distinct()
        val live = config.dataMode == DataMode.LIVE_PUBLIC
        val nowIst = IndianMarketUtils.nowIst(live, config.replayIsoIst)
        val perSymbol = cleanSymbols.map { sym ->
            async {
                val news = runCatching { fetchNews(sym, config) }.getOrNull()
                val analyst = fetchAnalyst(sym, "consolidated", config).getOrNull()
                Triple(sym, news, analyst)
            }
        }.awaitAll()
        AlertsNewsAnalystInsidersEngine.computeWatchlistSentiment(
            watchlist = cleanSymbols,
            perSymbolInputs = perSymbol,
            computedAtIst = IndianMarketUtils.formatIstDateTime(nowIst)
        )
    }

    // ---------------------------------------------------------------- Entity Mappers

    private fun AlertRule.toEntity() = AlertRuleEntity(
        id = id,
        symbol = symbol,
        exchange = exchange,
        typeCode = type.code,
        value = value,
        timeHm = timeHm,
        label = label,
        armed = armed,
        lastTriggeredDate = lastTriggeredDate,
        lastTriggeredInfo = lastTriggeredInfo
    )

    private fun BrokerRecord.toEntity() = BrokerRecordEntity(
        timestampIst = timestampIst,
        date = date,
        fingerprint = fingerprint,
        symbol = symbol,
        exchange = exchange,
        side = side,
        quantity = quantity,
        product = product,
        price = price,
        stopPrice = stopPrice,
        targetPrice = targetPrice,
        chargesInr = chargesInr,
        paper = paper,
        status = status,
        confirmationReply = confirmationReply,
        plainSummary = plainSummary
    )
}
