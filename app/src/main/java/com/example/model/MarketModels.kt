package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class DataMode {
    OFFLINE_REPLAY,
    LIVE_PUBLIC
}

@Serializable
data class DeskConfig(
    val budget: Double = 50000.0,
    val cashBufferPct: Double = 10.0,
    val maxPositionPct: Double = 75.0,
    val maxOrderInr: Double = 40000.0,
    val minOrderInr: Double = 100.0,
    val maxLossPerTradeInr: Double = 500.0,
    val maxDailyLossInr: Double = 1500.0,
    val maxTradesPerDay: Int = 3,
    val maxOrdersPerDay: Int = 10,
    val minRewardRisk: Double = 1.0,
    val requireStop: Boolean = true,
    val noEntriesBefore: String = "09:30",
    val noEntriesAfter: String = "14:45",
    val flatBy: String = "auto",
    val product: String = "MIS",
    val exchange: String = "NSE",
    val broker: String = "zerodha",
    val paper: Boolean = true,
    val blocked: List<String> = emptyList(),
    val allowOptions: Boolean = false,
    val allowMargin: Boolean = false,
    val allowShort: Boolean = false,
    val casSymbolsExtra: List<String> = emptyList(),
    val nonCasSymbols: List<String> = emptyList(),
    val dataMode: DataMode = DataMode.OFFLINE_REPLAY,
    val replayIsoIst: String = "2026-10-19 10:15",
    val orbDurationMinutes: Int = 15,
    val orbMinVolumeX: Double = 1.0,
    val orbStopRule: StopRuleType = StopRuleType.OR_MID,
    val orbTargetRule: TargetRuleType = TargetRuleType.R1,
    val selectedBroker: IndianBrokerId = IndianBrokerId.ZERODHA,
    val autoIntervalMinutes: Int = 5
)

@Serializable
data class SessionRules(
    val open: String,
    val close: String,
    val squareOff: String,
    val flatByDefault: String,
    val noEntriesAfterDefault: String,
    val bars5m: Int,
    val cas: Boolean?,
    val casSource: String,
    val label: String
)

@Serializable
data class MarketClockStatus(
    val istDate: String,
    val istTime: String,
    val dayOfWeek: String,
    val isTradingDay: Boolean,
    val phaseLabel: String,
    val detail: String,
    val holidayName: String? = null,
    val isLiveMode: Boolean = false
)

@Serializable
data class OhlcvBar(
    val epochSec: Long,
    val date: String,
    val timeHm: String,
    val slot: Int,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val filled: Boolean = false
)

@Serializable
data class UniverseSecurity(
    val symbol: String,
    val series: String = "EQ",
    val name: String = "",
    val bandPct: Double? = null,
    val bandText: String = "No Band",
    val isFoCas: Boolean = false
)

@Serializable
enum class ScanMode {
    ORB,
    DAILY,
    WATCHLIST
}

@Serializable
data class ScanHit(
    val symbol: String,
    val exchange: String = "NSE",
    val setup: String,
    val badge: String,
    val lastPrice: Double,
    val prevClose: Double,
    val changePct: Double,
    val volumeX: Double? = null,
    val rsi14: Double? = null,
    val orHigh: Double? = null,
    val orLow: Double? = null,
    val breakoutLevel: Double? = null,
    val triggerTime: String? = null,
    val nearUpperCircuit: Boolean = false,
    val series: String = "EQ",
    val bandPct: Double? = null,
    val isCas: Boolean? = null,
    val summary: String
)

@Serializable
enum class OrbBreakoutState(val label: String) {
    BROKEN_ORH("ORH BREAKOUT"),
    AT_HIGH("TESTING ORH"),
    INSIDE_OR("INSIDE OR"),
    BELOW_ORL("BELOW ORL")
}

@Serializable
data class OrbQuickRow(
    val rank: Int,
    val symbol: String,
    val exchange: String = "NSE",
    val lastPrice: Double,
    val prevClose: Double,
    val changePct: Double,
    val relativeVolumeX: Double,
    val orHigh: Double,
    val orLow: Double,
    val orRangePct: Double,
    val breakoutState: OrbBreakoutState,
    val triggerTime: String? = null,
    val isCas: Boolean? = null,
    val nearUpperCircuit: Boolean = false,
    val summary: String
)

@Serializable
enum class HeatmapUniverse(val label: String) {
    NIFTY_50("Nifty 50"),
    WATCHLIST("Watchlist")
}

@Serializable
enum class HeatmapIntensityMetric(val label: String) {
    COMBINED("Vol × Volatility"),
    VOLATILITY("Volatility %"),
    RELATIVE_VOLUME("Rel Volume")
}

@Serializable
data class MarketHeatmapCell(
    val symbol: String,
    val exchange: String = "NSE",
    val lastPrice: Double,
    val changePct: Double,
    val volatilityPct: Double,
    val relativeVolumeX: Double,
    val orHigh: Double? = null,
    val orLow: Double? = null,
    val intensity: Float
)

@Serializable
enum class EntryStatus {
    TRIGGERED,
    WAITING,
    EXPIRED,
    NO_DATA
}

@Serializable
data class EntryCheckResult(
    val symbol: String,
    val exchange: String,
    val date: String,
    val setup: String,
    val interval: String,
    val status: EntryStatus,
    val entryPrice: Double? = null,
    val entryTime: String? = null,
    val entryBar: Int? = null,
    val level: Double? = null,
    val orHigh: Double? = null,
    val orLow: Double? = null,
    val orMinutes: Int = 15,
    val volumeX: Double? = null,
    val minVolumeX: Double = 1.0,
    val lastClose: Double? = null,
    val lastTime: String? = null,
    val message: String,
    val bars: List<OhlcvBar> = emptyList()
)

@Serializable
enum class StopRuleType(val code: String, val label: String) {
    OR_MID("or_mid", "OR Midpoint"),
    OR_LOW("or_low", "OR Low"),
    PCT("pct", "Stop %"),
    ATR("atr", "ATR(14)"),
    PRICE("price", "Exact Price")
}

@Serializable
enum class TargetRuleType(val code: String, val label: String) {
    R1("1R", "1.0R"),
    R1_5("1.5R", "1.5R"),
    R2("2R", "2.0R"),
    PRICE("price", "Exact Price")
}

@Serializable
data class OpeningRange(
    val high: Double,
    val low: Double,
    val minutes: Int = 15
)

@Serializable
data class TradePlan(
    val symbol: String,
    val exchange: String = "NSE",
    val currency: String = "INR",
    val tick: Double,
    val side: String = "buy",
    val product: String = "MIS",
    val date: String,
    val setup: String = "orb",
    val entry: Double,
    val entryTime: String,
    val entryBar: Int,
    val interval: String = "5m",
    val openingRange: OpeningRange? = null,
    val stop: Double,
    val stopRule: String,
    val stopLabel: String,
    val target: Double,
    val targetR: Double,
    val targetLabel: String,
    val riskPerShare: Double,
    val rewardRisk: Double,
    val flatBy: String,
    val cas: Boolean? = null,
    val warnings: List<String> = emptyList()
)

@Serializable
data class BandRow(
    val barIndex: Int,
    val p5: Double,
    val p25: Double,
    val p50: Double,
    val p75: Double,
    val p95: Double
)

@Serializable
data class HistogramData(
    val lo: Double,
    val hi: Double,
    val bins: List<Int>
)

@Serializable
data class BacktestTrade(
    val date: String,
    val time: String,
    val entry: Double,
    val stop: Double,
    val target: Double,
    val outcome: String,
    val r: Double,
    val rNet: Double,
    val qty: Int
)

@Serializable
data class TimeMachineResult(
    val symbol: String,
    val exchange: String,
    val date: String,
    val until: String,
    val sessionsUsed: Int,
    val skippedSessions: Int,
    val replays: Int,
    val seed: Int,
    val window: Int,
    val barsLeft: Int,
    val pTarget: Double,
    val pStop: Double,
    val pNeither: Double,
    val pGreen: Double,
    val medianClose: Double,
    val p5Close: Double,
    val p95Close: Double,
    val avgR: Double,
    val avgRNet: Double,
    val chargesR: Double,
    val rAtTarget: Double,
    val qtyForNet: Int,
    val verdict: String,
    val verdictNotes: List<String>,
    val bands: List<BandRow>,
    val samplePaths: List<List<Double>>,
    val hist: HistogramData,
    val historyTrades: List<BacktestTrade>,
    val warnings: List<String> = emptyList()
)

@Serializable
data class ChargesBreakdown(
    val brokerage: Double,
    val stt: Double,
    val exchange: Double,
    val sebi: Double,
    val stamp: Double,
    val gst: Double,
    val total: Double
)

@Serializable
data class OrderLeg(
    val variety: String = "regular",
    val exchange: String,
    val tradingsymbol: String,
    val transactionType: String = "SELL",
    val quantity: Int,
    val product: String,
    val validity: String = "DAY",
    val tag: String = "indiabudget",
    val orderType: String,
    val triggerPrice: Double? = null,
    val price: Double? = null
)

@Serializable
data class GttOcoLeg(
    val conditionSymbol: String,
    val lowerTriggerValue: Double,
    val lowerLimitPrice: Double,
    val upperTriggerValue: Double,
    val upperLimitPrice: Double,
    val quantity: Int,
    val product: String = "CNC"
)

@Serializable
data class ExitPlan(
    val kind: String,
    val whenText: String,
    val stopLeg: OrderLeg? = null,
    val targetLeg: OrderLeg? = null,
    val gtt: GttOcoLeg? = null
)

@Serializable
data class OrderProposal(
    val variety: String = "regular",
    val exchange: String,
    val tradingsymbol: String,
    val transactionType: String = "BUY",
    val quantity: Int,
    val product: String,
    val orderType: String = "MARKET",
    val price: Double? = null,
    val validity: String = "DAY",
    val tag: String = "indiabudget",
    val exitPlan: ExitPlan? = null,
    val why: String,
    val entryPrice: Double,
    val stopPrice: Double,
    val targetPrice: Double,
    val riskPerShare: Double,
    val charges: ChargesBreakdown,
    val maxLossInr: Double,
    val orderValueInr: Double,
    val boundBy: String,
    val setup: String,
    val date: String,
    val entryTime: String,
    val flatBy: String,
    val cas: Boolean?,
    val squareOff: String,
    val sessionClose: String,
    val fingerprint: String,
    val kitePayloadJson: String
)

@Serializable
data class AllocationRow(
    val symbol: String,
    val exchange: String,
    val weightPct: Double,
    val price: Double,
    val shares: Int,
    val amountInr: Double,
    val chargesInr: Double
)

@Serializable
data class RiskCheckItem(
    val rule: String,
    val passed: Boolean,
    val detail: String,
    val isWarning: Boolean = false
)

@Serializable
data class RiskResult(
    val index: Int,
    val fingerprint: String,
    val symbol: String,
    val exchange: String,
    val passed: Boolean,
    val reasons: List<String>,
    val warnings: List<String>,
    val items: List<RiskCheckItem>
)

@Serializable
data class RiskVerdict(
    val date: String,
    val checkedAtIst: String,
    val allPassed: Boolean,
    val results: List<RiskResult>
)

@Serializable
data class FifoLot(
    val date: String,
    val shares: Int,
    val price: Double,
    val product: String
)

@Serializable
data class PortfolioPosition(
    val symbol: String,
    val exchange: String,
    val shares: Int,
    val avgPrice: Double,
    val lastPrice: Double,
    val product: String,
    val entryDate: String,
    val stopPrice: Double? = null,
    val targetPrice: Double? = null,
    val lots: List<FifoLot> = emptyList()
)

@Serializable
data class PortfolioState(
    val cash: Double = 50000.0,
    val realizedPnlToday: Double = 0.0,
    val tradesToday: Int = 0,
    val ordersToday: Int = 0,
    val lastDay: String = "2026-10-19",
    val positions: List<PortfolioPosition> = emptyList()
)

@Serializable
data class BrokerRecord(
    val id: Long = 0,
    val timestampIst: String,
    val date: String,
    val fingerprint: String,
    val symbol: String,
    val exchange: String,
    val side: String,
    val quantity: Int,
    val product: String,
    val price: Double,
    val stopPrice: Double? = null,
    val targetPrice: Double? = null,
    val chargesInr: Double,
    val paper: Boolean,
    val status: String,
    val confirmationReply: String,
    val plainSummary: String
)

@Serializable
enum class AlertType(val code: String, val label: String) {
    ABOVE("above", "Above Price"),
    BELOW("below", "Below Price"),
    PCT_MOVE("pct_move", "% Move"),
    CIRCUIT("circuit", "Near Circuit"),
    OPENING_BELL("opening_bell", "Opening Bell"),
    PRE_OPEN("pre_open", "Pre-Open Phase"),
    TIME("time", "IST Time / Square-Off")
}

@Serializable
data class AlertRule(
    val id: String,
    val symbol: String,
    val exchange: String = "NSE",
    val type: AlertType,
    val value: Double? = null,
    val timeHm: String? = null,
    val label: String,
    val armed: Boolean = true,
    val lastTriggeredDate: String? = null,
    val lastTriggeredInfo: String? = null
)

@Serializable
data class AlertCheckSummary(
    val checkedAtIst: String,
    val triggeredCount: Int,
    val alerts: List<AlertRule>,
    val hits: List<String>
)

@Serializable
data class NewsItem(
    val id: String,
    val source: String,
    val symbol: String,
    val timestamp: String,
    val title: String,
    val summary: String,
    val tags: List<String>,
    val important: Boolean,
    val routine: Boolean,
    val link: String? = null
)

@Serializable
data class NewsReport(
    val symbol: String,
    val lastPrice: Double?,
    val prevClose: Double?,
    val changePct: Double?,
    val movingFlag: Boolean,
    val items: List<NewsItem>
)

@Serializable
data class FilingQuarter(
    val end: String,
    val fiscal: String,
    val basis: String,
    val format: String,
    val audited: String?,
    val filed: String?,
    val revenueCr: Double?,
    val otherIncomeCr: Double?,
    val ebitdaCr: Double?,
    val operatingProfitCr: Double?,
    val niiCr: Double?,
    val provisionsCr: Double?,
    val pbtCr: Double?,
    val netProfitCr: Double?,
    val exceptionalCr: Double?,
    val epsDiluted: Double?,
    val epsAdjustedTodayShares: Double?,
    val epsAdjusted: Boolean,
    val ebitdaMarginPct: Double?,
    val operatingMarginPct: Double?,
    val netMarginPct: Double?,
    val revenueYoyPct: Double?,
    val netProfitYoyPct: Double?,
    val niiYoyPct: Double?,
    val epsYoyPct: Double?
)

@Serializable
data class AnalystReport(
    val symbol: String,
    val entityName: String,
    val basis: String,
    val basisNote: String?,
    val format: String,
    val quarters: List<FilingQuarter>,
    val ttmRevenueCr: Double?,
    val ttmProfitCr: Double?,
    val ttmNiiCr: Double?,
    val ttmEps: Double?,
    val whatChanged: List<String>,
    val upcomingEvent: String?,
    val transcriptLinks: List<Pair<String, String>> = emptyList()
)

@Serializable
data class InsiderFiling(
    val symbol: String,
    val company: String,
    val acquirer: String,
    val category: String,
    val transactionType: String,
    val mode: String,
    val shares: Double,
    val valueInr: Double,
    val txDate: String,
    val intimationDate: String,
    val broadcastDate: String,
    val lagTradingDays: Int?,
    val isLate: Boolean
)

@Serializable
data class BulkBlockDeal(
    val date: String,
    val dealType: String, // BULK or BLOCK
    val symbol: String,
    val securityName: String,
    val clientName: String,
    val side: String, // BUY or SELL
    val quantity: Long,
    val price: Double,
    val valueCr: Double,
    val matchedFollowInvestor: String? = null,
    val sameDayBothSides: Boolean = false
)

@Serializable
data class InsidersReport(
    val symbol: String?,
    val pitFilings: List<InsiderFiling>,
    val deals: List<BulkBlockDeal>,
    val followedMatches: List<BulkBlockDeal>
)

@Serializable
enum class SentimentVerdict(val label: String) {
    BEARISH("Bearish"),
    MILDLY_BEARISH("Lean Bearish"),
    NEUTRAL("Neutral"),
    MILDLY_BULLISH("Lean Bullish"),
    BULLISH("Bullish")
}

@Serializable
data class SymbolSentiment(
    val symbol: String,
    val score: Int, // 0 (Extreme Bearish) .. 100 (Extreme Bullish)
    val verdict: SentimentVerdict,
    val newsScore: Int,
    val analystScore: Int?,
    val newsItemsCount: Int,
    val importantNewsCount: Int,
    val changePct: Double? = null,
    val newsSummary: String,
    val analystSummary: String? = null
)

@Serializable
data class WatchlistSentimentReport(
    val watchlist: List<String>,
    val overallScore: Int,
    val verdict: SentimentVerdict,
    val newsAvgScore: Int,
    val analystAvgScore: Int?,
    val xbrlCoveredCount: Int,
    val bullishCount: Int,
    val bearishCount: Int,
    val neutralCount: Int,
    val computedAtIst: String,
    val symbols: List<SymbolSentiment>
)

@Serializable
enum class IndianBrokerId(
    val code: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val requiresClientId: Boolean,
    val requiresApiSecret: Boolean,
    val supportsCustomBaseUrl: Boolean,
    val authHint: String
) {
    ZERODHA(
        code = "zerodha",
        displayName = "Zerodha Kite",
        defaultBaseUrl = "https://api.kite.trade",
        requiresClientId = false,
        requiresApiSecret = true,
        supportsCustomBaseUrl = false,
        authHint = "Uses API Key + Access Token (X-Kite-Version: 3)"
    ),
    UPSTOX(
        code = "upstox",
        displayName = "Upstox",
        defaultBaseUrl = "https://api.upstox.com/v2",
        requiresClientId = false,
        requiresApiSecret = true,
        supportsCustomBaseUrl = false,
        authHint = "Uses OAuth2 Access Token (Bearer token) + API Key/Secret"
    ),
    ANGEL_ONE(
        code = "angel_one",
        displayName = "Angel One (SmartAPI)",
        defaultBaseUrl = "https://apiconnect.angelone.in",
        requiresClientId = true,
        requiresApiSecret = true,
        supportsCustomBaseUrl = false,
        authHint = "Uses SmartAPI Private Key, Client ID & JWT Access Token"
    ),
    FYERS(
        code = "fyers",
        displayName = "Fyers",
        defaultBaseUrl = "https://api-t1.fyers.in/api/v3",
        requiresClientId = false,
        requiresApiSecret = true,
        supportsCustomBaseUrl = false,
        authHint = "Uses App ID (API Key) + Access Token (<appId>:<token>)"
    ),
    DHAN(
        code = "dhan",
        displayName = "Dhan",
        defaultBaseUrl = "https://api.dhan.co/v2",
        requiresClientId = true,
        requiresApiSecret = false,
        supportsCustomBaseUrl = false,
        authHint = "Uses Dhan Client ID + Access Token headers"
    ),
    GENERIC(
        code = "generic",
        displayName = "Generic / Custom Broker",
        defaultBaseUrl = "",
        requiresClientId = false,
        requiresApiSecret = true,
        supportsCustomBaseUrl = true,
        authHint = "Custom REST Base URL with /orders, /positions, /funds"
    );

    companion object {
        fun fromCode(code: String?): IndianBrokerId =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: ZERODHA
    }
}

@Serializable
data class BrokerCredentials(
    val brokerId: IndianBrokerId,
    val apiKey: String = "",
    val apiSecret: String = "",
    val accessToken: String = "",
    val requestToken: String = "",
    val clientId: String = "",
    val baseUrl: String = ""
) {
    fun hasUsableAuth(): Boolean {
        val hasTokenOrRequest = accessToken.isNotBlank() || requestToken.isNotBlank()
        return when (brokerId) {
            IndianBrokerId.ZERODHA -> apiKey.isNotBlank() && hasTokenOrRequest
            IndianBrokerId.UPSTOX -> hasTokenOrRequest
            IndianBrokerId.ANGEL_ONE -> apiKey.isNotBlank() && clientId.isNotBlank() && hasTokenOrRequest
            IndianBrokerId.FYERS -> apiKey.isNotBlank() && hasTokenOrRequest
            IndianBrokerId.DHAN -> clientId.isNotBlank() && hasTokenOrRequest
            IndianBrokerId.GENERIC -> baseUrl.isNotBlank() && hasTokenOrRequest && apiKey.isNotBlank()
        }
    }
}

@Serializable
data class BrokerOrderRequest(
    val symbol: String,
    val exchange: String = "NSE",
    val side: String = "BUY", // BUY or SELL
    val quantity: Int,
    val product: String = "MIS", // MIS or CNC
    val orderType: String = "MARKET", // MARKET or LIMIT
    val price: Double? = null,
    val triggerPrice: Double? = null,
    val stopPrice: Double? = null,
    val targetPrice: Double? = null,
    val fingerprint: String = ""
)

@Serializable
data class BrokerExecutionResult(
    val brokerId: IndianBrokerId,
    val brokerOrderId: String,
    val status: String,
    val message: String,
    val executedPrice: Double? = null,
    val rawPayload: String = ""
)

@Serializable
data class BrokerFundsSnapshot(
    val brokerId: IndianBrokerId,
    val availableCash: Double,
    val usedMargin: Double = 0.0,
    val collateral: Double = 0.0,
    val netBalance: Double = availableCash,
    val fetchedAtIst: String = ""
)

@Serializable
data class BrokerRemoteOrder(
    val orderId: String,
    val symbol: String,
    val exchange: String,
    val side: String,
    val quantity: Int,
    val product: String,
    val orderType: String,
    val price: Double,
    val status: String,
    val timestampIst: String = ""
)

@Serializable
data class LivePnlSummary(
    val isRealMode: Boolean,
    val modeLabel: String,
    val equity: Double,
    val availableCash: Double,
    val investedValue: Double,
    val openUnrealizedPnl: Double,
    val realizedPnlToday: Double,
    val dayPnl: Double,
    val openPositionsCount: Int,
    val lastUpdatedIst: String = ""
)

@Serializable
enum class AutoIntervalOption(val minutes: Int, val label: String, val shortLabel: String) {
    MIN_5(5, "5 min", "5m"),
    MIN_15(15, "15 min", "15m"),
    MIN_30(30, "30 min", "30m"),
    HOUR_1(60, "1 hour", "1h");

    companion object {
        fun fromMinutes(minutes: Int): AutoIntervalOption =
            entries.firstOrNull { it.minutes == minutes } ?: MIN_5
    }
}

@Serializable
data class AutoModeStatus(
    val enabled: Boolean = false,
    val cycleRunning: Boolean = false,
    val intervalMinutes: Int = 5,
    val countdownSec: Int = 300,
    val nextRunIst: String = "--",
    val lastRunIst: String? = null,
    val cyclesCompleted: Int = 0,
    val autoStopReason: String? = null
)

@Serializable
data class AutoActivityEvent(
    val id: String,
    val timestampIst: String,
    val title: String,
    val detail: String,
    val isWarning: Boolean = false,
    val isError: Boolean = false
)



