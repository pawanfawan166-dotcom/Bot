package com.example.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.domain.IndianMarketUtils
import com.example.model.AnalystReport
import com.example.model.EntryCheckResult
import com.example.model.InsidersReport
import com.example.model.MarketClockStatus
import com.example.model.NewsReport
import com.example.model.OrderProposal
import com.example.model.RiskVerdict
import com.example.model.ScanHit
import com.example.model.TimeMachineResult
import com.example.model.TradePlan
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AdvisorCandidateDecision(
    val rankedSymbols: List<String>,
    val rationale: String,
    val aiSource: String
)

data class AdvisorIntelDecision(
    val proceed: Boolean,
    val convictionNote: String,
    val riskFlags: List<String>,
    val aiSource: String
)

data class AdvisorNarrative(
    val summary: String,
    val aiSource: String
)

interface AgentAdvisor {
    suspend fun prioritizeCandidates(
        hits: List<ScanHit>,
        clockStatus: MarketClockStatus
    ): AdvisorCandidateDecision

    suspend fun evaluateNewsAndAnalyst(
        symbol: String,
        newsReport: NewsReport?,
        analystReport: AnalystReport?,
        insidersReport: InsidersReport?
    ): AdvisorIntelDecision

    suspend fun summarizeEntryAndOdds(
        entry: EntryCheckResult,
        plan: TradePlan,
        timeMachine: TimeMachineResult?
    ): AdvisorNarrative

    suspend fun explainRiskAndVeto(
        order: OrderProposal?,
        verdict: RiskVerdict
    ): AdvisorNarrative

    suspend fun summarizeCycleForAlerts(
        symbol: String,
        paperSummary: String,
        alertsArmedCount: Int,
        verdictPassed: Boolean
    ): AdvisorNarrative
}

/**
 * Hybrid Gemini 3.8 Flash advisor using Firebase AI Logic structured Schema.obj JSON output.
 * Streams responses on Dispatchers.IO for low TTFT, performs a fast connectivity preflight,
 * and transparently falls back to deterministic Kotlin reasoning on offline/timeout/App Check errors.
 */
class GeminiAgentAdvisor(
    private val context: Context? = null,
    private val hasNetwork: (() -> Boolean)? = null
) : AgentAdvisor {

    companion object {
        private const val TAG = "GeminiAgentAdvisor"
        private const val MODEL_NAME = "gemini-3.8-flash"
        private const val TIMEOUT_MS = 45_000L
        private const val CIRCUIT_COOLDOWN_MS = 90_000L
    }

    @Volatile
    private var aiBypassUntilMs: Long = 0L

    @Volatile
    private var aiBypassReason: String = ""

    private fun isNetworkAvailable(): Boolean {
        hasNetwork?.let { return runCatching { it() }.getOrDefault(true) }
        val ctx = context ?: return true
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching true
            val network = cm.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    private fun activeBypassReason(): String? {
        if (!isNetworkAvailable()) return "Offline"
        return if (System.currentTimeMillis() < aiBypassUntilMs) aiBypassReason else null
    }

    private fun tripCircuit(reason: String) {
        aiBypassReason = reason
        aiBypassUntilMs = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS
    }

    private fun resetCircuit() {
        aiBypassReason = ""
        aiBypassUntilMs = 0L
    }

    private val candidateModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "rankedSymbols" to Schema.array(
                    items = Schema.string(),
                    description = "Top 1 to 3 NSE symbols ranked best-first for long intraday paper entry"
                ),
                "rationale" to Schema.string(
                    description = "Concise 1-2 sentence explanation citing volume/change/RSI"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val intelModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "proceed" to Schema.boolean(
                    description = "True if news, quarterly financials, and insider/deal flows are acceptable for long intraday trade"
                ),
                "convictionNote" to Schema.string(
                    description = "Concise 1-2 sentence synthesis of news headlines, XBRL results, and insider deals"
                ),
                "riskFlags" to Schema.array(
                    items = Schema.string(),
                    description = "Short risk flags found, or empty array"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val summaryModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "summary" to Schema.string(
                    description = "Crisp 1-2 sentence institutional trading desk explanation"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private suspend fun generateStructuredJson(model: GenerativeModel, prompt: String): JSONObject =
        withContext(Dispatchers.IO) {
            val raw = withTimeout(TIMEOUT_MS) {
                val acc = StringBuilder()
                model.generateContentStream(prompt).collect { chunk ->
                    chunk.text?.let { acc.append(it) }
                }
                acc.toString().trim()
            }
            if (raw.isBlank()) throw IllegalStateException("Empty Gemini response")
            JSONObject(raw)
        }

    override suspend fun prioritizeCandidates(
        hits: List<ScanHit>,
        clockStatus: MarketClockStatus
    ): AdvisorCandidateDecision {
        if (hits.isEmpty()) {
            return AdvisorCandidateDecision(
                rankedSymbols = emptyList(),
                rationale = "No candidates cleared the scanner universe thresholds.",
                aiSource = "Deterministic Engine"
            )
        }
        val deterministicTop = hits.sortedByDescending {
            val volScore = (it.volumeX ?: 1.0) * 10.0
            val moveScore = it.changePct
            val orbBonus = if (it.setup == "orb") 20.0 else 0.0
            orbBonus + volScore + moveScore
        }.map { it.symbol }.distinct().take(3)

        activeBypassReason()?.let { reason ->
            return AdvisorCandidateDecision(
                rankedSymbols = deterministicTop,
                rationale = "Deterministic ranking selected ${deterministicTop.joinToString(", ")} from scanner hits.",
                aiSource = "Deterministic Fallback ($reason)"
            )
        }

        val prompt = buildString {
            appendLine("You are the Scanner Agent for an Indian NSE equity paper-trading desk (LONG ONLY).")
            appendLine("Clock IST: ${clockStatus.istDate} ${clockStatus.istTime} (${clockStatus.phaseLabel}).")
            appendLine("Select the top 1 to 3 strongest intraday long candidates from these scan hits:")
            hits.take(10).forEach { h ->
                appendLine("- ${h.symbol}: setup=${h.setup}, price=${h.lastPrice}, chg=${h.changePct}%, volX=${h.volumeX ?: "-"}, rsi=${h.rsi14 ?: "-"}, summary=${h.summary}")
            }
        }

        return try {
            val obj = generateStructuredJson(candidateModel, prompt)
            val arr = obj.optJSONArray("rankedSymbols")
            val aiSymbols = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val sym = arr.optString(i).trim().uppercase()
                        if (sym.isNotEmpty() && hits.any { it.symbol == sym }) add(sym)
                    }
                }
            }
            val finalSymbols = aiSymbols.ifEmpty { deterministicTop }
            val rationale = obj.optString("rationale").ifBlank {
                "Selected ${finalSymbols.joinToString(", ")} on momentum and volume confirmation."
            }
            resetCircuit()
            AdvisorCandidateDecision(
                rankedSymbols = finalSymbols,
                rationale = rationale,
                aiSource = "Gemini 3.8 Flash"
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Scanner Gemini call timed out after ${TIMEOUT_MS}ms; using deterministic fallback")
            tripCircuit("AI timeout")
            AdvisorCandidateDecision(
                rankedSymbols = deterministicTop,
                rationale = "Deterministic ranking selected ${deterministicTop.joinToString(", ")} (AI timeout after ${TIMEOUT_MS / 1000}s).",
                aiSource = "Deterministic Fallback (AI timeout)"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errTag = e.javaClass.simpleName.ifBlank { "AIError" }
            Log.e(TAG, "Scanner Gemini call failed", e)
            tripCircuit(errTag)
            AdvisorCandidateDecision(
                rankedSymbols = deterministicTop,
                rationale = "Deterministic ranking selected ${deterministicTop.joinToString(", ")} from scanner hits.",
                aiSource = "Deterministic Fallback ($errTag)"
            )
        }
    }

    override suspend fun evaluateNewsAndAnalyst(
        symbol: String,
        newsReport: NewsReport?,
        analystReport: AnalystReport?,
        insidersReport: InsidersReport?
    ): AdvisorIntelDecision {
        val flags = mutableListOf<String>()
        if (newsReport?.movingFlag == true) {
            flags += "Large intraday move (${newsReport.changePct ?: 0.0}%)"
        }
        val importantNews = newsReport?.items?.filter { it.important }.orEmpty()
        importantNews.take(2).forEach { flags += "! ${it.tags.joinToString("/")}: ${it.title.take(55)}" }
        analystReport?.whatChanged?.take(2)?.forEach { flags += it }
        val latePit = insidersReport?.pitFilings?.count { it.isLate } ?: 0
        if (latePit > 0) flags += "$latePit late SEBI PIT filing(s) (>4d)"
        val churnCount = insidersReport?.deals?.count { it.sameDayBothSides } ?: 0
        if (churnCount > 0) flags += "$churnCount same-day Buy+Sell churn deal(s)"

        val fallbackProceed = true
        val fallbackNote = buildString {
            append("$symbol cleared news/fundamental checks: ")
            append("${newsReport?.items?.size ?: 0} news/filing item(s)")
            analystReport?.let {
                append(", ${it.format} (${it.basis}) ${it.quarters.size}Q loaded")
                it.ttmProfitCr?.let { pat -> append(", TTM PAT ₹${IndianMarketUtils.round2(pat)} Cr") }
            }
            insidersReport?.let {
                append(", ${it.pitFilings.size} PIT & ${it.deals.size} bulk/block deals.")
            }
        }

        activeBypassReason()?.let { reason ->
            return AdvisorIntelDecision(
                proceed = fallbackProceed,
                convictionNote = fallbackNote,
                riskFlags = flags,
                aiSource = "Deterministic Fallback ($reason)"
            )
        }

        val prompt = buildString {
            appendLine("You are the News & Fundamental Analyst Agent for NSE symbol $symbol (long-only intraday paper desk).")
            appendLine("Moving >=3%: ${newsReport?.movingFlag ?: false}, chgPct=${newsReport?.changePct ?: "n/a"}")
            appendLine("Important News (!): ${importantNews.take(3).joinToString(" | ") { it.title }}")
            appendLine("Analyst highlights: ${analystReport?.whatChanged?.joinToString(" | ") ?: "No XBRL filing loaded"}")
            appendLine("Insiders/Deals: PIT=${insidersReport?.pitFilings?.size ?: 0} (late=$latePit), Deals=${insidersReport?.deals?.size ?: 0} (churn=$churnCount)")
            appendLine("Should a long intraday setup proceed for entry analysis?")
        }

        return try {
            val obj = generateStructuredJson(intelModel, prompt)
            val aiFlags = buildList {
                val arr = obj.optJSONArray("riskFlags")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i).trim()
                        if (s.isNotEmpty()) add(s)
                    }
                }
            }
            resetCircuit()
            AdvisorIntelDecision(
                proceed = obj.optBoolean("proceed", fallbackProceed),
                convictionNote = obj.optString("convictionNote").ifBlank { fallbackNote },
                riskFlags = aiFlags.ifEmpty { flags },
                aiSource = "Gemini 3.8 Flash"
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Intel Gemini call timed out after ${TIMEOUT_MS}ms; using deterministic fallback")
            tripCircuit("AI timeout")
            AdvisorIntelDecision(
                proceed = fallbackProceed,
                convictionNote = fallbackNote,
                riskFlags = flags,
                aiSource = "Deterministic Fallback (AI timeout)"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errTag = e.javaClass.simpleName.ifBlank { "AIError" }
            Log.e(TAG, "Intel Gemini call failed", e)
            tripCircuit(errTag)
            AdvisorIntelDecision(
                proceed = fallbackProceed,
                convictionNote = fallbackNote,
                riskFlags = flags,
                aiSource = "Deterministic Fallback ($errTag)"
            )
        }
    }

    override suspend fun summarizeEntryAndOdds(
        entry: EntryCheckResult,
        plan: TradePlan,
        timeMachine: TimeMachineResult?
    ): AdvisorNarrative {
        val tmLine = if (timeMachine != null) {
            "MC (${timeMachine.replays}x): P(target)=${"%.1f".format(timeMachine.pTarget * 100)}%, " +
                "P(stop)=${"%.1f".format(timeMachine.pStop * 100)}%, avgR_net=${"%.2f".format(timeMachine.avgRNet)}R [${timeMachine.verdict}]."
        } else {
            "60d5m replay pool unavailable for ${plan.symbol}; deterministic ORB plan built."
        }
        val fallback = "${plan.symbol} ${plan.setup.uppercase()} plan: Entry ${IndianMarketUtils.inr(plan.entry)} at ${plan.entryTime} IST, " +
            "Stop ${IndianMarketUtils.inr(plan.stop)}, Target ${IndianMarketUtils.inr(plan.target)} (${"%.2f".format(plan.rewardRisk)}R). $tmLine"

        activeBypassReason()?.let { reason ->
            return AdvisorNarrative(
                summary = fallback,
                aiSource = "Deterministic Fallback ($reason)"
            )
        }

        val prompt = buildString {
            appendLine("You are the Exit & TimeMachine Agent summarizing trade plan & probability odds.")
            appendLine("Symbol: ${plan.symbol}, Entry: ${plan.entry} (${entry.status}), Stop: ${plan.stop} (${plan.stopRule}), Target: ${plan.target} (${plan.targetLabel}), R:R=${plan.rewardRisk}")
            appendLine("TimeMachine: $tmLine")
        }

        return try {
            val obj = generateStructuredJson(summaryModel, prompt)
            resetCircuit()
            AdvisorNarrative(
                summary = obj.optString("summary").ifBlank { fallback },
                aiSource = "Gemini 3.8 Flash"
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Entry/TimeMachine Gemini call timed out after ${TIMEOUT_MS}ms; using deterministic fallback")
            tripCircuit("AI timeout")
            AdvisorNarrative(
                summary = fallback,
                aiSource = "Deterministic Fallback (AI timeout)"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errTag = e.javaClass.simpleName.ifBlank { "AIError" }
            Log.e(TAG, "Entry/TimeMachine Gemini call failed", e)
            tripCircuit(errTag)
            AdvisorNarrative(
                summary = fallback,
                aiSource = "Deterministic Fallback ($errTag)"
            )
        }
    }

    override suspend fun explainRiskAndVeto(
        order: OrderProposal?,
        verdict: RiskVerdict
    ): AdvisorNarrative {
        val firstRes = verdict.results.firstOrNull()
        val fallback = if (verdict.allPassed && order != null) {
            "Risk Gate PASSED all 14 NSE checks for ${order.quantity} x ${order.tradingsymbol} (${order.product}, " +
                "Max Loss ${IndianMarketUtils.inr(order.maxLossInr)}, Fingerprint ${order.fingerprint})."
        } else {
            val reasons = firstRes?.reasons?.joinToString("; ") ?: "Order failed sizing or risk bounds"
            "Risk Agent VETOED trade: $reasons"
        }

        activeBypassReason()?.let { reason ->
            return AdvisorNarrative(
                summary = fallback,
                aiSource = "Deterministic Fallback ($reason)"
            )
        }

        val prompt = buildString {
            appendLine("You are the Risk Gatekeeper Agent with final veto power.")
            appendLine("All passed: ${verdict.allPassed}, Checked IST: ${verdict.checkedAtIst}")
            appendLine("Order: ${order?.quantity ?: 0} x ${order?.tradingsymbol ?: "N/A"}, Value=${order?.orderValueInr ?: 0.0}, MaxLoss=${order?.maxLossInr ?: 0.0}")
            appendLine("Failures: ${firstRes?.reasons?.joinToString(" | ") ?: "None"}")
            appendLine("Warnings: ${firstRes?.warnings?.joinToString(" | ") ?: "None"}")
            appendLine("Summarize the Risk Gate verdict clearly.")
        }

        return try {
            val obj = generateStructuredJson(summaryModel, prompt)
            resetCircuit()
            AdvisorNarrative(
                summary = obj.optString("summary").ifBlank { fallback },
                aiSource = "Gemini 3.8 Flash"
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Risk Gemini call timed out after ${TIMEOUT_MS}ms; using deterministic fallback")
            tripCircuit("AI timeout")
            AdvisorNarrative(
                summary = fallback,
                aiSource = "Deterministic Fallback (AI timeout)"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errTag = e.javaClass.simpleName.ifBlank { "AIError" }
            Log.e(TAG, "Risk Gemini call failed", e)
            tripCircuit(errTag)
            AdvisorNarrative(
                summary = fallback,
                aiSource = "Deterministic Fallback ($errTag)"
            )
        }
    }

    override suspend fun summarizeCycleForAlerts(
        symbol: String,
        paperSummary: String,
        alertsArmedCount: Int,
        verdictPassed: Boolean
    ): AdvisorNarrative {
        val fallback = if (verdictPassed) {
            if (alertsArmedCount > 0) {
                "Paper fill recorded ($paperSummary) and $alertsArmedCount protective alert rung(s) armed for $symbol."
            } else {
                "Preview verified for $symbol: $paperSummary"
            }
        } else {
            "Cycle halted cleanly before broker submission."
        }

        activeBypassReason()?.let { reason ->
            return AdvisorNarrative(
                summary = fallback,
                aiSource = "Deterministic Fallback ($reason)"
            )
        }

        val prompt = buildString {
            appendLine("You are the Alerts & Paper Broker Agent wrapping up an autonomous cycle.")
            appendLine("Symbol: $symbol, Paper Fill: $paperSummary, Alerts Armed: $alertsArmedCount")
        }
        return try {
            val obj = generateStructuredJson(summaryModel, prompt)
            resetCircuit()
            AdvisorNarrative(
                summary = obj.optString("summary").ifBlank { fallback },
                aiSource = "Gemini 3.8 Flash"
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Alerts Gemini call timed out after ${TIMEOUT_MS}ms; using deterministic fallback")
            tripCircuit("AI timeout")
            AdvisorNarrative(
                summary = fallback,
                aiSource = "Deterministic Fallback (AI timeout)"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errTag = e.javaClass.simpleName.ifBlank { "AIError" }
            Log.e(TAG, "Alerts Gemini call failed", e)
            tripCircuit(errTag)
            AdvisorNarrative(
                summary = fallback,
                aiSource = "Deterministic Fallback ($errTag)"
            )
        }
    }
}

/**
 * Pure deterministic advisor used during fast cold-boot previews so the dashboard
 * populates immediately without waiting on outbound Gemini/App Check network timeouts.
 */
class DeterministicAgentAdvisor : AgentAdvisor {
    override suspend fun prioritizeCandidates(
        hits: List<ScanHit>,
        clockStatus: MarketClockStatus
    ): AdvisorCandidateDecision {
        if (hits.isEmpty()) {
            return AdvisorCandidateDecision(
                rankedSymbols = emptyList(),
                rationale = "No candidates cleared the scanner universe thresholds.",
                aiSource = "Deterministic Engine"
            )
        }
        val ranked = hits.sortedByDescending {
            val volScore = (it.volumeX ?: 1.0) * 10.0
            val moveScore = it.changePct
            val orbBonus = if (it.setup == "orb") 20.0 else 0.0
            orbBonus + volScore + moveScore
        }.map { it.symbol }.distinct().take(3)
        return AdvisorCandidateDecision(
            rankedSymbols = ranked,
            rationale = "Deterministic scan ranked ${ranked.joinToString(", ")} by ORB momentum & relative volume.",
            aiSource = "Deterministic Engine"
        )
    }

    override suspend fun evaluateNewsAndAnalyst(
        symbol: String,
        newsReport: NewsReport?,
        analystReport: AnalystReport?,
        insidersReport: InsidersReport?
    ): AdvisorIntelDecision {
        val flags = mutableListOf<String>()
        if (newsReport?.movingFlag == true) {
            flags += "Large intraday move (${newsReport.changePct ?: 0.0}%)"
        }
        val importantNews = newsReport?.items?.filter { it.important }.orEmpty()
        importantNews.take(2).forEach { flags += "! ${it.tags.joinToString("/")}: ${it.title.take(55)}" }
        analystReport?.whatChanged?.take(2)?.forEach { flags += it }
        val latePit = insidersReport?.pitFilings?.count { it.isLate } ?: 0
        if (latePit > 0) flags += "$latePit late SEBI PIT filing(s) (>4d)"
        val churnCount = insidersReport?.deals?.count { it.sameDayBothSides } ?: 0
        if (churnCount > 0) flags += "$churnCount same-day Buy+Sell churn deal(s)"

        val note = buildString {
            append("$symbol cleared news/fundamental checks: ")
            append("${newsReport?.items?.size ?: 0} news/filing item(s)")
            analystReport?.let {
                append(", ${it.format} (${it.basis}) ${it.quarters.size}Q loaded")
                it.ttmProfitCr?.let { pat -> append(", TTM PAT ₹${IndianMarketUtils.round2(pat)} Cr") }
            }
            insidersReport?.let {
                append(", ${it.pitFilings.size} PIT & ${it.deals.size} bulk/block deals.")
            }
        }
        return AdvisorIntelDecision(
            proceed = true,
            convictionNote = note,
            riskFlags = flags,
            aiSource = "Deterministic Engine"
        )
    }

    override suspend fun summarizeEntryAndOdds(
        entry: EntryCheckResult,
        plan: TradePlan,
        timeMachine: TimeMachineResult?
    ): AdvisorNarrative {
        val tmLine = if (timeMachine != null) {
            "MC (${timeMachine.replays}x): P(target)=${"%.1f".format(timeMachine.pTarget * 100)}%, " +
                "P(stop)=${"%.1f".format(timeMachine.pStop * 100)}%, avgR_net=${"%.2f".format(timeMachine.avgRNet)}R [${timeMachine.verdict}]."
        } else {
            "60d5m replay pool unavailable for ${plan.symbol}; deterministic ORB plan built."
        }
        return AdvisorNarrative(
            summary = "${plan.symbol} ${plan.setup.uppercase()} plan: Entry ${IndianMarketUtils.inr(plan.entry)} at ${plan.entryTime} IST, " +
                "Stop ${IndianMarketUtils.inr(plan.stop)}, Target ${IndianMarketUtils.inr(plan.target)} (${"%.2f".format(plan.rewardRisk)}R). $tmLine",
            aiSource = "Deterministic Engine"
        )
    }

    override suspend fun explainRiskAndVeto(
        order: OrderProposal?,
        verdict: RiskVerdict
    ): AdvisorNarrative {
        val firstRes = verdict.results.firstOrNull()
        val msg = if (verdict.allPassed && order != null) {
            "Risk Gate PASSED all 14 NSE checks for ${order.quantity} x ${order.tradingsymbol} (${order.product}, " +
                "Max Loss ${IndianMarketUtils.inr(order.maxLossInr)}, Fingerprint ${order.fingerprint})."
        } else {
            val reasons = firstRes?.reasons?.joinToString("; ") ?: "Order failed sizing or risk bounds"
            "Risk Agent VETOED trade: $reasons"
        }
        return AdvisorNarrative(summary = msg, aiSource = "Deterministic Engine")
    }

    override suspend fun summarizeCycleForAlerts(
        symbol: String,
        paperSummary: String,
        alertsArmedCount: Int,
        verdictPassed: Boolean
    ): AdvisorNarrative {
        val msg = if (verdictPassed) {
            if (alertsArmedCount > 0) {
                "Paper fill recorded ($paperSummary) and $alertsArmedCount protective alert rung(s) armed for $symbol."
            } else {
                "Preview verified for $symbol: $paperSummary"
            }
        } else {
            "Cycle halted cleanly before broker submission."
        }
        return AdvisorNarrative(summary = msg, aiSource = "Deterministic Engine")
    }
}
