package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.IndianMarketUtils
import com.example.model.DataMode
import com.example.model.DeskConfig
import com.example.model.HeatmapIntensityMetric
import com.example.model.HeatmapUniverse
import com.example.model.MarketClockStatus
import com.example.model.MarketHeatmapCell
import com.example.model.OrbQuickRow
import com.example.model.ScanHit
import com.example.model.ScanMode
import com.example.ui.components.LiveOrbTop5Card
import com.example.ui.components.MarketHeatmapCard
import com.example.ui.theme.LocalDeskColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    clockStatus: MarketClockStatus,
    config: DeskConfig,
    scanMode: ScanMode,
    setupFilter: String,
    orMinutes: Int,
    includeT2t: Boolean,
    watchlist: List<String>,
    hits: List<ScanHit>,
    isLoading: Boolean,
    orbExchange: String,
    orbQuickMinutes: Int,
    topOrbRows: List<OrbQuickRow>,
    topOrbLoading: Boolean,
    heatmapUniverse: HeatmapUniverse,
    heatmapMetric: HeatmapIntensityMetric,
    heatmapCells: List<MarketHeatmapCell>,
    heatmapLoading: Boolean,
    onToggleDataMode: () -> Unit,
    onSelectScanMode: (ScanMode) -> Unit,
    onSelectSetupFilter: (String) -> Unit,
    onSelectOrMinutes: (Int) -> Unit,
    onToggleT2t: (Boolean) -> Unit,
    onAddWatchlist: (String) -> Unit,
    onRemoveWatchlist: (String) -> Unit,
    onRefreshScan: () -> Unit,
    onSelectOrbExchange: (String) -> Unit,
    onSelectOrbMinutes: (Int) -> Unit,
    onRefreshTopOrb: () -> Unit,
    onSelectHeatmapUniverse: (HeatmapUniverse) -> Unit,
    onSelectHeatmapMetric: (HeatmapIntensityMetric) -> Unit,
    onRefreshHeatmap: () -> Unit,
    onRunAgentForSymbol: (String) -> Unit,
    onPlanTrade: (String) -> Unit,
    onInspectIntel: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var watchInput by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Terminal Header with IST Clock & Mode Switch
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_market_hero),
                            contentDescription = "Indian Equities Trading Desk Banner",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0x880B1018),
                                            Color(0xEE0B1018)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "NSE / BSE DAY DESK",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = desk.bullEmerald
                                    )
                                    Text(
                                        text = "${clockStatus.istDate} · ${clockStatus.istTime} IST",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AssistChip(
                                        onClick = onToggleDataMode,
                                        modifier = Modifier.testTag("toggle_data_mode_chip"),
                                        label = {
                                            Text(
                                                if (config.dataMode == DataMode.LIVE_PUBLIC) "LIVE FEED" else "OCT 19 REPLAY",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (config.dataMode == DataMode.LIVE_PUBLIC) Icons.Default.Public else Icons.Default.Schedule,
                                                contentDescription = "Toggle market feed mode",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                    IconButton(
                                        onClick = onOpenSettings,
                                        modifier = Modifier.testTag("open_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Desk Settings"
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (clockStatus.isTradingDay) desk.bullContainer else desk.stopContainer
                                ) {
                                    Text(
                                        text = clockStatus.phaseLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (clockStatus.isTradingDay) desk.bullEmerald else desk.stopCrimson,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = clockStatus.detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Live NSE/BSE Top-5 Relative Volume ORB Quick-View
            item {
                LiveOrbTop5Card(
                    exchange = orbExchange,
                    orMinutes = orbQuickMinutes,
                    dataMode = config.dataMode,
                    rows = topOrbRows,
                    isLoading = topOrbLoading,
                    onSelectExchange = onSelectOrbExchange,
                    onSelectOrMinutes = onSelectOrbMinutes,
                    onRefresh = onRefreshTopOrb,
                    onTradeSymbol = onPlanTrade,
                    onRunAgentForSymbol = onRunAgentForSymbol
                )
            }

            // Market Volatility & Relative Volume Heatmap
            item {
                MarketHeatmapCard(
                    universe = heatmapUniverse,
                    metric = heatmapMetric,
                    dataMode = config.dataMode,
                    cells = heatmapCells,
                    isLoading = heatmapLoading,
                    onSelectUniverse = onSelectHeatmapUniverse,
                    onSelectMetric = onSelectHeatmapMetric,
                    onRefresh = onRefreshHeatmap,
                    onTradeSymbol = onPlanTrade,
                    onInspectIntel = onInspectIntel
                )
            }

            // Scan Mode & Filter Controls
            item {
                Card(
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Scanner Mode", style = MaterialTheme.typography.titleMedium)
                            Button(
                                onClick = onRefreshScan,
                                modifier = Modifier.testTag("run_scan_button"),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan Nifty 50")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = scanMode == ScanMode.ORB,
                                onClick = { onSelectScanMode(ScanMode.ORB) },
                                modifier = Modifier.testTag("scan_mode_orb"),
                                label = { Text("Intraday ORB") }
                            )
                            FilterChip(
                                selected = scanMode == ScanMode.DAILY,
                                onClick = { onSelectScanMode(ScanMode.DAILY) },
                                modifier = Modifier.testTag("scan_mode_daily"),
                                label = { Text("Daily Setups") }
                            )
                            FilterChip(
                                selected = scanMode == ScanMode.WATCHLIST,
                                onClick = { onSelectScanMode(ScanMode.WATCHLIST) },
                                modifier = Modifier.testTag("scan_mode_watchlist"),
                                label = { Text("Watchlist (${watchlist.size})") }
                            )
                            FilterChip(
                                selected = includeT2t,
                                onClick = { onToggleT2t(!includeT2t) },
                                modifier = Modifier.testTag("toggle_t2t_chip"),
                                label = { Text("Include T2T (BE/BZ)") }
                            )
                        }

                        if (scanMode == ScanMode.ORB) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Opening Range:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                listOf(5, 10, 15, 30).forEach { mins ->
                                    FilterChip(
                                        selected = orMinutes == mins,
                                        onClick = { onSelectOrMinutes(mins) },
                                        modifier = Modifier.testTag("or_minutes_$mins"),
                                        label = { Text("${mins}m") }
                                    )
                                }
                            }
                        } else if (scanMode == ScanMode.DAILY) {
                            val setups = listOf(
                                "all" to "All Setups",
                                "breakout_20d" to "20D Breakout",
                                "bull_flag" to "Bull Flag",
                                "reclaim_ma20" to "Reclaim MA20",
                                "oversold" to "Oversold RSI",
                                "oversold_bounce" to "Oversold Bounce"
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                setups.forEach { (code, label) ->
                                    FilterChip(
                                        selected = setupFilter == code,
                                        onClick = { onSelectSetupFilter(code) },
                                        modifier = Modifier.testTag("setup_filter_$code"),
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = watchInput,
                                    onValueChange = { watchInput = it.uppercase() },
                                    label = { Text("Add symbol (e.g. M&M, INFY)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("watchlist_symbol_input")
                                )
                                Button(
                                    onClick = {
                                        if (watchInput.isNotBlank()) {
                                            onAddWatchlist(watchInput)
                                            watchInput = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("watchlist_add_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add to watchlist")
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                watchlist.forEach { sym ->
                                    InputChip(
                                        selected = false,
                                        onClick = { onPlanTrade(sym) },
                                        label = { Text(sym) },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { onRemoveWatchlist(sym) },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove $sym", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Results
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (hits.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, desk.borderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_watchlist),
                                contentDescription = "No scanner hits",
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Text("No qualifying setups found", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Try switching between Intraday ORB and Daily Setups, or add a symbol to your Watchlist.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "${hits.size} Setup Hit(s) · Paper Mode ${if (config.paper) "ON" else "OFF"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(hits, key = { "${it.symbol}_${it.setup}" }) { hit ->
                    ScanHitCard(
                        hit = hit,
                        onPlanTrade = { onPlanTrade(hit.symbol) },
                        onInspectIntel = { onInspectIntel(hit.symbol) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanHitCard(
    hit: ScanHit,
    onPlanTrade: () -> Unit,
    onInspectIntel: () -> Unit
) {
    val desk = LocalDeskColors.current
    val posChange = hit.changePct >= 0

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
        border = BorderStroke(1.dp, desk.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scan_hit_card_${hit.symbol}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = hit.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = desk.bullContainer
                    ) {
                        Text(
                            text = hit.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = desk.bullEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (hit.isCas == true) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = desk.amberContainer
                        ) {
                            Text(
                                text = "CAS 15:15",
                                style = MaterialTheme.typography.labelSmall,
                                color = desk.amberSaffron,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (hit.nearUpperCircuit) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Near upper circuit",
                            tint = desk.stopCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = IndianMarketUtils.inr(hit.lastPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = MaterialTheme.typography.labelMedium.fontFamily
                    )
                    Text(
                        text = "${if (posChange) "+" else ""}${"%.2f".format(hit.changePct)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (posChange) desk.bullEmerald else desk.stopCrimson
                    )
                }
            }

            Text(
                text = hit.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlanTrade,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("plan_trade_${hit.symbol}")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trade Pipeline")
                }
                OutlinedButton(
                    onClick = onInspectIntel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inspect_intel_${hit.symbol}")
                ) {
                    Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("News & Results")
                }
            }
        }
    }
}
