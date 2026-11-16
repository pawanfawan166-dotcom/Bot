package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.DeskConfig
import com.example.model.StopRuleType
import com.example.model.TargetRuleType
import com.example.ui.theme.LocalDeskColors

/**
 * Interactive ORB (Opening Range Breakout) Strategy & Risk Agent R:R Threshold Configurator.
 * Allows toggling OR duration (5m / 10m / 15m / 30m), entry start window, min relative volume,
 * stop rule (OR Midpoint / OR Low), target R-multiple, and Risk Gate minimum Reward:Risk ratio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrbStrategyConfigCard(
    config: DeskConfig,
    cycleRunning: Boolean,
    onUpdateOrbStrategy: (
        orbDurationMinutes: Int,
        orbMinVolumeX: Double,
        orbStopRule: StopRuleType,
        orbTargetRule: TargetRuleType,
        minRewardRisk: Double,
        noEntriesBefore: String
    ) -> Unit,
    onRunCycleNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var expanded by remember { mutableStateOf(true) }

    val targetRValue = when (config.orbTargetRule) {
        TargetRuleType.R1 -> 1.0
        TargetRuleType.R1_5 -> 1.5
        TargetRuleType.R2 -> 2.0
        TargetRuleType.PRICE -> 1.5
    }
    val riskWillVeto = targetRValue < config.minRewardRisk - 0.01

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("orb_strategy_config_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Collapsible Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .minimumInteractiveComponentSize()
                    .testTag("orb_cfg_header_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(desk.amberContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = desk.amberSaffron,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(id = R.string.orb_config_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${config.orbDurationMinutes}m OR · Vol ≥${config.orbMinVolumeX}x · ${config.orbStopRule.label} · Target ${config.orbTargetRule.label} (Min ${config.minRewardRisk}R)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse ORB config" else "Expand ORB config"
                )
            }

            // Live Risk Gate R:R Compatibility Banner
            Surface(
                color = if (riskWillVeto) desk.stopContainer else desk.bullContainer,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(
                    1.dp,
                    if (riskWillVeto) desk.stopCrimson.copy(alpha = 0.55f) else desk.bullEmerald.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("orb_cfg_rr_status_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (riskWillVeto) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (riskWillVeto) desk.stopCrimson else desk.bullEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (riskWillVeto) {
                            "Target ${config.orbTargetRule.label} < Risk Min ${config.minRewardRisk}R — Risk Agent #12 will VETO order"
                        } else {
                            "Risk Gate #12 Ready: Target ${config.orbTargetRule.label} ≥ Min ${config.minRewardRisk}R"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (riskWillVeto) desk.stopCrimson else desk.bullEmerald
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 1. Opening Range Duration (5m / 10m / 15m / 30m)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Opening Range Duration",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(5, 10, 15, 30).forEach { mins ->
                                val autoStart = if (mins <= 10 && config.noEntriesBefore == "09:30") "09:20" else config.noEntriesBefore
                                FilterChip(
                                    selected = config.orbDurationMinutes == mins,
                                    onClick = {
                                        onUpdateOrbStrategy(
                                            mins,
                                            config.orbMinVolumeX,
                                            config.orbStopRule,
                                            config.orbTargetRule,
                                            config.minRewardRisk,
                                            autoStart
                                        )
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 40.dp)
                                        .testTag("orb_cfg_duration_$mins"),
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                    }

                    // 2. Entry Window Start + Stop Rule
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Entry Start (IST)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("09:20", "09:30").forEach { startHm ->
                                    val cleanTag = startHm.replace(":", "")
                                    FilterChip(
                                        selected = config.noEntriesBefore == startHm,
                                        onClick = {
                                            onUpdateOrbStrategy(
                                                config.orbDurationMinutes,
                                                config.orbMinVolumeX,
                                                config.orbStopRule,
                                                config.orbTargetRule,
                                                config.minRewardRisk,
                                                startHm
                                            )
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 40.dp)
                                            .testTag("orb_cfg_start_$cleanTag"),
                                        label = { Text(startHm, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ORB Stop Rule",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(StopRuleType.OR_MID, StopRuleType.OR_LOW).forEach { sRule ->
                                    FilterChip(
                                        selected = config.orbStopRule == sRule,
                                        onClick = {
                                            onUpdateOrbStrategy(
                                                config.orbDurationMinutes,
                                                config.orbMinVolumeX,
                                                sRule,
                                                config.orbTargetRule,
                                                config.minRewardRisk,
                                                config.noEntriesBefore
                                            )
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 40.dp)
                                            .testTag("orb_cfg_stop_${sRule.code}"),
                                        label = { Text(sRule.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Min Relative Volume Filter
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Min Breakout Relative Volume",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1.0, 1.25, 1.5, 2.0).forEach { volX ->
                                FilterChip(
                                    selected = kotlin.math.abs(config.orbMinVolumeX - volX) < 0.01,
                                    onClick = {
                                        onUpdateOrbStrategy(
                                            config.orbDurationMinutes,
                                            volX,
                                            config.orbStopRule,
                                            config.orbTargetRule,
                                            config.minRewardRisk,
                                            config.noEntriesBefore
                                        )
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 40.dp)
                                        .testTag("orb_cfg_vol_${volX.toString().replace('.', '_')}"),
                                    label = { Text("${volX}x") }
                                )
                            }
                        }
                    }

                    // 4. Target Profit R:R vs Risk Agent Minimum R:R Threshold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Strategy Target R:R",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(TargetRuleType.R1, TargetRuleType.R1_5, TargetRuleType.R2).forEach { tRule ->
                                    FilterChip(
                                        selected = config.orbTargetRule == tRule,
                                        onClick = {
                                            onUpdateOrbStrategy(
                                                config.orbDurationMinutes,
                                                config.orbMinVolumeX,
                                                config.orbStopRule,
                                                tRule,
                                                config.minRewardRisk,
                                                config.noEntriesBefore
                                            )
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 40.dp)
                                            .testTag("orb_cfg_target_${tRule.code}"),
                                        label = { Text(tRule.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Risk Agent Min R:R",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1.0, 1.5, 2.0, 2.5).forEach { minRr ->
                                    FilterChip(
                                        selected = kotlin.math.abs(config.minRewardRisk - minRr) < 0.01,
                                        onClick = {
                                            onUpdateOrbStrategy(
                                                config.orbDurationMinutes,
                                                config.orbMinVolumeX,
                                                config.orbStopRule,
                                                config.orbTargetRule,
                                                minRr,
                                                config.noEntriesBefore
                                            )
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 40.dp)
                                            .testTag("orb_cfg_min_rr_${minRr.toString().replace('.', '_')}"),
                                        label = { Text("${minRr}R", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }

                    // 5. Quick Run Cycle Evaluation Button
                    Button(
                        onClick = onRunCycleNow,
                        enabled = !cycleRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (riskWillVeto) desk.amberSaffron else desk.bullEmerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("orb_cfg_run_cycle_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (riskWillVeto) {
                                "Test Risk Gate Veto (${config.orbDurationMinutes}m • ${config.orbTargetRule.label} < ${config.minRewardRisk}R)"
                            } else {
                                "Evaluate ${config.orbDurationMinutes}m ORB with Risk Agent"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
