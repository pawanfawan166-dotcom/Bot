package com.example.data.broker

import com.example.domain.IndianMarketUtils
import com.example.model.BrokerCredentials
import com.example.model.BrokerExecutionResult
import com.example.model.BrokerFundsSnapshot
import com.example.model.BrokerOrderRequest
import com.example.model.BrokerRemoteOrder
import com.example.model.IndianBrokerId
import com.example.model.PortfolioPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Common broker adapter interface so Buy, Sell, Get Positions, Get Orders, and Get Funds
 * behave uniformly regardless of which Indian broker is selected in Real trading mode.
 */
interface IndianBrokerAdapter {
    val brokerId: IndianBrokerId

    fun buildPreviewPayload(request: BrokerOrderRequest): String

    suspend fun placeOrder(
        credentials: BrokerCredentials,
        request: BrokerOrderRequest
    ): Result<BrokerExecutionResult>

    suspend fun getPositions(credentials: BrokerCredentials): Result<List<PortfolioPosition>>

    suspend fun getOrders(credentials: BrokerCredentials): Result<List<BrokerRemoteOrder>>

    suspend fun getFunds(credentials: BrokerCredentials): Result<BrokerFundsSnapshot>
}

class BrokerAdapterRegistry(
    private val httpClient: OkHttpClient = defaultBrokerHttpClient()
) {
    private val adapters: Map<IndianBrokerId, IndianBrokerAdapter> = mapOf(
        IndianBrokerId.ZERODHA to ZerodhaKiteAdapter(httpClient),
        IndianBrokerId.UPSTOX to UpstoxAdapter(httpClient),
        IndianBrokerId.ANGEL_ONE to AngelOneSmartApiAdapter(httpClient),
        IndianBrokerId.FYERS to FyersAdapter(httpClient),
        IndianBrokerId.DHAN to DhanAdapter(httpClient),
        IndianBrokerId.GENERIC to GenericCustomBrokerAdapter(httpClient)
    )

    fun adapterFor(brokerId: IndianBrokerId): IndianBrokerAdapter =
        adapters[brokerId] ?: adapters.getValue(IndianBrokerId.ZERODHA)

    companion object {
        fun defaultBrokerHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

private fun nowIstClock(): String {
    val z = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
    return "%02d:%02d:%02d".format(z.hour, z.minute, z.second)
}

private fun effectiveToken(credentials: BrokerCredentials): String =
    credentials.accessToken.trim().ifBlank { credentials.requestToken.trim() }

private fun validateCredentials(credentials: BrokerCredentials): String? {
    val tok = effectiveToken(credentials)
    return when (credentials.brokerId) {
        IndianBrokerId.ZERODHA -> when {
            credentials.apiKey.isBlank() -> "Zerodha Kite requires an API Key."
            tok.isBlank() -> "Zerodha Kite requires an Access Token (or Request Token)."
            else -> null
        }
        IndianBrokerId.UPSTOX -> if (tok.isBlank()) "Upstox requires an Access Token." else null
        IndianBrokerId.ANGEL_ONE -> when {
            credentials.apiKey.isBlank() -> "Angel One SmartAPI requires an API Key."
            credentials.clientId.isBlank() -> "Angel One SmartAPI requires a Client ID."
            tok.isBlank() -> "Angel One SmartAPI requires a JWT Access Token."
            else -> null
        }
        IndianBrokerId.FYERS -> when {
            credentials.apiKey.isBlank() -> "Fyers requires an App ID (API Key)."
            tok.isBlank() -> "Fyers requires an Access Token."
            else -> null
        }
        IndianBrokerId.DHAN -> when {
            credentials.clientId.isBlank() -> "Dhan requires a Client ID."
            tok.isBlank() -> "Dhan requires an Access Token."
            else -> null
        }
        IndianBrokerId.GENERIC -> when {
            credentials.baseUrl.isBlank() -> "Generic / Custom Broker requires a Base URL."
            !credentials.baseUrl.trim().startsWith("http://") && !credentials.baseUrl.trim().startsWith("https://") ->
                "Custom Base URL must start with https:// or http://"
            credentials.apiKey.isBlank() -> "Generic / Custom Broker requires an API Key."
            tok.isBlank() -> "Generic / Custom Broker requires an Access Token."
            else -> null
        }
    }
}

private suspend fun executeHttp(
    client: OkHttpClient,
    request: Request,
    brokerName: String
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = extractErrorMessage(raw).ifBlank { "HTTP ${resp.code} ${resp.message}" }
                throw IllegalStateException("$brokerName rejected request (${resp.code}): $msg")
            }
            raw
        }
    }
}

private fun extractErrorMessage(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val obj = JSONObject(raw)
        obj.optString("message").ifBlank {
            obj.optString("error").ifBlank {
                obj.optString("errorMessage").ifBlank { raw.take(180) }
            }
        }
    }.getOrElse { raw.take(180) }
}

private fun parseGenericOrderRow(obj: JSONObject): BrokerRemoteOrder = BrokerRemoteOrder(
    orderId = obj.optString("order_id").ifBlank {
        obj.optString("orderId").ifBlank { obj.optString("id") }
    },
    symbol = obj.optString("tradingsymbol").ifBlank {
        obj.optString("tradingSymbol").ifBlank { obj.optString("symbol") }
    }.uppercase(),
    exchange = obj.optString("exchange", "NSE").ifBlank { "NSE" }.uppercase(),
    side = obj.optString("transaction_type").ifBlank {
        obj.optString("transactionType").ifBlank { obj.optString("side", "BUY") }
    }.uppercase(),
    quantity = obj.optInt("quantity", obj.optInt("qty", 0)),
    product = obj.optString("product", obj.optString("productType", "MIS")).uppercase(),
    orderType = obj.optString("order_type").ifBlank {
        obj.optString("orderType", "MARKET")
    }.uppercase(),
    price = obj.optDouble("average_price", 0.0).takeIf { !it.isNaN() && it > 0 }
        ?: obj.optDouble("price", 0.0).let { if (it.isNaN()) 0.0 else it },
    status = obj.optString("status", obj.optString("orderStatus", "UNKNOWN")).uppercase(),
    timestampIst = obj.optString("order_timestamp").ifBlank {
        obj.optString("createTime").ifBlank { obj.optString("timestamp", "") }
    }
)

private fun parseGenericPositionRow(obj: JSONObject): PortfolioPosition? {
    val qty = obj.optInt("quantity", obj.optInt("netQty", 0))
    if (qty == 0) return null
    val avg = obj.optDouble("average_price", Double.NaN).takeIf { !it.isNaN() && it > 0 }
        ?: obj.optDouble("buyAvg", Double.NaN).takeIf { !it.isNaN() && it > 0 }
        ?: obj.optDouble("avgPrice", 0.0).let { if (it.isNaN()) 0.0 else it }
    val ltp = obj.optDouble("last_price", Double.NaN).takeIf { !it.isNaN() && it > 0 }
        ?: obj.optDouble("ltp", Double.NaN).takeIf { !it.isNaN() && it > 0 }
        ?: avg
    val sym = obj.optString("tradingsymbol").ifBlank {
        obj.optString("tradingSymbol").ifBlank { obj.optString("symbol") }
    }.uppercase()
    if (sym.isBlank()) return null
    return PortfolioPosition(
        symbol = sym,
        exchange = obj.optString("exchange", "NSE").uppercase(),
        shares = qty,
        avgPrice = IndianMarketUtils.round2(avg),
        lastPrice = IndianMarketUtils.round2(ltp),
        product = obj.optString("product", obj.optString("productType", "MIS")).uppercase(),
        entryDate = IndianMarketUtils.nowIst(true, "").toLocalDate().toString()
    )
}

// =================================================================================================
// 1. Zerodha Kite Adapter
// =================================================================================================
class ZerodhaKiteAdapter(private val http: OkHttpClient) : IndianBrokerAdapter {
    override val brokerId = IndianBrokerId.ZERODHA

    private fun baseUrl(creds: BrokerCredentials) = creds.baseUrl.trim().ifBlank { brokerId.defaultBaseUrl }.trimEnd('/')

    private fun Request.Builder.kiteAuth(creds: BrokerCredentials): Request.Builder =
        header("X-Kite-Version", "3")
            .header("Authorization", "token ${creds.apiKey.trim()}:${effectiveToken(creds)}")

    override fun buildPreviewPayload(request: BrokerOrderRequest): String = JSONObject().apply {
        put("broker", brokerId.displayName)
        put("endpoint", "${brokerId.defaultBaseUrl}/orders/regular")
        put("variety", "regular")
        put("exchange", request.exchange)
        put("tradingsymbol", request.symbol)
        put("transaction_type", request.side.uppercase())
        put("quantity", request.quantity)
        put("product", request.product.uppercase())
        put("order_type", request.orderType.uppercase())
        if (request.price != null) put("price", request.price)
        if (request.triggerPrice != null) put("trigger_price", request.triggerPrice)
        put("validity", "DAY")
        put("tag", "tradedesk")
    }.toString(2)

    override suspend fun placeOrder(
        credentials: BrokerCredentials,
        request: BrokerOrderRequest
    ): Result<BrokerExecutionResult> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val form = FormBody.Builder()
            .add("exchange", request.exchange)
            .add("tradingsymbol", request.symbol)
            .add("transaction_type", request.side.uppercase())
            .add("quantity", request.quantity.toString())
            .add("product", request.product.uppercase())
            .add("order_type", request.orderType.uppercase())
            .add("validity", "DAY")
            .add("tag", "tradedesk")
            .apply {
                if (request.price != null) add("price", request.price.toString())
                if (request.triggerPrice != null) add("trigger_price", request.triggerPrice.toString())
            }
            .build()
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/orders/regular")
            .kiteAuth(credentials)
            .post(form)
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val obj = JSONObject(raw)
            val data = obj.optJSONObject("data")
            val oid = data?.optString("order_id").orEmpty().ifBlank { "KITE-${System.currentTimeMillis()}" }
            BrokerExecutionResult(
                brokerId = brokerId,
                brokerOrderId = oid,
                status = "PLACED",
                message = "Zerodha Kite order $oid accepted",
                executedPrice = request.price,
                rawPayload = raw
            )
        }
    }

    override suspend fun getPositions(credentials: BrokerCredentials): Result<List<PortfolioPosition>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/portfolio/positions")
            .kiteAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val net = JSONObject(raw).optJSONObject("data")?.optJSONArray("net") ?: JSONArray()
            (0 until net.length()).mapNotNull { parseGenericPositionRow(net.getJSONObject(it)) }
        }
    }

    override suspend fun getOrders(credentials: BrokerCredentials): Result<List<BrokerRemoteOrder>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/orders")
            .kiteAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).map { parseGenericOrderRow(arr.getJSONObject(it)) }
        }
    }

    override suspend fun getFunds(credentials: BrokerCredentials): Result<BrokerFundsSnapshot> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/user/margins/equity")
            .kiteAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val data = JSONObject(raw).optJSONObject("data") ?: JSONObject()
            val avail = data.optJSONObject("available")
            val util = data.optJSONObject("utilised")
            val cash = avail?.optDouble("live_balance", avail.optDouble("cash", 0.0)) ?: 0.0
            val collateral = avail?.optDouble("collateral", 0.0) ?: 0.0
            val used = util?.optDouble("debits", 0.0) ?: 0.0
            val net = data.optDouble("net", cash + collateral)
            BrokerFundsSnapshot(
                brokerId = brokerId,
                availableCash = IndianMarketUtils.round2(cash),
                usedMargin = IndianMarketUtils.round2(used),
                collateral = IndianMarketUtils.round2(collateral),
                netBalance = IndianMarketUtils.round2(net),
                fetchedAtIst = nowIstClock()
            )
        }
    }
}

// =================================================================================================
// 2. Upstox Adapter
// =================================================================================================
class UpstoxAdapter(private val http: OkHttpClient) : IndianBrokerAdapter {
    override val brokerId = IndianBrokerId.UPSTOX

    private fun baseUrl(creds: BrokerCredentials) = creds.baseUrl.trim().ifBlank { brokerId.defaultBaseUrl }.trimEnd('/')

    private fun Request.Builder.upstoxAuth(creds: BrokerCredentials): Request.Builder =
        header("Authorization", "Bearer ${effectiveToken(creds)}")
            .header("Api-Version", "2.0")
            .header("Accept", "application/json")

    override fun buildPreviewPayload(request: BrokerOrderRequest): String = JSONObject().apply {
        put("broker", brokerId.displayName)
        put("endpoint", "${brokerId.defaultBaseUrl}/order/place")
        put("quantity", request.quantity)
        put("product", if (request.product.equals("MIS", true)) "I" else "D")
        put("validity", "DAY")
        put("price", request.price ?: 0.0)
        put("instrument_token", "${request.exchange}_EQ|${request.symbol}")
        put("order_type", request.orderType.uppercase())
        put("transaction_type", request.side.uppercase())
        put("trigger_price", request.triggerPrice ?: 0.0)
        put("is_amo", false)
    }.toString(2)

    override suspend fun placeOrder(
        credentials: BrokerCredentials,
        request: BrokerOrderRequest
    ): Result<BrokerExecutionResult> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val payload = JSONObject().apply {
            put("quantity", request.quantity)
            put("product", if (request.product.equals("MIS", true)) "I" else "D")
            put("validity", "DAY")
            put("price", request.price ?: 0.0)
            put("instrument_token", "${request.exchange}_EQ|${request.symbol}")
            put("order_type", request.orderType.uppercase())
            put("transaction_type", request.side.uppercase())
            put("disclosed_quantity", 0)
            put("trigger_price", request.triggerPrice ?: 0.0)
            put("is_amo", false)
        }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/order/place")
            .upstoxAuth(credentials)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val obj = JSONObject(raw)
            val oid = obj.optJSONObject("data")?.optString("order_id").orEmpty()
                .ifBlank { "UPX-${System.currentTimeMillis()}" }
            BrokerExecutionResult(
                brokerId = brokerId,
                brokerOrderId = oid,
                status = obj.optString("status", "PLACED").uppercase(),
                message = "Upstox order $oid placed",
                executedPrice = request.price,
                rawPayload = raw
            )
        }
    }

    override suspend fun getPositions(credentials: BrokerCredentials): Result<List<PortfolioPosition>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/portfolio/short-term-positions")
            .upstoxAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).mapNotNull { parseGenericPositionRow(arr.getJSONObject(it)) }
        }
    }

    override suspend fun getOrders(credentials: BrokerCredentials): Result<List<BrokerRemoteOrder>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/order/retrieve-all")
            .upstoxAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).map { parseGenericOrderRow(arr.getJSONObject(it)) }
        }
    }

    override suspend fun getFunds(credentials: BrokerCredentials): Result<BrokerFundsSnapshot> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/user/get-funds-and-margin?segment=SEC")
            .upstoxAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val equity = JSONObject(raw).optJSONObject("data")?.optJSONObject("equity") ?: JSONObject()
            val avail = equity.optDouble("available_margin", 0.0)
            val used = equity.optDouble("used_margin", 0.0)
            BrokerFundsSnapshot(
                brokerId = brokerId,
                availableCash = IndianMarketUtils.round2(avail),
                usedMargin = IndianMarketUtils.round2(used),
                netBalance = IndianMarketUtils.round2(avail + used),
                fetchedAtIst = nowIstClock()
            )
        }
    }
}

// =================================================================================================
// 3. Angel One (SmartAPI) Adapter
// =================================================================================================
class AngelOneSmartApiAdapter(private val http: OkHttpClient) : IndianBrokerAdapter {
    override val brokerId = IndianBrokerId.ANGEL_ONE

    private fun baseUrl(creds: BrokerCredentials) = creds.baseUrl.trim().ifBlank { brokerId.defaultBaseUrl }.trimEnd('/')

    private fun Request.Builder.smartApiAuth(creds: BrokerCredentials): Request.Builder =
        header("Authorization", "Bearer ${effectiveToken(creds)}")
            .header("X-PrivateKey", creds.apiKey.trim())
            .header("X-UserType", "USER")
            .header("X-SourceID", "WEB")
            .header("X-ClientLocalIP", "127.0.0.1")
            .header("X-ClientPublicIP", "127.0.0.1")
            .header("X-MACAddress", "00:00:00:00:00:00")
            .header("Accept", "application/json")

    override fun buildPreviewPayload(request: BrokerOrderRequest): String = JSONObject().apply {
        put("broker", brokerId.displayName)
        put("endpoint", "${brokerId.defaultBaseUrl}/rest/secure/angelbroking/order/v1/placeOrder")
        put("variety", "NORMAL")
        put("tradingsymbol", "${request.symbol}-EQ")
        put("exchange", request.exchange)
        put("transactiontype", request.side.uppercase())
        put("ordertype", request.orderType.uppercase())
        put("producttype", if (request.product.equals("MIS", true)) "INTRADAY" else "DELIVERY")
        put("duration", "DAY")
        put("price", (request.price ?: 0.0).toString())
        put("quantity", request.quantity.toString())
    }.toString(2)

    override suspend fun placeOrder(
        credentials: BrokerCredentials,
        request: BrokerOrderRequest
    ): Result<BrokerExecutionResult> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val payload = JSONObject().apply {
            put("variety", "NORMAL")
            put("tradingsymbol", "${request.symbol}-EQ")
            put("symboltoken", request.symbol)
            put("transactiontype", request.side.uppercase())
            put("exchange", request.exchange)
            put("ordertype", request.orderType.uppercase())
            put("producttype", if (request.product.equals("MIS", true)) "INTRADAY" else "DELIVERY")
            put("duration", "DAY")
            put("price", (request.price ?: 0.0).toString())
            put("squareoff", "0")
            put("stoploss", "0")
            put("quantity", request.quantity.toString())
        }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/rest/secure/angelbroking/order/v1/placeOrder")
            .smartApiAuth(credentials)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val obj = JSONObject(raw)
            val data = obj.optJSONObject("data")
            val oid = data?.optString("orderid").orEmpty().ifBlank { "AONE-${System.currentTimeMillis()}" }
            BrokerExecutionResult(
                brokerId = brokerId,
                brokerOrderId = oid,
                status = if (obj.optBoolean("status", true)) "PLACED" else "REJECTED",
                message = obj.optString("message", "SmartAPI order $oid placed"),
                executedPrice = request.price,
                rawPayload = raw
            )
        }
    }

    override suspend fun getPositions(credentials: BrokerCredentials): Result<List<PortfolioPosition>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/rest/secure/angelbroking/order/v1/getPosition")
            .smartApiAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val sym = obj.optString("tradingsymbol").removeSuffix("-EQ").uppercase()
                val qty = obj.optString("netqty").toIntOrNull() ?: obj.optInt("netqty", 0)
                if (sym.isBlank() || qty == 0) return@mapNotNull null
                val avg = obj.optString("avgnetprice").toDoubleOrNull() ?: obj.optDouble("avgnetprice", 0.0)
                val ltp = obj.optString("ltp").toDoubleOrNull() ?: avg
                PortfolioPosition(
                    symbol = sym,
                    exchange = obj.optString("exchange", "NSE").uppercase(),
                    shares = qty,
                    avgPrice = IndianMarketUtils.round2(avg),
                    lastPrice = IndianMarketUtils.round2(ltp),
                    product = if (obj.optString("producttype").equals("DELIVERY", true)) "CNC" else "MIS",
                    entryDate = IndianMarketUtils.nowIst(true, "").toLocalDate().toString()
                )
            }
        }
    }

    override suspend fun getOrders(credentials: BrokerCredentials): Result<List<BrokerRemoteOrder>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/rest/secure/angelbroking/order/v1/getOrderBook")
            .smartApiAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BrokerRemoteOrder(
                    orderId = obj.optString("orderid"),
                    symbol = obj.optString("tradingsymbol").removeSuffix("-EQ").uppercase(),
                    exchange = obj.optString("exchange", "NSE").uppercase(),
                    side = obj.optString("transactiontype", "BUY").uppercase(),
                    quantity = obj.optString("quantity").toIntOrNull() ?: obj.optInt("quantity", 0),
                    product = if (obj.optString("producttype").equals("DELIVERY", true)) "CNC" else "MIS",
                    orderType = obj.optString("ordertype", "MARKET").uppercase(),
                    price = obj.optString("price").toDoubleOrNull() ?: obj.optDouble("price", 0.0),
                    status = obj.optString("status", obj.optString("orderstatus", "UNKNOWN")).uppercase(),
                    timestampIst = obj.optString("updatetime", "")
                )
            }
        }
    }

    override suspend fun getFunds(credentials: BrokerCredentials): Result<BrokerFundsSnapshot> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/rest/secure/angelbroking/user/v1/getRMS")
            .smartApiAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val data = JSONObject(raw).optJSONObject("data") ?: JSONObject()
            val avail = data.optString("availablecash").toDoubleOrNull() ?: data.optDouble("availablecash", 0.0)
            val net = data.optString("net").toDoubleOrNull() ?: data.optDouble("net", avail)
            val collateral = data.optString("collateral").toDoubleOrNull() ?: 0.0
            val used = (net - avail).coerceAtLeast(0.0)
            BrokerFundsSnapshot(
                brokerId = brokerId,
                availableCash = IndianMarketUtils.round2(avail),
                usedMargin = IndianMarketUtils.round2(used),
                collateral = IndianMarketUtils.round2(collateral),
                netBalance = IndianMarketUtils.round2(net),
                fetchedAtIst = nowIstClock()
            )
        }
    }
}

// =================================================================================================
// 4. Fyers Adapter
// =================================================================================================
class FyersAdapter(private val http: OkHttpClient) : IndianBrokerAdapter {
    override val brokerId = IndianBrokerId.FYERS

    private fun baseUrl(creds: BrokerCredentials) = creds.baseUrl.trim().ifBlank { brokerId.defaultBaseUrl }.trimEnd('/')

    private fun Request.Builder.fyersAuth(creds: BrokerCredentials): Request.Builder =
        header("Authorization", "${creds.apiKey.trim()}:${effectiveToken(creds)}")
            .header("Accept", "application/json")

    override fun buildPreviewPayload(request: BrokerOrderRequest): String = JSONObject().apply {
        put("broker", brokerId.displayName)
        put("endpoint", "${brokerId.defaultBaseUrl}/orders/sync")
        put("symbol", "${request.exchange}:${request.symbol}-EQ")
        put("qty", request.quantity)
        put("type", if (request.orderType.equals("LIMIT", true)) 1 else 2)
        put("side", if (request.side.equals("SELL", true)) -1 else 1)
        put("productType", if (request.product.equals("MIS", true)) "INTRADAY" else "CNC")
        put("limitPrice", request.price ?: 0.0)
        put("stopPrice", request.triggerPrice ?: 0.0)
        put("validity", "DAY")
    }.toString(2)

    override suspend fun placeOrder(
        credentials: BrokerCredentials,
        request: BrokerOrderRequest
    ): Result<BrokerExecutionResult> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val payload = JSONObject().apply {
            put("symbol", "${request.exchange}:${request.symbol}-EQ")
            put("qty", request.quantity)
            put("type", if (request.orderType.equals("LIMIT", true)) 1 else 2)
            put("side", if (request.side.equals("SELL", true)) -1 else 1)
            put("productType", if (request.product.equals("MIS", true)) "INTRADAY" else "CNC")
            put("limitPrice", request.price ?: 0.0)
            put("stopPrice", request.triggerPrice ?: 0.0)
            put("validity", "DAY")
            put("disclosedQty", 0)
            put("offlineOrder", false)
        }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/orders/sync")
            .fyersAuth(credentials)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val obj = JSONObject(raw)
            val oid = obj.optString("id").ifBlank { "FYR-${System.currentTimeMillis()}" }
            BrokerExecutionResult(
                brokerId = brokerId,
                brokerOrderId = oid,
                status = if (obj.optString("s") == "ok" || obj.optInt("code", 200) in 200..299) "PLACED" else "REJECTED",
                message = obj.optString("message", "Fyers order $oid submitted"),
                executedPrice = request.price,
                rawPayload = raw
            )
        }
    }

    override suspend fun getPositions(credentials: BrokerCredentials): Result<List<PortfolioPosition>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/positions")
            .fyersAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = JSONObject(raw).optJSONArray("netPositions") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val sym = obj.optString("symbol").substringAfter(":").removeSuffix("-EQ").uppercase()
                val qty = obj.optInt("netQty", 0)
                if (sym.isBlank() || qty == 0) return@mapNotNull null
                val avg = obj.optDouble("netAvg", obj.optDouble("buyAvg", 0.0))
                val ltp = obj.optDouble("ltp", avg)
                PortfolioPosition(
                    symbol = sym,
                    exchange = "NSE",
                    shares = qty,
                    avgPrice = IndianMarketUtils.round2(avg),
                    lastPrice = IndianMarketUtils.round2(ltp),
                    product = if (obj.optString("productType").equals("INTRADAY", true)) "MIS" else "CNC",
                    entryDate = IndianMarketUtils.nowIst(true, "").toLocalDate().toString()
                )
            }
        }
    }

    override suspend fun getOrders(credentials: BrokerCredentials): Result<List<BrokerRemoteOrder>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/orders")
            .fyersAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = JSONObject(raw).optJSONArray("orderBook") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BrokerRemoteOrder(
                    orderId = obj.optString("id"),
                    symbol = obj.optString("symbol").substringAfter(":").removeSuffix("-EQ").uppercase(),
                    exchange = "NSE",
                    side = if (obj.optInt("side", 1) < 0) "SELL" else "BUY",
                    quantity = obj.optInt("qty", 0),
                    product = obj.optString("productType", "INTRADAY").uppercase(),
                    orderType = if (obj.optInt("type", 2) == 1) "LIMIT" else "MARKET",
                    price = obj.optDouble("tradedPrice", obj.optDouble("limitPrice", 0.0)),
                    status = obj.optString("status", "UNKNOWN").uppercase(),
                    timestampIst = obj.optString("orderDateTime", "")
                )
            }
        }
    }

    override suspend fun getFunds(credentials: BrokerCredentials): Result<BrokerFundsSnapshot> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/funds")
            .fyersAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = JSONObject(raw).optJSONArray("fund_limit") ?: JSONArray()
            var avail = 0.0
            var utilized = 0.0
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val title = item.optString("title", "").lowercase()
                val amt = item.optDouble("equityAmount", 0.0)
                when {
                    "available balance" in title -> avail = amt
                    "utilized" in title -> utilized = amt
                }
            }
            BrokerFundsSnapshot(
                brokerId = brokerId,
                availableCash = IndianMarketUtils.round2(avail),
                usedMargin = IndianMarketUtils.round2(utilized),
                netBalance = IndianMarketUtils.round2(avail + utilized),
                fetchedAtIst = nowIstClock()
            )
        }
    }
}

// =================================================================================================
// 5. Dhan Adapter
// =================================================================================================
class DhanAdapter(private val http: OkHttpClient) : IndianBrokerAdapter {
    override val brokerId = IndianBrokerId.DHAN

    private fun baseUrl(creds: BrokerCredentials) = creds.baseUrl.trim().ifBlank { brokerId.defaultBaseUrl }.trimEnd('/')

    private fun Request.Builder.dhanAuth(creds: BrokerCredentials): Request.Builder =
        header("access-token", effectiveToken(creds))
            .header("client-id", creds.clientId.trim())
            .header("Accept", "application/json")

    override fun buildPreviewPayload(request: BrokerOrderRequest): String = JSONObject().apply {
        put("broker", brokerId.displayName)
        put("endpoint", "${brokerId.defaultBaseUrl}/orders")
        put("transactionType", request.side.uppercase())
        put("exchangeSegment", if (request.exchange == "BSE") "BSE_EQ" else "NSE_EQ")
        put("productType", if (request.product.equals("MIS", true)) "INTRADAY" else "CNC")
        put("orderType", request.orderType.uppercase())
        put("validity", "DAY")
        put("tradingSymbol", request.symbol)
        put("quantity", request.quantity)
        put("price", request.price ?: 0.0)
        put("triggerPrice", request.triggerPrice ?: 0.0)
    }.toString(2)

    override suspend fun placeOrder(
        credentials: BrokerCredentials,
        request: BrokerOrderRequest
    ): Result<BrokerExecutionResult> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val payload = JSONObject().apply {
            put("dhanClientId", credentials.clientId.trim())
            put("transactionType", request.side.uppercase())
            put("exchangeSegment", if (request.exchange == "BSE") "BSE_EQ" else "NSE_EQ")
            put("productType", if (request.product.equals("MIS", true)) "INTRADAY" else "CNC")
            put("orderType", request.orderType.uppercase())
            put("validity", "DAY")
            put("tradingSymbol", request.symbol)
            put("securityId", request.symbol)
            put("quantity", request.quantity)
            put("price", request.price ?: 0.0)
            put("triggerPrice", request.triggerPrice ?: 0.0)
        }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/orders")
            .dhanAuth(credentials)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val obj = JSONObject(raw)
            val oid = obj.optString("orderId").ifBlank { "DHAN-${System.currentTimeMillis()}" }
            BrokerExecutionResult(
                brokerId = brokerId,
                brokerOrderId = oid,
                status = obj.optString("orderStatus", "TRANSIT").uppercase(),
                message = "Dhan order $oid placed (${obj.optString("orderStatus", "PENDING")})",
                executedPrice = request.price,
                rawPayload = raw
            )
        }
    }

    override suspend fun getPositions(credentials: BrokerCredentials): Result<List<PortfolioPosition>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/positions")
            .dhanAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = if (raw.trim().startsWith("[")) JSONArray(raw) else JSONObject(raw).optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).mapNotNull { parseGenericPositionRow(arr.getJSONObject(it)) }
        }
    }

    override suspend fun getOrders(credentials: BrokerCredentials): Result<List<BrokerRemoteOrder>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/orders")
            .dhanAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = if (raw.trim().startsWith("[")) JSONArray(raw) else JSONObject(raw).optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).map { parseGenericOrderRow(arr.getJSONObject(it)) }
        }
    }

    override suspend fun getFunds(credentials: BrokerCredentials): Result<BrokerFundsSnapshot> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/fundlimit")
            .dhanAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val obj = JSONObject(raw)
            val avail = obj.optDouble("availabelBalance", obj.optDouble("availableBalance", 0.0))
            val used = obj.optDouble("utilizedAmount", 0.0)
            val collateral = obj.optDouble("collateralAmount", 0.0)
            BrokerFundsSnapshot(
                brokerId = brokerId,
                availableCash = IndianMarketUtils.round2(avail),
                usedMargin = IndianMarketUtils.round2(used),
                collateral = IndianMarketUtils.round2(collateral),
                netBalance = IndianMarketUtils.round2(avail + collateral),
                fetchedAtIst = nowIstClock()
            )
        }
    }
}

// =================================================================================================
// 6. Generic / Custom Broker Adapter (Configurable Base URL + Key/Secret/Token)
// =================================================================================================
class GenericCustomBrokerAdapter(private val http: OkHttpClient) : IndianBrokerAdapter {
    override val brokerId = IndianBrokerId.GENERIC

    private fun baseUrl(creds: BrokerCredentials) = creds.baseUrl.trim().trimEnd('/')

    private fun Request.Builder.customAuth(creds: BrokerCredentials): Request.Builder = apply {
        header("Authorization", "Bearer ${effectiveToken(creds)}")
        header("X-Api-Key", creds.apiKey.trim())
        if (creds.apiSecret.isNotBlank()) header("X-Api-Secret", creds.apiSecret.trim())
        if (creds.clientId.isNotBlank()) header("X-Client-Id", creds.clientId.trim())
        header("Accept", "application/json")
    }

    override fun buildPreviewPayload(request: BrokerOrderRequest): String = JSONObject().apply {
        put("broker", brokerId.displayName)
        put("endpoint", "<custom-base-url>/orders")
        put("symbol", request.symbol)
        put("exchange", request.exchange)
        put("side", request.side.uppercase())
        put("quantity", request.quantity)
        put("product", request.product.uppercase())
        put("order_type", request.orderType.uppercase())
        if (request.price != null) put("price", request.price)
        if (request.stopPrice != null) put("stop_price", request.stopPrice)
        if (request.targetPrice != null) put("target_price", request.targetPrice)
        if (request.fingerprint.isNotBlank()) put("fingerprint", request.fingerprint)
    }.toString(2)

    override suspend fun placeOrder(
        credentials: BrokerCredentials,
        request: BrokerOrderRequest
    ): Result<BrokerExecutionResult> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val payload = JSONObject().apply {
            put("symbol", request.symbol)
            put("exchange", request.exchange)
            put("side", request.side.uppercase())
            put("quantity", request.quantity)
            put("product", request.product.uppercase())
            put("order_type", request.orderType.uppercase())
            if (request.price != null) put("price", request.price)
            if (request.stopPrice != null) put("stop_price", request.stopPrice)
            if (request.targetPrice != null) put("target_price", request.targetPrice)
            if (request.fingerprint.isNotBlank()) put("fingerprint", request.fingerprint)
        }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/orders")
            .customAuth(credentials)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val obj = JSONObject(raw)
            val data = obj.optJSONObject("data") ?: obj
            val oid = data.optString("order_id")
                .ifBlank { data.optString("orderId") }
                .ifBlank { "GEN-${System.currentTimeMillis()}" }
            BrokerExecutionResult(
                brokerId = brokerId,
                brokerOrderId = oid,
                status = data.optString("status", "PLACED").uppercase(),
                message = obj.optString("message", "Custom broker order $oid placed"),
                executedPrice = request.price,
                rawPayload = raw
            )
        }
    }

    override suspend fun getPositions(credentials: BrokerCredentials): Result<List<PortfolioPosition>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/positions")
            .customAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = if (raw.trim().startsWith("[")) {
                JSONArray(raw)
            } else {
                val obj = JSONObject(raw)
                obj.optJSONArray("positions") ?: obj.optJSONArray("data") ?: JSONArray()
            }
            (0 until arr.length()).mapNotNull { parseGenericPositionRow(arr.getJSONObject(it)) }
        }
    }

    override suspend fun getOrders(credentials: BrokerCredentials): Result<List<BrokerRemoteOrder>> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/orders")
            .customAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val arr = if (raw.trim().startsWith("[")) {
                JSONArray(raw)
            } else {
                val obj = JSONObject(raw)
                obj.optJSONArray("orders") ?: obj.optJSONArray("data") ?: JSONArray()
            }
            (0 until arr.length()).map { parseGenericOrderRow(arr.getJSONObject(it)) }
        }
    }

    override suspend fun getFunds(credentials: BrokerCredentials): Result<BrokerFundsSnapshot> {
        validateCredentials(credentials)?.let { return Result.failure(IllegalStateException(it)) }
        val req = Request.Builder()
            .url("${baseUrl(credentials)}/funds")
            .customAuth(credentials)
            .get()
            .build()
        return executeHttp(http, req, brokerId.displayName).mapCatching { raw ->
            val root = JSONObject(raw)
            val data = root.optJSONObject("data") ?: root
            val avail = data.optDouble("available_cash", Double.NaN).takeIf { !it.isNaN() }
                ?: data.optDouble("availableCash", Double.NaN).takeIf { !it.isNaN() }
                ?: data.optDouble("cash", 0.0)
            val used = data.optDouble("used_margin", Double.NaN).takeIf { !it.isNaN() }
                ?: data.optDouble("usedMargin", 0.0)
            val collateral = data.optDouble("collateral", 0.0)
            BrokerFundsSnapshot(
                brokerId = brokerId,
                availableCash = IndianMarketUtils.round2(avail),
                usedMargin = IndianMarketUtils.round2(used),
                collateral = IndianMarketUtils.round2(collateral),
                netBalance = IndianMarketUtils.round2(avail + collateral),
                fetchedAtIst = nowIstClock()
            )
        }
    }
}
