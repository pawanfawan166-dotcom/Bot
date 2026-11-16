package com.example.domain

import com.example.model.DeskConfig
import com.example.model.EntryCheckResult
import com.example.model.EntryStatus
import com.example.model.OhlcvBar
import com.example.model.ScanHit
import com.example.model.UniverseSecurity
import kotlin.math.abs
import kotlin.math.round

object ScannerAndEntryEngine {

    fun parseSecListCsv(csvText: String): Map<String, UniverseSecurity> {
        val out = linkedMapOf<String, UniverseSecurity>()
        val lines = csvText.lineSequence().drop(1)
        for (line in lines) {
            if (line.isBlank()) continue
            val cols = parseCsvLine(line)
            if (cols.size < 4) continue
            val sym = cols[0].trim().uppercase()
            if (sym.isEmpty()) continue
            val series = cols[1].trim().uppercase()
            val name = cols[2].trim()
            val bandText = cols[3].trim()
            val band = bandText.toDoubleOrNull()
            val rec = UniverseSecurity(
                symbol = sym,
                series = series,
                name = name,
                bandPct = band,
                bandText = bandText
            )
            if (!out.containsKey(sym) || series == "EQ") {
                out[sym] = rec
            }
        }
        return out
    }

    fun parseIndexCsv(csvText: String): List<String> {
        val out = mutableListOf<String>()
        val lines = csvText.lines()
        if (lines.isEmpty()) return out
        val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
        val symIdx = header.indexOf("symbol").takeIf { it >= 0 } ?: 2
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val cols = parseCsvLine(line)
            val sym = cols.getOrNull(symIdx)?.trim()?.uppercase() ?: continue
            if (sym.isNotEmpty() && sym != "SYMBOL") out.add(sym)
        }
        return out.distinct()
    }

    fun parseCsvLine(line: String): List<String> {
        val res = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    res.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        res.add(sb.toString())
        return res
    }

    /**
     * Wilder RSI(14) over closing prices.
     */
    fun wilderRsi(closes: List<Double>, period: Int = 14): Double? {
        if (closes.size <= period) return null
        val deltas = closes.zipWithNext { a, b -> b - a }
        var avgGain = deltas.take(period).filter { it > 0 }.sum() / period
        var avgLoss = deltas.take(period).filter { it < 0 }.sumOf { -it } / period
        for (i in period until deltas.size) {
            val d = deltas[i]
            val gain = if (d > 0) d else 0.0
            val loss = if (d < 0) -d else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return round((100.0 - 100.0 / (1.0 + rs)) * 10.0) / 10.0
    }

    fun scanDaily(
        seriesMap: Map<String, YahooChartSeries>,
        secList: Map<String, UniverseSecurity>,
        foSet: Set<String>,
        setupFilter: String = "all",
        includeT2t: Boolean = false
    ): List<ScanHit> {
        val hits = mutableListOf<ScanHit>()
        for ((sym, series) in seriesMap) {
            val sec = secList[sym]
            if (!includeT2t && sec != null && sec.series in setOf("BE", "BZ")) continue
            val closes = series.bars.map { it.close }
            if (closes.size < 22) continue

            val lastClose = closes.last()
            val prevClose = closes[closes.size - 2]
            val chgPct = if (prevClose > 0) (lastClose - prevClose) / prevClose * 100.0 else 0.0
            val rsi = wilderRsi(closes, 14)
            val ma20 = closes.takeLast(20).average()
            val high20Prior = closes.dropLast(1).takeLast(20).maxOrNull() ?: lastClose

            // Bull flag: >10% run in prior 10d followed by 5d consolidation < 5% range
            val isBullFlag = if (closes.size >= 16) {
                val cons = closes.takeLast(5)
                val pole = closes.dropLast(5).takeLast(10)
                val poleMin = pole.minOrNull() ?: 1.0
                val poleMax = pole.maxOrNull() ?: 1.0
                val poleRun = (poleMax - poleMin) / poleMin * 100.0
                val consRange = ((cons.maxOrNull() ?: 0.0) - (cons.minOrNull() ?: 0.0)) / (cons.minOrNull() ?: 1.0) * 100.0
                poleRun >= 10.0 && consRange <= 5.0 && lastClose >= poleMax * 0.94
            } else false

            val isBreakout20d = lastClose > high20Prior
            val isReclaimMa20 = prevClose < ma20 && lastClose > ma20
            val isOversold = rsi != null && rsi < 30.0
            val isOversoldBounce = rsi != null && rsi < 38.0 && chgPct > 1.2 && prevClose < ma20

            val detected = mutableListOf<Pair<String, String>>()
            if (isBreakout20d) detected.add("breakout_20d" to "20D Breakout")
            if (isBullFlag) detected.add("bull_flag" to "Bull Flag")
            if (isReclaimMa20) detected.add("reclaim_ma20" to "Reclaim MA20")
            if (isOversoldBounce) detected.add("oversold_bounce" to "Oversold Bounce")
            else if (isOversold) detected.add("oversold" to "RSI Oversold")

            for ((code, badge) in detected) {
                if (setupFilter != "all" && setupFilter != code) continue
                val band = sec?.bandPct
                val circ = band?.let { IndianMarketUtils.circuits(prevClose, it) }
                val nearUc = circ != null && lastClose >= circ.second * 0.99
                hits.add(
                    ScanHit(
                        symbol = sym,
                        exchange = "NSE",
                        setup = code,
                        badge = badge,
                        lastPrice = IndianMarketUtils.round2(lastClose),
                        prevClose = IndianMarketUtils.round2(prevClose),
                        changePct = IndianMarketUtils.round2(chgPct),
                        rsi14 = rsi,
                        breakoutLevel = IndianMarketUtils.round2(high20Prior),
                        nearUpperCircuit = nearUc,
                        series = sec?.series ?: "EQ",
                        bandPct = band,
                        isCas = foSet.contains(sym),
                        summary = buildString {
                            append("Close ${IndianMarketUtils.inr(lastClose)} (${if (chgPct >= 0) "+" else ""}${"%.2f".format(chgPct)}%)")
                            if (rsi != null) append(" · RSI $rsi")
                            append(" · MA20 ${IndianMarketUtils.inr(ma20)}")
                        }
                    )
                )
            }
        }
        return hits.sortedByDescending { abs(it.changePct) }
    }

    fun scanOrb(
        intradayMap: Map<String, YahooChartSeries>,
        secList: Map<String, UniverseSecurity>,
        foSet: Set<String>,
        asOfIst: java.time.LocalDateTime,
        orMinutes: Int = 15,
        minVolumeX: Double = 1.0,
        includeT2t: Boolean = false
    ): List<ScanHit> {
        val nOr = (orMinutes / 5).coerceAtLeast(1)
        val hits = mutableListOf<ScanHit>()
        val targetDay = asOfIst.toLocalDate().toString()

        for ((sym, series) in intradayMap) {
            val sec = secList[sym]
            if (!includeT2t && sec != null && sec.series in setOf("BE", "BZ")) continue

            val completed = YahooParser.filterCompletedBars(series.bars, 300, asOfIst)
            val dayBars = completed.filter { it.date == targetDay }.ifEmpty {
                val lastDay = completed.lastOrNull()?.date ?: continue
                completed.filter { it.date == lastDay }
            }
            if (dayBars.size <= nOr) continue
            val bySlot = dayBars.associateBy { it.slot }
            if ((0 until nOr).any { !bySlot.containsKey(it) }) continue

            val orBars = (0 until nOr).map { bySlot.getValue(it) }
            val orHigh = orBars.maxOf { it.high }
            val orLow = orBars.minOf { it.low }

            // Zero-volume 09:15 bar exclusion matching Python rule
            val avgVolSamples = dayBars.filter { !(it.slot == 0 && it.volume == 0L) }.map { it.volume.toDouble() }
            val avgBarVol = avgVolSamples.takeIf { it.isNotEmpty() }?.average() ?: 1.0

            val triggerBar = (nOr until IndianMarketUtils.SESSION_BARS)
                .asSequence()
                .mapNotNull { bySlot[it] }
                .firstOrNull { b ->
                    val volX = if (avgBarVol > 0) b.volume / avgBarVol else 1.0
                    b.close > orHigh && volX >= minVolumeX
                } ?: continue

            val lastBar = dayBars.last()
            val prevClose = series.prevClose ?: dayBars.first().open
            val chgPct = if (prevClose > 0) (lastBar.close - prevClose) / prevClose * 100.0 else 0.0
            val trigVolX = if (avgBarVol > 0) IndianMarketUtils.round2(triggerBar.volume / avgBarVol) else 1.0
            val trigCloseTime = IndianMarketUtils.clockString(
                IndianMarketUtils.OPEN_MIN + (triggerBar.slot + 1) * 5
            )
            val band = sec?.bandPct
            val circ = band?.let { IndianMarketUtils.circuits(prevClose, it) }
            val nearUc = circ != null && lastBar.close >= circ.second * 0.99

            hits.add(
                ScanHit(
                    symbol = sym,
                    exchange = "NSE",
                    setup = "orb",
                    badge = "ORB ${orMinutes}m",
                    lastPrice = IndianMarketUtils.round2(lastBar.close),
                    prevClose = IndianMarketUtils.round2(prevClose),
                    changePct = IndianMarketUtils.round2(chgPct),
                    volumeX = trigVolX,
                    orHigh = IndianMarketUtils.round2(orHigh),
                    orLow = IndianMarketUtils.round2(orLow),
                    triggerTime = trigCloseTime,
                    nearUpperCircuit = nearUc,
                    series = sec?.series ?: "EQ",
                    bandPct = band,
                    isCas = foSet.contains(sym),
                    summary = "Broke OR high ${IndianMarketUtils.inr(orHigh)} at $trigCloseTime IST (${trigVolX}x bar vol)"
                )
            )
        }
        return hits.sortedByDescending { it.changePct }
    }

    /**
     * Ranks the top [limit] (default 5) NSE or BSE equities by intraday relative volume (bar vol / avg bar vol)
     * and computes Opening Range Breakout (ORB) high/low levels and breakout state for quick-view scanning.
     */
    fun topOrbByRelativeVolume(
        intradayMap: Map<String, YahooChartSeries>,
        secList: Map<String, UniverseSecurity>,
        foSet: Set<String>,
        exchange: String,
        asOfIst: java.time.LocalDateTime,
        orMinutes: Int = 15,
        includeT2t: Boolean = false,
        limit: Int = 5
    ): List<com.example.model.OrbQuickRow> {
        val cleanEx = if (exchange.uppercase() == "BSE") "BSE" else "NSE"
        val nOr = (orMinutes / 5).coerceAtLeast(1)
        val targetDay = asOfIst.toLocalDate().toString()
        val rows = mutableListOf<com.example.model.OrbQuickRow>()

        for ((sym, series) in intradayMap) {
            val sec = secList[sym]
            if (!includeT2t && sec != null && sec.series in setOf("BE", "BZ")) continue

            val completed = YahooParser.filterCompletedBars(series.bars, 300, asOfIst)
            val dayBars = completed.filter { it.date == targetDay }.ifEmpty {
                val lastDay = completed.lastOrNull()?.date ?: continue
                completed.filter { it.date == lastDay }
            }
            if (dayBars.size < nOr) continue
            val bySlot = dayBars.associateBy { it.slot }
            if ((0 until nOr).any { !bySlot.containsKey(it) }) continue

            val orBars = (0 until nOr).map { bySlot.getValue(it) }
            val orHigh = orBars.maxOf { it.high }
            val orLow = orBars.minOf { it.low }
            val orRangePct = if (orLow > 0) IndianMarketUtils.round2((orHigh - orLow) / orLow * 100.0) else 0.0

            // Exclude zero-volume 09:15 bar from average bar volume baseline
            val avgVolSamples = dayBars.filter { !(it.slot == 0 && it.volume == 0L) }.map { it.volume.toDouble() }
            val avgBarVol = avgVolSamples.takeIf { it.isNotEmpty() }?.average() ?: continue
            if (avgBarVol <= 0.0) continue

            val lastBar = dayBars.last()
            val latestNonZero = dayBars.lastOrNull { it.volume > 0L } ?: lastBar
            val latestVolX = latestNonZero.volume / avgBarVol

            val triggerBar = (nOr until IndianMarketUtils.SESSION_BARS)
                .asSequence()
                .mapNotNull { bySlot[it] }
                .firstOrNull { b -> b.close > orHigh && (b.volume / avgBarVol) >= 1.0 }

            val trigVolX = triggerBar?.let { it.volume / avgBarVol } ?: 0.0
            val effectiveVolX = IndianMarketUtils.round2(maxOf(latestVolX, trigVolX))
            val trigTime = triggerBar?.let {
                IndianMarketUtils.clockString(IndianMarketUtils.OPEN_MIN + (it.slot + 1) * 5)
            }

            val breakoutState = when {
                lastBar.close > orHigh || (triggerBar != null && lastBar.close >= orHigh * 0.998) ->
                    com.example.model.OrbBreakoutState.BROKEN_ORH
                lastBar.close >= orHigh * 0.995 ->
                    com.example.model.OrbBreakoutState.AT_HIGH
                lastBar.close < orLow ->
                    com.example.model.OrbBreakoutState.BELOW_ORL
                else ->
                    com.example.model.OrbBreakoutState.INSIDE_OR
            }

            val prevClose = series.prevClose ?: dayBars.first().open
            val chgPct = if (prevClose > 0) IndianMarketUtils.round2((lastBar.close - prevClose) / prevClose * 100.0) else 0.0
            val circ = sec?.bandPct?.let { IndianMarketUtils.circuits(prevClose, it) }
            val nearUc = circ != null && lastBar.close >= circ.second * 0.99

            val summaryText = when (breakoutState) {
                com.example.model.OrbBreakoutState.BROKEN_ORH ->
                    if (trigTime != null) "ORH ${IndianMarketUtils.inr(orHigh)} broken @ $trigTime IST"
                    else "Trading above ${orMinutes}m ORH ${IndianMarketUtils.inr(orHigh)}"
                com.example.model.OrbBreakoutState.AT_HIGH ->
                    "Coiling near ${orMinutes}m ORH ${IndianMarketUtils.inr(orHigh)}"
                com.example.model.OrbBreakoutState.INSIDE_OR ->
                    "Inside ${orMinutes}m OR (${IndianMarketUtils.inr(orLow)}–${IndianMarketUtils.inr(orHigh)})"
                com.example.model.OrbBreakoutState.BELOW_ORL ->
                    "Below ${orMinutes}m ORL ${IndianMarketUtils.inr(orLow)}"
            }

            rows.add(
                com.example.model.OrbQuickRow(
                    rank = 0,
                    symbol = sym,
                    exchange = cleanEx,
                    lastPrice = IndianMarketUtils.round2(lastBar.close),
                    prevClose = IndianMarketUtils.round2(prevClose),
                    changePct = chgPct,
                    relativeVolumeX = effectiveVolX,
                    orHigh = IndianMarketUtils.round2(orHigh),
                    orLow = IndianMarketUtils.round2(orLow),
                    orRangePct = orRangePct,
                    breakoutState = breakoutState,
                    triggerTime = trigTime,
                    isCas = foSet.contains(sym),
                    nearUpperCircuit = nearUc,
                    summary = summaryText
                )
            )
        }

        return rows
            .sortedWith(
                compareByDescending<com.example.model.OrbQuickRow> { it.relativeVolumeX }
                    .thenByDescending { abs(it.changePct) }
                    .thenBy { it.symbol }
            )
            .take(limit.coerceAtLeast(1))
            .mapIndexed { idx, row -> row.copy(rank = idx + 1) }
    }

    /**
     * Computes market heatmap cells for the given [symbols] (Nifty 50 or Watchlist),
     * measuring intraday price volatility ((dayHigh - dayLow) / refPrice * 100) and
     * relative volume (latest completed bar volume / average bar volume), normalizing
     * color intensity across 0.18f..1.0f according to [metric].
     */
    fun buildMarketHeatmap(
        intradayMap: Map<String, YahooChartSeries>,
        dailyMap: Map<String, YahooChartSeries>,
        symbols: List<String>,
        secList: Map<String, UniverseSecurity>,
        exchange: String,
        asOfIst: java.time.LocalDateTime,
        orMinutes: Int = 15,
        metric: com.example.model.HeatmapIntensityMetric = com.example.model.HeatmapIntensityMetric.COMBINED,
        includeT2t: Boolean = false
    ): List<com.example.model.MarketHeatmapCell> {
        val cleanEx = if (exchange.uppercase() == "BSE") "BSE" else "NSE"
        val targetDay = asOfIst.toLocalDate().toString()
        val nOr = (orMinutes / 5).coerceAtLeast(1)

        data class RawCell(
            val symbol: String,
            val lastPrice: Double,
            val changePct: Double,
            val volatilityPct: Double,
            val relativeVolumeX: Double,
            val orHigh: Double?,
            val orLow: Double?,
            val rawScore: Double
        )

        val cleanUniverse = symbols.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        val sourceSymbols = if (cleanUniverse.isNotEmpty()) {
            cleanUniverse
        } else {
            (intradayMap.keys + dailyMap.keys).distinct().sorted()
        }

        val rawList = mutableListOf<RawCell>()
        for (sym in sourceSymbols) {
            val sec = secList[sym]
            if (!includeT2t && sec != null && sec.series in setOf("BE", "BZ")) continue

            val intra = intradayMap[sym]
            val dayBars = intra?.let { s ->
                val completed = YahooParser.filterCompletedBars(s.bars, 300, asOfIst)
                completed.filter { it.date == targetDay }.ifEmpty {
                    val lastDay = completed.lastOrNull()?.date
                    if (lastDay != null) completed.filter { it.date == lastDay } else emptyList()
                }
            }.orEmpty()

            if (dayBars.isNotEmpty() && intra != null) {
                val lastBar = dayBars.last()
                val prevClose = intra.prevClose ?: dayBars.first().open
                val refPrice = prevClose.takeIf { it > 0 } ?: lastBar.close
                val sessionHigh = dayBars.maxOf { it.high }
                val sessionLow = dayBars.minOf { it.low }
                val volatilityPct = if (refPrice > 0) {
                    IndianMarketUtils.round2((sessionHigh - sessionLow) / refPrice * 100.0)
                } else 0.0
                val chgPct = if (refPrice > 0) {
                    IndianMarketUtils.round2((lastBar.close - refPrice) / refPrice * 100.0)
                } else 0.0

                val validVols = dayBars.filter { !(it.slot == 0 && it.volume == 0L) }.map { it.volume.toDouble() }
                val avgVol = validVols.takeIf { it.isNotEmpty() }?.average() ?: 1.0
                val latestNonZero = dayBars.lastOrNull { it.volume > 0L } ?: lastBar
                val relVolX = if (avgVol > 0.0) IndianMarketUtils.round2(latestNonZero.volume / avgVol) else 1.0

                val bySlot = dayBars.associateBy { it.slot }
                val orAvailable = (0 until nOr).all { bySlot.containsKey(it) }
                val orHigh = if (orAvailable) IndianMarketUtils.round2((0 until nOr).maxOf { bySlot.getValue(it).high }) else null
                val orLow = if (orAvailable) IndianMarketUtils.round2((0 until nOr).minOf { bySlot.getValue(it).low }) else null

                val score = when (metric) {
                    com.example.model.HeatmapIntensityMetric.VOLATILITY -> volatilityPct
                    com.example.model.HeatmapIntensityMetric.RELATIVE_VOLUME -> relVolX
                    com.example.model.HeatmapIntensityMetric.COMBINED -> (volatilityPct + 0.35) * relVolX.coerceAtLeast(0.35)
                }

                rawList.add(
                    RawCell(
                        symbol = sym,
                        lastPrice = IndianMarketUtils.round2(lastBar.close),
                        changePct = chgPct,
                        volatilityPct = volatilityPct,
                        relativeVolumeX = relVolX,
                        orHigh = orHigh,
                        orLow = orLow,
                        rawScore = score
                    )
                )
            } else {
                val daily = dailyMap[sym] ?: continue
                if (daily.bars.isEmpty()) continue
                val lastBar = daily.bars.last()
                val prevClose = if (daily.bars.size >= 2) daily.bars[daily.bars.size - 2].close else (daily.prevClose ?: lastBar.open)
                val refPrice = prevClose.takeIf { it > 0 } ?: lastBar.close
                val volatilityPct = if (refPrice > 0) {
                    IndianMarketUtils.round2((lastBar.high - lastBar.low) / refPrice * 100.0)
                } else 0.0
                val chgPct = if (refPrice > 0) {
                    IndianMarketUtils.round2((lastBar.close - refPrice) / refPrice * 100.0)
                } else 0.0
                val avgDailyVol = daily.bars.takeLast(20).map { it.volume.toDouble() }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average() ?: 1.0
                val relVolX = if (avgDailyVol > 0 && lastBar.volume > 0) IndianMarketUtils.round2(lastBar.volume / avgDailyVol) else 1.0

                val score = when (metric) {
                    com.example.model.HeatmapIntensityMetric.VOLATILITY -> volatilityPct
                    com.example.model.HeatmapIntensityMetric.RELATIVE_VOLUME -> relVolX
                    com.example.model.HeatmapIntensityMetric.COMBINED -> (volatilityPct + 0.35) * relVolX.coerceAtLeast(0.35)
                }

                rawList.add(
                    RawCell(
                        symbol = sym,
                        lastPrice = IndianMarketUtils.round2(lastBar.close),
                        changePct = chgPct,
                        volatilityPct = volatilityPct,
                        relativeVolumeX = relVolX,
                        orHigh = null,
                        orLow = null,
                        rawScore = score
                    )
                )
            }
        }

        val maxScore = rawList.maxOfOrNull { it.rawScore }?.coerceAtLeast(1e-6) ?: 1.0
        return rawList
            .sortedWith(
                compareByDescending<RawCell> { it.rawScore }
                    .thenByDescending { abs(it.changePct) }
                    .thenBy { it.symbol }
            )
            .map { r ->
                val norm = (r.rawScore / maxScore).toFloat().coerceIn(0f, 1f)
                com.example.model.MarketHeatmapCell(
                    symbol = r.symbol,
                    exchange = cleanEx,
                    lastPrice = r.lastPrice,
                    changePct = r.changePct,
                    volatilityPct = r.volatilityPct,
                    relativeVolumeX = r.relativeVolumeX,
                    orHigh = r.orHigh,
                    orLow = r.orLow,
                    intensity = (0.18f + norm * 0.82f).coerceIn(0.18f, 1.0f)
                )
            }
    }

    fun evaluateEntry(
        symbol: String,
        exchange: String,
        chart: YahooChartSeries?,
        setup: String, // "orb" or "level"
        levelPrice: Double? = null,
        orMinutes: Int = 15,
        minVolumeX: Double = 1.0,
        interval: String = "5m",
        config: DeskConfig,
        asOfIst: java.time.LocalDateTime
    ): EntryCheckResult {
        val intervalSec = if (interval == "1m") 60 else 300
        val barsPerHour = if (interval == "1m") 1 else 5
        val dayStr = asOfIst.toLocalDate().toString()

        if (chart == null || chart.bars.isEmpty()) {
            return EntryCheckResult(
                symbol = symbol,
                exchange = exchange,
                date = dayStr,
                setup = setup,
                interval = interval,
                status = EntryStatus.NO_DATA,
                orMinutes = orMinutes,
                minVolumeX = minVolumeX,
                message = "No intraday bars available for $exchange:$symbol"
            )
        }

        val completed = YahooParser.filterCompletedBars(chart.bars, intervalSec, asOfIst)
        val activeDay = completed.filter { it.date == dayStr }.let { todayBars ->
            if (todayBars.isNotEmpty()) dayStr to todayBars
            else {
                val latestDay = completed.lastOrNull()?.date ?: dayStr
                latestDay to completed.filter { it.date == latestDay }
            }
        }
        val (evalDate, dayBars) = activeDay
        if (dayBars.isEmpty()) {
            return EntryCheckResult(
                symbol = symbol,
                exchange = exchange,
                date = evalDate,
                setup = setup,
                interval = interval,
                status = EntryStatus.NO_DATA,
                orMinutes = orMinutes,
                minVolumeX = minVolumeX,
                message = "Waiting for the first completed $interval bar on $evalDate"
            )
        }

        val bySlot = dayBars.associateBy { it.slot }
        val lastBar = dayBars.last()
        val lastCloseTime = IndianMarketUtils.clockString(
            IndianMarketUtils.OPEN_MIN + (lastBar.slot + 1) * barsPerHour
        )
        val noAfterMin = IndianMarketUtils.parseClockMinutes(config.noEntriesAfter) ?: (14 * 60 + 45)
        val noBeforeMin = IndianMarketUtils.parseClockMinutes(config.noEntriesBefore) ?: (9 * 60 + 30)

        val nOr = (orMinutes / barsPerHour).coerceAtLeast(1)
        var orHigh: Double? = null
        var orLow: Double? = null
        if (setup == "orb") {
            if ((0 until nOr).any { !bySlot.containsKey(it) }) {
                return EntryCheckResult(
                    symbol = symbol,
                    exchange = exchange,
                    date = evalDate,
                    setup = setup,
                    interval = interval,
                    status = EntryStatus.WAITING,
                    orMinutes = orMinutes,
                    minVolumeX = minVolumeX,
                    lastClose = IndianMarketUtils.round2(lastBar.close),
                    lastTime = lastCloseTime,
                    message = "Opening range ($orMinutes min) is still forming on $evalDate",
                    bars = dayBars
                )
            }
            val orSlice = (0 until nOr).map { bySlot.getValue(it) }
            orHigh = IndianMarketUtils.round2(orSlice.maxOf { it.high })
            orLow = IndianMarketUtils.round2(orSlice.minOf { it.low })
        }

        val triggerTarget = if (setup == "orb") orHigh!! else (levelPrice ?: orHigh ?: lastBar.close)
        val startSlot = if (setup == "orb") nOr else 0

        // Exclude zero-volume 09:15 bar from average volume baseline
        val validVols = dayBars.filter { !(it.slot == 0 && it.volume == 0L) }.map { it.volume.toDouble() }
        val avgVol = validVols.takeIf { it.isNotEmpty() }?.average() ?: 1.0

        val maxSlot = ((noAfterMin - IndianMarketUtils.OPEN_MIN) / barsPerHour) - 1
        val minSlot = ((noBeforeMin - IndianMarketUtils.OPEN_MIN) / barsPerHour) - 1

        val matched = dayBars.firstOrNull { b ->
            if (b.slot < startSlot) return@firstOrNull false
            if (b.slot < minSlot || b.slot > maxSlot) return@firstOrNull false
            val volX = if (avgVol > 0) b.volume / avgVol else 1.0
            b.close > triggerTarget && volX >= minVolumeX
        }

        if (matched != null) {
            val trigTime = IndianMarketUtils.clockString(
                IndianMarketUtils.OPEN_MIN + (matched.slot + 1) * barsPerHour
            )
            val volX = IndianMarketUtils.round2(if (avgVol > 0) matched.volume / avgVol else 1.0)
            return EntryCheckResult(
                symbol = symbol,
                exchange = exchange,
                date = evalDate,
                setup = setup,
                interval = interval,
                status = EntryStatus.TRIGGERED,
                entryPrice = IndianMarketUtils.roundToTick(matched.close, "nearest"),
                entryTime = trigTime,
                entryBar = matched.slot,
                level = IndianMarketUtils.round2(triggerTarget),
                orHigh = orHigh,
                orLow = orLow,
                orMinutes = orMinutes,
                volumeX = volX,
                minVolumeX = minVolumeX,
                lastClose = IndianMarketUtils.round2(lastBar.close),
                lastTime = lastCloseTime,
                message = "Triggered at $trigTime IST: bar closed at ${IndianMarketUtils.inr(matched.close)} > ${IndianMarketUtils.inr(triggerTarget)} (${volX}x vol)",
                bars = dayBars
            )
        }

        val asOfMin = asOfIst.hour * 60 + asOfIst.minute
        val expired = asOfMin > noAfterMin
        return EntryCheckResult(
            symbol = symbol,
            exchange = exchange,
            date = evalDate,
            setup = setup,
            interval = interval,
            status = if (expired) EntryStatus.EXPIRED else EntryStatus.WAITING,
            level = IndianMarketUtils.round2(triggerTarget),
            orHigh = orHigh,
            orLow = orLow,
            orMinutes = orMinutes,
            minVolumeX = minVolumeX,
            lastClose = IndianMarketUtils.round2(lastBar.close),
            lastTime = lastCloseTime,
            message = if (expired) {
                "Entry window closed at ${config.noEntriesAfter} IST without breaking ${IndianMarketUtils.inr(triggerTarget)}"
            } else {
                "Waiting for a completed $interval close above ${IndianMarketUtils.inr(triggerTarget)} (last ${IndianMarketUtils.inr(lastBar.close)} at $lastCloseTime IST)"
            },
            bars = dayBars
        )
    }
}
