package com.example.domain

import com.example.model.OhlcvBar
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

data class YahooChartSeries(
    val symbol: String,
    val prevClose: Double?,
    val regularMarketPrice: Double?,
    val regularMarketTimeEpoch: Long?,
    val granularity: String,
    val bars: List<OhlcvBar>
)

object YahooParser {

    fun parseSparkSeries(jsonText: String): Map<String, YahooChartSeries> {
        val root = JSONObject(jsonText)
        val out = linkedMapOf<String, YahooChartSeries>()
        val spark = root.optJSONObject("spark")
        if (spark != null) {
            val results = spark.optJSONArray("result") ?: JSONArray()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val respArr = item.optJSONArray("response") ?: continue
                val resp = respArr.optJSONObject(0) ?: continue
                val parsed = parseChartObject(resp) ?: continue
                val cleanSym = parsed.symbol.removeSuffix(".NS").removeSuffix(".BO")
                out[cleanSym] = parsed
            }
            return out
        }
        // Handle bundled map format: { "RELIANCE": { "chart": { "result": [...] } }, ... }
        val keys = root.keys()
        while (keys.hasNext()) {
            val symKey = keys.next()
            val entryObj = root.optJSONObject(symKey) ?: continue
            val chart = entryObj.optJSONObject("chart")
            val parsed = if (chart != null) {
                val err = chart.opt("error")
                if (err != null && err != JSONObject.NULL) null
                else chart.optJSONArray("result")?.optJSONObject(0)?.let { parseChartObject(it) }
            } else {
                parseChartObject(entryObj)
            }
            if (parsed != null) {
                val cleanSym = parsed.symbol.ifBlank { symKey }.removeSuffix(".NS").removeSuffix(".BO")
                out[cleanSym] = parsed
            }
        }
        return out
    }

    fun parseSingleChart(jsonText: String): YahooChartSeries? {
        val root = JSONObject(jsonText)
        val chart = root.optJSONObject("chart") ?: return null
        val err = chart.opt("error")
        if (err != null && err != JSONObject.NULL) return null
        val results = chart.optJSONArray("result") ?: return null
        val first = results.optJSONObject(0) ?: return null
        return parseChartObject(first)
    }

    private fun parseChartObject(obj: JSONObject): YahooChartSeries? {
        val meta = obj.optJSONObject("meta") ?: return null
        val symbol = meta.optString("symbol", "").uppercase()
        val prevClose = meta.optDouble("chartPreviousClose")
            .takeIf { !it.isNaN() }
            ?: meta.optDouble("previousClose").takeIf { !it.isNaN() }
        val regPrice = meta.optDouble("regularMarketPrice").takeIf { !it.isNaN() }
        val regTime = meta.optLong("regularMarketTime", 0L).takeIf { it > 0L }
        val gran = meta.optString("dataGranularity", "5m")

        val tsArr = obj.optJSONArray("timestamp") ?: JSONArray()
        val indicators = obj.optJSONObject("indicators")
        val quoteArr = indicators?.optJSONArray("quote")
        val quote = quoteArr?.optJSONObject(0) ?: JSONObject()

        val opens = quote.optJSONArray("open")
        val highs = quote.optJSONArray("high")
        val lows = quote.optJSONArray("low")
        val closes = quote.optJSONArray("close")
        val vols = quote.optJSONArray("volume")

        val bars = mutableListOf<OhlcvBar>()
        for (i in 0 until tsArr.length()) {
            val ts = tsArr.optLong(i, -1L)
            if (ts <= 0L) continue
            val c = closes?.optDouble(i) ?: Double.NaN
            if (c.isNaN() || c <= 0.0) continue
            val o = opens?.optDouble(i)?.takeIf { !it.isNaN() && it > 0 } ?: c
            val h = highs?.optDouble(i)?.takeIf { !it.isNaN() && it > 0 } ?: maxOf(o, c)
            val l = lows?.optDouble(i)?.takeIf { !it.isNaN() && it > 0 } ?: minOf(o, c)
            val v = vols?.optLong(i, 0L) ?: 0L

            val ist = IndianMarketUtils.epochToIst(ts)
            val mins = ist.hour * 60 + ist.minute - IndianMarketUtils.OPEN_MIN
            val slot = if (gran == "1m") mins else mins / 5
            bars.add(
                OhlcvBar(
                    epochSec = ts,
                    date = ist.toLocalDate().toString(),
                    timeHm = "%02d:%02d".format(ist.hour, ist.minute),
                    slot = slot,
                    open = o,
                    high = h,
                    low = l,
                    close = c,
                    volume = v
                )
            )
        }
        return YahooChartSeries(
            symbol = symbol,
            prevClose = prevClose,
            regularMarketPrice = regPrice,
            regularMarketTimeEpoch = regTime,
            granularity = gran,
            bars = bars
        )
    }

    fun filterCompletedBars(
        bars: List<OhlcvBar>,
        intervalSec: Int,
        asOfIst: LocalDateTime
    ): List<OhlcvBar> {
        val asOfEpoch = IndianMarketUtils.istToEpoch(asOfIst)
        val maxBars = if (intervalSec == 60) 375 else IndianMarketUtils.SESSION_BARS
        val byEpoch = linkedMapOf<Long, OhlcvBar>()
        for (b in bars) {
            if (b.epochSec % intervalSec != 0L) continue
            if (b.slot < 0 || b.slot >= maxBars) continue
            if (b.epochSec + intervalSec > asOfEpoch) continue
            byEpoch[b.epochSec] = b
        }
        return byEpoch.entries.sortedBy { it.key }.map { it.value }
    }
}
