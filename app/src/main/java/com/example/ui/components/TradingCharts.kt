package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.OhlcvBar
import com.example.model.TimeMachineResult
import com.example.ui.theme.LocalDeskColors

@Composable
fun IntradayBarsChart(
    bars: List<OhlcvBar>,
    orHigh: Double?,
    orLow: Double?,
    entryPrice: Double?,
    entryBarSlot: Int?,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    val gridColor = desk.borderSubtle
    val bullColor = desk.bullEmerald
    val stopColor = desk.stopCrimson
    val amberColor = desk.amberSaffron
    val skyColor = desk.dataSky

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        if (bars.isEmpty()) return@Canvas
        val minLow = listOfNotNull(
            bars.minOf { it.low },
            orLow,
            entryPrice
        ).min()
        val maxHigh = listOfNotNull(
            bars.maxOf { it.high },
            orHigh,
            entryPrice
        ).max()
        val pad = ((maxHigh - minLow) * 0.08).coerceAtLeast(0.5)
        val lo = minLow - pad
        val hi = maxHigh + pad
        val span = (hi - lo).coerceAtLeast(1e-4)

        fun yFor(price: Double): Float =
            size.height - ((price - lo) / span).toFloat() * size.height

        // Subtle horizontal grid lines
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        // Shaded Opening Range band
        if (orHigh != null && orLow != null) {
            val topY = yFor(orHigh)
            val botY = yFor(orLow)
            drawRect(
                color = amberColor.copy(alpha = 0.10f),
                topLeft = Offset(0f, topY),
                size = Size(size.width, (botY - topY).coerceAtLeast(2f))
            )
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            drawLine(amberColor, Offset(0f, topY), Offset(size.width, topY), strokeWidth = 2f, pathEffect = dash)
            drawLine(amberColor.copy(alpha = 0.6f), Offset(0f, botY), Offset(size.width, botY), strokeWidth = 1.5f, pathEffect = dash)
        }

        val stepX = size.width / bars.size.coerceAtLeast(1)
        val candleW = (stepX * 0.56f).coerceIn(2f, 12f)
        bars.forEachIndexed { idx, b ->
            val cx = idx * stepX + stepX * 0.5f
            val isBull = b.close >= b.open
            val cColor = if (isBull) bullColor else stopColor
            val yHigh = yFor(b.high)
            val yLow = yFor(b.low)
            val yOpen = yFor(b.open)
            val yClose = yFor(b.close)
            drawLine(cColor, Offset(cx, yHigh), Offset(cx, yLow), strokeWidth = 1.6f)
            val top = minOf(yOpen, yClose)
            val bot = maxOf(yOpen, yClose)
            drawRect(
                color = cColor,
                topLeft = Offset(cx - candleW / 2f, top),
                size = Size(candleW, (bot - top).coerceAtLeast(2f))
            )
            if (entryBarSlot != null && b.slot == entryBarSlot) {
                drawCircle(
                    color = skyColor,
                    radius = 6.dp.toPx(),
                    center = Offset(cx, yClose)
                )
            }
        }
    }
}

@Composable
fun MonteCarloFanChart(
    result: TimeMachineResult,
    entryPrice: Double,
    stopPrice: Double,
    targetPrice: Double,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    val bullColor = desk.bullEmerald
    val stopColor = desk.stopCrimson
    val skyColor = desk.dataSky
    val gridColor = desk.borderSubtle

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
    ) {
        val bands = result.bands
        if (bands.size < 2) return@Canvas

        val allPrices = bands.flatMap { listOf(it.p5, it.p95) } + listOf(entryPrice, stopPrice, targetPrice)
        val minP = allPrices.min()
        val maxP = allPrices.max()
        val pad = ((maxP - minP) * 0.06).coerceAtLeast(0.5)
        val lo = minP - pad
        val hi = maxP + pad
        val span = (hi - lo).coerceAtLeast(1e-4)

        fun yFor(p: Double): Float = size.height - ((p - lo) / span).toFloat() * size.height
        fun xFor(idx: Int): Float = idx.toFloat() / (bands.size - 1) * size.width

        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        // P5-P95 outer band fill
        val outerPath = Path().apply {
            bands.forEachIndexed { idx, b ->
                val x = xFor(idx)
                val y = yFor(b.p95)
                if (idx == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (idx in bands.indices.reversed()) {
                lineTo(xFor(idx), yFor(bands[idx].p5))
            }
            close()
        }
        drawPath(outerPath, color = skyColor.copy(alpha = 0.14f))

        // P25-P75 inner band fill
        val innerPath = Path().apply {
            bands.forEachIndexed { idx, b ->
                val x = xFor(idx)
                val y = yFor(b.p75)
                if (idx == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (idx in bands.indices.reversed()) {
                lineTo(xFor(idx), yFor(bands[idx].p25))
            }
            close()
        }
        drawPath(innerPath, color = skyColor.copy(alpha = 0.25f))

        // Sample paths (faint)
        result.samplePaths.take(18).forEach { pathPts ->
            if (pathPts.size >= 2) {
                val p = Path()
                pathPts.forEachIndexed { idx, v ->
                    val x = idx.toFloat() / (pathPts.size - 1) * size.width
                    val y = yFor(v)
                    if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                drawPath(p, color = Color.White.copy(alpha = 0.10f), style = Stroke(width = 1.2f))
            }
        }

        // P50 Median line
        val medianPath = Path().apply {
            bands.forEachIndexed { idx, b ->
                val x = xFor(idx)
                val y = yFor(b.p50)
                if (idx == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(medianPath, color = skyColor, style = Stroke(width = 3f))

        // Target, Entry, Stop horizontal levels
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        drawLine(bullColor, Offset(0f, yFor(targetPrice)), Offset(size.width, yFor(targetPrice)), strokeWidth = 2.5f, pathEffect = dash)
        drawLine(stopColor, Offset(0f, yFor(stopPrice)), Offset(size.width, yFor(stopPrice)), strokeWidth = 2.5f, pathEffect = dash)
        drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, yFor(entryPrice)), Offset(size.width, yFor(entryPrice)), strokeWidth = 1.5f)
    }
}

@Composable
fun CloseHistogramChart(
    result: TimeMachineResult,
    entryPrice: Double,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    val bullColor = desk.bullEmerald
    val stopColor = desk.stopCrimson

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val bins = result.hist.bins
        if (bins.isEmpty()) return@Canvas
        val maxCount = (bins.maxOrNull() ?: 1).coerceAtLeast(1)
        val lo = result.hist.lo
        val hi = result.hist.hi
        val binSpan = (hi - lo) / bins.size
        val barW = size.width / bins.size

        bins.forEachIndexed { idx, count ->
            val h = (count.toFloat() / maxCount) * (size.height - 8f)
            val midPrice = lo + (idx + 0.5) * binSpan
            val color = if (midPrice >= entryPrice) bullColor.copy(alpha = 0.85f) else stopColor.copy(alpha = 0.85f)
            drawRect(
                color = color,
                topLeft = Offset(idx * barW + 1.5f, size.height - h),
                size = Size((barW - 3f).coerceAtLeast(2f), h)
            )
        }
    }
}
