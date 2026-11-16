package com.example.domain

import com.example.model.AlertCheckSummary
import com.example.model.AlertRule
import com.example.model.AlertType
import com.example.model.AnalystReport
import com.example.model.BulkBlockDeal
import com.example.model.FilingQuarter
import com.example.model.InsiderFiling
import com.example.model.InsidersReport
import com.example.model.NewsItem
import com.example.model.NewsReport
import com.example.model.SentimentVerdict
import com.example.model.SymbolSentiment
import com.example.model.TradePlan
import com.example.model.UniverseSecurity
import com.example.model.WatchlistSentimentReport
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

object AlertsNewsAnalystInsidersEngine {

    // ---------------------------------------------------------------- Alerts

    fun rulesFromPlan(plan: TradePlan): List<AlertRule> = listOf(
        AlertRule(
            id = "plan_${plan.symbol}_target",
            symbol = plan.symbol,
            exchange = plan.exchange,
            type = AlertType.ABOVE,
            value = plan.target,
            label = "${plan.symbol} Target ${IndianMarketUtils.inr(plan.target)}"
        ),
        AlertRule(
            id = "plan_${plan.symbol}_stop",
            symbol = plan.symbol,
            exchange = plan.exchange,
            type = AlertType.BELOW,
            value = plan.stop,
            label = "${plan.symbol} Stop ${IndianMarketUtils.inr(plan.stop)}"
        ),
        AlertRule(
            id = "plan_${plan.symbol}_flatby",
            symbol = plan.symbol,
            exchange = plan.exchange,
            type = AlertType.TIME,
            timeHm = plan.flatBy,
            label = "${plan.symbol} Flat-By ${plan.flatBy} IST"
        ),
        AlertRule(
            id = "plan_${plan.symbol}_sqoff_warn",
            symbol = plan.symbol,
            exchange = plan.exchange,
            type = AlertType.TIME,
            timeHm = subtractMinutes(if (plan.cas == false) "15:25" else "15:12", 3),
            label = "${plan.symbol} Zerodha MIS Square-Off 3-Min Warning"
        )
    )

    private fun subtractMinutes(hhmm: String, delta: Int): String {
        val m = IndianMarketUtils.parseClockMinutes(hhmm) ?: return hhmm
        return IndianMarketUtils.clockString((m - delta).coerceAtLeast(0))
    }

    fun evaluateAlerts(
        rules: List<AlertRule>,
        quotes: Map<String, Pair<Double, Double>>, // symbol -> (lastPrice, prevClose)
        secList: Map<String, UniverseSecurity>,
        nowIst: LocalDateTime
    ): AlertCheckSummary {
        val today = nowIst.toLocalDate().toString()
        val nowHm = "%02d:%02d".format(nowIst.hour, nowIst.minute)
        val nowMin = nowIst.hour * 60 + nowIst.minute
        val hits = mutableListOf<String>()

        val updated = rules.map { rule ->
            if (!rule.armed || rule.lastTriggeredDate == today) return@map rule
            val q = quotes[rule.symbol]
            val band = secList[rule.symbol]?.bandPct
            var hitText: String? = null

            when (rule.type) {
                AlertType.ABOVE -> {
                    val lvl = rule.value ?: 0.0
                    if (q != null && q.first >= lvl) {
                        hitText = "${rule.symbol} crossed above ${IndianMarketUtils.inr(lvl)} (now ${IndianMarketUtils.inr(q.first)})"
                    }
                }
                AlertType.BELOW -> {
                    val lvl = rule.value ?: 0.0
                    if (q != null && q.first <= lvl) {
                        hitText = "${rule.symbol} dropped to/below ${IndianMarketUtils.inr(lvl)} (now ${IndianMarketUtils.inr(q.first)})"
                    }
                }
                AlertType.PCT_MOVE -> {
                    val pct = rule.value ?: 3.0
                    if (q != null && q.second > 0) {
                        val move = abs(q.first - q.second) / q.second * 100.0
                        if (move >= pct) {
                            hitText = "${rule.symbol} moved ${"%.2f".format(move)}% (threshold ${"%.1f".format(pct)}%)"
                        }
                    }
                }
                AlertType.CIRCUIT -> {
                    val thresh = rule.value ?: 1.0
                    if (q != null && band != null) {
                        val circ = IndianMarketUtils.circuits(q.second, band)
                        if (circ != null) {
                            val distUp = (circ.second - q.first) / circ.second * 100.0
                            val distDn = (q.first - circ.first) / circ.first * 100.0
                            if (distUp <= thresh) hitText = "${rule.symbol} within ${"%.2f".format(distUp)}% of upper circuit ${IndianMarketUtils.inr(circ.second)}"
                            else if (distDn <= thresh) hitText = "${rule.symbol} within ${"%.2f".format(distDn)}% of lower circuit ${IndianMarketUtils.inr(circ.first)}"
                        }
                    }
                }
                AlertType.OPENING_BELL -> {
                    if (nowMin >= 9 * 60 + 15) {
                        hitText = "NSE Opening Bell 09:15 IST reached"
                    }
                }
                AlertType.PRE_OPEN -> {
                    if (nowMin in (9 * 60)..(9 * 60 + 14)) {
                        hitText = "NSE Pre-Open session active ($nowHm IST)"
                    }
                }
                AlertType.TIME -> {
                    val targetMin = IndianMarketUtils.parseClockMinutes(rule.timeHm) ?: Int.MAX_VALUE
                    if (nowMin >= targetMin) {
                        hitText = "${rule.label} reached ($nowHm IST)"
                    }
                }
            }

            if (hitText != null) {
                hits.add(hitText)
                rule.copy(
                    armed = false,
                    lastTriggeredDate = today,
                    lastTriggeredInfo = hitText
                )
            } else {
                rule
            }
        }

        return AlertCheckSummary(
            checkedAtIst = "$today $nowHm IST",
            triggeredCount = hits.size,
            alerts = updated,
            hits = hits
        )
    }

    // ---------------------------------------------------------------- News

    private val IMPORTANT_TAGS = setOf(
        "RESULTS", "DIVIDEND", "BONUS/SPLIT", "BUYBACK", "ORDER/CONTRACT",
        "M&A", "FUNDRAISE", "INSIDER/PIT", "MANAGEMENT", "REGULATORY", "MOVING"
    )
    private val ROUTINE_DESCS = setOf(
        "copy of newspaper publication",
        "loss of share certificate",
        "investor meet",
        "analysts/institutional investor meet",
        "reconciliation of share capital",
        "certificate under reg 74(5)",
        "closure of trading window"
    )

    fun buildNewsReport(
        symbol: String,
        lastPrice: Double?,
        prevClose: Double?,
        nseAnnJson: String?,
        nseRssXml: String?,
        gnewsXml: String?
    ): NewsReport {
        val chgPct = if (lastPrice != null && prevClose != null && prevClose > 0) {
            IndianMarketUtils.round2((lastPrice - prevClose) / prevClose * 100.0)
        } else null
        val moving = chgPct != null && abs(chgPct) >= 3.0

        val items = mutableListOf<NewsItem>()
        if (!nseAnnJson.isNullOrBlank()) {
            val arr = try {
                if (nseAnnJson.trim().startsWith("[")) JSONArray(nseAnnJson)
                else JSONObject(nseAnnJson).optJSONArray("data") ?: JSONArray()
            } catch (_: Exception) {
                JSONArray()
            }
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val desc = obj.optString("desc", "Filing")
                val text = obj.optString("attchmntText", "").ifEmpty { desc }
                val dt = obj.optString("an_dt", obj.optString("sort_date", ""))
                val seq = obj.optString("seq_id", "${i}_${dt}")
                val file = obj.optString("attchmntFile", "").takeIf { it.isNotBlank() }
                val tags = classifyNews("$desc $text")
                val isRoutine = ROUTINE_DESCS.any { desc.lowercase().contains(it) || text.lowercase().contains(it) }
                items.add(
                    NewsItem(
                        id = "nse_$seq",
                        source = "NSE Filing",
                        symbol = symbol,
                        timestamp = dt,
                        title = desc,
                        summary = text,
                        tags = tags,
                        important = tags.any { it in IMPORTANT_TAGS },
                        routine = isRoutine,
                        link = file
                    )
                )
            }
        }

        if (!nseRssXml.isNullOrBlank()) {
            parseRssItems(nseRssXml, "NSE RSS").filter {
                it.title.uppercase().contains(symbol) || it.summary.uppercase().contains(symbol)
            }.forEach { items.add(it.copy(symbol = symbol)) }
        }

        if (!gnewsXml.isNullOrBlank()) {
            parseRssItems(gnewsXml, "Google News IN").take(15).forEach {
                items.add(it.copy(symbol = symbol))
            }
        }

        val deduped = items.distinctBy { it.title.lowercase().take(65) }
        return NewsReport(
            symbol = symbol,
            lastPrice = lastPrice,
            prevClose = prevClose,
            changePct = chgPct,
            movingFlag = moving,
            items = deduped
        )
    }

    private fun parseRssItems(xmlText: String, source: String): List<NewsItem> {
        val out = mutableListOf<NewsItem>()
        try {
            val dbf = DocumentBuilderFactory.newInstance()
            val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(xmlText.toByteArray(Charsets.UTF_8)))
            val nodes = doc.getElementsByTagName("item")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val title = el.getElementsByTagName("title").item(0)?.textContent?.trim() ?: continue
                val link = el.getElementsByTagName("link").item(0)?.textContent?.trim()
                val pubDate = el.getElementsByTagName("pubDate").item(0)?.textContent?.trim() ?: ""
                val descRaw = el.getElementsByTagName("description").item(0)?.textContent?.trim() ?: ""
                val desc = descRaw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                val tags = classifyNews("$title $desc")
                out.add(
                    NewsItem(
                        id = "${source}_${i}_${title.hashCode()}",
                        source = source,
                        symbol = "",
                        timestamp = pubDate.take(22),
                        title = title,
                        summary = desc.take(240),
                        tags = tags,
                        important = tags.any { it in IMPORTANT_TAGS },
                        routine = false,
                        link = link
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun classifyNews(text: String): List<String> {
        val t = text.lowercase()
        val tags = mutableListOf<String>()
        if (Regex("financial result|quarter|q[1-4]|fy\\d|ebitda|profit").containsMatchIn(t)) tags.add("RESULTS")
        if ("dividend" in t) tags.add("DIVIDEND")
        if ("bonus" in t || "split" in t) tags.add("BONUS/SPLIT")
        if ("buyback" in t) tags.add("BUYBACK")
        if (Regex("order|contract|award|bagged|work order|loa").containsMatchIn(t)) tags.add("ORDER/CONTRACT")
        if (Regex("acquir|acquisition|merger|amalgamation|demerger|jv|joint venture").containsMatchIn(t)) tags.add("M&A")
        if (Regex("qip|rights issue|preferential|ncd|debenture|fund rais").containsMatchIn(t)) tags.add("FUNDRAISE")
        if ("credit rating" in t || "icra" in t || "crisil" in t) tags.add("RATING")
        if (Regex("resign|appoint|ceo|cfo|md|director").containsMatchIn(t)) tags.add("MANAGEMENT")
        if (Regex("sebi|penalty|show cause|search|raid|inquiry").containsMatchIn(t)) tags.add("REGULATORY")
        if (tags.isEmpty()) tags.add("UPDATE")
        return tags.distinct()
    }

    // ---------------------------------------------------------------- Analyst (XBRL)

    private val CONCEPTS = mapOf(
        "revenue" to listOf("RevenueFromOperations"),
        "other_income" to listOf("OtherIncome"),
        "total_income" to listOf("Income"),
        "finance_costs" to listOf("FinanceCosts"),
        "depreciation" to listOf("DepreciationDepletionAndAmortisationExpense"),
        "pbt_before_exceptional" to listOf("ProfitBeforeExceptionalItemsAndTax"),
        "exceptional" to listOf("ExceptionalItemsBeforeTax", "ExceptionalItems"),
        "pbt" to listOf("ProfitBeforeTax", "ProfitLossFromOrdinaryActivitiesBeforeTax"),
        "profit_owners" to listOf("ProfitOrLossAttributableToOwnersOfParent", "ProfitLossAfterTaxesMinorityInterestAndShareOfProfitLossOfAssociates"),
        "profit" to listOf("ProfitLossForPeriod", "ProfitLossForThePeriod"),
        "eps_diluted" to listOf(
            "DilutedEarningsLossPerShareFromContinuingAndDiscontinuedOperations",
            "DilutedEarningsLossPerShareFromContinuingOperations",
            "DilutedEarningsPerShareAfterExtraordinaryItems",
            "DilutedEarningsPerShareBeforeExtraordinaryItems",
            "BasicEarningsLossPerShareFromContinuingAndDiscontinuedOperations",
            "BasicEarningsPerShareAfterExtraordinaryItems"
        ),
        "interest_earned" to listOf("InterestEarned"),
        "interest_expended" to listOf("InterestExpended"),
        "ppop" to listOf("OperatingProfitBeforeProvisionAndContingencies"),
        "provisions" to listOf("ProvisionsOtherThanTaxAndContingencies"),
        "paid_up" to listOf("PaidUpValueOfEquityShareCapital"),
        "face_value" to listOf("FaceValueOfEquityShareCapital")
    )

    data class FilingMeta(
        val end: String,
        val basis: String,
        val xbrlFileName: String,
        val filed: String?,
        val audited: String?,
        val company: String?,
        val bank: Boolean
    )

    data class ParsedXbrlQuarter(
        val end: String,
        val companyName: String?,
        val audited: String?,
        val filed: String?,
        val basis: String,
        val format: String,
        val values: Map<String, Double>
    )

    fun extractFilingsList(integratedJson: String?, legacyJson: String?): List<FilingMeta> {
        val out = mutableListOf<FilingMeta>()
        val dateFormats = listOf(
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH)
        )
        fun parseFlexDate(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val clean = raw.trim().lowercase().replaceFirstChar { it.uppercase() }
                .replace(Regex("-([a-z]{3})-")) { "-${it.groupValues[1].lowercase().replaceFirstChar { c -> c.uppercase() }}-" }
            for (fmt in dateFormats) {
                try {
                    return LocalDate.parse(clean.take(11), DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)).toString()
                } catch (_: Exception) {
                }
            }
            return null
        }

        if (!integratedJson.isNullOrBlank()) {
            val arr = try {
                JSONObject(integratedJson).optJSONArray("data") ?: JSONArray()
            } catch (_: Exception) {
                JSONArray()
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = o.optString("type", "Financials")
                if (!type.contains("Financ", ignoreCase = true)) continue
                val xbrl = o.optString("xbrl", "").takeIf { it.isNotBlank() } ?: continue
                val end = parseFlexDate(o.optString("qe_Date")) ?: continue
                val cons = o.optString("consolidated", "").lowercase()
                val basis = if (cons.startsWith("stand") || cons.startsWith("non")) "standalone" else "consolidated"
                out.add(
                    FilingMeta(
                        end = end,
                        basis = basis,
                        xbrlFileName = xbrl.substringAfterLast('/'),
                        filed = o.optString("broadcast_Date").takeIf { it.isNotBlank() },
                        audited = o.optString("audited").takeIf { it.isNotBlank() },
                        company = o.optString("cmName").ifEmpty { o.optString("smName") }.takeIf { it.isNotBlank() },
                        bank = xbrl.uppercase().contains("BANKING")
                    )
                )
            }
        }
        if (!legacyJson.isNullOrBlank()) {
            val arr = try {
                if (legacyJson.trim().startsWith("[")) JSONArray(legacyJson)
                else JSONObject(legacyJson).optJSONArray("data") ?: JSONArray()
            } catch (_: Exception) {
                JSONArray()
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("cumulative", "").lowercase().startsWith("cumul")) continue
                val period = o.optString("period", "Quarterly")
                if (period != "Quarterly") continue
                val xbrl = o.optString("xbrl", "").takeIf { it.isNotBlank() } ?: continue
                val end = parseFlexDate(o.optString("toDate")) ?: continue
                val cons = o.optString("consolidated", "").lowercase()
                val basis = if (cons.startsWith("stand") || cons.startsWith("non")) "standalone" else "consolidated"
                out.add(
                    FilingMeta(
                        end = end,
                        basis = basis,
                        xbrlFileName = xbrl.substringAfterLast('/'),
                        filed = o.optString("broadCastDate").ifEmpty { o.optString("filingDate") }.takeIf { it.isNotBlank() },
                        audited = o.optString("audited").takeIf { it.isNotBlank() },
                        company = o.optString("companyName").takeIf { it.isNotBlank() },
                        bank = o.optString("bank") == "B"
                    )
                )
            }
        }
        return out
            .groupBy { it.end to it.basis }
            .mapValues { (_, list) -> list.maxBy { it.filed ?: "" } }
            .values
            .sortedByDescending { it.end }
    }

    fun parseXbrlQuarter(xmlBytes: ByteArray, meta: FilingMeta?): ParsedXbrlQuarter? {
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val root = doc.documentElement

        val contexts = linkedMapOf<String, Pair<LocalDate, LocalDate>>()
        val ctxNodes = root.getElementsByTagNameNS("*", "context")
        for (i in 0 until ctxNodes.length) {
            val c = ctxNodes.item(i) as? Element ?: continue
            if (c.getElementsByTagNameNS("*", "scenario").length > 0) continue
            val cid = c.getAttribute("id")
            val sText = c.getElementsByTagNameNS("*", "startDate").item(0)?.textContent?.trim() ?: continue
            val eText = c.getElementsByTagNameNS("*", "endDate").item(0)?.textContent?.trim() ?: continue
            val s = runCatching { LocalDate.parse(sText) }.getOrNull() ?: continue
            val e = runCatching { LocalDate.parse(eText) }.getOrNull() ?: continue
            val days = java.time.temporal.ChronoUnit.DAYS.between(s, e)
            if (days in 80..100) contexts[cid] = s to e
        }
        val qid = contexts.keys.sortedWith(compareBy({ it != "OneD" }, { it })).firstOrNull() ?: return null
        val (_, endDate) = contexts.getValue(qid)

        val facts = linkedMapOf<String, Double>()
        val texts = linkedMapOf<String, String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            if (el.getAttribute("contextRef") != qid) continue
            val localTag = el.localName ?: el.tagName.substringAfter(':')
            val unitRef = el.getAttribute("unitRef")
            val content = el.textContent?.trim() ?: ""
            if (unitRef.isNotBlank()) {
                val num = content.toDoubleOrNull()
                if (num != null && !facts.containsKey(localTag)) facts[localTag] = num
            } else if (!texts.containsKey(localTag)) {
                texts[localTag] = content
            }
        }

        val values = linkedMapOf<String, Double>()
        for ((key, tags) in CONCEPTS) {
            for (t in tags) {
                val v = facts[t]
                if (v != null) {
                    values[key] = v
                    break
                }
            }
        }
        val isBank = values.containsKey("interest_earned") && !values.containsKey("revenue")
        if (isBank && values.containsKey("total_income")) {
            values["revenue"] = values.getValue("total_income")
        }

        val comp = texts["NameOfTheCompany"] ?: texts["NameOfBank"] ?: meta?.company
        return ParsedXbrlQuarter(
            end = endDate.toString(),
            companyName = comp,
            audited = texts["WhetherResultsAreAuditedOrUnaudited"] ?: meta?.audited,
            filed = meta?.filed,
            basis = meta?.basis ?: "consolidated",
            format = if (isBank) "bank" else "ind-as",
            values = values
        )
    }

    fun buildAnalystReport(
        symbol: String,
        parsedQuarters: List<ParsedXbrlQuarter>,
        eventsJson: String?,
        wantedBasis: String = "consolidated"
    ): AnalystReport {
        val byEnd = parsedQuarters.associateBy { it.end }
        val sortedEnds = byEnd.keys.sortedDescending()
        val latest = sortedEnds.firstOrNull()?.let { byEnd[it] }
        val format = latest?.format ?: "ind-as"
        val entityName = latest?.companyName ?: symbol

        fun sharesOf(q: ParsedXbrlQuarter): Double? {
            val pu = q.values["paid_up"] ?: return null
            val fv = q.values["face_value"] ?: return null
            return if (fv > 0) pu / fv else null
        }

        fun epsFactor(cur: ParsedXbrlQuarter, prior: ParsedXbrlQuarter?): Double {
            if (prior == null) return 1.0
            val s1 = sharesOf(cur) ?: return 1.0
            val s0 = sharesOf(prior) ?: return 1.0
            val r = s1 / s0
            return if (r >= 1.45 || r <= 1.0 / 1.45) r else 1.0
        }

        fun calcOperating(q: ParsedXbrlQuarter): Pair<Double?, Double?> {
            val v = q.values
            if (q.format == "bank") {
                return v["ppop"] to null
            }
            val base = v["pbt_before_exceptional"] ?: v["pbt"]?.let { it - (v["exceptional"] ?: 0.0) }
            val fin = v["finance_costs"]
            if (base != null && fin != null) {
                val ebit = base + fin - (v["other_income"] ?: 0.0)
                val ebitda = v["depreciation"]?.let { ebit + it }
                return ebit to ebitda
            }
            return null to null
        }

        fun calcNii(q: ParsedXbrlQuarter): Double? {
            if (q.format != "bank") return null
            val ie = q.values["interest_earned"] ?: return null
            val ix = q.values["interest_expended"] ?: return null
            return ie - ix
        }

        fun netProfit(q: ParsedXbrlQuarter): Double? = q.values["profit_owners"] ?: q.values["profit"]

        fun fiscalLabel(end: String): String {
            val d = LocalDate.parse(end)
            val qNum = when (d.monthValue) {
                6 -> 1
                9 -> 2
                12 -> 3
                3 -> 4
                else -> return end
            }
            val fy = d.year + if (d.monthValue > 3) 1 else 0
            return "Q$qNum FY${"%02d".format(fy % 100)}"
        }

        fun pctChange(a: Double?, b: Double?): Double? {
            if (a == null || b == null || b == 0.0) return null
            return round((a - b) / abs(b) * 1000.0) / 10.0
        }

        fun marginPct(part: Double?, whole: Double?): Double? {
            if (part == null || whole == null || whole == 0.0) return null
            return round((part / whole) * 1000.0) / 10.0
        }

        fun toCr(v: Double?): Double? = v?.let { IndianMarketUtils.round2(it / 1e7) }

        val rows = sortedEnds.take(8).map { end ->
            val cur = byEnd.getValue(end)
            val dEnd = LocalDate.parse(end)
            val priorKey = dEnd.minusYears(1).toString()
            val prior = byEnd[priorKey]
            val factor = epsFactor(cur, prior)
            val latestFactor = latest?.let { epsFactor(it, cur) } ?: 1.0

            val rev = cur.values["revenue"]
            val oi = cur.values["other_income"]
            val (ebit, ebitda) = calcOperating(cur)
            val nii = calcNii(cur)
            val np = netProfit(cur)
            val eps = cur.values["eps_diluted"]
            val priorEpsAdj = prior?.values?.get("eps_diluted")?.let { it / factor }

            FilingQuarter(
                end = end,
                fiscal = fiscalLabel(end),
                basis = cur.basis,
                format = cur.format,
                audited = cur.audited,
                filed = cur.filed,
                revenueCr = toCr(rev),
                otherIncomeCr = toCr(oi),
                ebitdaCr = toCr(ebitda),
                operatingProfitCr = toCr(ebit),
                niiCr = toCr(nii),
                provisionsCr = toCr(cur.values["provisions"]),
                pbtCr = toCr(cur.values["pbt"]),
                netProfitCr = toCr(np),
                exceptionalCr = toCr(cur.values["exceptional"]),
                epsDiluted = eps?.let { IndianMarketUtils.round2(it) },
                epsAdjustedTodayShares = eps?.let { IndianMarketUtils.round2(it / latestFactor) },
                epsAdjusted = factor != 1.0,
                ebitdaMarginPct = marginPct(ebitda, rev),
                operatingMarginPct = marginPct(ebit, rev),
                netMarginPct = marginPct(np, rev),
                revenueYoyPct = pctChange(rev, prior?.values?.get("revenue")),
                netProfitYoyPct = pctChange(np, prior?.let { netProfit(it) }),
                niiYoyPct = pctChange(nii, prior?.let { calcNii(it) }),
                epsYoyPct = pctChange(eps, priorEpsAdj)
            )
        }

        val last4 = sortedEnds.take(4).map { byEnd.getValue(it) }
        val ttmRev = if (last4.size == 4) toCr(last4.sumOf { it.values["revenue"] ?: 0.0 }) else null
        val ttmNp = if (last4.size == 4) toCr(last4.sumOf { netProfit(it) ?: 0.0 }) else null
        val ttmNii = if (last4.size == 4 && format == "bank") toCr(last4.sumOf { calcNii(it) ?: 0.0 }) else null
        val ttmEps = if (last4.size == 4 && latest != null) {
            IndianMarketUtils.round2(last4.sumOf { (it.values["eps_diluted"] ?: 0.0) / epsFactor(latest, it) })
        } else null

        val insights = mutableListOf<String>()
        val latestRow = rows.firstOrNull()
        if (latestRow != null) {
            if (format == "bank") {
                if (latestRow.niiCr != null) {
                    val yoy = latestRow.niiYoyPct?.let { " (${if (it >= 0) "+" else ""}$it% YoY)" } ?: ""
                    insights.add("${latestRow.fiscal} Net Interest Income: ₹${IndianMarketUtils.crore(latestRow.niiCr * 1e7)} cr$yoy")
                }
            } else if (latestRow.revenueCr != null) {
                val yoy = latestRow.revenueYoyPct?.let { " (${if (it >= 0) "+" else ""}$it% YoY)" } ?: ""
                insights.add("${latestRow.fiscal} Revenue: ₹${IndianMarketUtils.crore(latestRow.revenueCr * 1e7)} cr$yoy")
            }
            if (latestRow.netProfitCr != null) {
                val yoy = latestRow.netProfitYoyPct?.let { " (${if (it >= 0) "+" else ""}$it% YoY)" } ?: ""
                insights.add("Net Profit: ₹${IndianMarketUtils.crore(latestRow.netProfitCr * 1e7)} cr$yoy · Net Margin ${latestRow.netMarginPct ?: "-"}%")
            }
            val oi = latestRow.otherIncomeCr
            val pbt = latestRow.pbtCr
            if (oi != null && pbt != null && pbt > 0 && oi >= 0.25 * pbt) {
                val share = (oi / pbt * 100.0).toInt()
                insights.add("Note: Other income accounts for $share% of PBT (₹${"%.0f".format(oi)} cr vs ₹${"%.0f".format(pbt)} cr)")
            }
            val exc = latestRow.exceptionalCr
            if (exc != null && abs(exc) >= 1.0) {
                insights.add("Exceptional item in ${latestRow.fiscal}: ₹${"%.1f".format(exc)} cr")
            }
        }

        val upcomingEvent = parseUpcomingEvent(eventsJson)
        return AnalystReport(
            symbol = symbol,
            entityName = entityName,
            basis = latest?.basis ?: wantedBasis,
            basisNote = null,
            format = format,
            quarters = rows,
            ttmRevenueCr = ttmRev,
            ttmProfitCr = ttmNp,
            ttmNiiCr = ttmNii,
            ttmEps = ttmEps,
            whatChanged = insights,
            upcomingEvent = upcomingEvent
        )
    }

    private fun parseUpcomingEvent(eventsJson: String?): String? {
        if (eventsJson.isNullOrBlank()) return null
        return try {
            val arr = if (eventsJson.trim().startsWith("[")) JSONArray(eventsJson)
            else JSONObject(eventsJson).optJSONArray("data") ?: JSONArray()
            if (arr.length() == 0) return null
            val first = arr.optJSONObject(0) ?: return null
            val dt = first.optString("date", first.optString("bm_date", ""))
            val purpose = first.optString("purpose", first.optString("bm_purpose", ""))
            if (dt.isBlank()) null else "$dt — $purpose"
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- Insiders (PIT + Bulk/Block)

    fun parsePitJson(jsonText: String): List<InsiderFiling> {
        val arr = try {
            JSONObject(jsonText).optJSONArray("data") ?: JSONArray()
        } catch (_: Exception) {
            return emptyList()
        }
        val dateFmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
        fun parsePitDate(raw: String): LocalDate? = try {
            val normalized = raw.trim().take(11)
            LocalDate.parse(normalized, dateFmt)
        } catch (_: Exception) {
            null
        }

        val out = mutableListOf<InsiderFiling>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val txType = o.optString("tdpTransactionType", "")
            val fromStr = o.optString("acqfromDt", "")
            val toStr = o.optString("acqtoDt", fromStr)
            val intimStr = o.optString("intimDt", "")
            val bcastStr = o.optString("date", "")
            val shares = o.optString("secAcq", "0").toDoubleOrNull() ?: 0.0
            val valInr = o.optString("secVal", "0").toDoubleOrNull() ?: 0.0
            val dTx = parsePitDate(toStr.ifBlank { fromStr })
            val dBcast = parsePitDate(bcastStr.substringBefore(" "))
            val lag = if (dTx != null && dBcast != null) {
                IndianMarketUtils.tradingDaysBetween(dTx, dBcast)
            } else null
            out.add(
                InsiderFiling(
                    symbol = o.optString("symbol", ""),
                    company = o.optString("company", ""),
                    acquirer = o.optString("acqName", ""),
                    category = o.optString("personCategory", ""),
                    transactionType = txType,
                    mode = o.optString("acqMode", ""),
                    shares = shares,
                    valueInr = valInr,
                    txDate = toStr.ifBlank { fromStr },
                    intimationDate = intimStr,
                    broadcastDate = bcastStr,
                    lagTradingDays = lag,
                    isLate = lag != null && lag > 4
                )
            )
        }
        return out
    }

    fun parseBulkBlockCsv(csvText: String, dealType: String, followedInvestors: List<String>): List<BulkBlockDeal> {
        val deals = mutableListOf<BulkBlockDeal>()
        for (line in csvText.lineSequence().drop(1)) {
            if (line.isBlank()) continue
            val cols = ScannerAndEntryEngine.parseCsvLine(line)
            if (cols.size < 7) continue
            val date = cols[0].trim()
            val sym = cols[1].trim().uppercase()
            val secName = cols[2].trim()
            val client = cols[3].trim()
            val side = cols[4].trim().uppercase()
            val qty = cols[5].trim().replace(",", "").toLongOrNull() ?: 0L
            val price = cols[6].trim().replace(",", "").toDoubleOrNull() ?: 0.0
            val valCr = IndianMarketUtils.round2(qty * price / 1e7)
            val matched = findFollowedMatch(client, followedInvestors)
            deals.add(
                BulkBlockDeal(
                    date = date,
                    dealType = dealType,
                    symbol = sym,
                    securityName = secName,
                    clientName = client,
                    side = side,
                    quantity = qty,
                    price = price,
                    valueCr = valCr,
                    matchedFollowInvestor = matched
                )
            )
        }
        // Detect same_day_both_sides churn (same client + symbol + date did both BUY and SELL)
        val sidesByKey = deals.groupBy { Triple(it.date, it.symbol, it.clientName.uppercase()) }
            .mapValues { (_, list) -> list.map { it.side }.toSet() }
        return deals.map { d ->
            val sides = sidesByKey[Triple(d.date, d.symbol, d.clientName.uppercase())].orEmpty()
            d.copy(sameDayBothSides = "BUY" in sides && "SELL" in sides)
        }
    }

    fun parseHistoricalDealsJson(jsonText: String, dealType: String, followedInvestors: List<String>): List<BulkBlockDeal> {
        val arr = try {
            JSONObject(jsonText).optJSONArray("data") ?: JSONArray()
        } catch (_: Exception) {
            return emptyList()
        }
        val deals = mutableListOf<BulkBlockDeal>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val date = o.optString("DT", o.optString("mTIMESTAMP", ""))
            val sym = o.optString("SYMBOL", "").uppercase()
            val secName = o.optString("NAME", "")
            val client = o.optString("CLIENT_NAME", "")
            val side = o.optString("BUY_SELL", "").uppercase()
            val qty = o.optString("QTY", "0").replace(",", "").toLongOrNull() ?: 0L
            val price = o.optString("WAT_PRICE", o.optString("PRICE", "0")).replace(",", "").toDoubleOrNull() ?: 0.0
            val valCr = IndianMarketUtils.round2(qty * price / 1e7)
            deals.add(
                BulkBlockDeal(
                    date = date,
                    dealType = dealType,
                    symbol = sym,
                    securityName = secName,
                    clientName = client,
                    side = side,
                    quantity = qty,
                    price = price,
                    valueCr = valCr,
                    matchedFollowInvestor = findFollowedMatch(client, followedInvestors)
                )
            )
        }
        val sidesByKey = deals.groupBy { Triple(it.date, it.symbol, it.clientName.uppercase()) }
            .mapValues { (_, list) -> list.map { it.side }.toSet() }
        return deals.map { d ->
            val sides = sidesByKey[Triple(d.date, d.symbol, d.clientName.uppercase())].orEmpty()
            d.copy(sameDayBothSides = "BUY" in sides && "SELL" in sides)
        }
    }

    /**
     * Fuzzy investor match: case-insensitive containment OR token prefix overlap (>=3 chars)
     * OR similarity ratio >= 0.85 (mirroring Python SequenceMatcher >= 0.85).
     */
    fun findFollowedMatch(clientName: String, followed: List<String>): String? {
        val cNorm = normalizeName(clientName)
        if (cNorm.isBlank()) return null
        for (inv in followed) {
            val iNorm = normalizeName(inv)
            if (iNorm.isBlank()) continue
            if (iNorm in cNorm || cNorm in iNorm) return inv
            val cTokens = cNorm.split(" ").filter { it.length >= 3 }
            val iTokens = iNorm.split(" ").filter { it.length >= 3 }
            if (iTokens.isNotEmpty() && iTokens.all { itTok -> cTokens.any { it.startsWith(itTok) || itTok.startsWith(it) } }) {
                return inv
            }
            if (similarityRatio(cNorm, iNorm) >= 0.85) return inv
        }
        return null
    }

    private fun normalizeName(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun similarityRatio(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dist = levenshtein(a, b)
        return 1.0 - dist.toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val cur = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(dp[j], dp[j - 1], prev)
                prev = cur
            }
        }
        return dp[b.length]
    }

    fun buildInsidersReport(
        symbolFilter: String?,
        pitFilings: List<InsiderFiling>,
        deals: List<BulkBlockDeal>
    ): InsidersReport {
        val sym = symbolFilter?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val filteredPit = if (sym == null) pitFilings else pitFilings.filter { it.symbol.equals(sym, ignoreCase = true) }
        val filteredDeals = if (sym == null) deals else deals.filter { it.symbol.equals(sym, ignoreCase = true) }
        val followed = deals.filter { it.matchedFollowInvestor != null }
        return InsidersReport(
            symbol = sym,
            pitFilings = filteredPit,
            deals = filteredDeals,
            followedMatches = followed
        )
    }

    // ---------------------------------------------------------------- Watchlist Sentiment (india-news + india-analyst)

    private val POSITIVE_NEWS_WORDS = listOf(
        "surge", "rally", "record", "beat", "upgrade", "buyback", "dividend",
        "bonus", "order win", "bagged", "awarded", "contract", "partnership",
        "approval", "growth", "strong", "gain", "expansion", "outperform",
        "profit jump", "higher profit", "rise", "boost"
    )

    private val NEGATIVE_NEWS_WORDS = listOf(
        "penalty", "sebi", "show cause", "raid", "inquiry", "resign",
        "slump", "drop", "fall", "loss", "downgrade", "miss", "debt",
        "default", "warning", "fraud", "weak", "decline", "delay",
        "crash", "probe", "fine", "concern", "sell-off", "plunge"
    )

    fun sentimentVerdict(score: Int): SentimentVerdict = when {
        score >= 62 -> SentimentVerdict.BULLISH
        score >= 54 -> SentimentVerdict.MILDLY_BULLISH
        score >= 46 -> SentimentVerdict.NEUTRAL
        score >= 38 -> SentimentVerdict.MILDLY_BEARISH
        else -> SentimentVerdict.BEARISH
    }

    fun computeSymbolSentiment(
        symbol: String,
        news: NewsReport?,
        analyst: AnalystReport?
    ): SymbolSentiment {
        var newsPoints = 50.0
        val chg = news?.changePct
        if (chg != null) {
            newsPoints += (chg * 3.5).coerceIn(-16.0, 16.0)
        }

        val items = news?.items.orEmpty()
        val activeItems = items.filterNot { it.routine }.take(20)
        var newsDelta = 0.0
        var posHitsTotal = 0
        var negHitsTotal = 0
        for (item in activeItems) {
            val text = "${item.title} ${item.summary}".lowercase()
            val posHits = POSITIVE_NEWS_WORDS.count { it in text }
            val negHits = NEGATIVE_NEWS_WORDS.count { it in text }
            posHitsTotal += posHits
            negHitsTotal += negHits

            var tagBias = 0.0
            if (item.tags.any { it in setOf("DIVIDEND", "BONUS/SPLIT", "BUYBACK", "ORDER/CONTRACT") }) tagBias += 1.2
            if ("REGULATORY" in item.tags) tagBias -= 2.0

            val weight = if (item.important) 1.4 else 1.0
            val netItem = ((posHits * 2.2) - (negHits * 2.8) + tagBias) * weight
            newsDelta += netItem.coerceIn(-8.0, 8.0)
        }
        newsPoints += newsDelta.coerceIn(-28.0, 28.0)
        val newsScore = newsPoints.roundToInt().coerceIn(0, 100)
        val importantCount = items.count { it.important }

        val newsSummary = buildString {
            if (chg != null) append("${if (chg >= 0) "+" else ""}${"%.2f".format(chg)}% px")
            else append("No quote")
            append(" · ${items.size} news")
            if (importantCount > 0) append(" ($importantCount !)")
            if (posHitsTotal > negHitsTotal) append(" · +cues")
            else if (negHitsTotal > posHitsTotal) append(" · -cues")
        }

        var analystScore: Int? = null
        var analystSummary: String? = null
        val quarters = analyst?.quarters.orEmpty()
        if (quarters.isNotEmpty()) {
            var aPts = 50.0
            val latest = quarters.first()
            val prev = quarters.getOrNull(1)

            fun seqPct(a: Double?, b: Double?): Double? {
                if (a == null || b == null || b == 0.0) return null
                return (a - b) / abs(b) * 100.0
            }

            val topGrowth = if (analyst?.format == "bank") {
                latest.niiYoyPct ?: seqPct(latest.niiCr, prev?.niiCr)
            } else {
                latest.revenueYoyPct ?: seqPct(latest.revenueCr, prev?.revenueCr)
            }
            if (topGrowth != null) {
                aPts += (topGrowth * 0.35).coerceIn(-12.0, 12.0)
            }

            val patGrowth = latest.netProfitYoyPct ?: seqPct(latest.netProfitCr, prev?.netProfitCr)
            if (patGrowth != null) {
                aPts += (patGrowth * 0.45).coerceIn(-16.0, 16.0)
            }

            val pat = latest.netProfitCr
            if (pat != null) {
                aPts += if (pat > 0.0) 5.0 else -12.0
            }

            val npm = latest.netMarginPct
            if (npm != null) {
                aPts += when {
                    npm >= 15.0 -> 5.0
                    npm >= 8.0 -> 3.0
                    npm < 0.0 -> -6.0
                    else -> 0.0
                }
                val prevNpm = prev?.netMarginPct
                if (prevNpm != null) {
                    aPts += ((npm - prevNpm) * 1.2).coerceIn(-6.0, 6.0)
                }
            }

            val oi = latest.otherIncomeCr
            val pbt = latest.pbtCr
            if (oi != null && pbt != null && pbt > 0 && oi >= 0.25 * pbt) {
                aPts -= 5.0
            }
            val exc = latest.exceptionalCr
            if (exc != null && abs(exc) >= 1.0) {
                aPts -= 3.0
            }

            analystScore = aPts.roundToInt().coerceIn(0, 100)
            analystSummary = buildString {
                append(latest.fiscal)
                if (analyst?.format == "bank" && latest.niiCr != null) {
                    append(" NII ₹${"%.0f".format(latest.niiCr)}Cr")
                } else if (latest.revenueCr != null) {
                    append(" Rev ₹${"%.0f".format(latest.revenueCr)}Cr")
                }
                if (pat != null) append(" · PAT ₹${"%.0f".format(pat)}Cr")
                if (patGrowth != null) append(" (${if (patGrowth >= 0) "+" else ""}${"%.1f".format(patGrowth)}%)")
            }
        }

        val combinedScore = if (analystScore != null) {
            (newsScore * 0.48 + analystScore * 0.52).roundToInt().coerceIn(0, 100)
        } else {
            newsScore
        }

        return SymbolSentiment(
            symbol = symbol.uppercase(),
            score = combinedScore,
            verdict = sentimentVerdict(combinedScore),
            newsScore = newsScore,
            analystScore = analystScore,
            newsItemsCount = items.size,
            importantNewsCount = importantCount,
            changePct = chg,
            newsSummary = newsSummary,
            analystSummary = analystSummary
        )
    }

    fun computeWatchlistSentiment(
        watchlist: List<String>,
        perSymbolInputs: List<Triple<String, NewsReport?, AnalystReport?>>,
        computedAtIst: String
    ): WatchlistSentimentReport {
        val symbolScores = perSymbolInputs.map { (sym, news, analyst) ->
            computeSymbolSentiment(sym, news, analyst)
        }
        if (symbolScores.isEmpty()) {
            return WatchlistSentimentReport(
                watchlist = watchlist,
                overallScore = 50,
                verdict = SentimentVerdict.NEUTRAL,
                newsAvgScore = 50,
                analystAvgScore = null,
                xbrlCoveredCount = 0,
                bullishCount = 0,
                bearishCount = 0,
                neutralCount = 0,
                computedAtIst = computedAtIst,
                symbols = emptyList()
            )
        }

        var weightedSum = 0.0
        var totalWeight = 0.0
        for (s in symbolScores) {
            val w = if (s.analystScore != null) 1.25 else 1.0
            weightedSum += s.score * w
            totalWeight += w
        }
        val overall = (weightedSum / totalWeight).roundToInt().coerceIn(0, 100)
        val newsAvg = symbolScores.map { it.newsScore }.average().roundToInt().coerceIn(0, 100)
        val xbrlScores = symbolScores.mapNotNull { it.analystScore }
        val analystAvg = if (xbrlScores.isNotEmpty()) xbrlScores.average().roundToInt().coerceIn(0, 100) else null

        val bullish = symbolScores.count { it.verdict == SentimentVerdict.BULLISH || it.verdict == SentimentVerdict.MILDLY_BULLISH }
        val bearish = symbolScores.count { it.verdict == SentimentVerdict.BEARISH || it.verdict == SentimentVerdict.MILDLY_BEARISH }
        val neutral = symbolScores.count { it.verdict == SentimentVerdict.NEUTRAL }

        return WatchlistSentimentReport(
            watchlist = watchlist,
            overallScore = overall,
            verdict = sentimentVerdict(overall),
            newsAvgScore = newsAvg,
            analystAvgScore = analystAvg,
            xbrlCoveredCount = xbrlScores.size,
            bullishCount = bullish,
            bearishCount = bearish,
            neutralCount = neutral,
            computedAtIst = computedAtIst,
            symbols = symbolScores
        )
    }
}
