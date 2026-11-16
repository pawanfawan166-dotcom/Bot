package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.IndianMarketUtils
import com.example.model.AgentCycleSnapshot
import com.example.model.AgentLogMessage
import com.example.model.AgentStageState
import com.example.model.AgentVerdict
import com.example.model.AutoActivityEvent
import com.example.model.AutoModeStatus
import com.example.model.BrokerCredentials
import com.example.model.DataMode
import com.example.model.DeskConfig
import com.example.model.HeatmapIntensityMetric
import com.example.model.HeatmapUniverse
import com.example.model.IndianBrokerId
import com.example.model.LivePnlSummary
import com.example.model.MarketClockStatus
import com.example.model.MarketHeatmapCell
import com.example.model.OrbQuickRow
import com.example.model.PortfolioPosition
import com.example.model.PortfolioState
import com.example.model.StopRuleType
import com.example.model.TargetRuleType
import com.example.model.WatchlistSentimentReport
import com.example.ui.components.AgentProgressTimeline
import com.example.ui.components.AgentStatusBar
import com.example.ui.components.AutoModeControlCard
import com.example.ui.components.DashboardPositionsPnlCard
import com.example.ui.components.LiveOrbTop5Card
import com.example.ui.components.MarketHeatmapCard
import com.example.ui.components.OrbStrategyConfigCard
import com.example.ui.components.TradingModeBanner
import com.example.ui.components.WatchlistSentimentGauge
import com.example.ui.theme.LocalDeskColors

@Composable
fun AgentsHubScreen(
    clockStatus: MarketClockStatus,
    config: DeskConfig,
    portfolio: PortfolioState,
    realPositions: List<PortfolioPosition>,
    livePnl: LivePnlSummary,
    brokerCredentials: Map<IndianBrokerId, BrokerCredentials>,
    stages: List<AgentStageState>,
    logs: List<AgentLogMessage>,
    cycles: List<AgentCycleSnapshot>,
    lastSnapshot: AgentCycleSnapshot?,
    cycleRunning: Boolean,
    autoStatus: AutoModeStatus,
    tradesToday: Int,
    autoActivityLog: List<AutoActivityEvent>,
    watchlistSentiment: WatchlistSentimentReport?,
    sentimentLoading: Boolean,
    orbExchange: String,
    orbQuickMinutes: Int,
    topOrbRows: List<OrbQuickRow>,
    topOrbLoading: Boolean,
    heatmapUniverse: HeatmapUniverse,
    heatmapMetric: HeatmapIntensityMetric,
    heatmapCells: List<MarketHeatmapCell>,
    heatmapLoading: Boolean,
    onRunCycle: (String?) -> Unit,
    onStartAuto: (String?) -> Unit,
    onStopAuto: () -> Unit,
    onEmergencyStop: () -> Unit,
    onSelectAutoInterval: (Int) -> Unit,
    onUpdateAutoLimits: (Int, Double) -> Unit,
    onToggleDataMode: () -> Unit,
    onToggleTradingMode: (Boolean) -> Unit,
    onRefreshPnl: () -> Unit,
    onRefreshSentiment: () -> Unit,
    onSelectOrbExchange: (String) -> Unit,
    onSelectOrbMinutes: (Int) -> Unit,
    onRefreshTopOrb: () -> Unit,
    onSelectHeatmapUniverse: (HeatmapUniverse) -> Unit,
    onSelectHeatmapMetric: (HeatmapIntensityMetric) -> Unit,
    onRefreshHeatmap: () -> Unit,
    onUpdateOrbStrategy: (Int, Double, StopRuleType, TargetRuleType, Double, String) -> Unit,
    onClearLogs: () -> Unit,
    onOpenTradeFlow: (String) -> Unit,
    onOpenPortfolio: () -> Unit,
    onOpenIntel: (String) -> Unit,
    onOpenBrokerSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var targetSymbolInput by remember { mutableStateOf("") }
    var confirmRealCycleSymbol by remember { mutableStateOf<String?>(null) }
    var showConfirmRealCycle by remember { mutableStateOf(false) }
    val expandedJsonIds = remember { mutableStateMapOf<String, Boolean>() }

    fun triggerCycleSafe(sym: String?) {
        if (config.paper) {
            onRunCycle(sym)
        } else {
            confirmRealCycleSymbol = sym
            showConfirmRealCycle = true
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AgentStatusBar(
            stages = stages,
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 880.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Multi-Agent Command Terminal Header
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_desk_emblem),
                                    contentDescription = "TradeDesk IN Emblem",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Multi-Agent Desk",
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            color = if (config.paper) desk.bullContainer else desk.stopContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (config.paper) "PAPER MODE" else "REAL • ${config.selectedBroker.displayName.substringBefore(" (").uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (config.paper) desk.bullEmerald else desk.stopCrimson,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${clockStatus.istDate} ${clockStatus.istTime} IST • ${clockStatus.phaseLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("agents_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open Desk Settings"
                                )
                            }
                        }

                        // Active Account KPI Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AgentKpiPill(
                                label = if (config.paper) "Paper Equity" else "Live Equity",
                                value = IndianMarketUtils.money(livePnl.equity),
                                accent = desk.bullEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            AgentKpiPill(
                                label = "Free Cash",
                                value = IndianMarketUtils.money(livePnl.availableCash),
                                accent = desk.dataSky,
                                modifier = Modifier.weight(1f)
                            )
                            AgentKpiPill(
                                label = "Trades",
                                value = "$tradesToday/${config.maxTradesPerDay}",
                                accent = desk.amberSaffron,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Data Mode + Disclaimer Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AssistChip(
                                onClick = onToggleDataMode,
                                modifier = Modifier.testTag("agents_data_mode_chip"),
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (config.dataMode == DataMode.LIVE_PUBLIC) Icons.Default.Public else Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (config.dataMode == DataMode.LIVE_PUBLIC) "Live Public Feeds" else "Offline Replay",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                            Text(
                                text = "Hybrid: Gemini 3.8 + 11 Skills",
                                style = MaterialTheme.typography.labelSmall,
                                color = desk.dataSky
                            )
                        }

                        // Controls: Optional Symbol Pin + Run Cycle + Auto 5m Switch
                        OutlinedTextField(
                            value = targetSymbolInput,
                            onValueChange = { targetSymbolInput = it.uppercase() },
                            label = { Text("Optional Target Symbol (blank = Scanner picks)") },
                            placeholder = { Text("e.g. RELIANCE or HDFCBANK") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("agent_target_symbol_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    triggerCycleSafe(targetSymbolInput.trim().ifBlank { null })
                                },
                                enabled = !cycleRunning,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("run_cycle_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (config.paper) desk.bullEmerald else desk.stopCrimson,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (cycleRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Running Pipeline…")
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Run Cycle (Manual)")
                                }
                            }
                        }

                        Text(
                            text = stringResource(id = R.string.disclaimer_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1.35 Dedicated Automatic Trading Mode Control & Safety Guardrails (Separate from Paper/Real)
            item {
                AutoModeControlCard(
                    config = config,
                    autoStatus = autoStatus,
                    tradesToday = tradesToday,
                    lastSnapshot = lastSnapshot,
                    activityLog = autoActivityLog,
                    onStartAuto = { onStartAuto(targetSymbolInput.trim().ifBlank { null }) },
                    onStopAuto = onStopAuto,
                    onEmergencyStop = onEmergencyStop,
                    onSelectInterval = onSelectAutoInterval,
                    onUpdateLimits = onUpdateAutoLimits
                )
            }

            // 1.5 Paper <-> Real Trading Switcher + Live P&L Banner
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

            // 1.7 Dashboard Open Positions & Live P&L Breakdown
            item {
                DashboardPositionsPnlCard(
                    config = config,
                    livePnl = livePnl,
                    positions = if (config.paper) portfolio.positions else realPositions,
                    onOpenPortfolio = onOpenPortfolio,
                    onInspectSymbol = onOpenTradeFlow
                )
            }

            // 2. 11-Skill Sequential Agent Progress Timeline
            item {
                AgentProgressTimeline(
                    stages = stages,
                    logs = logs
                )
            }

            // 2.5 Watchlist Bullish/Bearish Sentiment Gauge (india-news + india-analyst)
            item {
                WatchlistSentimentGauge(
                    report = watchlistSentiment,
                    isLoading = sentimentLoading,
                    onRefresh = onRefreshSentiment,
                    onInspectSymbol = onOpenIntel
                )
            }

            // 2.65 Configurable ORB Strategy & Risk Gate R:R Configurator
            item {
                OrbStrategyConfigCard(
                    config = config,
                    cycleRunning = cycleRunning,
                    onUpdateOrbStrategy = onUpdateOrbStrategy,
                    onRunCycleNow = { triggerCycleSafe(targetSymbolInput.trim().ifBlank { null }) }
                )
            }

            // 2.75 Live NSE/BSE Top-5 Relative Volume ORB Quick Scanner
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
                    onTradeSymbol = onOpenTradeFlow,
                    onRunAgentForSymbol = { sym ->
                        targetSymbolInput = sym
                        triggerCycleSafe(sym)
                    }
                )
            }

            // 2.9 Market Volatility & Relative Volume Heatmap (Nifty 50 / Watchlist)
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
                    onTradeSymbol = onOpenTradeFlow,
                    onInspectIntel = onOpenIntel
                )
            }

            // 3. Last Cycle Outcome Summary Banner
            lastSnapshot?.let { snap ->
                item {
                    val bg = if (snap.succeeded) desk.bullContainer else desk.stopContainer
                    val accent = if (snap.succeeded) desk.bullEmerald else desk.stopCrimson
                    Surface(
                        color = bg,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
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
                                Text(
                                    text = snap.outcome,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = accent,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "AI:${snap.aiCalls} • Det:${snap.fallbackCalls}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            snap.selectedSymbol?.let { sym ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onOpenTradeFlow(sym) },
                                        modifier = Modifier.testTag("agent_open_trade_flow")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Inspect $sym in Trade Flow")
                                    }
                                    OutlinedButton(
                                        onClick = { onOpenIntel(sym) },
                                        modifier = Modifier.testTag("agent_open_intel")
                                    ) {
                                        Text("Intel")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Live Agent Conversation Log Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = desk.dataSky)
                        Text(
                            text = "Agent Conversation & JSON State Log",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (logs.isNotEmpty() || cycles.isNotEmpty()) {
                        TextButton(
                            onClick = onClearLogs,
                            modifier = Modifier.testTag("clear_agent_logs_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
            }

            if (logs.isEmpty()) {
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
                                painter = painterResource(id = R.drawable.img_market_hero),
                                contentDescription = "TradeDesk Multi-Agent Terminal Banner",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = "Ready for Autonomous Daily Cycle",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap 'Run Cycle' above to dispatch Scanner, News/Analyst, Entry, Exit/TimeMachine, Risk Veto Gate, and Paper Execution agents in sequence.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(logs, key = { it.id }) { msg ->
                    val expanded = expandedJsonIds[msg.id] == true
                    val (badgeBg, badgeFg) = when (msg.verdict) {
                        AgentVerdict.VETO_BLOCK, AgentVerdict.ERROR -> desk.stopContainer to desk.stopCrimson
                        AgentVerdict.PAPER_FILLED, AgentVerdict.PASS -> desk.bullContainer to desk.bullEmerald
                        AgentVerdict.SELECTED, AgentVerdict.FILTERED -> MaterialTheme.colorScheme.tertiaryContainer to desk.dataSky
                        AgentVerdict.AI_FALLBACK -> desk.amberContainer to desk.amberSaffron
                        AgentVerdict.INFO -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(badgeBg, desk.cardElevated))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = msg.agentRole.badge.take(2),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeFg,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = msg.agentTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${msg.timestampIst} IST • ${msg.skillsUsed.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = msg.verdict.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeFg,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = msg.headline,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = msg.reasoning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (msg.aiSource.startsWith("Gemini")) Icons.Default.AutoAwesome else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (msg.aiSource.startsWith("Gemini")) desk.dataSky else desk.amberSaffron,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = msg.aiSource,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (msg.aiSource.startsWith("Gemini")) desk.dataSky else desk.amberSaffron
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { expandedJsonIds[msg.id] = !expanded }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = "Inspect JSON State",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (expanded) "Hide JSON" else "JSON State",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            AnimatedVisibility(visible = expanded) {
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(1.dp, desk.borderSubtle)
                                ) {
                                    Text(
                                        text = msg.jsonPayload,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recent Persisted Cycle History
            if (cycles.size > 1) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, desk.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Recent Autonomous Runs", style = MaterialTheme.typography.titleMedium)
                            cycles.take(5).forEach { c ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${c.cycleId} • ${c.startedAtIst}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = c.outcome,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (c.succeeded) desk.bullEmerald else desk.amberSaffron,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmRealCycle) {
        AlertDialog(
            onDismissRequest = { showConfirmRealCycle = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = desk.stopCrimson) },
            title = { Text("Dispatch Real-Money Agent Cycle?") },
            text = {
                Text(
                    "Real Trading is ON with ${config.selectedBroker.displayName}. If the 14-point Risk Gate approves${confirmRealCycleSymbol?.let { " $it" } ?: ""}, Agent 7 will place a REAL order using real funds.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmRealCycle = false
                        onRunCycle(confirmRealCycleSymbol)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = desk.stopCrimson)
                ) {
                    Text("Run Real Cycle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRealCycle = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AgentKpiPill(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
