package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.IndianMarketUtils
import com.example.model.AlertRule
import com.example.model.AlertType
import com.example.model.AllocationRow
import com.example.model.BrokerCredentials
import com.example.model.BrokerRecord
import com.example.model.BrokerRemoteOrder
import com.example.model.DeskConfig
import com.example.model.IndianBrokerId
import com.example.model.LivePnlSummary
import com.example.model.PortfolioPosition
import com.example.model.PortfolioState
import com.example.ui.components.TradingModeBanner
import com.example.ui.components.signedInr
import com.example.ui.theme.LocalDeskColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioAlertsScreen(
    config: DeskConfig,
    portfolio: PortfolioState,
    livePnl: LivePnlSummary,
    brokerCredentials: Map<IndianBrokerId, BrokerCredentials>,
    realPositions: List<PortfolioPosition>,
    realOrders: List<BrokerRemoteOrder>,
    realBrokerLoading: Boolean,
    brokerRecords: List<BrokerRecord>,
    alerts: List<AlertRule>,
    allocations: List<AllocationRow>,
    onToggleTradingMode: (Boolean) -> Unit,
    onOpenBrokerSettings: () -> Unit,
    onRefreshPnl: () -> Unit,
    onQuickTrade: (String, String) -> Unit,
    onFlattenPosition: (PortfolioPosition) -> Unit,
    onComputeWeights: (List<Pair<String, Double>>) -> Unit,
    onAddAlertsFromPlan: () -> Unit,
    onAddCustomAlert: (String, AlertType, Double?, String?, String) -> Unit,
    onDeleteAlert: (String) -> Unit,
    onCheckAlertsNow: () -> Unit,
    onRearmAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var showAddAlert by remember { mutableStateOf(false) }
    var weightsInput by remember { mutableStateOf("RELIANCE:50,HDFCBANK:30,TCS:20") }
    var quickSymbol by remember { mutableStateOf("RELIANCE") }

    val activePositions = if (config.paper) portfolio.positions else realPositions
    val isReal = !config.paper

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Paper <-> Real Switcher + Warning + Live P&L Strip
            item {
                TradingModeBanner(
                    config = config,
                    livePnl = livePnl,
                    credentials = brokerCredentials,
                    onToggleTradingMode = onToggleTradingMode,
                    onOpenBrokerSettings = onOpenBrokerSettings,
                    onRefreshPnl = onRefreshPnl
                )
            }

            // 2. Live Account & P&L Summary Card (Open + Realized + Day P&L)
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
                    border = BorderStroke(1.dp, if (isReal) desk.stopCrimson.copy(alpha = 0.55f) else desk.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (config.paper) "Paper Portfolio & Live P&L" else "${config.selectedBroker.displayName} Real Account & Live P&L",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (realBrokerLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Total Equity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(IndianMarketUtils.inr(livePnl.equity), style = MaterialTheme.typography.headlineMedium, color = desk.bullEmerald)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Available Cash", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(IndianMarketUtils.inr(livePnl.availableCash), style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Open Unrealized P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = signedInr(livePnl.openUnrealizedPnl),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (livePnl.openUnrealizedPnl >= 0) desk.bullEmerald else desk.stopCrimson
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Realized P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = signedInr(livePnl.realizedPnlToday),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (livePnl.realizedPnlToday >= 0) desk.bullEmerald else desk.stopCrimson
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Day P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = signedInr(livePnl.dayPnl),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (livePnl.dayPnl >= 0) desk.bullEmerald else desk.stopCrimson
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = "Invested: ${IndianMarketUtils.inr(livePnl.investedValue)} · Updated ${livePnl.lastUpdatedIst} IST",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (config.paper) {
                                Text(
                                    text = "Trades: ${portfolio.tradesToday} · Orders: ${portfolio.ordersToday}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Quick Buy / Sell Action Bar (works in both Paper and Real modes, guarded by Risk Agent)
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quick Buy / Sell Order (Risk-Checked)", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quickSymbol,
                                onValueChange = { quickSymbol = it.uppercase() },
                                label = { Text("Symbol") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("portfolio_quick_symbol_input")
                            )
                            Button(
                                onClick = { if (quickSymbol.isNotBlank()) onQuickTrade(quickSymbol, "BUY") },
                                colors = ButtonDefaults.buttonColors(containerColor = desk.bullEmerald),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("portfolio_quick_buy_button")
                            ) {
                                Text("Buy")
                            }
                            Button(
                                onClick = { if (quickSymbol.isNotBlank()) onQuickTrade(quickSymbol, "SELL") },
                                colors = ButtonDefaults.buttonColors(containerColor = desk.stopCrimson),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("portfolio_quick_sell_button")
                            ) {
                                Text("Sell")
                            }
                        }
                    }
                }
            }

            // 4. Open Positions (Paper FIFO vs Live Broker Positions)
            item {
                Text(
                    text = if (config.paper) {
                        "Open Paper Holdings (${activePositions.size})"
                    } else {
                        "${config.selectedBroker.displayName} Open Positions (${activePositions.size})"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (activePositions.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, desk.borderSubtle)
                    ) {
                        Text(
                            text = if (config.paper) {
                                "No open Paper positions. Execute a simulated fill in Trade Flow to track FIFO lots here."
                            } else {
                                "No open ${config.selectedBroker.displayName} positions loaded. Ensure API credentials are saved in Broker Settings or tap Refresh."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(activePositions, key = { "${it.exchange}:${it.symbol}:${it.product}" }) { pos ->
                    val openPnl = (pos.lastPrice - pos.avgPrice) * pos.shares
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
                        border = BorderStroke(1.dp, desk.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${pos.exchange}:${pos.symbol} (${pos.product})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${pos.shares} sh @ avg ${IndianMarketUtils.inr(pos.avgPrice)} · LTP ${IndianMarketUtils.inr(pos.lastPrice)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Open P&L: ${signedInr(openPnl)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (openPnl >= 0) desk.bullEmerald else desk.stopCrimson
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = { onFlattenPosition(pos) },
                                        colors = ButtonDefaults.buttonColors(containerColor = desk.stopCrimson),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("flatten_${pos.symbol}")
                                    ) {
                                        Icon(Icons.Default.Sell, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Sell / Exit")
                                    }
                                    OutlinedButton(
                                        onClick = { onQuickTrade(pos.symbol, "BUY") },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Buy More")
                                    }
                                }
                            }
                            if (pos.stopPrice != null && pos.targetPrice != null) {
                                Text(
                                    "Exit Bracket: Stop ${IndianMarketUtils.inr(pos.stopPrice)} · Target ${IndianMarketUtils.inr(pos.targetPrice)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = desk.dataSky
                                )
                            }
                        }
                    }
                }
            }

            // 4.5 Live Broker Orders (Real Mode only)
            if (isReal) {
                item {
                    Text(
                        text = "${config.selectedBroker.displayName} Orders Today (${realOrders.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (realOrders.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, desk.borderSubtle)
                        ) {
                            Text(
                                "No remote orders retrieved from ${config.selectedBroker.displayName}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(realOrders, key = { it.orderId }) { ord ->
                        Card(
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, desk.borderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${ord.side} ${ord.quantity} ${ord.exchange}:${ord.symbol} (${ord.product})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (ord.side == "BUY") desk.bullEmerald else desk.amberSaffron
                                    )
                                    Text(
                                        "${ord.orderType} @ ${IndianMarketUtils.inr(ord.price)} · #${ord.orderId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(ord.status, style = MaterialTheme.typography.labelMedium, color = desk.dataSky)
                            }
                        }
                    }
                }
            }

            // Basket Weights Allocator (india-budget --weights)
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.PieChart, contentDescription = null, tint = desk.amberSaffron)
                            Text("Basket Weights Allocator", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = weightsInput,
                                onValueChange = { weightsInput = it.uppercase() },
                                label = { Text("SYM:WEIGHT pairs") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("weights_input")
                            )
                            Button(
                                onClick = {
                                    val parsed = weightsInput.split(",").mapNotNull { tok ->
                                        val parts = tok.split(":")
                                        val s = parts.getOrNull(0)?.trim().orEmpty()
                                        val w = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
                                        if (s.isNotEmpty() && w != null && w > 0) s to w else null
                                    }
                                    if (parsed.isNotEmpty()) onComputeWeights(parsed)
                                },
                                modifier = Modifier.testTag("compute_weights_button")
                            ) { Text("Size") }
                        }
                        allocations.forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${row.symbol} (${row.weightPct}%)", style = MaterialTheme.typography.bodyMedium)
                                Text("${row.shares} sh · ${IndianMarketUtils.inr(row.amountInr)}", style = MaterialTheme.typography.labelMedium, color = desk.bullEmerald)
                            }
                        }
                    }
                }
            }

            // Alerts Manager
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("IST & Price Alerts (${alerts.size})", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onAddAlertsFromPlan, modifier = Modifier.testTag("alerts_from_plan_button")) {
                            Text("+ Plan")
                        }
                        OutlinedButton(onClick = { showAddAlert = true }, modifier = Modifier.testTag("add_custom_alert_button")) {
                            Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Button(onClick = onCheckAlertsNow, modifier = Modifier.testTag("check_alerts_button")) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Check")
                        }
                        IconButton(onClick = onRearmAlerts, modifier = Modifier.testTag("rearm_alerts_button")) {
                            Icon(Icons.Default.Refresh, contentDescription = "Re-arm all alerts")
                        }
                    }
                }
            }

            items(alerts, key = { it.id }) { alert ->
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(alert.label, style = MaterialTheme.typography.titleMedium)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (alert.armed) desk.bullContainer else desk.amberContainer
                                ) {
                                    Text(
                                        if (alert.armed) "ARMED" else "TRIGGERED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (alert.armed) desk.bullEmerald else desk.amberSaffron,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (!alert.lastTriggeredInfo.isNullOrBlank()) {
                                Text(alert.lastTriggeredInfo, style = MaterialTheme.typography.bodyMedium, color = desk.amberSaffron)
                            }
                        }
                        IconButton(onClick = { onDeleteAlert(alert.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete alert", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Broker Audit Log
            item {
                HorizontalDivider(color = desk.borderSubtle)
                Text("Order & Broker Audit Log (${brokerRecords.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(brokerRecords, key = { "${it.id}_${it.fingerprint}" }) { rec ->
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (rec.paper) desk.borderSubtle else desk.stopCrimson.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rec.plainSummary,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (rec.side == "BUY") desk.bullEmerald else desk.amberSaffron,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (rec.paper) desk.bullContainer else desk.stopContainer
                            ) {
                                Text(
                                    text = if (rec.paper) "PAPER · ${rec.status}" else "REAL · ${rec.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (rec.paper) desk.bullEmerald else desk.stopCrimson,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text("${rec.timestampIst} IST · Charges ${IndianMarketUtils.inr(rec.chargesInr)} · Reply: \"${rec.confirmationReply}\"",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showAddAlert) {
        AddAlertDialog(
            onDismiss = { showAddAlert = false },
            onSubmit = { sym, type, valNum, timeHm, lbl ->
                showAddAlert = false
                onAddCustomAlert(sym, type, valNum, timeHm, lbl)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAlertDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, AlertType, Double?, String?, String) -> Unit
) {
    var sym by remember { mutableStateOf("RELIANCE") }
    var type by remember { mutableStateOf(AlertType.ABOVE) }
    var valText by remember { mutableStateOf("1240.0") }
    var timeText by remember { mutableStateOf("15:00") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New NSE/BSE Alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = sym, onValueChange = { sym = it.uppercase() }, label = { Text("Symbol") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(AlertType.ABOVE, AlertType.BELOW, AlertType.PCT_MOVE, AlertType.CIRCUIT, AlertType.TIME).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                if (type == AlertType.TIME) {
                    OutlinedTextField(value = timeText, onValueChange = { timeText = it }, label = { Text("IST Time (HH:MM)") }, singleLine = true)
                } else {
                    OutlinedTextField(value = valText, onValueChange = { valText = it }, label = { Text("Threshold Value") }, singleLine = true)
                }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Optional Label") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(sym, type, valText.toDoubleOrNull(), if (type == AlertType.TIME) timeText else null, label)
            }) { Text("Save Alert") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
