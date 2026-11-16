package com.example.data

import android.content.Context
import com.example.domain.IndianMarketUtils
import com.example.domain.ScannerAndEntryEngine
import com.example.domain.YahooChartSeries
import com.example.domain.YahooParser
import com.example.model.UniverseSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class MarketDataSource(private val context: Context) {

    val appContext: Context get() = context

    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val merged = (cookieStore[host].orEmpty().associateBy { it.name } + cookies.associateBy { it.name }).values.toList()
                cookieStore[host] = merged
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val now = System.currentTimeMillis()
                return cookieStore.entries
                    .filter { url.host.endsWith(it.key.removePrefix("www.")) || it.key == url.host }
                    .flatMap { it.value }
                    .filter { it.expiresAt >= now }
            }
        })
        .build()

    @Volatile
    private var nseWarmed = false

    private val browserUa = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    private val yahooUa = "Mozilla/5.0 (compatible; TradeDeskIN/1.0)"

    // ---------------------------------------------------------------- Bundled Assets

    suspend fun loadFoSymbols(): Set<String> = withContext(Dispatchers.IO) {
        val text = readAssetText("fo_list.txt") ?: return@withContext emptySet()
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.uppercase() }
            .toSet()
    }

    suspend fun loadSecList(live: Boolean): Map<String, UniverseSecurity> = withContext(Dispatchers.IO) {
        if (live) {
            val csv = fetchText("https://nsearchives.nseindia.com/content/equities/sec_list.csv", browserUa)
            if (!csv.isNullOrBlank()) {
                val parsed = ScannerAndEntryEngine.parseSecListCsv(csv)
                if (parsed.isNotEmpty()) return@withContext parsed
            }
        }
        val offline = readAssetText("fixtures/india-scanner/nse/sec_list.csv") ?: ""
        ScannerAndEntryEngine.parseSecListCsv(offline)
    }

    suspend fun loadNifty50Symbols(live: Boolean): List<String> = withContext(Dispatchers.IO) {
        if (live) {
            val csv = fetchText("https://nsearchives.nseindia.com/content/indices/ind_nifty50list.csv", browserUa)
            if (!csv.isNullOrBlank()) {
                val parsed = ScannerAndEntryEngine.parseIndexCsv(csv)
                if (parsed.isNotEmpty()) return@withContext parsed
            }
        }
        val offline = readAssetText("fixtures/india-scanner/nse/ind_nifty50list.csv") ?: ""
        ScannerAndEntryEngine.parseIndexCsv(offline)
    }

    suspend fun loadHolidays(live: Boolean): Map<String, String> = withContext(Dispatchers.IO) {
        val merged = IndianMarketUtils.NSE_HOLIDAYS_2026.toMutableMap()
        val raw = if (live) {
            fetchNseJson("https://www.nseindia.com/api/holiday-master?type=trading")
        } else {
            readAssetText("fixtures/india-scanner/holiday-master-2026-10-23.json")
        }
        if (!raw.isNullOrBlank()) {
            runCatching {
                val doc = JSONObject(raw)
                val cm = doc.optJSONArray("CM")
                if (cm != null) {
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH)
                    for (i in 0 until cm.length()) {
                        val item = cm.optJSONObject(i) ?: continue
                        val dStr = item.optString("tradingDate")
                        val desc = item.optString("description", "NSE trading holiday")
                        runCatching {
                            val d = java.time.LocalDate.parse(dStr, fmt).toString()
                            merged[d] = desc
                        }
                    }
                }
            }
        }
        merged
    }

    // ---------------------------------------------------------------- Scanner & Chart Feeds

    suspend fun loadDailySpark(symbols: List<String>, live: Boolean): Map<String, YahooChartSeries> = withContext(Dispatchers.IO) {
        if (live && symbols.isNotEmpty()) {
            val out = linkedMapOf<String, YahooChartSeries>()
            for (chunk in symbols.chunked(20)) {
                val ySyms = chunk.joinToString(",") { IndianMarketUtils.yahooSymbol(it, "NSE") }
                val url = "https://query1.finance.yahoo.com/v7/finance/spark?symbols=${URLEncoder.encode(ySyms, "UTF-8")}&range=3mo&interval=1d"
                val json = fetchText(url, yahooUa)
                if (!json.isNullOrBlank()) {
                    runCatching { out.putAll(YahooParser.parseSparkSeries(json)) }
                }
            }
            if (out.isNotEmpty()) return@withContext out
        }
        val fixture = readAssetText("fixtures/india-scanner/daily-spark-2026-10-23.json") ?: return@withContext emptyMap()
        YahooParser.parseSparkSeries(fixture)
    }

    suspend fun loadIntradaySpark(
        symbols: List<String>,
        live: Boolean,
        exchange: String = "NSE"
    ): Map<String, YahooChartSeries> = withContext(Dispatchers.IO) {
        if (live && symbols.isNotEmpty()) {
            val out = linkedMapOf<String, YahooChartSeries>()
            for (chunk in symbols.chunked(20)) {
                val ySyms = chunk.joinToString(",") { IndianMarketUtils.yahooSymbol(it, exchange) }
                val url = "https://query1.finance.yahoo.com/v7/finance/spark?symbols=${URLEncoder.encode(ySyms, "UTF-8")}&range=1d&interval=5m"
                val json = fetchText(url, yahooUa)
                if (!json.isNullOrBlank()) {
                    runCatching { out.putAll(YahooParser.parseSparkSeries(json)) }
                }
            }
            if (out.isNotEmpty()) return@withContext out
        }
        val gzText = readAssetGzipText("fixtures/india-scanner/nifty50-5m-2026-10-19.json.gz")
        if (!gzText.isNullOrBlank()) {
            val parsed = YahooParser.parseSparkSeries(gzText)
            if (parsed.isNotEmpty()) return@withContext parsed
        }
        val liveSnap = readAssetText("fixtures/india-scanner/spark-5m-live-2026-10-23-1129.json") ?: return@withContext emptyMap()
        YahooParser.parseSparkSeries(liveSnap)
    }

    suspend fun loadSymbolIntradayChart(
        symbol: String,
        exchange: String,
        interval: String = "5m",
        range: String = "5d",
        live: Boolean
    ): YahooChartSeries? = withContext(Dispatchers.IO) {
        val ySym = IndianMarketUtils.yahooSymbol(symbol, exchange)
        if (live) {
            val hosts = listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")
            for (host in hosts) {
                val url = "https://$host/v8/finance/chart/${URLEncoder.encode(ySym, "UTF-8")}?range=$range&interval=$interval"
                val json = fetchText(url, yahooUa)
                if (!json.isNullOrBlank()) {
                    val parsed = YahooParser.parseSingleChart(json)
                    if (parsed != null && parsed.bars.isNotEmpty()) return@withContext parsed
                }
            }
        }
        // Offline replay fixtures
        if (symbol == "RELIANCE" && interval == "5m") {
            if (range == "60d") {
                val gz = readAssetGzipText("fixtures/shared/RELIANCE-60d5m.json.gz")
                if (!gz.isNullOrBlank()) return@withContext YahooParser.parseSingleChart(gz)
            }
            val gzSpark = readAssetGzipText("fixtures/india-scanner/nifty50-5m-2026-10-19.json.gz")
            if (!gzSpark.isNullOrBlank()) {
                YahooParser.parseSparkSeries(gzSpark)["RELIANCE"]?.let { return@withContext it }
            }
            readAssetText("fixtures/india-scanner/RELIANCE-5m-live-2026-10-23-1129.json")
                ?.let { return@withContext YahooParser.parseSingleChart(it) }
        }
        if (symbol == "DEMO" && interval == "1m") {
            readAssetText("fixtures/india-entry/DEMO-1m-synthetic.json")
                ?.let { return@withContext YahooParser.parseSingleChart(it) }
        }
        // Check nifty50-5m-2026-10-19.json.gz for any Nifty50 symbol
        val gzNifty = readAssetGzipText("fixtures/india-scanner/nifty50-5m-2026-10-19.json.gz")
        if (!gzNifty.isNullOrBlank()) {
            YahooParser.parseSparkSeries(gzNifty)[symbol]?.let { return@withContext it }
        }
        // Fallback to alerts quote fixture if available
        readAssetText("fixtures/india-alerts/yahoo-$ySym.json")?.let {
            return@withContext YahooParser.parseSingleChart(it)
        }
        readAssetText("fixtures/india-news/chart-$symbol.json")?.let {
            return@withContext YahooParser.parseSingleChart(it)
        }
        // If 60d5m requested for another symbol offline, fall back to RELIANCE 60d5m pool only if explicitly RELIANCE
        null
    }

    suspend fun load60d5mChart(symbol: String, exchange: String, live: Boolean): YahooChartSeries? =
        loadSymbolIntradayChart(symbol, exchange, interval = "5m", range = "60d", live = live)

    suspend fun loadQuotePair(symbol: String, exchange: String, live: Boolean): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val chart = loadSymbolIntradayChart(symbol, exchange, interval = "5m", range = "1d", live = live)
        if (chart != null) {
            val last = chart.regularMarketPrice ?: chart.bars.lastOrNull()?.close
            val prev = chart.prevClose ?: chart.bars.firstOrNull()?.open
            if (last != null && prev != null) return@withContext last to prev
        }
        val spark = loadDailySpark(listOf(symbol), live)[symbol]
        if (spark != null && spark.bars.size >= 2) {
            val last = spark.regularMarketPrice ?: spark.bars.last().close
            val prev = spark.bars[spark.bars.size - 2].close
            return@withContext last to prev
        }
        null
    }

    // ---------------------------------------------------------------- News, Analyst, Insiders Feeds

    suspend fun loadNewsRaw(symbol: String, live: Boolean): Triple<String?, String?, String?> = withContext(Dispatchers.IO) {
        if (live) {
            val annUrl = "https://www.nseindia.com/api/corporate-announcements?index=equities&symbol=${URLEncoder.encode(symbol, "UTF-8")}"
            val ann = fetchNseJson(annUrl)
            val rss = fetchText("https://nsearchives.nseindia.com/content/RSS/Online_announcements.xml", browserUa)
            val gUrl = "https://news.google.com/rss/search?q=${URLEncoder.encode("$symbol NSE stock", "UTF-8")}&hl=en-IN&gl=IN&ceid=IN:en"
            val gnews = fetchText(gUrl, browserUa)
            if (ann != null || gnews != null) return@withContext Triple(ann, rss, gnews)
        }
        val ann = readAssetText("fixtures/india-news/nse-ann-$symbol.json")
            ?: readAssetText("fixtures/india-analyst/ann-$symbol.json")
        val rss = readAssetText("fixtures/india-news/nse-rss.xml")
        val gnews = readAssetText("fixtures/india-news/gnews-$symbol.xml")
        Triple(ann, rss, gnews)
    }

    data class AnalystRawBundle(
        val integratedJson: String?,
        val legacyJson: String?,
        val eventsJson: String?,
        val fetchXbrl: suspend (String) -> ByteArray?
    )

    suspend fun loadAnalystRaw(symbol: String, live: Boolean): AnalystRawBundle = withContext(Dispatchers.IO) {
        if (live) {
            val enc = URLEncoder.encode(symbol, "UTF-8")
            val integ = fetchNseJson("https://www.nseindia.com/api/integrated-filing-results?index=equities&symbol=$enc&period_ended=all&type=Integrated%20Filing-%20Financials")
            val leg = fetchNseJson("https://www.nseindia.com/api/corporates-financial-results?index=equities&symbol=$enc&period=Quarterly")
            val evt = fetchNseJson("https://www.nseindia.com/api/event-calendar?index=equities&symbol=$enc")
            if (integ != null || leg != null) {
                return@withContext AnalystRawBundle(integ, leg, evt) { fileName ->
                    val url = if (fileName.startsWith("http")) fileName else "https://nsearchives.nseindia.com/corporate/xbrl/$fileName"
                    fetchBytes(url, browserUa) ?: readAssetBytes("fixtures/india-analyst/$fileName")
                }
            }
        }
        val integ = readAssetText("fixtures/india-analyst/integrated-$symbol.json")
        val leg = readAssetText("fixtures/india-analyst/results-$symbol.json")
        val evt = readAssetText("fixtures/india-analyst/events-$symbol.json")
        AnalystRawBundle(integ, leg, evt) { fileName ->
            readAssetBytes("fixtures/india-analyst/${fileName.substringAfterLast('/')}")
        }
    }

    data class InsidersRawBundle(
        val pitJson: String?,
        val bulkCsv: String?,
        val blockCsv: String?,
        val histDealsJson: String?
    )

    suspend fun loadInsidersRaw(symbol: String?, live: Boolean): InsidersRawBundle = withContext(Dispatchers.IO) {
        if (live) {
            val pit = if (!symbol.isNullOrBlank()) {
                fetchNseJson("https://www.nseindia.com/api/corporates-pit?index=equities&symbol=${URLEncoder.encode(symbol, "UTF-8")}")
            } else null
            val bulk = fetchText("https://nsearchives.nseindia.com/content/equities/bulk.csv", browserUa)
            val block = fetchText("https://nsearchives.nseindia.com/content/equities/block.csv", browserUa)
            if (pit != null || bulk != null || block != null) {
                return@withContext InsidersRawBundle(pit, bulk, block, null)
            }
        }
        val targetSym = symbol?.uppercase()?.takeIf { it.isNotBlank() } ?: "RELIANCE"
        val pit = readAssetText("fixtures/india-insiders/pit-$targetSym.json")
        val bulk = readAssetText("fixtures/india-insiders/bulk.csv")
        val block = readAssetText("fixtures/india-insiders/block.csv")
        val hist = readAssetText("fixtures/india-insiders/hist-block-2026-10-16_23.json")
        InsidersRawBundle(pit, bulk, block, hist)
    }

    // ---------------------------------------------------------------- Low-level I/O

    private fun readAssetText(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    private fun readAssetBytes(path: String): ByteArray? = runCatching {
        context.assets.open(path).use { it.readBytes() }
    }.getOrNull()

    private fun readAssetGzipText(path: String): String? {
        val candidates = if (path.endsWith(".gz")) listOf(path, path.removeSuffix(".gz")) else listOf(path)
        for (candidate in candidates) {
            val txt = runCatching {
                context.assets.open(candidate).use { raw ->
                    val bytes = raw.readBytes()
                    if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else {
                        bytes.toString(Charsets.UTF_8)
                    }
                }
            }.getOrNull()
            if (!txt.isNullOrBlank()) return txt
        }
        return null
    }

    private fun fetchText(url: String, ua: String): String? =
        fetchBytes(url, ua)?.toString(Charsets.UTF_8)

    private fun fetchBytes(url: String, ua: String): ByteArray? {
        repeat(2) { attempt ->
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", ua)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "gzip")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 429 && attempt == 0) {
                        Thread.sleep(1500)
                        return@repeat
                    }
                    if (!resp.isSuccessful) return null
                    val body = resp.body?.bytes() ?: return null
                    return if (resp.header("Content-Encoding").equals("gzip", ignoreCase = true)) {
                        GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
                    } else {
                        body
                    }
                }
            } catch (_: Exception) {
                if (attempt == 0) Thread.sleep(400)
            }
        }
        return null
    }

    private fun fetchNseJson(url: String): String? {
        if (!nseWarmed) {
            nseWarmed = true
            fetchBytes("https://www.nseindia.com/companies-listing/corporate-filings-insider-trading", browserUa)
        }
        repeat(2) { attempt ->
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", browserUa)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://www.nseindia.com/")
                    .header("Accept-Encoding", "gzip")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code in listOf(401, 403) && attempt == 0) {
                        fetchBytes("https://www.nseindia.com/companies-listing/corporate-filings-insider-trading", browserUa)
                        return@repeat
                    }
                    if (!resp.isSuccessful) return null
                    val raw = resp.body?.bytes() ?: return null
                    val bytes = if (resp.header("Content-Encoding").equals("gzip", ignoreCase = true)) {
                        GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
                    } else {
                        raw
                    }
                    val txt = bytes.toString(Charsets.UTF_8).trim()
                    if (!txt.startsWith("{") && !txt.startsWith("[")) return null
                    return txt
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}
