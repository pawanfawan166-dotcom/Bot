package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.data.AppDatabase
import com.example.data.MarketDataSource
import com.example.data.TradingRepository
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.screens.AgentsHubScreen
import com.example.ui.screens.BrokerSettingsScreen
import com.example.ui.screens.IntelScreen
import com.example.ui.screens.PortfolioAlertsScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TradeFlowScreen
import com.example.ui.theme.LocalDeskColors
import com.example.ui.theme.TradeDeskTheme
import kotlinx.serialization.Serializable

@Serializable object AgentsRoute
@Serializable object ScannerRoute
@Serializable object TradeFlowRoute
@Serializable object PortfolioRoute
@Serializable object IntelRoute
@Serializable object SettingsRoute
@Serializable object BrokerSettingsRoute

private data class TopTab(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val tag: String
)

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check synchronously with launch intent before ViewModels/UI
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repo = TradingRepository(db.deskDao(), MarketDataSource(applicationContext))

        setContent {
            val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
            TradeDeskTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModelFactory(repo))
                TradeDeskApp(vm = vm, useRail = widthSizeClass != WindowWidthSizeClass.Compact)
            }
        }
    }
}

@Composable
private fun TradeDeskApp(vm: MainViewModel, useRail: Boolean) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val config by vm.config.collectAsStateWithLifecycle()
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val brokerRecords by vm.brokerRecords.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val followedInvestors by vm.followedInvestors.collectAsStateWithLifecycle()
    val holidays by vm.holidays.collectAsStateWithLifecycle()
    val clockStatus by vm.clockStatus.collectAsStateWithLifecycle()

    val brokerCredentials by vm.brokerCredentials.collectAsStateWithLifecycle()
    val realFunds by vm.realFunds.collectAsStateWithLifecycle()
    val realPositions by vm.realPositions.collectAsStateWithLifecycle()
    val realOrders by vm.realOrders.collectAsStateWithLifecycle()
    val realBrokerLoading by vm.realBrokerLoading.collectAsStateWithLifecycle()
    val brokerTestMessage by vm.brokerTestMessage.collectAsStateWithLifecycle()
    val livePnlSummary by vm.livePnlSummary.collectAsStateWithLifecycle()

    val scanMode by vm.scanMode.collectAsStateWithLifecycle()
    val setupFilter by vm.setupFilter.collectAsStateWithLifecycle()
    val watchlist by vm.watchlist.collectAsStateWithLifecycle()
    val orMinutes by vm.orMinutes.collectAsStateWithLifecycle()
    val includeT2t by vm.includeT2t.collectAsStateWithLifecycle()
    val scanHits by vm.scanHits.collectAsStateWithLifecycle()
    val scanLoading by vm.scanLoading.collectAsStateWithLifecycle()

    val tradeSymbol by vm.tradeSymbol.collectAsStateWithLifecycle()
    val tradeSide by vm.tradeSide.collectAsStateWithLifecycle()
    val activeStep by vm.activeStep.collectAsStateWithLifecycle()
    val entryResult by vm.entryResult.collectAsStateWithLifecycle()
    val stopRule by vm.stopRule.collectAsStateWithLifecycle()
    val stopParam by vm.stopParam.collectAsStateWithLifecycle()
    val targetRule by vm.targetRule.collectAsStateWithLifecycle()
    val targetParam by vm.targetParam.collectAsStateWithLifecycle()
    val tradePlan by vm.tradePlan.collectAsStateWithLifecycle()
    val timeMachineResult by vm.timeMachineResult.collectAsStateWithLifecycle()
    val orderType by vm.orderType.collectAsStateWithLifecycle()
    val orderProposal by vm.orderProposal.collectAsStateWithLifecycle()
    val riskVerdict by vm.riskVerdict.collectAsStateWithLifecycle()
    val tradeFlowLoading by vm.tradeFlowLoading.collectAsStateWithLifecycle()

    val allocations by vm.allocations.collectAsStateWithLifecycle()
    val intelSymbol by vm.intelSymbol.collectAsStateWithLifecycle()
    val newsReport by vm.newsReport.collectAsStateWithLifecycle()
    val analystReport by vm.analystReport.collectAsStateWithLifecycle()
    val analystBasis by vm.analystBasis.collectAsStateWithLifecycle()
    val insidersReport by vm.insidersReport.collectAsStateWithLifecycle()
    val intelLoading by vm.intelLoading.collectAsStateWithLifecycle()

    val agentStages by vm.agentStages.collectAsStateWithLifecycle()
    val agentLogs by vm.agentLogs.collectAsStateWithLifecycle()
    val agentCycles by vm.agentCycles.collectAsStateWithLifecycle()
    val lastCycleSnapshot by vm.lastCycleSnapshot.collectAsStateWithLifecycle()
    val cycleRunning by vm.cycleRunning.collectAsStateWithLifecycle()
    val autoModeStatus by vm.autoModeStatus.collectAsStateWithLifecycle()
    val tradesToday by vm.tradesTodayCount.collectAsStateWithLifecycle()
    val autoActivityLog by vm.autoActivityLog.collectAsStateWithLifecycle()
    val watchlistSentiment by vm.watchlistSentiment.collectAsStateWithLifecycle()
    val sentimentLoading by vm.sentimentLoading.collectAsStateWithLifecycle()
    val orbExchange by vm.orbExchange.collectAsStateWithLifecycle()
    val orbQuickMinutes by vm.orbQuickMinutes.collectAsStateWithLifecycle()
    val topOrbRows by vm.topOrbRows.collectAsStateWithLifecycle()
    val topOrbLoading by vm.topOrbLoading.collectAsStateWithLifecycle()
    val heatmapUniverse by vm.heatmapUniverse.collectAsStateWithLifecycle()
    val heatmapMetric by vm.heatmapMetric.collectAsStateWithLifecycle()
    val heatmapCells by vm.heatmapCells.collectAsStateWithLifecycle()
    val heatmapLoading by vm.heatmapLoading.collectAsStateWithLifecycle()

    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearStatus()
        }
    }

    val tabs = remember {
        listOf(
            TopTab("Agents", Icons.Default.SmartToy, AgentsRoute, "nav_agents"),
            TopTab("Scanner", Icons.Default.Radar, ScannerRoute, "nav_scanner"),
            TopTab("Trade", Icons.AutoMirrored.Filled.ShowChart, TradeFlowRoute, "nav_trade"),
            TopTab("Portfolio", Icons.Default.AccountBalanceWallet, PortfolioRoute, "nav_portfolio"),
            TopTab("Intel", Icons.Default.Newspaper, IntelRoute, "nav_intel")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isSettings = currentDest?.hasRoute(SettingsRoute::class) == true ||
        currentDest?.hasRoute(BrokerSettingsRoute::class) == true

    fun navigateTop(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!useRail && !isSettings) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDest?.hasRoute(tab.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTop(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val desk = LocalDeskColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = autoModeStatus.enabled || cycleRunning) {
                Surface(
                    color = if (!config.paper && autoModeStatus.enabled) desk.stopContainer else desk.cardElevated,
                    border = BorderStroke(
                        1.dp,
                        if (!config.paper && autoModeStatus.enabled) desk.stopCrimson else desk.bullEmerald.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_emergency_stop_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (!config.paper && autoModeStatus.enabled) desk.stopCrimson else desk.bullEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                val mm = autoModeStatus.countdownSec / 60
                                val ss = autoModeStatus.countdownSec % 60
                                Text(
                                    text = when {
                                        !config.paper && autoModeStatus.enabled -> "REAL + AUTO ACTIVE • Live Orders Armed"
                                        autoModeStatus.enabled && cycleRunning -> "AUTO MODE RUNNING • Pipeline Executing"
                                        autoModeStatus.enabled -> "AUTO RUNNING • Next ${autoModeStatus.nextRunIst} (%02d:%02d)".format(mm, ss)
                                        else -> "AGENT CYCLE RUNNING"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (!config.paper && autoModeStatus.enabled) desk.stopCrimson else desk.bullEmerald,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Button(
                            onClick = { vm.emergencyStopAuto() },
                            colors = ButtonDefaults.buttonColors(containerColor = desk.stopCrimson),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("global_emergency_stop_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.emergency_stop_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (useRail && !isSettings) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTop(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AgentsRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<AgentsRoute> {
                        AgentsHubScreen(
                            clockStatus = clockStatus,
                            config = config,
                            portfolio = portfolio,
                            realPositions = realPositions,
                            livePnl = livePnlSummary,
                            brokerCredentials = brokerCredentials,
                            stages = agentStages,
                            logs = agentLogs,
                            cycles = agentCycles,
                            lastSnapshot = lastCycleSnapshot,
                            cycleRunning = cycleRunning,
                            autoStatus = autoModeStatus,
                            tradesToday = tradesToday,
                            autoActivityLog = autoActivityLog,
                            watchlistSentiment = watchlistSentiment,
                            sentimentLoading = sentimentLoading,
                            orbExchange = orbExchange,
                            orbQuickMinutes = orbQuickMinutes,
                            topOrbRows = topOrbRows,
                            topOrbLoading = topOrbLoading,
                            heatmapUniverse = heatmapUniverse,
                            heatmapMetric = heatmapMetric,
                            heatmapCells = heatmapCells,
                            heatmapLoading = heatmapLoading,
                            onRunCycle = { sym -> vm.runAgentCycle(sym) },
                            onStartAuto = { sym -> vm.startAutoMode(sym) },
                            onStopAuto = { vm.stopAutoMode() },
                            onEmergencyStop = { vm.emergencyStopAuto() },
                            onSelectAutoInterval = { mins -> vm.setAutoIntervalMinutes(mins) },
                            onUpdateAutoLimits = { maxT, maxLoss -> vm.updateAutoSafetyLimits(maxT, maxLoss) },
                            onToggleDataMode = { vm.toggleDataMode() },
                            onToggleTradingMode = { paper -> vm.setTradingMode(paper) },
                            onRefreshPnl = { vm.refreshLivePortfolioAndPnl() },
                            onRefreshSentiment = { vm.refreshWatchlistSentiment() },
                            onSelectOrbExchange = { vm.setOrbExchange(it) },
                            onSelectOrbMinutes = { vm.setOrbQuickMinutes(it) },
                            onRefreshTopOrb = { vm.refreshTopOrbScanner() },
                            onSelectHeatmapUniverse = { vm.setHeatmapUniverse(it) },
                            onSelectHeatmapMetric = { vm.setHeatmapMetric(it) },
                            onRefreshHeatmap = { vm.refreshMarketHeatmap() },
                            onUpdateOrbStrategy = { dur, volX, stopRule, targetRule, minRr, startHm ->
                                vm.updateOrbStrategyConfig(dur, volX, stopRule, targetRule, minRr, startHm)
                            },
                            onClearLogs = { vm.clearAgentLogs() },
                            onOpenTradeFlow = { sym ->
                                vm.startTradeForSymbol(sym)
                                navigateTop(TradeFlowRoute)
                            },
                            onOpenPortfolio = { navigateTop(PortfolioRoute) },
                            onOpenIntel = { sym ->
                                vm.loadIntelForSymbol(sym)
                                navigateTop(IntelRoute)
                            },
                            onOpenBrokerSettings = { navController.navigate(BrokerSettingsRoute) },
                            onOpenSettings = { navController.navigate(SettingsRoute) }
                        )
                    }
                composable<ScannerRoute> {
                    ScannerScreen(
                        clockStatus = clockStatus,
                        config = config,
                        scanMode = scanMode,
                        setupFilter = setupFilter,
                        orMinutes = orMinutes,
                        includeT2t = includeT2t,
                        watchlist = watchlist,
                        hits = scanHits,
                        isLoading = scanLoading,
                        orbExchange = orbExchange,
                        orbQuickMinutes = orbQuickMinutes,
                        topOrbRows = topOrbRows,
                        topOrbLoading = topOrbLoading,
                        heatmapUniverse = heatmapUniverse,
                        heatmapMetric = heatmapMetric,
                        heatmapCells = heatmapCells,
                        heatmapLoading = heatmapLoading,
                        onToggleDataMode = { vm.toggleDataMode() },
                        onSelectScanMode = { vm.setScanMode(it) },
                        onSelectSetupFilter = { vm.setSetupFilter(it) },
                        onSelectOrMinutes = { vm.setOrMinutes(it) },
                        onToggleT2t = { vm.setIncludeT2t(it) },
                        onAddWatchlist = { vm.addWatchlistSymbol(it) },
                        onRemoveWatchlist = { vm.removeWatchlistSymbol(it) },
                        onRefreshScan = { vm.runScan() },
                        onSelectOrbExchange = { vm.setOrbExchange(it) },
                        onSelectOrbMinutes = { vm.setOrbQuickMinutes(it) },
                        onRefreshTopOrb = { vm.refreshTopOrbScanner() },
                        onSelectHeatmapUniverse = { vm.setHeatmapUniverse(it) },
                        onSelectHeatmapMetric = { vm.setHeatmapMetric(it) },
                        onRefreshHeatmap = { vm.refreshMarketHeatmap() },
                        onRunAgentForSymbol = { sym ->
                            vm.runAgentCycle(sym)
                            navigateTop(AgentsRoute)
                        },
                        onPlanTrade = { sym ->
                            vm.startTradeForSymbol(sym)
                            navigateTop(TradeFlowRoute)
                        },
                        onInspectIntel = { sym ->
                            vm.loadIntelForSymbol(sym)
                            navigateTop(IntelRoute)
                        },
                        onOpenSettings = { navController.navigate(SettingsRoute) }
                    )
                }
                composable<TradeFlowRoute> {
                    TradeFlowScreen(
                        symbol = tradeSymbol,
                        tradeSide = tradeSide,
                        activeStep = activeStep,
                        config = config,
                        livePnl = livePnlSummary,
                        brokerCredentials = brokerCredentials,
                        brokerPreviewPayload = orderProposal?.let { vm.buildBrokerPreviewPayload(config.selectedBroker, it) },
                        entryResult = entryResult,
                        stopRule = stopRule,
                        stopParam = stopParam,
                        targetRule = targetRule,
                        targetParam = targetParam,
                        tradePlan = tradePlan,
                        timeMachineResult = timeMachineResult,
                        orderType = orderType,
                        orderProposal = orderProposal,
                        riskVerdict = riskVerdict,
                        isLoading = tradeFlowLoading,
                        onSelectStep = { vm.setTradeStep(it) },
                        onEvaluateEntry = { sym, setup, lvl, orM, minVol, iv ->
                            vm.evaluateEntry(sym, setup, lvl, orM, minVol, iv)
                        },
                        onUpdateExit = { sR, sP, tR, tP -> vm.updateExitParams(sR, sP, tR, tP) },
                        onRunTimeMachine = { vm.runTimeMachine() },
                        onChangeOrderType = { type, lim -> vm.setOrderType(type, lim) },
                        onChangeSide = { side -> vm.setTradeSide(side) },
                        onRecheckRisk = { vm.recheckRisk() },
                        onConfirmPaperOrder = { reply -> vm.confirmBrokerTicket(reply) },
                        onAddPlanAlerts = { vm.addAlertsFromCurrentPlan() },
                        onToggleTradingMode = { paper -> vm.setTradingMode(paper) },
                        onOpenBrokerSettings = { navController.navigate(BrokerSettingsRoute) }
                    )
                }
                composable<PortfolioRoute> {
                    PortfolioAlertsScreen(
                        config = config,
                        portfolio = portfolio,
                        livePnl = livePnlSummary,
                        brokerCredentials = brokerCredentials,
                        realPositions = realPositions,
                        realOrders = realOrders,
                        realBrokerLoading = realBrokerLoading,
                        brokerRecords = brokerRecords,
                        alerts = alerts,
                        allocations = allocations,
                        onToggleTradingMode = { paper -> vm.setTradingMode(paper) },
                        onOpenBrokerSettings = { navController.navigate(BrokerSettingsRoute) },
                        onRefreshPnl = { vm.refreshLivePortfolioAndPnl() },
                        onQuickTrade = { sym, side ->
                            vm.startTradeForSymbol(sym, side = side, advanceToStep = 5)
                            navigateTop(TradeFlowRoute)
                        },
                        onFlattenPosition = { vm.flattenPosition(it) },
                        onComputeWeights = { vm.computeBasketAllocation(it) },
                        onAddAlertsFromPlan = { vm.addAlertsFromCurrentPlan() },
                        onAddCustomAlert = { sym, type, v, hm, lbl -> vm.addCustomAlert(sym, type, v, hm, lbl) },
                        onDeleteAlert = { vm.deleteAlert(it) },
                        onCheckAlertsNow = { vm.checkAlertsNow() },
                        onRearmAlerts = { vm.rearmAlerts() }
                    )
                }
                composable<IntelRoute> {
                    IntelScreen(
                        symbol = intelSymbol,
                        analystBasis = analystBasis,
                        newsReport = newsReport,
                        analystReport = analystReport,
                        insidersReport = insidersReport,
                        followedInvestors = followedInvestors,
                        isLoading = intelLoading,
                        onLoadSymbol = { vm.loadIntelForSymbol(it) },
                        onChangeBasis = { vm.setAnalystBasis(it) },
                        onLoadAllDeals = { vm.loadAllBulkBlockDeals() },
                        onAddInvestor = { vm.addFollowedInvestor(it) },
                        onRemoveInvestor = { vm.removeFollowedInvestor(it) }
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        config = config,
                        holidays = holidays,
                        onSaveConfig = { updated ->
                            vm.saveConfig(updated)
                            navController.popBackStack()
                        },
                        onResetPaperAccount = { vm.resetPaperAccount() },
                        onOpenBrokerSettings = { navController.navigate(BrokerSettingsRoute) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<BrokerSettingsRoute> {
                    BrokerSettingsScreen(
                        config = config,
                        livePnl = livePnlSummary,
                        credentialsMap = brokerCredentials,
                        realFunds = realFunds,
                        isTestingConnection = realBrokerLoading,
                        testMessage = brokerTestMessage,
                        onSelectBroker = { vm.selectBroker(it) },
                        onToggleTradingMode = { vm.setTradingMode(it) },
                        onSaveCredentials = { vm.saveBrokerCredentials(it) },
                        onClearCredentials = { vm.clearBrokerCredentials(it) },
                        onTestConnection = { vm.testBrokerConnection(it) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
        }
    }
}
