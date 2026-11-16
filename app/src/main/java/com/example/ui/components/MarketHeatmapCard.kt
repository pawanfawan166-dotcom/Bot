package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.IndianMarketUtils
import com.example.model.DataMode
import com.example.model.HeatmapIntensityMetric
import com.example.model.HeatmapUniverse
import com.example.model.MarketHeatmapCell
import com.example.ui.theme.LocalDeskColors

/**
 * Native Jetpack Compose Canvas-backed market heatmap for Nifty 50 or the user's watchlist,
 * encoding price volatility and relative volume via thermal color intensity.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketHeatmapCard(
    universe: HeatmapUniverse,
    metric: HeatmapIntensityMetric,
    dataMode: DataMode,
    cells: List<MarketHeatmapCell>,
    isLoading: Boolean,
    onSelectUniverse: (HeatmapUniverse) -> Unit,
    onSelectMetric: (HeatmapIntensityMetric) -> Unit,
    onRefresh: () -> Unit,
    onTradeSymbol: (String) -> Unit,
    onInspectIntel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var selectedCell by remember(cells) { mutableStateOf<MarketHeatmapCell?>(null) }

    val peakVol = cells.maxOfOrNull { it.volatilityPct } ?: 0.0
    val peakRelVol = cells.maxOfOrNull { it.relativeVolumeX } ?: 0.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("market_heatmap_card"),
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = desk.dataSky,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.heatmap_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = if (dataMode == DataMode.LIVE_PUBLIC) desk.bullContainer else desk.amberContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (dataMode == DataMode.LIVE_PUBLIC) "LIVE" else "REPLAY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (dataMode == DataMode.LIVE_PUBLIC) desk.bullEmerald else desk.amberSaffron,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${stringResource(id = R.string.heatmap_subtitle)} • ${cells.size} equities",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("heatmap_refresh_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Market Heatmap"
                        )
                    }
                }
            }

            // Universe + Intensity Metric Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HeatmapUniverse.entries.forEach { u ->
                    val tag = if (u == HeatmapUniverse.NIFTY_50) "heatmap_universe_nifty50" else "heatmap_universe_watchlist"
                    FilterChip(
                        selected = universe == u,
                        onClick = { onSelectUniverse(u) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag(tag),
                        label = {
                            Text(
                                text = u.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    )
                }

                HeatmapIntensityMetric.entries.forEach { m ->
                    val tag = when (m) {
                        HeatmapIntensityMetric.COMBINED -> "heatmap_metric_combined"
                        HeatmapIntensityMetric.VOLATILITY -> "heatmap_metric_volatility"
                        HeatmapIntensityMetric.RELATIVE_VOLUME -> "heatmap_metric_relvol"
                    }
                    FilterChip(
                        selected = metric == m,
                        onClick = { onSelectMetric(m) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag(tag),
                        label = {
                            Text(
                                text = m.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            // Thermal Color-Intensity Scale Legend (Canvas)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val baseSurface = desk.cardElevated
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .testTag("heatmap_legend_canvas")
                        .semantics {
                            contentDescription = "Color intensity legend from crimson bearish volatility to emerald bullish volatility"
                        }
                ) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                desk.stopCrimson,
                                lerp(baseSurface, desk.stopCrimson, 0.35f),
                                baseSurface,
                                lerp(baseSurface, desk.bullEmerald, 0.35f),
                                desk.bullEmerald
                            )
                        ),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                        size = size
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Bear High Intensity",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = desk.stopCrimson
                    )
                    Text(
                        text = "Peak ${"%.2f".format(peakVol)}% vol · ${"%.2f".format(peakRelVol)}x RVol",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Bull High Intensity",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = desk.bullEmerald
                    )
                }
            }

            // Adaptive Multi-Column Heatmap Matrix
            if (isLoading && cells.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else if (cells.isEmpty()) {
                Surface(
                    color = desk.cardElevated,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, desk.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No market volatility/volume tiles available for ${universe.label}. Try refreshing or switching feed mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                val displayedCells = remember(cells) { cells.take(30) }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cols = when {
                        maxWidth >= 680.dp -> 5
                        maxWidth >= 480.dp -> 4
                        else -> 3
                    }
                    val rowsChunked = remember(displayedCells, cols) { displayedCells.chunked(cols) }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowsChunked.forEach { rowCells ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowCells.forEach { cell ->
                                    HeatmapCellTile(
                                        cell = cell,
                                        selected = selectedCell?.symbol == cell.symbol,
                                        onClick = {
                                            selectedCell = if (selectedCell?.symbol == cell.symbol) null else cell
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(cols - rowCells.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Tap-to-Inspect Detail Strip
            AnimatedVisibility(visible = selectedCell != null) {
                selectedCell?.let { sel ->
                    val signColor = when {
                        sel.changePct >= 0.15 -> desk.bullEmerald
                        sel.changePct <= -0.15 -> desk.stopCrimson
                        else -> desk.amberSaffron
                    }
                    val signBg = when {
                        sel.changePct >= 0.15 -> desk.bullContainer
                        sel.changePct <= -0.15 -> desk.stopContainer
                        else -> desk.amberContainer
                    }
                    Surface(
                        color = desk.cardElevated,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, signColor.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("heatmap_selected_detail")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
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
                                        text = "${sel.exchange}:${sel.symbol}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Surface(color = signBg, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = "${if (sel.changePct >= 0) "+" else ""}${"%.2f".format(sel.changePct)}%",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = signColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = IndianMarketUtils.inr(sel.lastPrice),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Volatility", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${"%.2f".format(sel.volatilityPct)}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = desk.amberSaffron
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Rel Volume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${"%.2f".format(sel.relativeVolumeX)}x",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = desk.dataSky
                                    )
                                }
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text("Opening Range", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (sel.orHigh != null && sel.orLow != null) {
                                            "${IndianMarketUtils.inr(sel.orLow)}–${IndianMarketUtils.inr(sel.orHigh)}"
                                        } else {
                                            "Daily Swing"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onTradeSymbol(sel.symbol) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = desk.bullEmerald,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("heatmap_trade_orb_${sel.symbol}")
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Trade ORB", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { onInspectIntel(sel.symbol) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("heatmap_intel_${sel.symbol}")
                                ) {
                                    Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Intel", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCellTile(
    cell: MarketHeatmapCell,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    val baseColor = desk.cardElevated
    val accentColor = when {
        cell.changePct >= 0.15 -> desk.bullEmerald
        cell.changePct <= -0.15 -> desk.stopCrimson
        else -> desk.amberSaffron
    }
    val fillAlpha = (0.14f + cell.intensity * 0.62f).coerceIn(0.14f, 0.78f)
    val startTile = lerp(baseColor, accentColor, fillAlpha * 0.72f)
    val endTile = lerp(baseColor, accentColor, fillAlpha)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        accentColor.copy(alpha = (0.28f + cell.intensity * 0.52f).coerceIn(0.28f, 0.85f))
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .defaultMinSize(minHeight = 76.dp)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(listOf(startTile, endTile))
                )
            }
            .clickable(onClick = onClick)
            .semantics {
                contentDescription =
                    "${cell.symbol} ${cell.changePct} percent, volatility ${cell.volatilityPct} percent, relative volume ${cell.relativeVolumeX}x"
            }
            .testTag("heatmap_tile_${cell.symbol}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cell.symbol,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "${if (cell.changePct >= 0) "+" else ""}${"%.2f".format(cell.changePct)}%",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Text(
                text = "${"%.1f".format(cell.volatilityPct)}% · ${"%.1f".format(cell.relativeVolumeX)}x",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Bottom micro-intensity indicator drawn on Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                drawRoundRect(
                    color = baseColor.copy(alpha = 0.55f),
                    cornerRadius = CornerRadius(4f, 4f),
                    size = size
                )
                drawRoundRect(
                    color = accentColor,
                    cornerRadius = CornerRadius(4f, 4f),
                    size = Size(width = size.width * cell.intensity.coerceIn(0.18f, 1f), height = size.height)
                )
            }
        }
    }
}
