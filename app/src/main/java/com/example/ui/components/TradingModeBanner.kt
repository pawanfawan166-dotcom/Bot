package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.IndianMarketUtils
import com.example.model.BrokerCredentials
import com.example.model.DeskConfig
import com.example.model.IndianBrokerId
import com.example.model.LivePnlSummary
import com.example.ui.theme.LocalDeskColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradingModeBanner(
    config: DeskConfig,
    livePnl: LivePnlSummary,
    credentials: Map<IndianBrokerId, BrokerCredentials>,
    onToggleTradingMode: (Boolean) -> Unit,
    onOpenBrokerSettings: () -> Unit,
    onRefreshPnl: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var showRealWarningDialog by remember { mutableStateOf(false) }

    val isReal = !config.paper
    val activeCreds = credentials[config.selectedBroker]
    val hasAuth = activeCreds?.hasUsableAuth() == true
    val borderColor = if (isReal) desk.stopCrimson.copy(alpha = 0.7f) else desk.bullEmerald.copy(alpha = 0.45f)
    val badgeBg = if (isReal) desk.stopContainer else desk.bullContainer
    val badgeFg = if (isReal) desk.stopCrimson else desk.bullEmerald

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trading_mode_banner"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Mode Status + Paper <-> Real Segmented Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isReal) Icons.Default.Warning else Icons.Default.Shield,
                        contentDescription = if (isReal) "Real trading active" else "Paper simulation active",
                        tint = badgeFg,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (isReal) "REAL TRADING • ${config.selectedBroker.displayName}" else "PAPER TRADING (DEFAULT)",
                            style = MaterialTheme.typography.titleSmall,
                            color = badgeFg,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isReal) {
                                if (hasAuth) "API credentials armed • Live broker routing" else "Credentials missing — configure Broker Settings"
                            } else {
                                "Isolated simulation • No broker credentials required"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Clear Paper <-> Real Toggle Pills
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = desk.cardElevated,
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (config.paper) desk.bullContainer else androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .minimumInteractiveComponentSize()
                                .clickable { if (!config.paper) onToggleTradingMode(true) }
                                .testTag("mode_toggle_paper")
                        ) {
                            Text(
                                text = "Paper",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (config.paper) FontWeight.Bold else FontWeight.Medium,
                                color = if (config.paper) desk.bullEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isReal) desk.stopContainer else androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .minimumInteractiveComponentSize()
                                .clickable { if (config.paper) showRealWarningDialog = true }
                                .testTag("mode_toggle_real")
                        ) {
                            Text(
                                text = "Real",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isReal) FontWeight.Bold else FontWeight.Medium,
                                color = if (isReal) desk.stopCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Explicit Real Money Warning Banner
            AnimatedVisibility(visible = isReal) {
                Surface(
                    color = desk.stopContainer,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, desk.stopCrimson.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("real_money_warning_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Real money warning",
                            tint = desk.stopCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.real_money_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = desk.stopCrimson,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Live P&L Strip (Open + Realized + Day P&L)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PnlStatCell(
                    label = "Open P&L",
                    value = signedInr(livePnl.openUnrealizedPnl),
                    color = if (livePnl.openUnrealizedPnl >= 0) desk.bullEmerald else desk.stopCrimson,
                    modifier = Modifier.weight(1f)
                )
                PnlStatCell(
                    label = "Realized",
                    value = signedInr(livePnl.realizedPnlToday),
                    color = if (livePnl.realizedPnlToday >= 0) desk.bullEmerald else desk.stopCrimson,
                    modifier = Modifier.weight(1f)
                )
                PnlStatCell(
                    label = "Day P&L",
                    value = signedInr(livePnl.dayPnl),
                    color = if (livePnl.dayPnl >= 0) desk.bullEmerald else desk.stopCrimson,
                    modifier = Modifier.weight(1f)
                )
            }

            // Footer Actions: Broker Settings + Live Refresh + Risk Notice
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = desk.dataSky,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Risk Gate Active • Cash ${IndianMarketUtils.money(livePnl.availableCash)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onRefreshPnl != null) {
                        IconButton(
                            onClick = onRefreshPnl,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("refresh_live_pnl_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Live P&L",
                                tint = desk.dataSky
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenBrokerSettings,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("open_broker_settings_button")
                    ) {
                        Icon(
                            imageVector = if (isReal) Icons.Default.Key else Icons.Default.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isReal && !hasAuth) "Connect Broker" else "Broker Settings")
                    }
                }
            }
        }
    }

    if (showRealWarningDialog) {
        AlertDialog(
            onDismissRequest = { showRealWarningDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = desk.stopCrimson
                )
            },
            title = { Text("Enable Real Money Trading?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Switching to Real Trading routes BUY and SELL orders directly to ${config.selectedBroker.displayName} using real funds.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = desk.stopContainer,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, desk.stopCrimson.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = stringResource(id = R.string.real_money_warning),
                            style = MaterialTheme.typography.labelMedium,
                            color = desk.stopCrimson,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = "Your Paper Trading account remains separate. The 14-point Risk Agent still blocks unsafe orders in Real Mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRealWarningDialog = false
                        onToggleTradingMode(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = desk.stopCrimson),
                    modifier = Modifier.testTag("confirm_enable_real_mode_button")
                ) {
                    Text("Switch to Real")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRealWarningDialog = false }) {
                    Text("Stay in Paper")
                }
            }
        )
    }
}

@Composable
private fun PnlStatCell(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = desk.cardElevated,
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun signedInr(value: Double): String {
    val prefix = if (value > 0.005) "+" else if (value < -0.005) "-" else ""
    return "$prefix${IndianMarketUtils.inr(kotlin.math.abs(value))}"
}
