package com.example.domain

import com.example.model.BacktestTrade
import com.example.model.BandRow
import com.example.model.DeskConfig
import com.example.model.EntryCheckResult
import com.example.model.HistogramData
import com.example.model.OhlcvBar
import com.example.model.OpeningRange
import com.example.model.StopRuleType
import com.example.model.TargetRuleType
import com.example.model.TimeMachineResult
import com.example.model.TradePlan
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.random.Random

object ExitAndTimeMachineEngine {

    private const val COIN_FLIP = 0.05
    private const val STRONG = 0.15
    private const val MANY_NEITHER = 0.25
    private const val BAND_PATHS = 2000
    private const val SAMPLE_PATHS = 40
    private const val HIST_BINS = 24
    private const val MAX_FILL = 3

    fun computeAtr14(bars: List<OhlcvBar>, upToIndex: Int? = null): Double? {
        val slice = if (upToIndex != null) bars.take(upToIndex + 1) else bars
        if (slice.size < 15) return null
        val trs = mutableListOf<Double>()
        for (i in 1 until slice.size) {
            val b = slice[i]
            val pc = slice[i - 1].close
            val tr = maxOf(b.high - b.low, abs(b.high - pc), abs(b.low - pc))
            trs.add(tr)
        }
        return trs.takeLast(14).average()
    }

    fun buildTradePlan(
        entryResult: EntryCheckResult,
        stopRule: StopRuleType,
        stopParam: Double, // pct (e.g. 1.0), atr multiple (e.g. 1.5), or custom stop price
        targetRule: TargetRuleType,
        targetPriceParam: Double,
        config: DeskConfig,
        foSymbols: Set<String>?,
        secBandPct: Double?,
        prevClose: Double?
    ): Result<TradePlan> {
        val rawEntry = entryResult.entryPrice ?: entryResult.lastClose
            ?: return Result.failure(IllegalArgumentException("No entry price available to build exit plan"))
        val entry = IndianMarketUtils.roundToTick(rawEntry, "nearest")
        val tick = IndianMarketUtils.tickSize(entry)
        val product = config.product.uppercase().ifEmpty { "MIS" }
        val (cas, _) = IndianMarketUtils.casStatus(
            symbol = entryResult.symbol,
            extraCas = config.casSymbolsExtra.map { it.uppercase() }.toSet(),
            nonCas = config.nonCasSymbols.map { it.uppercase() }.toSet(),
            foSymbols = foSymbols
        )
        val rules = IndianMarketUtils.sessionRules(cas, entryResult.date)
        val (flatBy, flatWarn) = IndianMarketUtils.resolveFlatBy(config.flatBy, rules)
        val warnings = mutableListOf<String>()
        if (flatWarn != null) warnings.add(flatWarn)

        val orHigh = entryResult.orHigh
        val orLow = entryResult.orLow
        val rawStop: Double
        val stopLabel: String
        when (stopRule) {
            StopRuleType.OR_MID -> {
                if (orHigh == null || orLow == null) {
                    return Result.failure(IllegalArgumentException("Opening range unavailable for OR Midpoint stop"))
                }
                rawStop = (orHigh + orLow) / 2.0
                stopLabel = "opening-range midpoint"
            }
            StopRuleType.OR_LOW -> {
                if (orLow == null) {
                    return Result.failure(IllegalArgumentException("Opening range unavailable for OR Low stop"))
                }
                rawStop = orLow
                stopLabel = "opening-range low"
            }
            StopRuleType.PCT -> {
                val p = stopParam.coerceIn(0.1, 20.0)
                rawStop = entry * (1.0 - p / 100.0)
                stopLabel = "${"%.2f".format(p)}% below entry"
            }
            StopRuleType.ATR -> {
                val mult = stopParam.coerceIn(0.25, 5.0)
                val atr = computeAtr14(entryResult.bars, entryResult.entryBar)
                    ?: (entry * 0.008)
                rawStop = entry - mult * atr
                stopLabel = "${"%.2f".format(mult)}x ATR(14)"
            }
            StopRuleType.PRICE -> {
                rawStop = stopParam
                stopLabel = "custom stop price"
            }
        }

        val stop = IndianMarketUtils.roundToTick(rawStop, "down")
        if (stop >= entry) {
            return Result.failure(
                IllegalArgumentException("Stop (${IndianMarketUtils.inr(stop)}) must be below entry (${IndianMarketUtils.inr(entry)}) for a long trade")
            )
        }
        val riskPerShare = IndianMarketUtils.round2(entry - stop)
        val minRisk = max(entry * 0.0005, tick)
        if (riskPerShare < minRisk) {
            return Result.failure(
                IllegalArgumentException("Risk per share (${IndianMarketUtils.inr(riskPerShare)}) is below noise floor (${IndianMarketUtils.inr(minRisk)})")
            )
        }

        if (prevClose != null && secBandPct != null) {
            val circ = IndianMarketUtils.circuits(prevClose, secBandPct)
            if (circ != null && stop < circ.first) {
                return Result.failure(
                    IllegalArgumentException("Stop ${IndianMarketUtils.inr(stop)} sits below lower circuit ${IndianMarketUtils.inr(circ.first)} (${secBandPct.toInt()}% band)")
                )
            }
        }

        val rawTarget: Double
        val targetLabel: String
        val targetR: Double
        when (targetRule) {
            TargetRuleType.R1 -> {
                rawTarget = entry + 1.0 * riskPerShare
                targetR = 1.0
                targetLabel = "1R (one times the risk)"
            }
            TargetRuleType.R1_5 -> {
                rawTarget = entry + 1.5 * riskPerShare
                targetR = 1.5
                targetLabel = "1.5R"
            }
            TargetRuleType.R2 -> {
                rawTarget = entry + 2.0 * riskPerShare
                targetR = 2.0
                targetLabel = "2R"
            }
            TargetRuleType.PRICE -> {
                rawTarget = targetPriceParam
                targetR = IndianMarketUtils.round2((rawTarget - entry) / riskPerShare)
                targetLabel = "custom target (${targetR}R)"
            }
        }
        val target = IndianMarketUtils.roundToTick(rawTarget, "down")
        if (target <= entry) {
            return Result.failure(
                IllegalArgumentException("Target (${IndianMarketUtils.inr(target)}) must be above entry (${IndianMarketUtils.inr(entry)})")
            )
        }
        val actualRr = IndianMarketUtils.round2((target - entry) / riskPerShare)

        val openingRange = if (orHigh != null && orLow != null) {
            OpeningRange(high = orHigh, low = orLow, minutes = entryResult.orMinutes)
        } else null

        return Result.success(
            TradePlan(
                symbol = entryResult.symbol,
                exchange = entryResult.exchange,
                currency = "INR",
                tick = tick,
                side = "buy",
                product = product,
                date = entryResult.date,
                setup = entryResult.setup,
                entry = entry,
                entryTime = entryResult.entryTime ?: entryResult.lastTime ?: "09:50",
                entryBar = entryResult.entryBar ?: 6,
                interval = "5m",
                openingRange = openingRange,
                stop = stop,
                stopRule = stopRule.code,
                stopLabel = stopLabel,
                target = target,
                targetR = actualRr,
                targetLabel = targetLabel,
                riskPerShare = riskPerShare,
                rewardRisk = actualRr,
                flatBy = flatBy,
                cas = cas,
                warnings = warnings
            )
        )
    }

    fun runTimeMachine(
        historySeries: YahooChartSeries,
        plan: TradePlan,
        foSymbols: Set<String>?,
        replays: Int = 10000,
        seed: Int = 19,
        window: Int = 2,
        qtyForNet: Int = 30
    ): Result<TimeMachineResult> {
        val (cas, _) = IndianMarketUtils.casStatus(plan.symbol, plan.cas, foSymbols = foSymbols)
        val rules = IndianMarketUtils.sessionRules(cas, plan.date)
        val until = plan.flatBy
        val untilMin = IndianMarketUtils.parseClockMinutes(until) ?: 15 * 60
        val last = ((untilMin - IndianMarketUtils.OPEN_MIN) / 5) - 1
        val k = plan.entryBar
        if (last <= k) {
            return Result.failure(IllegalArgumentException("flat_by $until leaves no bars after entry ${plan.entryTime}"))
        }

        val byDay = historySeries.bars.groupBy { it.date }
        val fullSessions = mutableListOf<Pair<String, List<OhlcvBar>>>()
        var skipped = 0
        val warnings = mutableListOf<String>()

        for (day in byDay.keys.sorted()) {
            if (day >= plan.date) continue
            val dayRules = IndianMarketUtils.sessionRules(cas, day)
            val nb = dayRules.bars5m
            val slotMap = byDay.getValue(day).filter { it.slot in 0 until nb }.associateBy { it.slot }
            val gaps = nb - slotMap.size
            if (slotMap.containsKey(0) && gaps <= MAX_FILL) {
                val rows = mutableListOf<OhlcvBar>()
                for (i in 0 until nb) {
                    val existing = slotMap[i]
                    if (existing != null) {
                        rows.add(existing)
                    } else {
                        val pc = rows.last().close
                        rows.add(
                            OhlcvBar(
                                epochSec = rows.last().epochSec + 300,
                                date = day,
                                timeHm = IndianMarketUtils.clockString(IndianMarketUtils.OPEN_MIN + 5 * i),
                                slot = i,
                                open = pc,
                                high = pc,
                                low = pc,
                                close = pc,
                                volume = 0L,
                                filled = true
                            )
                        )
                    }
                }
                fullSessions.add(day to rows)
            } else {
                skipped++
            }
        }

        if (fullSessions.size < 20) {
            return Result.failure(
                IllegalArgumentException("Need at least 20 full 5-minute sessions before ${plan.date} for replay, found ${fullSessions.size}")
            )
        }

        // pool[i] = list of (o, h, l, c) ratios / prev_close
        data class RelBar(val o: Double, val h: Double, val l: Double, val c: Double)
        val pool = Array(IndianMarketUtils.SESSION_BARS) { mutableListOf<RelBar>() }
        for ((_, sessionBars) in fullSessions) {
            for (i in 1 until sessionBars.size) {
                val pc = sessionBars[i - 1].close
                if (pc <= 0) continue
                val b = sessionBars[i]
                pool[i].add(RelBar(b.open / pc, b.high / pc, b.low / pc, b.close / pc))
            }
        }

        val nbars = rules.bars5m
        fun sampleBar(rng: Random, i: Int): RelBar {
            var s = if (window == 0) i else (i + rng.nextInt(-window, window + 1)).coerceIn(1, nbars - 1)
            while (pool[s].isEmpty() && s > 1) s--
            val list = pool[s]
            return list[rng.nextInt(list.size)]
        }

        val risk = plan.entry - plan.stop
        val rAtTarget = round((plan.target - plan.entry) / risk * 10000.0) / 10000.0

        val rng1 = Random(seed)
        var targetCount = 0
        var stopCount = 0
        var neitherCount = 0
        var greenCount = 0
        val closes = DoubleArray(replays)
        val rs = DoubleArray(replays)
        val rNets = DoubleArray(replays)
        val samplePaths = mutableListOf<List<Double>>()
        val q = qtyForNet.coerceAtLeast(1)

        for (n in 0 until replays) {
            var p = plan.entry
            var outcome: String? = null
            val keep = n < SAMPLE_PATHS
            val path = if (keep) mutableListOf(plan.entry) else null
            for (i in (k + 1)..last) {
                val rel = sampleBar(rng1, i)
                val hi = p * rel.h
                val lo = p * rel.l
                val cl = p * rel.c
                if (outcome == null) {
                    if (lo <= plan.stop) {
                        outcome = "stop" // conservative stop-before-target tie break
                    } else if (hi >= plan.target) {
                        outcome = "target"
                    }
                }
                p = cl
                path?.add(IndianMarketUtils.round2(p))
            }
            if (path != null) samplePaths.add(path)
            closes[n] = p
            when (outcome) {
                "target" -> {
                    targetCount++
                    greenCount++
                    rs[n] = rAtTarget
                    val ch = IndianMarketUtils.charges(plan.entry * q, plan.target * q, plan.product, plan.exchange).total
                    rNets[n] = rAtTarget - ch / (risk * q)
                }
                "stop" -> {
                    stopCount++
                    rs[n] = -1.0
                    val ch = IndianMarketUtils.charges(plan.entry * q, plan.stop * q, plan.product, plan.exchange).total
                    rNets[n] = -1.0 - ch / (risk * q)
                }
                else -> {
                    neitherCount++
                    if (p > plan.entry) greenCount++
                    val rClose = (p - plan.entry) / risk
                    rs[n] = rClose
                    val ch = IndianMarketUtils.charges(plan.entry * q, p * q, plan.product, plan.exchange).total
                    rNets[n] = rClose - ch / (risk * q)
                }
            }
        }

        val sortedCloses = closes.sortedArray()
        fun qVal(arr: DoubleArray, qFrac: Double): Double =
            arr[min(arr.size - 1, (qFrac * arr.size).toInt())]

        // Second run of 2,000 paths for fan chart quantile bands
        val rng2 = Random(seed)
        val barsLen = last - k + 1
        val bandMatrix = Array(barsLen) { DoubleArray(BAND_PATHS) }
        for (n in 0 until BAND_PATHS) {
            var p = plan.entry
            bandMatrix[0][n] = p
            for ((idx, i) in ((k + 1)..last).withIndex()) {
                val rel = sampleBar(rng2, i)
                p *= rel.c
                bandMatrix[idx + 1][n] = p
            }
        }
        val bands = (0 until barsLen).map { j ->
            val col = bandMatrix[j].sortedArray()
            BandRow(
                barIndex = k + j,
                p5 = IndianMarketUtils.round2(qVal(col, 0.05)),
                p25 = IndianMarketUtils.round2(qVal(col, 0.25)),
                p50 = IndianMarketUtils.round2(qVal(col, 0.50)),
                p75 = IndianMarketUtils.round2(qVal(col, 0.75)),
                p95 = IndianMarketUtils.round2(qVal(col, 0.95))
            )
        }

        // 24-bin histogram between p1 and p99
        val loHist = qVal(sortedCloses, 0.01)
        val hiHist = qVal(sortedCloses, 0.99)
        val binWidth = (hiHist - loHist) / HIST_BINS
        val bins = MutableList(HIST_BINS) { 0 }
        for (c in sortedCloses) {
            val b = if (binWidth > 0) floor((c - loHist) / binWidth).toInt() else 0
            if (b in 0 until HIST_BINS) bins[b]++
        }

        val pt = round(targetCount.toDouble() / replays * 10000.0) / 10000.0
        val ps = round(stopCount.toDouble() / replays * 10000.0) / 10000.0
        val pn = round(neitherCount.toDouble() / replays * 10000.0) / 10000.0
        val pg = round(greenCount.toDouble() / replays * 10000.0) / 10000.0
        val avgR = round(rs.average() * 1000.0) / 1000.0
        val avgRNet = round(rNets.average() * 1000.0) / 1000.0
        val approxCharges = IndianMarketUtils.charges(plan.entry * q, plan.entry * q, plan.product, plan.exchange).total
        val chargesR = round((approxCharges / (risk * q)) * 100.0) / 100.0

        val (verdictStr, notes) = buildVerdict(pt, ps, pn, rAtTarget, avgR, avgRNet, q, chargesR, until)

        // Historical ORB replay across full sessions
        val orBarsCount = ((plan.openingRange?.minutes ?: 15) / 5).coerceAtLeast(1)
        val stopPct = 100.0 * risk / plan.entry
        val backtest = runHistoricalOrbReplay(fullSessions, orBarsCount, plan.stopRule, stopPct, plan.targetR, last, plan.product, plan.exchange, q)

        return Result.success(
            TimeMachineResult(
                symbol = plan.symbol,
                exchange = plan.exchange,
                date = plan.date,
                until = until,
                sessionsUsed = fullSessions.size,
                skippedSessions = skipped,
                replays = replays,
                seed = seed,
                window = window,
                barsLeft = last - k,
                pTarget = pt,
                pStop = ps,
                pNeither = pn,
                pGreen = pg,
                medianClose = IndianMarketUtils.round2(qVal(sortedCloses, 0.50)),
                p5Close = IndianMarketUtils.round2(qVal(sortedCloses, 0.05)),
                p95Close = IndianMarketUtils.round2(qVal(sortedCloses, 0.95)),
                avgR = avgR,
                avgRNet = avgRNet,
                chargesR = chargesR,
                rAtTarget = rAtTarget,
                qtyForNet = q,
                verdict = verdictStr,
                verdictNotes = notes,
                bands = bands,
                samplePaths = samplePaths,
                hist = HistogramData(
                    lo = IndianMarketUtils.round2(loHist),
                    hi = IndianMarketUtils.round2(hiHist),
                    bins = bins
                ),
                historyTrades = backtest,
                warnings = warnings
            )
        )
    }

    private fun buildVerdict(
        pt: Double,
        ps: Double,
        pn: Double,
        rAtTarget: Double,
        avgR: Double,
        avgRNet: Double,
        qty: Int,
        chargesR: Double,
        until: String
    ): Pair<String, List<String>> {
        val notes = mutableListOf<String>()
        if (pt + ps <= 0.0) {
            return "No replay reached target or stop by $until: close price at $until decides." to notes
        }
        val share = pt / (pt + ps)
        val breakEven = 1.0 / (1.0 + rAtTarget)
        val d = share - breakEven
        val oneR = abs(rAtTarget - 1.0) < 0.005
        val head = when {
            abs(d) < COIN_FLIP -> if (oneR) "Close to a coin flip" else "Close to break-even"
            abs(d) < STRONG -> if (d > 0) "Leans toward the target" else "Leans toward the stop"
            else -> if (d > 0) "Strongly toward the target" else "Strongly toward the stop"
        }
        val odds = "target first ${"%.1f".format(100 * pt)}% vs stop first ${"%.1f".format(100 * ps)}%"
        val headline = if (oneR) {
            "$head: $odds."
        } else {
            "$head: at ${"%.2f".format(rAtTarget)}R target must hit in ${"%.0f".format(100 * breakEven)}% of decided paths; hit in ${"%.0f".format(100 * share)}% ($odds)."
        }

        if (pn > MANY_NEITHER) {
            notes.add("${"%.1f".format(100 * pn)}% of replays hit neither by $until; the $until close decides those.")
        }
        if (abs(d) >= COIN_FLIP && avgR != 0.0 && (avgR > 0) != (d > 0)) {
            notes.add("Average R is ${"%+.3f".format(avgR)}: undecided paths pull expectancy the other way.")
        }
        if (avgR > 0 && avgRNet <= 0) {
            notes.add("After Zerodha charges, net avg R drops to ${"%+.3f".format(avgRNet)} (~${"%.2f".format(chargesR)}R round-trip cost on $qty shares).")
        }
        return headline to notes
    }

    private fun runHistoricalOrbReplay(
        sessions: List<Pair<String, List<OhlcvBar>>>,
        orBarsCount: Int,
        stopRule: String,
        stopPct: Double,
        targetR: Double,
        lastSlot: Int,
        product: String,
        exchange: String,
        qty: Int
    ): List<BacktestTrade> {
        val trades = mutableListOf<BacktestTrade>()
        for ((date, bars) in sessions) {
            if (bars.size <= orBarsCount) continue
            val orSlice = bars.take(orBarsCount)
            val oh = orSlice.maxOf { it.high }
            val ol = orSlice.minOf { it.low }
            val limit = min(lastSlot, bars.size - 1)
            val entryIdx = (orBarsCount..limit).firstOrNull { bars[it].close > oh } ?: continue
            val e = IndianMarketUtils.roundToTick(bars[entryIdx].close, "nearest")
            val s = when (stopRule) {
                "or_mid" -> IndianMarketUtils.roundToTick((oh + ol) / 2.0, "down")
                "or_low" -> IndianMarketUtils.roundToTick(ol, "down")
                else -> IndianMarketUtils.roundToTick(e * (1.0 - stopPct / 100.0), "down")
            }
            val risk = e - s
            if (risk <= 0) continue
            val t = IndianMarketUtils.roundToTick(e + targetR * risk, "down")
            var outcome = "close"
            var exitPrice = bars[limit].close
            for (j in (entryIdx + 1)..limit) {
                val b = bars[j]
                if (b.low <= s) {
                    outcome = "stop"
                    exitPrice = s
                    break
                }
                if (b.high >= t) {
                    outcome = "target"
                    exitPrice = t
                    break
                }
            }
            val r = when (outcome) {
                "target" -> (t - e) / risk
                "stop" -> -1.0
                else -> (exitPrice - e) / risk
            }
            val ch = IndianMarketUtils.charges(e * qty, exitPrice * qty, product, exchange).total
            val rNet = r - ch / (risk * qty)
            trades.add(
                BacktestTrade(
                    date = date,
                    time = IndianMarketUtils.clockString(IndianMarketUtils.OPEN_MIN + 5 * (entryIdx + 1)),
                    entry = e,
                    stop = s,
                    target = t,
                    outcome = outcome,
                    r = IndianMarketUtils.round2(r),
                    rNet = IndianMarketUtils.round2(rNet),
                    qty = qty
                )
            )
        }
        return trades
    }
}
