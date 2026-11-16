package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.IndianMarketUtils
import com.example.model.BrokerCredentials
import com.example.model.DeskConfig
import com.example.model.EntryCheckResult
import com.example.model.EntryStatus
import com.example.model.IndianBrokerId
import com.example.model.LivePnlSummary
import com.example.model.OrderProposal
import com.example.model.RiskVerdict
import com.example.model.StopRuleType
import com.example.model.TargetRuleType
import com.example.model.TimeMachineResult
import com.example.model.TradePlan
import com.example.ui.components.CloseHistogramChart
import com.example.ui.components.IntradayBarsChart
import com.example.ui.components.MonteCarloFanChart
import com.example.ui.components.TradingModeBanner
import com.example.ui.theme.LocalDeskColors

@Composable
fun TradeFlowScreen(
    symbol: String,
    tradeSide: String,
    activeStep: Int,
    config: DeskConfig,
    livePnl: LivePnlSummary,
    brokerCredentials: Map<IndianBrokerId, BrokerCredentials>,
    brokerPreviewPayload: String?,
    entryResult: EntryCheckResult?,
    stopRule: StopRuleType,
    stopParam: Double,
    targetRule: TargetRuleType,
    targetParam: Double,
    tradePlan: TradePlan?,
    timeMachineResult: TimeMachineResult?,
    orderType: String,
    orderProposal: OrderProposal?,
    riskVerdict: RiskVerdict?,
    isLoading: Boolean,
    onSelectStep: (Int) -> Unit,
    onEvaluateEntry: (String, String, Double?, Int, Double, String) -> Unit,
    onUpdateExit: (StopRuleType, Double, TargetRuleType, Double) -> Unit,
    onRunTimeMachine: () -> Unit,
    onChangeOrderType: (String, Double?) -> Unit,
    onChangeSide: (String) -> Unit,
    onRecheckRisk: () -> Unit,
    onConfirmPaperOrder: (String) -> Unit,
    onAddPlanAlerts: () -> Unit,
    onToggleTradingMode: (Boolean) -> Unit,
    onOpenBrokerSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var symbolInput by remember(symbol) { mutableStateOf(symbol) }
    val stepTitles = listOf("1. Entry", "2. Exit", "3. Replay", "4. Budget", "5. Risk", "6. Ticket")

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            // Symbol & Stepper Bar
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = symbolInput,
                            onValueChange = { symbolInput = it.uppercase() },
                            label = { Text("NSE/BSE Symbol") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trade_symbol_input")
                        )
                        Button(
                            onClick = { onEvaluateEntry(symbolInput, "orb", null, 15, 1.0, "5m") },
                            modifier = Modifier.testTag("load_pipeline_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Evaluate")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        stepTitles.forEachIndexed { idx, label ->
                            FilterChip(
                                selected = activeStep == idx,
                                onClick = { onSelectStep(idx) },
                                modifier = Modifier.testTag("pipeline_step_$idx"),
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = desk.borderSubtle)

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    TradingModeBanner(
                        config = config,
                        livePnl = livePnl,
                        credentials = brokerCredentials,
                        onToggleTradingMode = onToggleTradingMode,
                        onOpenBrokerSettings = onOpenBrokerSettings
                    )
                }
                item {
                    when (activeStep) {
                        0 -> EntryStepCard(
                            entry = entryResult,
                            onEvaluate = onEvaluateEntry,
                            onNext = { onSelectStep(1) }
                        )
                        1 -> ExitStepCard(
                            entry = entryResult,
                            plan = tradePlan,
                            stopRule = stopRule,
                            stopParam = stopParam,
                            targetRule = targetRule,
                            targetParam = targetParam,
                            onUpdateExit = onUpdateExit,
                            onAddPlanAlerts = onAddPlanAlerts,
                            onNext = {
                                onRunTimeMachine()
                                onSelectStep(2)
                            }
                        )
                        2 -> TimeMachineStepCard(
                            plan = tradePlan,
                            tm = timeMachineResult,
                            onRunTimeMachine = onRunTimeMachine,
                            onNext = { onSelectStep(3) }
                        )
                        3 -> BudgetStepCard(
                            config = config,
                            tradeSide = tradeSide,
                            orderType = orderType,
                            order = orderProposal,
                            onChangeSide = onChangeSide,
                            onChangeOrderType = onChangeOrderType,
                            onNext = {
                                onRecheckRisk()
                                onSelectStep(4)
                            }
                        )
                        4 -> RiskStepCard(
                            verdict = riskVerdict,
                            onRecheck = onRecheckRisk,
                            onNext = { onSelectStep(5) }
                        )
                        else -> BrokerStepCard(
                            config = config,
                            tradeSide = tradeSide,
                            order = orderProposal,
                            brokerPreviewPayload = brokerPreviewPayload,
                            verdict = riskVerdict,
                            onChangeSide = onChangeSide,
                            onOpenBrokerSettings = onOpenBrokerSettings,
                            onConfirm = onConfirmPaperOrder
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryStepCard(
    entry: EntryCheckResult?,
    onEvaluate: (String, String, Double?, Int, Double, String) -> Unit,
    onNext: () -> Unit
) {
    val desk = LocalDeskColors.current
    var setup by remember(entry?.setup) { mutableStateOf(entry?.setup ?: "orb") }
    var levelText by remember(entry?.level) { mutableStateOf(entry?.level?.toString() ?: "") }
    var interval by remember(entry?.interval) { mutableStateOf(entry?.interval ?: "5m") }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Step 1 · Entry Timer", style = MaterialTheme.typography.titleLarge)
                if (entry != null) {
                    val statusColor = when (entry.status) {
                        EntryStatus.TRIGGERED -> desk.bullEmerald
                        EntryStatus.WAITING -> desk.amberSaffron
                        else -> desk.stopCrimson
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.16f)) {
                        Text(
                            text = entry.status.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = setup == "orb", onClick = { setup = "orb" }, label = { Text("ORB (15m)") })
                FilterChip(selected = setup == "level", onClick = { setup = "level" }, label = { Text("Price Level") })
                FilterChip(selected = interval == "5m", onClick = { interval = "5m" }, label = { Text("5m Bars") })
                FilterChip(selected = interval == "1m", onClick = { interval = "1m" }, label = { Text("1m Bars") })
            }

            if (setup == "level") {
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it },
                    label = { Text("Trigger Level (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (entry != null) {
                if (entry.bars.isNotEmpty()) {
                    IntradayBarsChart(
                        bars = entry.bars,
                        orHigh = entry.orHigh,
                        orLow = entry.orLow,
                        entryPrice = entry.entryPrice,
                        entryBarSlot = entry.entryBar
                    )
                }
                Text(entry.message, style = MaterialTheme.typography.bodyLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricPill("Entry", entry.entryPrice?.let { IndianMarketUtils.inr(it) } ?: "Waiting")
                    MetricPill("OR High", entry.orHigh?.let { IndianMarketUtils.inr(it) } ?: "-")
                    MetricPill("OR Low", entry.orLow?.let { IndianMarketUtils.inr(it) } ?: "-")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onEvaluate(entry?.symbol ?: "RELIANCE", setup, levelText.toDoubleOrNull(), 15, 1.0, interval)
                    },
                    modifier = Modifier.weight(1f).testTag("recheck_entry_button")
                ) {
                    Text("Check Bars")
                }
                Button(
                    onClick = onNext,
                    enabled = entry != null,
                    modifier = Modifier.weight(1f).testTag("entry_next_button")
                ) {
                    Text("Next: Exit Plan")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExitStepCard(
    entry: EntryCheckResult?,
    plan: TradePlan?,
    stopRule: StopRuleType,
    stopParam: Double,
    targetRule: TargetRuleType,
    targetParam: Double,
    onUpdateExit: (StopRuleType, Double, TargetRuleType, Double) -> Unit,
    onAddPlanAlerts: () -> Unit,
    onNext: () -> Unit
) {
    val desk = LocalDeskColors.current
    var stopInput by remember(stopParam) { mutableStateOf(stopParam.toString()) }
    var targetInput by remember(targetParam) { mutableStateOf(targetParam.toString()) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Step 2 · Exit Planner (NSE Tick Grid)", style = MaterialTheme.typography.titleLarge)

            Text("Stop Rule", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StopRuleType.entries.forEach { rule ->
                    FilterChip(
                        selected = stopRule == rule,
                        onClick = { onUpdateExit(rule, stopInput.toDoubleOrNull() ?: 1.0, targetRule, targetParam) },
                        modifier = Modifier.testTag("stop_rule_${rule.code}"),
                        label = { Text(rule.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (stopRule in setOf(StopRuleType.PCT, StopRuleType.ATR, StopRuleType.PRICE)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = stopInput,
                        onValueChange = { stopInput = it },
                        label = { Text(stopRule.label) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        onUpdateExit(stopRule, stopInput.toDoubleOrNull() ?: 1.0, targetRule, targetParam)
                    }) { Text("Apply") }
                }
            }

            Text("Target Rule", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TargetRuleType.entries.forEach { rule ->
                    FilterChip(
                        selected = targetRule == rule,
                        onClick = { onUpdateExit(stopRule, stopParam, rule, targetInput.toDoubleOrNull() ?: 1250.0) },
                        modifier = Modifier.testTag("target_rule_${rule.code}"),
                        label = { Text(rule.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (targetRule == TargetRuleType.PRICE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = { Text("Target Price (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        onUpdateExit(stopRule, stopParam, targetRule, targetInput.toDoubleOrNull() ?: 1250.0)
                    }) { Text("Apply") }
                }
            }

            if (plan != null) {
                HorizontalDivider(color = desk.borderSubtle)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricPill("Entry (${plan.entryTime})", IndianMarketUtils.inr(plan.entry))
                    MetricPill("Stop", IndianMarketUtils.inr(plan.stop), desk.stopCrimson)
                    MetricPill("Target (${plan.targetR}R)", IndianMarketUtils.inr(plan.target), desk.bullEmerald)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricPill("Tick", "₹${plan.tick}")
                    MetricPill("Risk / Share", IndianMarketUtils.inr(plan.riskPerShare))
                    MetricPill("Flat By", "${plan.flatBy} IST", desk.amberSaffron)
                }
                plan.warnings.forEach { w ->
                    Text(w, style = MaterialTheme.typography.bodyMedium, color = desk.amberSaffron)
                }
            } else if (entry == null) {
                Text("Evaluate an entry bar in Step 1 first.", style = MaterialTheme.typography.bodyMedium)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAddPlanAlerts,
                    enabled = plan != null,
                    modifier = Modifier.weight(1f).testTag("create_alerts_from_plan_button")
                ) {
                    Text("Set Plan Alerts")
                }
                Button(
                    onClick = onNext,
                    enabled = plan != null,
                    modifier = Modifier.weight(1f).testTag("exit_next_button")
                ) {
                    Text("Next: Time Machine")
                }
            }
        }
    }
}

@Composable
private fun TimeMachineStepCard(
    plan: TradePlan?,
    tm: TimeMachineResult?,
    onRunTimeMachine: () -> Unit,
    onNext: () -> Unit
) {
    val desk = LocalDeskColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Step 3 · Monte Carlo Time Machine", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onRunTimeMachine, modifier = Modifier.testTag("rerun_timemachine_button")) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("10k Replay")
                }
            }

            if (tm != null && plan != null) {
                Text(tm.verdict, style = MaterialTheme.typography.titleMedium, color = desk.dataSky)
                tm.verdictNotes.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodyMedium, color = desk.amberSaffron)
                }

                MonteCarloFanChart(
                    result = tm,
                    entryPrice = plan.entry,
                    stopPrice = plan.stop,
                    targetPrice = plan.target
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricPill("Target 1st", "${"%.1f".format(tm.pTarget * 100)}%", desk.bullEmerald)
                    MetricPill("Stop 1st", "${"%.1f".format(tm.pStop * 100)}%", desk.stopCrimson)
                    MetricPill("Neither", "${"%.1f".format(tm.pNeither * 100)}%", desk.amberSaffron)
                    MetricPill("Net Avg R", "${"%+.2f".format(tm.avgRNet)}R", if (tm.avgRNet >= 0) desk.bullEmerald else desk.stopCrimson)
                }

                Text("Close Distribution at ${tm.until} IST (p5 ${IndianMarketUtils.inr(tm.p5Close)} · Median ${IndianMarketUtils.inr(tm.medianClose)} · p95 ${IndianMarketUtils.inr(tm.p95Close)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                CloseHistogramChart(result = tm, entryPrice = plan.entry)

                val histWin = tm.historyTrades.count { it.r > 0 }
                Text(
                    "Historical ORB Backtest (${tm.sessionsUsed} sessions): ${tm.historyTrades.size} triggers · $histWin green · Avg R ${tm.historyTrades.map { it.r }.average().let { if (it.isNaN()) "n/a" else "%+.2f".format(it) }}",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "Runs 10,000 bootstrap replays across 60 days of 5m bars (stop checked before target inside each bar). Note: Offline bundle includes 60d5m history for RELIANCE; use Live Public Feed for other symbols.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onNext,
                enabled = plan != null,
                modifier = Modifier.fillMaxWidth().testTag("timemachine_next_button")
            ) {
                Text("Next: Budget & Sizing")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetStepCard(
    config: DeskConfig,
    tradeSide: String,
    orderType: String,
    order: OrderProposal?,
    onChangeSide: (String) -> Unit,
    onChangeOrderType: (String, Double?) -> Unit,
    onNext: () -> Unit
) {
    val desk = LocalDeskColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Step 4 · Budget, Sizing & Statutory Charges", style = MaterialTheme.typography.titleLarge)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tradeSide == "BUY",
                    onClick = { onChangeSide("BUY") },
                    modifier = Modifier.testTag("trade_side_buy"),
                    label = { Text("BUY") }
                )
                FilterChip(
                    selected = tradeSide == "SELL",
                    onClick = { onChangeSide("SELL") },
                    modifier = Modifier.testTag("trade_side_sell"),
                    label = { Text("SELL") }
                )
                FilterChip(
                    selected = orderType == "MARKET",
                    onClick = { onChangeOrderType("MARKET", null) },
                    label = { Text("MARKET") }
                )
                FilterChip(
                    selected = orderType == "LIMIT",
                    onClick = { onChangeOrderType("LIMIT", order?.entryPrice) },
                    label = { Text("LIMIT") }
                )
            }

            if (order != null) {
                Text(order.why, style = MaterialTheme.typography.bodyLarge, color = desk.dataSky)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricPill("Side", order.transactionType, if (order.transactionType == "BUY") desk.bullEmerald else desk.amberSaffron)
                    MetricPill("Quantity", "${order.quantity} sh", desk.bullEmerald)
                    MetricPill("Notional", IndianMarketUtils.inr(order.orderValueInr))
                    MetricPill("Bound By", order.boundBy)
                }
                HorizontalDivider(color = desk.borderSubtle)
                Text("Estimated Brokerage & Statutory Charges (${order.product} · ${order.exchange})", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricPill("Brokerage", IndianMarketUtils.inr(order.charges.brokerage))
                    MetricPill("STT", IndianMarketUtils.inr(order.charges.stt))
                    MetricPill("Exch+SEBI+Stamp", IndianMarketUtils.inr(order.charges.exchange + order.charges.sebi + order.charges.stamp))
                    MetricPill("Total (+GST)", IndianMarketUtils.inr(order.charges.total), desk.amberSaffron)
                }
                Text(
                    "Exit Plan: ${order.exitPlan?.kind?.uppercase() ?: "DIRECT EXIT"} — ${order.exitPlan?.whenText ?: "Closes existing position"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("No sized order available. Check available cash (${IndianMarketUtils.inr(config.budget)}) and stop distance.", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = onNext,
                enabled = order != null,
                modifier = Modifier.fillMaxWidth().testTag("budget_next_button")
            ) {
                Text("Next: Risk Gatekeeper")
            }
        }
    }
}

@Composable
private fun RiskStepCard(
    verdict: RiskVerdict?,
    onRecheck: () -> Unit,
    onNext: () -> Unit
) {
    val desk = LocalDeskColors.current
    val first = verdict?.results?.firstOrNull()
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Step 5 · Risk Gatekeeper", style = MaterialTheme.typography.titleLarge)
                if (first != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (first.passed) desk.bullContainer else desk.stopContainer
                    ) {
                        Text(
                            text = if (first.passed) "PASS" else "BLOCK",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (first.passed) desk.bullEmerald else desk.stopCrimson,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (first != null) {
                Text(
                    "SHA-256 Order Fingerprint: ${first.fingerprint} · Checked ${verdict.date} ${verdict.checkedAtIst} IST",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                first.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = if (item.passed) "Passed" else "Blocked",
                            tint = if (item.passed) desk.bullEmerald else desk.stopCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.rule, style = MaterialTheme.typography.labelLarge)
                            Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRecheck, modifier = Modifier.weight(1f).testTag("recheck_risk_button")) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Re-Check")
                }
                Button(
                    onClick = onNext,
                    enabled = first?.passed == true,
                    modifier = Modifier.weight(1f).testTag("risk_next_button")
                ) {
                    Text("Next: Broker Ticket")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrokerStepCard(
    config: DeskConfig,
    tradeSide: String,
    order: OrderProposal?,
    brokerPreviewPayload: String?,
    verdict: RiskVerdict?,
    onChangeSide: (String) -> Unit,
    onOpenBrokerSettings: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val desk = LocalDeskColors.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmInput by remember { mutableStateOf("") }
    val riskResult = verdict?.results?.firstOrNull()
    val canConfirm = order != null && riskResult?.passed == true && riskResult.fingerprint == order.fingerprint
    val isReal = !config.paper
    val brokerName = config.selectedBroker.displayName
    val sideLower = tradeSide.lowercase()

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
        border = BorderStroke(1.dp, if (isReal) desk.stopCrimson.copy(alpha = 0.6f) else desk.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Step 6 · Execution Ticket", style = MaterialTheme.typography.titleLarge)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isReal) desk.stopContainer else desk.bullContainer
                ) {
                    Text(
                        text = if (config.paper) "PAPER MODE" else "REAL • ${brokerName.substringBefore(" (").uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isReal) desk.stopCrimson else desk.bullEmerald,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = tradeSide == "BUY",
                    onClick = { onChangeSide("BUY") },
                    modifier = Modifier.testTag("ticket_side_buy"),
                    label = { Text("BUY Order") }
                )
                FilterChip(
                    selected = tradeSide == "SELL",
                    onClick = { onChangeSide("SELL") },
                    modifier = Modifier.testTag("ticket_side_sell"),
                    label = { Text("SELL Order") }
                )
                if (isReal) {
                    OutlinedButton(onClick = onOpenBrokerSettings) {
                        Text("Switch Broker ($brokerName)")
                    }
                }
            }

            if (isReal) {
                Surface(
                    color = desk.stopContainer,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, desk.stopCrimson.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = desk.stopCrimson, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(id = R.string.real_money_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = desk.stopCrimson
                        )
                    }
                }
            }

            if (order != null) {
                Text(
                    "${order.transactionType} ${order.quantity} ${order.exchange}:${order.tradingsymbol} (${order.product} ${order.orderType}) · Stop ${IndianMarketUtils.inr(order.stopPrice)} · Target ${IndianMarketUtils.inr(order.targetPrice)} · Max Loss ${IndianMarketUtils.inr(order.maxLossInr)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (config.paper) {
                        "Simulated Paper Order Payload (Fingerprint ${order.fingerprint})"
                    } else {
                        "Adapter Payload: $brokerName (Fingerprint ${order.fingerprint})"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, desk.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (config.paper) order.kitePayloadJson else (brokerPreviewPayload ?: order.kitePayloadJson),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (!canConfirm && order != null) {
                Text(
                    text = "Risk Agent BLOCKED this order (${riskResult?.reasons?.firstOrNull() ?: "Run Risk Gate in Step 5"}). Unsafe trades are blocked in both Paper & Real modes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = desk.stopCrimson
                )
            }

            Button(
                onClick = { showConfirmDialog = true },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReal) desk.stopCrimson else desk.bullEmerald
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_broker_confirm_button")
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (config.paper) "Confirm & Record Paper $tradeSide Fill"
                    else "Confirm & Place REAL $tradeSide ($brokerName)"
                )
            }
        }
    }

    if (showConfirmDialog && order != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(if (isReal) "Confirm REAL Broker Order ($brokerName)" else "Explicit Order Confirmation")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isReal) {
                        Surface(color = desk.stopContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                text = "REAL MONEY WARNING: Executing ${order.transactionType} ${order.quantity} ${order.tradingsymbol} live via $brokerName.",
                                style = MaterialTheme.typography.labelMedium,
                                color = desk.stopCrimson,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    Text(
                        "Type \"yes\" (e.g. \"yes $sideLower ${order.quantity} ${order.tradingsymbol}\") to execute. Batch words like \"all\" or \"ok\" are rejected by the Risk & Confirmation gate.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = confirmInput,
                        onValueChange = { confirmInput = it },
                        label = { Text("Confirmation reply") },
                        placeholder = { Text("yes $sideLower ${order.quantity} ${order.tradingsymbol}") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("broker_yes_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onConfirm(confirmInput)
                        confirmInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReal) desk.stopCrimson else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("broker_yes_submit_button")
                ) {
                    Text(if (isReal) "Place REAL Order" else "Execute Paper Fill")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = valueColor, fontWeight = FontWeight.Bold)
    }
}
