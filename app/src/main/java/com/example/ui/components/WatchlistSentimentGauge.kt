package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SentimentVerdict
import com.example.model.SymbolSentiment
import com.example.model.WatchlistSentimentReport
import com.example.ui.theme.LocalDeskColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun verdictColors(verdict: SentimentVerdict): Pair<Color, Color> {
    val desk = LocalDeskColors.current
    return when (verdict) {
        SentimentVerdict.BULLISH, SentimentVerdict.MILDLY_BULLISH -> desk.bullContainer to desk.bullEmerald
        SentimentVerdict.BEARISH, SentimentVerdict.MILDLY_BEARISH -> desk.stopContainer to desk.stopCrimson
        SentimentVerdict.NEUTRAL -> desk.amberContainer to desk.amberSaffron
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchlistSentimentGauge(
    report: WatchlistSentimentReport?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onInspectSymbol: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var selectedSymbol by remember(report) { mutableStateOf<SymbolSentiment?>(null) }

    val targetScore = (report?.overallScore ?: 50).toFloat()
    val animatedScore by animateFloatAsState(
        targetValue = targetScore,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sentiment_gauge_needle"
    )
    val verdict = report?.verdict ?: SentimentVerdict.NEUTRAL
    val (verdictBg, verdictFg) = verdictColors(verdict)
    val animatedAccent by animateColorAsState(verdictFg, label = "sentiment_accent")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("watchlist_sentiment_gauge_card"),
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
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Watchlist Sentiment Gauge",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (report != null) {
                            "india-news + india-analyst • ${report.symbols.size} stocks (${report.xbrlCoveredCount} XBRL)"
                        } else {
                            "Scoring news headlines & XBRL quarterly results…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("refresh_sentiment_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Watchlist Sentiment"
                        )
                    }
                }
            }

            // 180-degree Arc Bullish/Bearish Gauge Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp)
                    .semantics {
                        contentDescription = "Watchlist sentiment ${report?.overallScore ?: 50} out of 100, ${verdict.label}"
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                val trackBg = MaterialTheme.colorScheme.surfaceVariant
                val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                val pivotRingColor = MaterialTheme.colorScheme.onSurface

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .testTag("sentiment_gauge_canvas")
                ) {
                    val strokePx = size.minDimension * 0.115f
                    val radius = minOf(size.width * 0.40f, size.height * 0.78f)
                    val cx = size.width / 2f
                    val cy = size.height * 0.85f
                    val topLeft = Offset(cx - radius, cy - radius)
                    val arcSize = Size(radius * 2f, radius * 2f)

                    // Background track
                    drawArc(
                        color = trackBg,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // Tri-zone color arc (Bearish -> Neutral -> Bullish)
                    drawArc(
                        color = desk.stopCrimson.copy(alpha = 0.35f),
                        startAngle = 180f,
                        sweepAngle = 66f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )
                    drawArc(
                        color = desk.amberSaffron.copy(alpha = 0.35f),
                        startAngle = 248f,
                        sweepAngle = 44f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )
                    drawArc(
                        color = desk.bullEmerald.copy(alpha = 0.35f),
                        startAngle = 294f,
                        sweepAngle = 66f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )

                    // Active sweep fill up to animatedScore
                    val activeSweep = (animatedScore.coerceIn(0f, 100f) / 100f) * 180f
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                desk.stopCrimson,
                                desk.amberSaffron,
                                desk.bullEmerald
                            ),
                            startX = cx - radius,
                            endX = cx + radius
                        ),
                        startAngle = 180f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx * 0.72f, cap = StrokeCap.Round)
                    )

                    // Outer ticks (0, 25, 50, 75, 100)
                    val tickInnerR = radius - strokePx * 0.72f
                    val tickOuterR = radius + strokePx * 0.72f
                    for (t in listOf(0, 25, 50, 75, 100)) {
                        val rad = Math.toRadians(180.0 + t * 1.8)
                        val cosA = cos(rad).toFloat()
                        val sinA = sin(rad).toFloat()
                        drawLine(
                            color = tickColor,
                            start = Offset(cx + tickInnerR * cosA, cy + tickInnerR * sinA),
                            end = Offset(cx + tickOuterR * cosA, cy + tickOuterR * sinA),
                            strokeWidth = if (t == 50) 3f else 2f
                        )
                    }

                    // Needle pointer
                    val needleRad = Math.toRadians(180.0 + animatedScore.coerceIn(0f, 100f) * 1.8)
                    val needleLen = radius - strokePx * 0.15f
                    val tip = Offset(
                        cx + needleLen * cos(needleRad).toFloat(),
                        cy + needleLen * sin(needleRad).toFloat()
                    )
                    drawLine(
                        color = pivotRingColor,
                        start = Offset(cx, cy),
                        end = tip,
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = animatedAccent,
                        radius = 10f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = pivotRingColor,
                        radius = 5f,
                        center = Offset(cx, cy)
                    )
                }

                // Bottom edge scale labels + center score badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "BEARISH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = desk.stopCrimson
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${report?.overallScore ?: 50}",
                                style = MaterialTheme.typography.displaySmall,
                                color = animatedAccent,
                                modifier = Modifier.testTag("sentiment_overall_score")
                            )
                            Text(
                                text = "/100",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 5.dp, start = 2.dp)
                            )
                        }
                        Surface(
                            color = verdictBg,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, verdictFg.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (verdict) {
                                        SentimentVerdict.BULLISH, SentimentVerdict.MILDLY_BULLISH -> Icons.AutoMirrored.Filled.TrendingUp
                                        SentimentVerdict.BEARISH, SentimentVerdict.MILDLY_BEARISH -> Icons.AutoMirrored.Filled.TrendingDown
                                        SentimentVerdict.NEUTRAL -> Icons.Default.Remove
                                    },
                                    contentDescription = null,
                                    tint = verdictFg,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = verdict.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = verdictFg,
                                    modifier = Modifier.testTag("sentiment_verdict_label")
                                )
                            }
                        }
                    }

                    Text(
                        text = "BULLISH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = desk.bullEmerald
                    )
                }
            }

            // Skill Sub-Scores: india-news & india-analyst + Bull/Bear Breadth
            report?.let { rep ->
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
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Newspaper,
                                contentDescription = null,
                                tint = desk.dataSky,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text("india-news", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${rep.newsAvgScore}/100",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = desk.dataSky
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        color = desk.cardElevated,
                        border = BorderStroke(1.dp, desk.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.QueryStats,
                                contentDescription = null,
                                tint = desk.amberSaffron,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text("india-analyst", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = rep.analystAvgScore?.let { "$it/100" } ?: "N/A",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = desk.amberSaffron
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        color = desk.cardElevated,
                        border = BorderStroke(1.dp, desk.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Breadth", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${rep.bullishCount}B · ${rep.neutralCount}N · ${rep.bearishCount}S",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Per-Symbol Watchlist Breakdown Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rep.symbols.forEach { item ->
                        val (chipBg, chipFg) = verdictColors(item.verdict)
                        val isSelected = selectedSymbol?.symbol == item.symbol
                        Surface(
                            color = chipBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else chipFg.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedSymbol = if (isSelected) null else item
                                }
                                .testTag("sentiment_symbol_chip_${item.symbol}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.symbol,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${item.score}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = chipFg
                                )
                                if (item.analystScore != null) {
                                    Text(
                                        text = "XBRL",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = desk.amberSaffron
                                    )
                                }
                            }
                        }
                    }
                }

                // Tap-to-inspect symbol detail strip
                selectedSymbol?.let { sel ->
                    val (selBg, selFg) = verdictColors(sel.verdict)
                    Surface(
                        color = selBg.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, selFg.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onInspectSymbol(sel.symbol) }
                            .testTag("sentiment_selected_detail")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${sel.symbol} • ${sel.verdict.label} (${sel.score}/100)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = selFg
                                )
                                Text(
                                    text = "Open Intel →",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "News (${sel.newsScore}): ${sel.newsSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            sel.analystSummary?.let { aSum ->
                                Text(
                                    text = "XBRL (${sel.analystScore}): $aSum",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
