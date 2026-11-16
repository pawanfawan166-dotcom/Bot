package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.IndianMarketUtils
import com.example.model.DataMode
import com.example.model.OrbBreakoutState
import com.example.model.OrbQuickRow
import com.example.ui.theme.LocalDeskColors

/**
 * Live NSE/BSE Top-5 Relative Volume ORB (Opening Range Breakout) quick-view scanner component.
 */
@Composable
fun LiveOrbTop5Card(
    exchange: String,
    orMinutes: Int,
    dataMode: DataMode,
    rows: List<OrbQuickRow>,
    isLoading: Boolean,
    onSelectExchange: (String) -> Unit,
    onSelectOrMinutes: (Int) -> Unit,
    onRefresh: () -> Unit,
    onTradeSymbol: (String) -> Unit,
    onRunAgentForSymbol: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    val maxRelVol = rows.maxOfOrNull { it.relativeVolumeX }?.coerceAtLeast(1.0) ?: 1.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_orb_top5_card"),
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
            // Header + Data feed indicator + Refresh
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
                            .background(desk.bullContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = desk.bullEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.orb_top5_title),
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
                            text = "${stringResource(id = R.string.orb_top5_subtitle)} • ${orMinutes}m ORB",
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
                    modifier = Modifier.testTag("orb_top5_refresh_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Live ORB Top 5"
                        )
                    }
                }
            }

            // Exchange (NSE / BSE) + OR Window (15m / 30m) Switch Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NSE", "BSE").forEach { ex ->
                        FilterChip(
                            selected = exchange.uppercase() == ex,
                            onClick = { onSelectExchange(ex) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("orb_top5_exchange_${ex.lowercase()}"),
                            label = {
                                Text(
                                    text = ex,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5, 10, 15, 30).forEach { mins ->
                        FilterChip(
                            selected = orMinutes == mins,
                            onClick = { onSelectOrMinutes(mins) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("orb_top5_or_$mins"),
                            label = {
                                Text(
                                    text = "${mins}m",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // Ranked Top 5 Table / Rows
            if (isLoading && rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else if (rows.isEmpty()) {
                Surface(
                    color = desk.cardElevated,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, desk.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No intraday $exchange 5m bars ready for ORB relative-volume ranking yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        OrbQuickRowCard(
                            row = row,
                            maxRelVol = maxRelVol,
                            onTrade = { onTradeSymbol(row.symbol) },
                            onRunAgent = { onRunAgentForSymbol(row.symbol) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbQuickRowCard(
    row: OrbQuickRow,
    maxRelVol: Double,
    onTrade: () -> Unit,
    onRunAgent: () -> Unit
) {
    val desk = LocalDeskColors.current
    val posChg = row.changePct >= 0.0
    val (stateBg, stateFg) = when (row.breakoutState) {
        OrbBreakoutState.BROKEN_ORH -> desk.bullContainer to desk.bullEmerald
        OrbBreakoutState.AT_HIGH -> desk.amberContainer to desk.amberSaffron
        OrbBreakoutState.INSIDE_OR -> MaterialTheme.colorScheme.tertiaryContainer to desk.dataSky
        OrbBreakoutState.BELOW_ORL -> desk.stopContainer to desk.stopCrimson
    }
    val volFraction = (row.relativeVolumeX / maxRelVol).toFloat().coerceIn(0.08f, 1f)

    Surface(
        color = desk.cardElevated,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = 1.dp,
            color = if (row.breakoutState == OrbBreakoutState.BROKEN_ORH) {
                desk.bullEmerald.copy(alpha = 0.45f)
            } else {
                desk.borderSubtle
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onTrade)
            .testTag("orb_top5_row_${row.rank}_${row.symbol}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Top Line: Rank + Symbol + Exchange + State Badge + LTP/Chg
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${row.rank}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = desk.dataSky,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = row.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, desk.borderSubtle)
                    ) {
                        Text(
                            text = row.exchange,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Surface(
                        color = stateBg,
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            text = row.breakoutState.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = stateFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (row.nearUpperCircuit) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Near upper circuit",
                            tint = desk.stopCrimson,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = IndianMarketUtils.inr(row.lastPrice),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${if (posChg) "+" else ""}${"%.2f".format(row.changePct)}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (posChg) desk.bullEmerald else desk.stopCrimson
                    )
                }
            }

            // Relative Volume Bar + ORH/ORL Levels
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rel Vol ${"%.2f".format(row.relativeVolumeX)}x",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (row.relativeVolumeX >= 1.5) desk.bullEmerald else desk.amberSaffron
                        )
                        Text(
                            text = "OR ${IndianMarketUtils.inr(row.orLow)} – ${IndianMarketUtils.inr(row.orHigh)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LinearProgressIndicator(
                        progress = { volFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = if (row.breakoutState == OrbBreakoutState.BROKEN_ORH) desk.bullEmerald else desk.dataSky,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            // Bottom Line: Summary + Quick Action Buttons (min 48dp accessible tap height via button defaults / minHeight)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onRunAgent,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("orb_top5_agent_${row.symbol}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Agent", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onTrade,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = desk.bullEmerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("orb_top5_trade_${row.symbol}")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("ORB", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
