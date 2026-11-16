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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DataMode
import com.example.model.DeskConfig
import com.example.model.StopRuleType
import com.example.model.TargetRuleType
import com.example.ui.theme.LocalDeskColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    config: DeskConfig,
    holidays: Map<String, String>,
    onSaveConfig: (DeskConfig) -> Unit,
    onResetPaperAccount: () -> Unit,
    onOpenBrokerSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current

    var budgetText by remember(config) { mutableStateOf(config.budget.toInt().toString()) }
    var cashBufferText by remember(config) { mutableStateOf(config.cashBufferPct.toString()) }
    var maxPosText by remember(config) { mutableStateOf(config.maxPositionPct.toString()) }
    var maxOrderText by remember(config) { mutableStateOf(config.maxOrderInr.toInt().toString()) }
    var minOrderText by remember(config) { mutableStateOf(config.minOrderInr.toInt().toString()) }
    var maxLossTradeText by remember(config) { mutableStateOf(config.maxLossPerTradeInr.toInt().toString()) }
    var maxDailyLossText by remember(config) { mutableStateOf(config.maxDailyLossInr.toInt().toString()) }
    var maxTradesText by remember(config) { mutableStateOf(config.maxTradesPerDay.toString()) }
    var minRrText by remember(config) { mutableStateOf(config.minRewardRisk.toString()) }
    var noBeforeText by remember(config) { mutableStateOf(config.noEntriesBefore) }
    var noAfterText by remember(config) { mutableStateOf(config.noEntriesAfter) }
    var replayText by remember(config) { mutableStateOf(config.replayIsoIst) }
    var product by remember(config) { mutableStateOf(config.product) }
    var exchange by remember(config) { mutableStateOf(config.exchange) }
    var dataMode by remember(config) { mutableStateOf(config.dataMode) }
    var orbDuration by remember(config) { mutableStateOf(config.orbDurationMinutes) }
    var orbMinVol by remember(config) { mutableStateOf(config.orbMinVolumeX) }
    var orbStop by remember(config) { mutableStateOf(config.orbStopRule) }
    var orbTarget by remember(config) { mutableStateOf(config.orbTargetRule) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Desk Configuration & Risk Caps", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (config.paper) {
                                "Paper Mode Active (Default) • Risk Gate Enabled"
                            } else {
                                "REAL TRADING (${config.selectedBroker.displayName}) • Risk Gate Enabled"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (config.paper) desk.bullEmerald else desk.stopCrimson
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenBrokerSettings,
                        modifier = Modifier.testTag("settings_open_broker_button")
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Brokers")
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Data Mode & Clock", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = dataMode == DataMode.OFFLINE_REPLAY,
                                onClick = { dataMode = DataMode.OFFLINE_REPLAY },
                                label = { Text("Offline Replay (Oct 2026 Fixtures)") }
                            )
                            FilterChip(
                                selected = dataMode == DataMode.LIVE_PUBLIC,
                                onClick = { dataMode = DataMode.LIVE_PUBLIC },
                                label = { Text("Live Public Feeds") }
                            )
                        }
                        OutlinedTextField(
                            value = replayText,
                            onValueChange = { replayText = it },
                            label = { Text("Replay DateTime IST (YYYY-MM-DD HH:MM)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Product & Exchange", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("MIS", "CNC").forEach { p ->
                                FilterChip(selected = product == p, onClick = { product = p }, label = { Text(p) })
                            }
                            listOf("NSE", "BSE").forEach { e ->
                                FilterChip(selected = exchange == e, onClick = { exchange = e }, label = { Text(e) })
                            }
                        }

                        Text("ORB Strategy Parameters", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 10, 15, 30).forEach { mins ->
                                FilterChip(
                                    selected = orbDuration == mins,
                                    onClick = { orbDuration = mins },
                                    label = { Text("${mins}m OR") }
                                )
                            }
                            listOf(1.0, 1.25, 1.5, 2.0).forEach { vx ->
                                FilterChip(
                                    selected = kotlin.math.abs(orbMinVol - vx) < 0.01,
                                    onClick = { orbMinVol = vx },
                                    label = { Text("Vol ${vx}x") }
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(StopRuleType.OR_MID, StopRuleType.OR_LOW).forEach { sr ->
                                FilterChip(
                                    selected = orbStop == sr,
                                    onClick = { orbStop = sr },
                                    label = { Text(sr.label) }
                                )
                            }
                            listOf(TargetRuleType.R1, TargetRuleType.R1_5, TargetRuleType.R2).forEach { tr ->
                                FilterChip(
                                    selected = orbTarget == tr,
                                    onClick = { orbTarget = tr },
                                    label = { Text("Target ${tr.label}") }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = budgetText,
                                onValueChange = { budgetText = it },
                                label = { Text("Budget (₹)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cashBufferText,
                                onValueChange = { cashBufferText = it },
                                label = { Text("Cash Buffer %") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = maxPosText,
                                onValueChange = { maxPosText = it },
                                label = { Text("Max Position %") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxOrderText,
                                onValueChange = { maxOrderText = it },
                                label = { Text("Max Order (₹)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = maxLossTradeText,
                                onValueChange = { maxLossTradeText = it },
                                label = { Text("Max Loss/Trade (₹)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxDailyLossText,
                                onValueChange = { maxDailyLossText = it },
                                label = { Text("Max Daily Loss (₹)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = maxTradesText,
                                onValueChange = { maxTradesText = it },
                                label = { Text("Max Trades/Day") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = minRrText,
                                onValueChange = { minRrText = it },
                                label = { Text("Min R:R") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = noBeforeText,
                                onValueChange = { noBeforeText = it },
                                label = { Text("No Entry Before IST") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = noAfterText,
                                onValueChange = { noAfterText = it },
                                label = { Text("No Entry After IST") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onSaveConfig(
                                        config.copy(
                                            budget = budgetText.toDoubleOrNull() ?: config.budget,
                                            cashBufferPct = cashBufferText.toDoubleOrNull() ?: config.cashBufferPct,
                                            maxPositionPct = maxPosText.toDoubleOrNull() ?: config.maxPositionPct,
                                            maxOrderInr = maxOrderText.toDoubleOrNull() ?: config.maxOrderInr,
                                            minOrderInr = minOrderText.toDoubleOrNull() ?: config.minOrderInr,
                                            maxLossPerTradeInr = maxLossTradeText.toDoubleOrNull() ?: config.maxLossPerTradeInr,
                                            maxDailyLossInr = maxDailyLossText.toDoubleOrNull() ?: config.maxDailyLossInr,
                                            maxTradesPerDay = maxTradesText.toIntOrNull() ?: config.maxTradesPerDay,
                                            minRewardRisk = minRrText.toDoubleOrNull() ?: config.minRewardRisk,
                                            noEntriesBefore = noBeforeText.trim(),
                                            noEntriesAfter = noAfterText.trim(),
                                            replayIsoIst = replayText.trim(),
                                            product = product,
                                            exchange = exchange,
                                            paper = config.paper,
                                            dataMode = dataMode,
                                            orbDurationMinutes = orbDuration,
                                            orbMinVolumeX = orbMinVol,
                                            orbStopRule = orbStop,
                                            orbTargetRule = orbTarget
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_save_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Save Config")
                            }
                            OutlinedButton(
                                onClick = onResetPaperAccount,
                                modifier = Modifier.testTag("settings_reset_paper_button")
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Reset Paper")
                            }
                        }
                    }
                }
            }

            // NSE 2026 Holiday Calendar
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("2026 NSE Holiday Calendar", style = MaterialTheme.typography.titleMedium)
                        holidays.toSortedMap().forEach { (date, name) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(date, style = MaterialTheme.typography.labelMedium, color = desk.dataSky)
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (name.contains("Muhurat", true)) desk.amberSaffron else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (config.paper) desk.amberContainer else desk.stopContainer,
                    border = BorderStroke(1.dp, (if (config.paper) desk.amberSaffron else desk.stopCrimson).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (config.paper) {
                            "${stringResource(R.string.disclaimer_banner)} Currently in isolated Paper Mode (default) — no broker API keys needed."
                        } else {
                            stringResource(R.string.real_money_warning)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (config.paper) desk.amberSaffron else desk.stopCrimson,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}
