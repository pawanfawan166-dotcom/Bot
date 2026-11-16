package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.IndianMarketUtils
import com.example.model.AgentCycleSnapshot
import com.example.model.AutoActivityEvent
import com.example.model.AutoIntervalOption
import com.example.model.AutoModeStatus
import com.example.model.DeskConfig
import com.example.model.LivePnlSummary
import com.example.model.PortfolioPosition
import com.example.ui.theme.LocalDeskColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoModeControlCard(
    config: DeskConfig,
    autoStatus: AutoModeStatus,
    tradesToday: Int,
    lastSnapshot: AgentCycleSnapshot?,
    activityLog: List<AutoActivityEvent>,
    onStartAuto: () -> Unit,
    onStopAuto: () -> Unit,
    onEmergencyStop: () -> Unit,
    onSelectInterval: (Int) -> Unit,
    onUpdateLimits: (Int, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var showRealAutoDialog by remember { mutableStateOf(false) }

    var draftMaxTrades by remember(config.maxTradesPerDay) { mutableIntStateOf(config.maxTradesPerDay) }
    var draftMaxLossText by remember(config.maxDailyLossInr) {
        mutableStateOf(config.maxDailyLossInr.toInt().toString())
    }

    val isRunning = autoStatus.enabled
    val isReal = !config.paper
    val isAutoStopped = !isRunning && autoStatus.autoStopReason != null

    val statusColor = when {
        isRunning && isReal -> desk.stopCrimson
        isRunning -> desk.bullEmerald
        isAutoStopped -> desk.amberSaffron
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusBg = when {
        isRunning && isReal -> desk.stopContainer
        isRunning -> desk.bullContainer
        isAutoStopped -> desk.amberContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusLabel = when {
        isRunning && autoStatus.cycleRunning -> "RUNNING • CYCLE ACTIVE"
        isRunning -> "RUNNING"
        isAutoStopped -> "AUTO-STOPPED"
        else -> "STOPPED (MANUAL)"
    }

    fun requestStartAuto() {
        if (!config.paper) {
            showRealAutoDialog = true
        } else {
            onStartAuto()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("auto_mode_control_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isRunning && isReal -> desk.stopCrimson.copy(alpha = 0.8f)
                isRunning -> desk.bullEmerald.copy(alpha = 0.65f)
                else -> desk.borderSubtle
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Title + Separate Auto Toggle + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(id = R.string.auto_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Separate from Paper/Real • Risk Agent hard veto enforced",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("auto_status_badge")
                        )
                    }
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { enable ->
                            if (enable) requestStartAuto() else onStopAuto()
                        },
                        modifier = Modifier.testTag("auto_mode_switch")
                    )
                }
            }

            // Strong Warning if BOTH Real Mode and Auto Mode are ON (or before starting in Real)
            AnimatedVisibility(visible = isReal && isRunning) {
                Surface(
                    color = desk.stopContainer,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, desk.stopCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("real_auto_danger_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Real auto trading danger",
                            tint = desk.stopCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.auto_real_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = desk.stopCrimson,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Auto-stop / safety reason banner
            AnimatedVisibility(visible = !autoStatus.autoStopReason.isNullOrBlank()) {
                Surface(
                    color = desk.amberContainer,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, desk.amberSaffron.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_stop_reason_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Auto-stop notice",
                            tint = desk.amberSaffron,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = autoStatus.autoStopReason.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = desk.amberSaffron,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Next Run Time, Countdown & Last Cycle Result Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = desk.cardElevated,
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(13.dp), tint = desk.dataSky)
                            Text("Next Scheduled Run", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val mm = autoStatus.countdownSec / 60
                        val ss = autoStatus.countdownSec % 60
                        Text(
                            text = if (isRunning) {
                                if (autoStatus.cycleRunning) "Executing now…"
                                else "${autoStatus.nextRunIst} (%02d:%02d)".format(mm, ss)
                            } else {
                                "Manual Only"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isRunning) desk.bullEmerald else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("auto_next_run_text")
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = desk.cardElevated,
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Last Cycle (${autoStatus.lastRunIst ?: "--"})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = lastSnapshot?.outcome ?: "No cycles run yet",
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                lastSnapshot == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                lastSnapshot.succeeded -> desk.bullEmerald
                                else -> desk.stopCrimson
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("auto_last_cycle_text")
                        )
                    }
                }
            }

            // Explicit Start Auto / Stop Auto / Emergency Stop Buttons (min 48dp touch target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { requestStartAuto() },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReal) desk.stopCrimson else desk.bullEmerald
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("start_auto_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Start Auto")
                }

                OutlinedButton(
                    onClick = onStopAuto,
                    enabled = isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("stop_auto_button")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop Auto")
                }
            }

            if (isRunning || autoStatus.cycleRunning) {
                Button(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.buttonColors(containerColor = desk.stopCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("card_emergency_stop_button")
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.emergency_stop_label),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = desk.borderSubtle)

            // Schedule Interval Selector (5 min / 15 min / 30 min / 1 hour)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Cycle Schedule Interval (7-Agent Chain)",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AutoIntervalOption.entries.forEach { opt ->
                        FilterChip(
                            selected = config.autoIntervalMinutes == opt.minutes,
                            onClick = { onSelectInterval(opt.minutes) },
                            label = { Text(opt.label) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("auto_interval_${opt.minutes}")
                        )
                    }
                }
            }

            // Daily Guardrails: Max Trades/Day & Max Daily Loss (Auto-Stop)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Daily Guardrails (Auto-Stop on Max Loss)",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Max Trades / Day", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "Used today: $tradesToday / ${config.maxTradesPerDay}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tradesToday >= config.maxTradesPerDay) desk.amberSaffron else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val next = (draftMaxTrades - 1).coerceAtLeast(1)
                                draftMaxTrades = next
                                val lossVal = draftMaxLossText.toDoubleOrNull() ?: config.maxDailyLossInr
                                onUpdateLimits(next, lossVal)
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("auto_max_trades_minus")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease max trades")
                        }
                        Text(
                            text = draftMaxTrades.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                val next = (draftMaxTrades + 1).coerceAtMost(50)
                                draftMaxTrades = next
                                val lossVal = draftMaxLossText.toDoubleOrNull() ?: config.maxDailyLossInr
                                onUpdateLimits(next, lossVal)
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("auto_max_trades_plus")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase max trades")
                        }
                    }
                }

                // Max Daily Loss Quick Presets + Custom INR Input
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(500.0, 1500.0, 3000.0, 5000.0).forEach { preset ->
                        FilterChip(
                            selected = kotlin.math.abs(config.maxDailyLossInr - preset) < 1.0,
                            onClick = {
                                draftMaxLossText = preset.toInt().toString()
                                onUpdateLimits(draftMaxTrades, preset)
                            },
                            label = { Text("Loss ${IndianMarketUtils.money(preset)}") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draftMaxLossText,
                        onValueChange = { draftMaxLossText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Max Daily Loss ₹ (Auto-Stop)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auto_max_loss_input")
                    )
                    Button(
                        onClick = {
                            val parsed = draftMaxLossText.toDoubleOrNull() ?: config.maxDailyLossInr
                            onUpdateLimits(draftMaxTrades, parsed.coerceAtLeast(100.0))
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("auto_apply_limits_button")
                    ) {
                        Text("Apply")
                    }
                }
            }

            // Live Auto Execution & Safety Activity Feed
            if (activityLog.isNotEmpty()) {
                HorizontalDivider(color = desk.borderSubtle)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Live Auto Scheduler & Safety Log",
                        style = MaterialTheme.typography.labelLarge
                    )
                    activityLog.take(4).forEach { evt ->
                        val fg = when {
                            evt.isError -> desk.stopCrimson
                            evt.isWarning -> desk.amberSaffron
                            else -> desk.bullEmerald
                        }
                        Surface(
                            color = desk.cardElevated,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = evt.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = fg,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = evt.timestampIst,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = evt.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRealAutoDialog) {
        AlertDialog(
            onDismissRequest = { showRealAutoDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = desk.stopCrimson) },
            title = { Text("Enable REAL-MONEY Auto Trading?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Real Trading (${config.selectedBroker.displayName}) is ON. Enabling Auto Mode will automatically run Scanner → News/Analyst → Entry → TimeMachine → Risk and send REAL orders whenever Risk passes, without asking you per order.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = desk.stopContainer,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, desk.stopCrimson.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Limits armed: Max ${config.maxTradesPerDay} trades/day • Auto-stop at ${IndianMarketUtils.inr(config.maxDailyLossInr)} loss.",
                            style = MaterialTheme.typography.labelMedium,
                            color = desk.stopCrimson,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRealAutoDialog = false
                        onStartAuto()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = desk.stopCrimson),
                    modifier = Modifier.testTag("confirm_real_auto_start_button")
                ) {
                    Text("Start Real Auto")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRealAutoDialog = false }) {
                    Text("Cancel (Use Paper First)")
                }
            }
        )
    }
}

@Composable
fun DashboardPositionsPnlCard(
    config: DeskConfig,
    livePnl: LivePnlSummary,
    positions: List<PortfolioPosition>,
    onOpenPortfolio: () -> Unit,
    onInspectSymbol: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_positions_pnl_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (config.paper) "Open Paper Positions & P&L" else "${config.selectedBroker.displayName} Open Positions & P&L",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${positions.size} open holding(s) • Cash ${IndianMarketUtils.inr(livePnl.availableCash)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onOpenPortfolio,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("dashboard_view_portfolio_button")
                ) {
                    Text("Portfolio")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Portfolio", modifier = Modifier.size(16.dp))
                }
            }

            if (positions.isEmpty()) {
                Text(
                    text = "No open holdings right now. Autonomous or manual fills appear here with live mark-to-market P&L.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                positions.take(4).forEach { pos ->
                    val unrl = (pos.lastPrice - pos.avgPrice) * pos.shares
                    val pnlColor = if (unrl >= 0) desk.bullEmerald else desk.stopCrimson
                    Surface(
                        color = desk.cardElevated,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, desk.borderSubtle),
                        onClick = { onInspectSymbol(pos.symbol) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${pos.symbol} (${pos.shares} sh • ${pos.product})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Avg ${IndianMarketUtils.inr(pos.avgPrice)} → LTP ${IndianMarketUtils.inr(pos.lastPrice)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = signedInr(unrl),
                                style = MaterialTheme.typography.labelLarge,
                                color = pnlColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
