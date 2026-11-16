package com.example.domain

import com.example.model.AllocationRow
import com.example.model.BrokerRecord
import com.example.model.DeskConfig
import com.example.model.ExitPlan
import com.example.model.FifoLot
import com.example.model.GttOcoLeg
import com.example.model.LivePnlSummary
import com.example.model.OrderLeg
import com.example.model.OrderProposal
import com.example.model.PortfolioPosition
import com.example.model.PortfolioState
import com.example.model.RiskCheckItem
import com.example.model.RiskResult
import com.example.model.RiskVerdict
import com.example.model.TradePlan
import com.example.model.UniverseSecurity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.floor

object BudgetRiskBrokerEngine {

    private val YES_WORDS = setOf("yes", "y", "yea", "yeah", "yep")
    private val BATCH_WORDS = setOf("all", "both", "everything", "every", "each")
    private val DOUBT_WORDS = setOf(
        "maybe", "perhaps", "possibly", "probably", "later", "wait", "hold", "think", "unsure",
        "try", "check", "confirm", "ask", "if", "unless", "when", "until", "only", "except", "but", "not"
    )
    private val NON_EQUITY_PRODUCTS = setOf("NRML", "MTF")
    private val NON_EQUITY_EXCHANGES = setOf("NFO", "BFO", "MCX", "CDS", "BCD")
    private val DERIVATIVE_SYMBOL_RE = Regex("(\\d{2}[A-Z]{3}FUT|\\d+(CE|PE))$")

    fun sizeOrder(
        plan: TradePlan,
        config: DeskConfig,
        portfolio: PortfolioState,
        foSymbols: Set<String>?,
        orderType: String = "MARKET",
        limitPrice: Double? = null,
        side: String = "BUY"
    ): Result<OrderProposal> {
        val cleanSide = if (side.equals("SELL", true)) "SELL" else "BUY"
        val entry = if (orderType == "LIMIT" && limitPrice != null && limitPrice > 0) {
            IndianMarketUtils.roundToTick(limitPrice, "nearest")
        } else {
            plan.entry
        }
        val product = plan.product.uppercase().ifEmpty { config.product.uppercase() }
        val exchange = plan.exchange.uppercase().ifEmpty { config.exchange.uppercase() }
        val risk = (entry - plan.stop).coerceAtLeast(plan.tick)
        if (cleanSide == "BUY" && entry <= plan.stop) {
            return Result.failure(IllegalArgumentException("Entry ${IndianMarketUtils.inr(entry)} must be above stop ${IndianMarketUtils.inr(plan.stop)}"))
        }

        val (cas, _) = IndianMarketUtils.casStatus(plan.symbol, plan.cas, foSymbols = foSymbols)
        val rules = IndianMarketUtils.sessionRules(cas, plan.date)
        val heldShares = portfolio.positions
            .filter { it.symbol.equals(plan.symbol, true) }
            .sumOf { it.shares }

        val qty: Int
        val boundReason: String

        if (cleanSide == "SELL" && heldShares > 0) {
            qty = heldShares
            boundReason = "held position ($heldShares sh)"
        } else {
            val equity = portfolio.cash + portfolio.positions.sumOf { it.shares * it.lastPrice }
            val cashAfterBuffer = (portfolio.cash - equity * (config.cashBufferPct / 100.0)).coerceAtLeast(0.0)
            val existingPosValue = portfolio.positions.filter { it.symbol == plan.symbol }.sumOf { it.shares * it.lastPrice }
            val maxPosRoom = (equity * (config.maxPositionPct / 100.0) - existingPosValue).coerceAtLeast(0.0)

            var maxLossCap = floor(config.maxLossPerTradeInr / risk).toInt().coerceAtLeast(0)
            while (maxLossCap > 0) {
                val ch = IndianMarketUtils.charges(maxLossCap * entry, maxLossCap * plan.stop, product, exchange).total
                if (maxLossCap * risk + ch <= config.maxLossPerTradeInr + 0.005) break
                maxLossCap--
            }

            fun sharesForBudget(inrCap: Double): Int {
                if (inrCap <= 0) return 0
                var q = floor(inrCap / entry).toInt()
                while (q > 0) {
                    val buyCh = IndianMarketUtils.charges(q * entry, 0.0, product, exchange).total
                    if (q * entry + buyCh <= inrCap + 0.005) return q
                    q--
                }
                return 0
            }

            val posShares = sharesForBudget(maxPosRoom)
            val orderShares = sharesForBudget(config.maxOrderInr)
            val cashShares = sharesForBudget(cashAfterBuffer)

            val candidates = listOf(
                "max_loss_per_trade_inr" to maxLossCap,
                "max_position_pct" to posShares,
                "max_order_inr" to orderShares,
                "available cash after buffer" to cashShares
            )
            val minPair = candidates.minBy { it.second }
            qty = minPair.second
            boundReason = minPair.first
            if (qty <= 0) {
                return Result.failure(
                    IllegalArgumentException("0 whole shares fit inside ${minPair.first} (entry ${IndianMarketUtils.inr(entry)}, cash ${IndianMarketUtils.inr(portfolio.cash)})")
                )
            }
        }

        val exitVal = plan.stop
        val chBreakdown = if (cleanSide == "SELL" && heldShares > 0) {
            IndianMarketUtils.charges(0.0, qty * entry, product, exchange)
        } else {
            IndianMarketUtils.charges(qty * entry, qty * exitVal, product, exchange)
        }
        val maxLossInr = IndianMarketUtils.round2(qty * risk + chBreakdown.total)
        val orderValue = IndianMarketUtils.round2(qty * entry)

        val exitPlan = if (cleanSide == "SELL" && heldShares > 0) {
            null
        } else if (product == "CNC") {
            val stopLimit = IndianMarketUtils.roundToTick(plan.stop * 0.99, "down")
            ExitPlan(
                kind = "gtt-oco",
                whenText = "after delivery buy settles (CNC): place GTT OCO with stop limit 1% below trigger",
                gtt = GttOcoLeg(
                    conditionSymbol = plan.symbol,
                    lowerTriggerValue = plan.stop,
                    lowerLimitPrice = stopLimit,
                    upperTriggerValue = plan.target,
                    upperLimitPrice = plan.target,
                    quantity = qty,
                    product = "CNC"
                )
            )
        } else {
            ExitPlan(
                kind = "sl-m+limit",
                whenText = "after the entry fills (MIS): place both; when one fills, cancel the other at once",
                stopLeg = OrderLeg(
                    exchange = exchange,
                    tradingsymbol = plan.symbol,
                    transactionType = if (cleanSide == "BUY") "SELL" else "BUY",
                    quantity = qty,
                    product = "MIS",
                    orderType = "SL-M",
                    triggerPrice = plan.stop
                ),
                targetLeg = OrderLeg(
                    exchange = exchange,
                    tradingsymbol = plan.symbol,
                    transactionType = if (cleanSide == "BUY") "SELL" else "BUY",
                    quantity = qty,
                    product = "MIS",
                    orderType = "LIMIT",
                    price = plan.target
                )
            )
        }

        val coreJson = buildCanonicalOrderJson(
            exchange = exchange,
            symbol = plan.symbol,
            transactionType = cleanSide,
            quantity = qty,
            product = product,
            orderType = orderType,
            price = if (orderType == "LIMIT") entry else null,
            exitPlan = exitPlan
        )
        val fp = IndianMarketUtils.sha256Hex16(coreJson)

        val kitePayload = buildPrettyKitePayload(
            exchange = exchange,
            symbol = plan.symbol,
            transactionType = cleanSide,
            quantity = qty,
            product = product,
            orderType = orderType,
            price = if (orderType == "LIMIT") entry else null,
            exitPlan = exitPlan
        )

        val why = if (cleanSide == "SELL" && heldShares > 0) {
            "SELL $qty shares of ${plan.symbol} to close open long position (~${IndianMarketUtils.inr(orderValue)})"
        } else {
            "${plan.setup} $cleanSide on ${plan.symbol}: $qty shares, bound by $boundReason " +
                "($qty × ${IndianMarketUtils.inr(risk)} + ${IndianMarketUtils.inr(chBreakdown.total)} charges = ${IndianMarketUtils.inr(maxLossInr)} at stop)"
        }

        return Result.success(
            OrderProposal(
                exchange = exchange,
                tradingsymbol = plan.symbol,
                transactionType = cleanSide,
                quantity = qty,
                product = product,
                orderType = orderType,
                price = if (orderType == "LIMIT") entry else null,
                exitPlan = exitPlan,
                why = why,
                entryPrice = entry,
                stopPrice = plan.stop,
                targetPrice = plan.target,
                riskPerShare = IndianMarketUtils.round2(risk),
                charges = chBreakdown,
                maxLossInr = maxLossInr,
                orderValueInr = orderValue,
                boundBy = boundReason,
                setup = plan.setup,
                date = plan.date,
                entryTime = plan.entryTime,
                flatBy = plan.flatBy,
                cas = cas,
                squareOff = rules.squareOff,
                sessionClose = rules.close,
                fingerprint = fp,
                kitePayloadJson = kitePayload
            )
        )
    }

    fun allocateWeights(
        items: List<Triple<String, Double, Double>>, // (symbol, weightPct, price)
        config: DeskConfig,
        portfolio: PortfolioState
    ): List<AllocationRow> {
        val totalWeight = items.sumOf { it.second }.coerceAtLeast(1e-6)
        val equity = portfolio.cash + portfolio.positions.sumOf { it.shares * it.lastPrice }
        val deployable = (portfolio.cash - equity * (config.cashBufferPct / 100.0)).coerceAtLeast(0.0)
        return items.map { (sym, weight, price) ->
            val normW = weight / totalWeight
            val cap = deployable * normW
            val shares = if (price > 0) floor(cap / price).toInt().coerceAtLeast(0) else 0
            val amt = IndianMarketUtils.round2(shares * price)
            val ch = IndianMarketUtils.charges(amt, 0.0, "CNC", config.exchange).total
            AllocationRow(
                symbol = sym,
                exchange = config.exchange,
                weightPct = IndianMarketUtils.round2(normW * 100.0),
                price = price,
                shares = shares,
                amountInr = amt,
                chargesInr = ch
            )
        }
    }

    private fun buildCanonicalOrderJson(
        exchange: String,
        symbol: String,
        transactionType: String,
        quantity: Int,
        product: String,
        orderType: String,
        price: Double?,
        exitPlan: ExitPlan?
    ): String {
        val root = linkedMapOf<String, Any>(
            "exchange" to exchange,
            "order_type" to orderType,
            "product" to product,
            "quantity" to quantity,
            "tag" to "indiabudget",
            "tradingsymbol" to symbol,
            "transaction_type" to transactionType,
            "validity" to "DAY",
            "variety" to "regular"
        )
        if (price != null) root["price"] = price
        if (exitPlan != null) {
            val epMap = linkedMapOf<String, Any>()
            exitPlan.stopLeg?.let { leg ->
                epMap["stop_leg"] = linkedMapOf<String, Any>(
                    "exchange" to leg.exchange,
                    "order_type" to leg.orderType,
                    "product" to leg.product,
                    "quantity" to leg.quantity,
                    "tag" to leg.tag,
                    "tradingsymbol" to leg.tradingsymbol,
                    "transaction_type" to leg.transactionType,
                    "trigger_price" to (leg.triggerPrice ?: 0.0),
                    "validity" to leg.validity,
                    "variety" to leg.variety
                )
            }
            exitPlan.targetLeg?.let { leg ->
                epMap["target_leg"] = linkedMapOf<String, Any>(
                    "exchange" to leg.exchange,
                    "order_type" to leg.orderType,
                    "price" to (leg.price ?: 0.0),
                    "product" to leg.product,
                    "quantity" to leg.quantity,
                    "tag" to leg.tag,
                    "tradingsymbol" to leg.tradingsymbol,
                    "transaction_type" to leg.transactionType,
                    "validity" to leg.validity,
                    "variety" to leg.variety
                )
            }
            exitPlan.gtt?.let { g ->
                epMap["gtt"] = linkedMapOf<String, Any>(
                    "lower_limit_price" to g.lowerLimitPrice,
                    "lower_trigger_value" to g.lowerTriggerValue,
                    "product" to g.product,
                    "quantity" to g.quantity,
                    "upper_limit_price" to g.upperLimitPrice,
                    "upper_trigger_value" to g.upperTriggerValue
                )
            }
            root["exit_plan"] = epMap
        }
        return JSONObject(root.toSortedMap()).toString()
    }

    private fun buildPrettyKitePayload(
        exchange: String,
        symbol: String,
        transactionType: String,
        quantity: Int,
        product: String,
        orderType: String,
        price: Double?,
        exitPlan: ExitPlan?
    ): String {
        val entryJson = JSONObject().apply {
            put("variety", "regular")
            put("exchange", exchange)
            put("tradingsymbol", symbol)
            put("transaction_type", transactionType)
            put("quantity", quantity)
            put("product", product)
            put("order_type", orderType)
            if (price != null) put("price", price)
            put("validity", "DAY")
            put("tag", "indiabudget")
        }
        val payload = JSONObject().apply {
            put("place_order", entryJson)
        }
        if (exitPlan?.stopLeg != null && exitPlan.targetLeg != null) {
            payload.put("after_fill_exits", JSONArray().apply {
                put(JSONObject().apply {
                    put("order_type", exitPlan.stopLeg.orderType)
                    put("transaction_type", "SELL")
                    put("quantity", exitPlan.stopLeg.quantity)
                    put("trigger_price", exitPlan.stopLeg.triggerPrice)
                })
                put(JSONObject().apply {
                    put("order_type", exitPlan.targetLeg.orderType)
                    put("transaction_type", "SELL")
                    put("quantity", exitPlan.targetLeg.quantity)
                    put("price", exitPlan.targetLeg.price)
                })
            })
        } else if (exitPlan?.gtt != null) {
            val g = exitPlan.gtt
            payload.put("place_gtt_oco", JSONObject().apply {
                put("type", "two-leg")
                put("exchange", exchange)
                put("tradingsymbol", symbol)
                put("stop_trigger", g.lowerTriggerValue)
                put("stop_limit", g.lowerLimitPrice)
                put("target_trigger", g.upperTriggerValue)
                put("target_limit", g.upperLimitPrice)
                put("quantity", g.quantity)
                put("product", "CNC")
            })
        }
        return payload.toString(2)
    }

    fun checkOrderRisk(
        order: OrderProposal,
        config: DeskConfig,
        portfolio: PortfolioState,
        secList: Map<String, UniverseSecurity>,
        prevClose: Double?,
        nowIst: LocalDateTime,
        holidays: Map<String, String>
    ): RiskVerdict {
        val items = mutableListOf<RiskCheckItem>()
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        fun addCheck(rule: String, ok: Boolean, detail: String, warn: Boolean = false) {
            items.add(RiskCheckItem(rule = rule, passed = ok, detail = detail, isWarning = warn))
            if (!ok) reasons.add("$rule: $detail")
            else if (warn) warnings.add("$rule: $detail")
        }

        // 1. Format & Equity-only
        val isEquityExch = order.exchange in setOf("NSE", "BSE") && order.exchange !in NON_EQUITY_EXCHANGES
        val isEquityProduct = order.product in setOf("MIS", "CNC") && order.product !in NON_EQUITY_PRODUCTS
        val isDerivativeName = DERIVATIVE_SYMBOL_RE.containsMatchIn(order.tradingsymbol)
        addCheck(
            "Equity Only",
            isEquityExch && isEquityProduct && !isDerivativeName,
            if (isEquityExch && isEquityProduct && !isDerivativeName) {
                "${order.exchange}:${order.tradingsymbol} (${order.product}) equity cash"
            } else {
                "Derivatives/NRML/MTF blocked"
            }
        )

        // 2. Long-only / Closing Sell
        val isBuy = order.transactionType.equals("BUY", ignoreCase = true)
        val heldShares = portfolio.positions
            .filter { it.symbol.equals(order.tradingsymbol, ignoreCase = true) }
            .sumOf { it.shares }
        val isSellClosing = !isBuy && heldShares >= order.quantity && order.quantity > 0
        addCheck(
            "Long Only",
            isBuy || isSellClosing || config.allowShort,
            when {
                isBuy -> "BUY order matches allow_short=false"
                isSellClosing -> "SELL closing held position ($heldShares sh)"
                config.allowShort -> "SELL allowed by allowShort=true"
                else -> "Short selling disabled (no open holding in ${order.tradingsymbol} to sell)"
            }
        )

        // 3. Blocked list
        val blockedSet = config.blocked.map { it.uppercase() }.toSet()
        addCheck(
            "Blocked List",
            order.tradingsymbol !in blockedSet,
            if (order.tradingsymbol !in blockedSet) "Not on blocked symbols list" else "${order.tradingsymbol} is blocked in config"
        )

        // 4. Trade-to-Trade (BE/BZ) vs MIS
        val sec = secList[order.tradingsymbol]
        val isT2t = sec?.series in setOf("BE", "BZ")
        addCheck(
            "T2T / Series",
            !(isT2t && order.product == "MIS"),
            if (isT2t && order.product == "MIS") "${order.tradingsymbol} is ${sec?.series} (trade-to-trade): MIS not allowed"
            else "Series ${sec?.series ?: "EQ"} valid for ${order.product}"
        )

        // 5. Circuit safety (within 1% of upper circuit)
        val band = sec?.bandPct
        val circ = if (prevClose != null && band != null) IndianMarketUtils.circuits(prevClose, band) else null
        val tooCloseUc = isBuy && circ != null && order.entryPrice >= circ.second * 0.99
        addCheck(
            "Circuit Band",
            !tooCloseUc,
            if (tooCloseUc) "Entry ${IndianMarketUtils.inr(order.entryPrice)} is within 1% of upper circuit ${IndianMarketUtils.inr(circ!!.second)}"
            else if (circ != null) "Safe inside [${IndianMarketUtils.inr(circ.first)}, ${IndianMarketUtils.inr(circ.second)}]"
            else "No fixed % circuit band (${sec?.bandText ?: "No Band"})"
        )

        // 6. Min & Max Order INR
        val orderAmt = order.orderValueInr
        addCheck(
            "Order Size Bounds",
            orderAmt >= config.minOrderInr && orderAmt <= config.maxOrderInr,
            "${IndianMarketUtils.inr(orderAmt)} within [${IndianMarketUtils.inr(config.minOrderInr)}, ${IndianMarketUtils.inr(config.maxOrderInr)}]"
        )

        // 7. Cash buffer & no leverage
        val equity = portfolio.cash + portfolio.positions.sumOf { it.shares * it.lastPrice }
        val totalOutlay = orderAmt + order.charges.total
        val minKeep = equity * (config.cashBufferPct / 100.0)
        val cashRemaining = portfolio.cash - totalOutlay
        addCheck(
            "Cash & Buffer (${config.cashBufferPct.toInt()}%)",
            isSellClosing || (cashRemaining >= minKeep - 0.01 && totalOutlay <= portfolio.cash + 0.01),
            if (isSellClosing) "Closing sell frees ${IndianMarketUtils.inr(orderAmt)}"
            else "Cash ${IndianMarketUtils.inr(portfolio.cash)} - outlay ${IndianMarketUtils.inr(totalOutlay)} = ${IndianMarketUtils.inr(cashRemaining)} (min keep ${IndianMarketUtils.inr(minKeep)})"
        )

        // 8. Position concentration %
        val existingVal = portfolio.positions.filter { it.symbol == order.tradingsymbol }.sumOf { it.shares * it.lastPrice }
        val newPosPct = if (equity > 0) (existingVal + orderAmt) / equity * 100.0 else 100.0
        addCheck(
            "Max Position (${config.maxPositionPct.toInt()}%)",
            isSellClosing || newPosPct <= config.maxPositionPct + 0.05,
            if (isSellClosing) "Reduces open exposure in ${order.tradingsymbol}"
            else "${"%.1f".format(newPosPct)}% of equity (${IndianMarketUtils.inr(equity)})"
        )

        // 9. Duplicate position check
        val hasOpen = portfolio.positions.any { it.symbol == order.tradingsymbol && it.shares > 0 }
        addCheck(
            "No Duplicate Open",
            isSellClosing || !hasOpen,
            when {
                isSellClosing -> "Closing held position (${order.tradingsymbol})"
                !hasOpen -> "No open position in ${order.tradingsymbol}"
                else -> "Already holding open shares in ${order.tradingsymbol}"
            }
        )

        // 10. Stop & exit legs + tick grid
        val hasStop = order.stopPrice < order.entryPrice && IndianMarketUtils.onTick(order.stopPrice)
        val hasTarget = order.targetPrice > order.entryPrice && IndianMarketUtils.onTick(order.targetPrice)
        addCheck(
            "Stop & Exit Legs",
            isSellClosing || !config.requireStop || (hasStop && hasTarget && order.exitPlan != null),
            if (isSellClosing) "Direct position exit order"
            else "Stop ${IndianMarketUtils.inr(order.stopPrice)} · Target ${IndianMarketUtils.inr(order.targetPrice)} (${order.exitPlan?.kind ?: "none"})"
        )

        // 11. Max loss per trade
        addCheck(
            "Max Loss / Trade",
            isSellClosing || order.maxLossInr <= config.maxLossPerTradeInr + 0.05,
            if (isSellClosing) "Exit order closes open risk"
            else "${IndianMarketUtils.inr(order.maxLossInr)} <= limit ${IndianMarketUtils.inr(config.maxLossPerTradeInr)}"
        )

        // 12. Min reward:risk
        val riskDenom = (order.entryPrice - order.stopPrice).coerceAtLeast(0.01)
        val rr = (order.targetPrice - order.entryPrice) / riskDenom
        addCheck(
            "Reward : Risk",
            isSellClosing || rr >= config.minRewardRisk - 0.01,
            if (isSellClosing) "Exit liquidation order"
            else "${"%.2f".format(rr)}R >= min ${"%.2f".format(config.minRewardRisk)}R"
        )

        // 13. Daily limits (trades, orders, daily loss)
        val dailyLoss = (-portfolio.realizedPnlToday).coerceAtLeast(0.0)
        val dailyOk = isSellClosing || (dailyLoss + order.maxLossInr <= config.maxDailyLossInr + 0.05)
        addCheck(
            "Daily Loss Cap",
            dailyOk,
            "Realized loss ${IndianMarketUtils.inr(dailyLoss)} + trade risk ${IndianMarketUtils.inr(order.maxLossInr)} <= ${IndianMarketUtils.inr(config.maxDailyLossInr)}"
        )
        addCheck(
            "Daily Trade / Order Caps",
            isSellClosing || (portfolio.tradesToday < config.maxTradesPerDay && portfolio.ordersToday < config.maxOrdersPerDay),
            "Trades ${portfolio.tradesToday}/${config.maxTradesPerDay} · Orders ${portfolio.ordersToday}/${config.maxOrdersPerDay}"
        )

        // 14. IST Entry Window & Holiday & MIS Square-Off
        val date = nowIst.toLocalDate()
        val timeMin = nowIst.hour * 60 + nowIst.minute
        val (isTradingDay, holidayWhy) = IndianMarketUtils.tradingDay(date, holidays)
        val beforeMin = IndianMarketUtils.parseClockMinutes(config.noEntriesBefore) ?: (9 * 60 + 30)
        val afterMin = IndianMarketUtils.parseClockMinutes(config.noEntriesAfter) ?: (14 * 60 + 45)
        val sqMin = IndianMarketUtils.parseClockMinutes(order.squareOff) ?: (15 * 60 + 12)
        val windowOk = if (isSellClosing) {
            isTradingDay
        } else {
            isTradingDay && timeMin in beforeMin..afterMin && (order.product != "MIS" || timeMin < sqMin)
        }
        val windowDetail = when {
            !isTradingDay -> "Market closed ($holidayWhy)"
            isSellClosing -> "Exit liquidation permitted during trading session"
            timeMin < beforeMin -> "Before ${config.noEntriesBefore} IST (now ${"%02d:%02d".format(nowIst.hour, nowIst.minute)})"
            timeMin > afterMin -> "After ${config.noEntriesAfter} IST cutoff (now ${"%02d:%02d".format(nowIst.hour, nowIst.minute)})"
            order.product == "MIS" && timeMin >= sqMin -> "At/after ${config.selectedBroker.displayName} MIS square-off ${order.squareOff} IST"
            else -> "Open IST window (${config.noEntriesBefore}–${config.noEntriesAfter}; MIS square-off ${order.squareOff})"
        }
        addCheck("IST Session Window", windowOk, windowDetail)

        val passedAll = reasons.isEmpty()
        val res = RiskResult(
            index = 0,
            fingerprint = order.fingerprint,
            symbol = order.tradingsymbol,
            exchange = order.exchange,
            passed = passedAll,
            reasons = reasons,
            warnings = warnings,
            items = items
        )
        return RiskVerdict(
            date = nowIst.toLocalDate().toString(),
            checkedAtIst = nowIst.toLocalTime().toString().take(5),
            allPassed = passedAll,
            results = listOf(res)
        )
    }

    /**
     * Exact explicit "yes" confirmation rule matching india-broker/ticket.py:
     * - Non-empty, no '?'
     * - First word in YES_WORDS ("yes", "y", "yea", "yeah", "yep")
     * - No batch words ("all", "both", "everything", "every", "each")
     * - No doubt words ("maybe", "if", "wait", ...)
     * - No conflicting symbol token
     * - If reply contains numbers, at least one matches qty/entry/stop/target/maxLoss
     */
    fun parseYes(
        reply: String,
        order: OrderProposal,
        batchSymbols: Set<String> = emptySet()
    ): Pair<Boolean, String> {
        val raw = reply.trim()
        if (raw.isEmpty()) return false to "Reply is empty"
        if ("?" in raw) return false to "Reply is a question, not a confirmation"
        val words = Regex("[a-z']+").findAll(raw.lowercase()).map { it.value }.toList()
        if (words.isEmpty() || words.first() !in YES_WORDS) {
            return false to "Reply must start with \"yes\" (not ok, sure, haan, or go)"
        }
        if (words.any { it in BATCH_WORDS }) {
            return false to "A confirmation covers one order at a time, not a batch (\"all\" / \"both\")"
        }
        if (words.drop(1).any { it in DOUBT_WORDS }) {
            return false to "Reply contains a condition or doubt word; confirm cleanly"
        }
        val sym = order.tradingsymbol.uppercase()
        val tokens = Regex("[A-Z][A-Z0-9&\\-]{1,19}").findAll(raw.uppercase()).map { it.value }.toSet()
        val others = batchSymbols.map { it.uppercase() }.toSet() - sym
        val namedOther = tokens intersect others
        if (namedOther.isNotEmpty()) {
            return false to "Reply names ${namedOther.joinToString(", ")}, but this order is for $sym"
        }
        val nums = Regex("\\d[\\d,]*(?:\\.\\d+)?").findAll(raw)
            .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .toList()
        if (nums.isNotEmpty()) {
            val expected = listOfNotNull(
                order.quantity.toDouble(),
                order.entryPrice,
                order.stopPrice,
                order.targetPrice,
                order.maxLossInr
            )
            val matches = nums.any { n -> expected.any { w -> abs(n - w) < 0.05 } }
            if (!matches) {
                return false to "Number in reply does not match qty (${order.quantity}), entry (${order.entryPrice}), stop (${order.stopPrice}), target (${order.targetPrice}), or max loss (${order.maxLossInr})"
            }
        }
        return true to "Explicit yes confirmed for ${order.transactionType.uppercase()} ${order.quantity} ${order.exchange}:$sym (${order.product})"
    }

    fun applyPaperBuyFill(
        order: OrderProposal,
        portfolio: PortfolioState,
        verdict: RiskVerdict,
        reply: String,
        nowIst: LocalDateTime
    ): Result<Triple<PortfolioState, BrokerRecord, String>> {
        val rv = verdict.results.firstOrNull { it.fingerprint == order.fingerprint }
            ?: return Result.failure(IllegalArgumentException("Order fingerprint does not match the latest Risk verdict. Re-run Risk Check."))
        if (!rv.passed) {
            return Result.failure(IllegalArgumentException("Risk Check BLOCKED this order: ${rv.reasons.joinToString("; ")}"))
        }
        val (yesOk, yesMsg) = parseYes(reply, order)
        if (!yesOk) {
            return Result.failure(IllegalArgumentException(yesMsg))
        }

        val today = nowIst.toLocalDate().toString()
        val buyCharges = IndianMarketUtils.charges(order.orderValueInr, 0.0, order.product, order.exchange).total
        val totalDebit = IndianMarketUtils.round2(order.orderValueInr + buyCharges)
        if (totalDebit > portfolio.cash + 0.01) {
            return Result.failure(IllegalArgumentException("Insufficient cash (${IndianMarketUtils.inr(portfolio.cash)}) for ${IndianMarketUtils.inr(totalDebit)}"))
        }

        val newLot = FifoLot(
            date = today,
            shares = order.quantity,
            price = order.entryPrice,
            product = order.product
        )
        val existingIdx = portfolio.positions.indexOfFirst { it.symbol == order.tradingsymbol }
        val updatedPositions = portfolio.positions.toMutableList()
        if (existingIdx >= 0) {
            val cur = updatedPositions[existingIdx]
            val newShares = cur.shares + order.quantity
            val newAvg = IndianMarketUtils.round2((cur.shares * cur.avgPrice + order.quantity * order.entryPrice) / newShares)
            updatedPositions[existingIdx] = cur.copy(
                shares = newShares,
                avgPrice = newAvg,
                lastPrice = order.entryPrice,
                stopPrice = order.stopPrice,
                targetPrice = order.targetPrice,
                lots = cur.lots + newLot
            )
        } else {
            updatedPositions.add(
                PortfolioPosition(
                    symbol = order.tradingsymbol,
                    exchange = order.exchange,
                    shares = order.quantity,
                    avgPrice = order.entryPrice,
                    lastPrice = order.entryPrice,
                    product = order.product,
                    entryDate = today,
                    stopPrice = order.stopPrice,
                    targetPrice = order.targetPrice,
                    lots = listOf(newLot)
                )
            )
        }

        val sameDay = portfolio.lastDay == today
        val newPortfolio = portfolio.copy(
            cash = IndianMarketUtils.round2(portfolio.cash - totalDebit),
            realizedPnlToday = if (sameDay) portfolio.realizedPnlToday else 0.0,
            tradesToday = (if (sameDay) portfolio.tradesToday else 0) + 1,
            ordersToday = (if (sameDay) portfolio.ordersToday else 0) + 1,
            lastDay = today,
            positions = updatedPositions
        )

        val record = BrokerRecord(
            timestampIst = nowIst.toString().replace("T", " "),
            date = today,
            fingerprint = order.fingerprint,
            symbol = order.tradingsymbol,
            exchange = order.exchange,
            side = "BUY",
            quantity = order.quantity,
            product = order.product,
            price = order.entryPrice,
            stopPrice = order.stopPrice,
            targetPrice = order.targetPrice,
            chargesInr = buyCharges,
            paper = true,
            status = "PAPER_FILLED",
            confirmationReply = reply.trim(),
            plainSummary = "BUY ${order.quantity} ${order.exchange}:${order.tradingsymbol} @ ${IndianMarketUtils.inr(order.entryPrice)} (${order.product})"
        )
        return Result.success(Triple(newPortfolio, record, yesMsg))
    }

    fun applyPaperFlatten(
        position: PortfolioPosition,
        sellPrice: Double,
        portfolio: PortfolioState,
        nowIst: LocalDateTime
    ): Pair<PortfolioState, BrokerRecord> {
        val today = nowIst.toLocalDate().toString()
        val grossSell = position.shares * sellPrice
        val sellCharges = IndianMarketUtils.charges(0.0, grossSell, position.product, position.exchange).total
        val netProceeds = IndianMarketUtils.round2(grossSell - sellCharges)

        // FIFO realized PnL against lots
        var costBasis = 0.0
        var rem = position.shares
        for (lot in position.lots.ifEmpty { listOf(FifoLot(position.entryDate, position.shares, position.avgPrice, position.product)) }) {
            val take = minOf(rem, lot.shares)
            costBasis += take * lot.price
            rem -= take
            if (rem <= 0) break
        }
        val realized = IndianMarketUtils.round2(netProceeds - costBasis)
        val sameDay = portfolio.lastDay == today
        val remainingPositions = portfolio.positions.filterNot { it.symbol == position.symbol && it.exchange == position.exchange }

        val newPortfolio = portfolio.copy(
            cash = IndianMarketUtils.round2(portfolio.cash + netProceeds),
            realizedPnlToday = IndianMarketUtils.round2((if (sameDay) portfolio.realizedPnlToday else 0.0) + realized),
            tradesToday = if (sameDay) portfolio.tradesToday else 0,
            ordersToday = (if (sameDay) portfolio.ordersToday else 0) + 1,
            lastDay = today,
            positions = remainingPositions
        )
        val record = BrokerRecord(
            timestampIst = nowIst.toString().replace("T", " "),
            date = today,
            fingerprint = IndianMarketUtils.sha256Hex16("FLATTEN:${position.symbol}:${position.shares}:$sellPrice:$today"),
            symbol = position.symbol,
            exchange = position.exchange,
            side = "SELL",
            quantity = position.shares,
            product = position.product,
            price = sellPrice,
            chargesInr = sellCharges,
            paper = true,
            status = "PAPER_FLATTENED",
            confirmationReply = "yes flatten ${position.symbol}",
            plainSummary = "SELL ${position.shares} ${position.exchange}:${position.symbol} @ ${IndianMarketUtils.inr(sellPrice)} (P&L ${IndianMarketUtils.inr(realized)})"
        )
        return newPortfolio to record
    }

    fun applyPaperSellFill(
        order: OrderProposal,
        portfolio: PortfolioState,
        verdict: RiskVerdict,
        reply: String,
        nowIst: LocalDateTime
    ): Result<Triple<PortfolioState, BrokerRecord, String>> {
        val rv = verdict.results.firstOrNull { it.fingerprint == order.fingerprint }
            ?: return Result.failure(IllegalArgumentException("Order fingerprint does not match the latest Risk verdict. Re-run Risk Check."))
        if (!rv.passed) {
            return Result.failure(IllegalArgumentException("Risk Check BLOCKED this order: ${rv.reasons.joinToString("; ")}"))
        }
        val (yesOk, yesMsg) = parseYes(reply, order)
        if (!yesOk) {
            return Result.failure(IllegalArgumentException(yesMsg))
        }
        val pos = portfolio.positions.firstOrNull { it.symbol.equals(order.tradingsymbol, true) }
        return if (pos != null && pos.shares >= order.quantity) {
            val (newPort, rec) = applyPaperFlatten(
                position = pos,
                sellPrice = order.entryPrice,
                portfolio = portfolio,
                nowIst = nowIst
            )
            Result.success(
                Triple(
                    newPort,
                    rec.copy(
                        fingerprint = order.fingerprint,
                        confirmationReply = reply.trim(),
                        status = "PAPER_SOLD"
                    ),
                    "Executed SELL ${order.quantity} ${order.exchange}:${order.tradingsymbol} in Paper Mode"
                )
            )
        } else {
            Result.failure(IllegalArgumentException("No held long shares in ${order.tradingsymbol} to sell."))
        }
    }

    fun computeLivePnlSummary(
        portfolio: PortfolioState,
        isRealMode: Boolean,
        modeLabel: String,
        nowIst: LocalDateTime
    ): LivePnlSummary {
        val invested = IndianMarketUtils.round2(portfolio.positions.sumOf { it.shares * it.lastPrice })
        val openPnl = IndianMarketUtils.round2(portfolio.positions.sumOf { (it.lastPrice - it.avgPrice) * it.shares })
        val realized = IndianMarketUtils.round2(portfolio.realizedPnlToday)
        val dayPnl = IndianMarketUtils.round2(openPnl + realized)
        val equity = IndianMarketUtils.round2(portfolio.cash + invested)
        return LivePnlSummary(
            isRealMode = isRealMode,
            modeLabel = modeLabel,
            equity = equity,
            availableCash = IndianMarketUtils.round2(portfolio.cash),
            investedValue = invested,
            openUnrealizedPnl = openPnl,
            realizedPnlToday = realized,
            dayPnl = dayPnl,
            openPositionsCount = portfolio.positions.size,
            lastUpdatedIst = IndianMarketUtils.formatIstTime(nowIst)
        )
    }
}
