package com.example.domain

import com.example.model.ChargesBreakdown
import com.example.model.MarketClockStatus
import com.example.model.SessionRules
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

object IndianMarketUtils {

    val IST_OFFSET: ZoneOffset = ZoneOffset.ofHoursMinutes(5, 30)
    val CAS_START: LocalDate = LocalDate.of(2026, 8, 3)
    val PRE_OPEN_REVISION_START: LocalDate = LocalDate.of(2026, 9, 7)

    val OPEN_TIME: LocalTime = LocalTime.of(9, 15)
    val PRE_OPEN_TIME: LocalTime = LocalTime.of(9, 0)
    val NORMAL_CLOSE_TIME: LocalTime = LocalTime.of(15, 30)
    val CAS_CLOSE_TIME: LocalTime = LocalTime.of(15, 15)
    const val OPEN_MIN = 9 * 60 + 15
    const val SESSION_BARS = 75

    val NSE_HOLIDAYS_2026: MutableMap<String, String> = linkedMapOf(
        "2026-01-15" to "NSE trading holiday",
        "2026-01-26" to "Republic Day",
        "2026-02-15" to "Mahashivratri",
        "2026-03-03" to "Holi",
        "2026-03-21" to "Id-ul-Fitr (Ramzan Id)",
        "2026-03-26" to "Shri Ram Navami",
        "2026-03-31" to "Shri Mahavir Jayanti",
        "2026-04-03" to "Good Friday",
        "2026-04-14" to "Dr. Baba Saheb Ambedkar Jayanti",
        "2026-05-01" to "Maharashtra Day",
        "2026-05-28" to "Bakri Id",
        "2026-06-26" to "Muharram",
        "2026-08-15" to "Independence Day",
        "2026-09-14" to "Ganesh Chaturthi",
        "2026-10-02" to "Mahatma Gandhi Jayanti",
        "2026-10-20" to "Dussehra",
        "2026-11-08" to "Diwali Laxmi Pujan (Muhurat trading session only)",
        "2026-11-10" to "Diwali Balipratipada",
        "2026-11-24" to "Guru Nanak Jayanti",
        "2026-12-25" to "Christmas"
    )

    fun nowIst(isLive: Boolean, replayIsoIst: String): LocalDateTime {
        if (isLive) {
            return LocalDateTime.now(IST_OFFSET).withNano(0)
        }
        return parseIstDateTime(replayIsoIst)
            ?: LocalDateTime.of(2026, 10, 19, 10, 15)
    }

    fun parseIstDateTime(text: String): LocalDateTime? {
        val cleaned = text.trim().replace("T", " ").removeSuffix(" IST").trim()
        val formats = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        )
        for (fmt in formats) {
            try {
                return LocalDateTime.parse(cleaned, fmt)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun epochToIst(epochSec: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSec), IST_OFFSET)

    fun istToEpoch(dt: LocalDateTime): Long = dt.toEpochSecond(IST_OFFSET)

    fun formatIstDateTime(dt: LocalDateTime): String =
        "%04d-%02d-%02d %02d:%02d".format(dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)

    fun formatIstTime(dt: LocalDateTime): String =
        "%02d:%02d:%02d".format(dt.hour, dt.minute, dt.second)

    fun tradingDay(date: LocalDate, holidays: Map<String, String> = NSE_HOLIDAYS_2026): Pair<Boolean, String> {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return false to "Weekend"
        }
        val name = holidays[date.toString()]
        if (!name.isNullOrBlank()) {
            return false to "NSE Holiday: $name"
        }
        return true to ""
    }

    fun tradingDaysBetween(start: LocalDate, end: LocalDate, holidays: Map<String, String> = NSE_HOLIDAYS_2026): Int {
        if (!end.isAfter(start)) return 0
        var count = 0
        var d = start.plusDays(1)
        while (!d.isAfter(end)) {
            if (tradingDay(d, holidays).first) count++
            d = d.plusDays(1)
        }
        return count
    }

    fun sessionRules(cas: Boolean?, dayStr: String, casSource: String = "NSE F&O list"): SessionRules {
        val day = try {
            LocalDate.parse(dayStr.take(10))
        } catch (_: Exception) {
            LocalDate.of(2026, 10, 19)
        }
        val strict = cas != false && !day.isBefore(CAS_START)
        return SessionRules(
            open = "09:15",
            close = if (strict) "15:15" else "15:30",
            squareOff = if (strict) "15:12" else "15:25",
            flatByDefault = if (strict) "15:00" else "15:10",
            noEntriesAfterDefault = "14:45",
            bars5m = if (strict) 72 else 75,
            cas = cas,
            casSource = casSource,
            label = if (strict) {
                "CAS stock (F&O): continuous ends 15:15 IST, Zerodha MIS square-off 15:12 IST"
            } else {
                "Non-CAS stock: trades to 15:30 IST, Zerodha MIS square-off 15:25 IST"
            }
        )
    }

    fun casStatus(
        symbol: String,
        planCas: Boolean? = null,
        extraCas: Set<String> = emptySet(),
        nonCas: Set<String> = emptySet(),
        foSymbols: Set<String>? = null,
        flagOverride: Boolean? = null
    ): Pair<Boolean?, String> {
        val sym = symbol.trim().uppercase()
        if (flagOverride != null) return flagOverride to "manual override"
        if (planCas != null) return planCas to "trade plan"
        if (nonCas.contains(sym)) return false to "config non_cas_symbols"
        if (extraCas.contains(sym)) return true to "config cas_symbols_extra"
        if (foSymbols != null) return foSymbols.contains(sym) to "NSE F&O list"
        return null to "F&O list unavailable (strict CAS rules applied)"
    }

    fun clockStatus(
        nowIst: LocalDateTime,
        isLiveMode: Boolean,
        holidays: Map<String, String> = NSE_HOLIDAYS_2026
    ): MarketClockStatus {
        val date = nowIst.toLocalDate()
        val time = nowIst.toLocalTime()
        val holiday = holidays[date.toString()]
        val (isDay, dayReason) = tradingDay(date, holidays)
        val dow = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        val hhmm = "%02d:%02d".format(time.hour, time.minute)

        val (phase, detail) = when {
            !isDay -> "CLOSED" to dayReason
            time.isBefore(PRE_OPEN_TIME) -> "PRE-MARKET" to "Pre-open starts at 09:00 IST"
            !time.isBefore(PRE_OPEN_TIME) && time.isBefore(OPEN_TIME) -> {
                val mins = time.hour * 60 + time.minute
                val sub = when {
                    mins < 9 * 60 + 5 -> "Pre-Open: Market + Limit orders (09:00–09:05)"
                    mins < 9 * 60 + 10 -> "Pre-Open: Limit orders only (09:05–09:10)"
                    mins < 9 * 60 + 12 -> "Pre-Open: Order matching (09:10–09:12)"
                    else -> "Pre-Open: Buffer before open (09:12–09:15)"
                }
                "PRE-OPEN" to sub
            }
            !time.isBefore(OPEN_TIME) && time.isBefore(CAS_CLOSE_TIME) ->
                "CONTINUOUS" to "NSE/BSE continuous trading (CAS closes 15:15, Non-CAS 15:30)"
            !time.isBefore(CAS_CLOSE_TIME) && time.isBefore(NORMAL_CLOSE_TIME) ->
                "CAS AUCTION / NON-CAS" to "F&O/CAS stocks in Closing Auction (15:15–15:35); Non-CAS still trading"
            !time.isBefore(NORMAL_CLOSE_TIME) && time.isBefore(LocalTime.of(15, 35)) ->
                "CAS AUCTION" to "Closing Auction Session (15:15–15:35 IST)"
            else -> "AFTER HOURS" to "Session closed for today"
        }

        return MarketClockStatus(
            istDate = date.toString(),
            istTime = hhmm,
            dayOfWeek = dow,
            isTradingDay = isDay,
            phaseLabel = phase,
            detail = detail,
            holidayName = holiday,
            isLiveMode = isLiveMode
        )
    }

    /**
     * NSE tick size grid (from Apr 15, 2025).
     */
    fun tickSize(price: Double): Double = when {
        price < 250.0 -> 0.01
        price < 1000.0 -> 0.05
        price < 5000.0 -> 0.10
        price < 10000.0 -> 0.50
        price < 20000.0 -> 1.00
        else -> 5.00
    }

    /**
     * Round to NSE tick using paise precision: "down", "up", or "nearest".
     */
    fun roundToTick(price: Double, mode: String = "nearest", customTick: Double? = null): Double {
        val tick = customTick?.takeIf { it > 0 } ?: tickSize(price)
        val t = round(tick * 100.0).toLong().coerceAtLeast(1L)
        val p = BigDecimal.valueOf(price).multiply(BigDecimal(100)).toDouble()
        val n = p / t
        val k = when (mode.lowercase()) {
            "down" -> floor(n + 1e-9).toLong()
            "up" -> ceil(n - 1e-9).toLong()
            else -> floor(n + 0.5).toLong()
        }
        val snapped = BigDecimal.valueOf(k * t).divide(BigDecimal(100), 2, RoundingMode.HALF_UP).toDouble()
        // Re-snap if rounding down crossed a price band threshold with a smaller tick
        if (mode.lowercase() == "down" && customTick == null && tickSize(snapped) < tick) {
            return roundToTick(price, "down", tickSize(snapped))
        }
        return snapped
    }

    fun onTick(price: Double, customTick: Double? = null): Boolean =
        abs(roundToTick(price, "nearest", customTick) - price) < 0.001

    fun circuits(prevClose: Double, bandPct: Double?): Pair<Double, Double>? {
        if (prevClose <= 0 || bandPct == null || bandPct <= 0) return null
        val lower = roundToTick(prevClose * (1.0 - bandPct / 100.0), "up")
        val upper = roundToTick(prevClose * (1.0 + bandPct / 100.0), "down")
        return lower to upper
    }

    /**
     * Zerodha Indian equity charges (as of Oct 2026):
     * - MIS: brokerage min(20, 0.03%) per buy/sell leg, STT 0.025% sell, stamp 0.003% buy
     * - CNC: brokerage 0, STT 0.1% on both legs, stamp 0.015% buy
     * - Exchange: NSE 0.00297%, BSE 0.00375%
     * - SEBI: ₹10/crore (0.0001%)
     * - GST: 18% on (brokerage + exchange + SEBI)
     */
    fun charges(
        buyValue: Double,
        sellValue: Double,
        product: String = "MIS",
        exchange: String = "NSE"
    ): ChargesBreakdown {
        val intraday = product.uppercase() != "CNC"
        val turnover = buyValue + sellValue
        val brokerage = if (intraday) {
            (if (buyValue > 0) minOf(20.0, 0.0003 * buyValue) else 0.0) +
                (if (sellValue > 0) minOf(20.0, 0.0003 * sellValue) else 0.0)
        } else 0.0
        val stt = if (intraday) 0.00025 * sellValue else 0.001 * turnover
        val exchRate = if (exchange.uppercase() == "BSE") 0.0000375 else 0.0000297
        val exch = exchRate * turnover
        val sebi = 0.000001 * turnover
        val stamp = (if (intraday) 0.00003 else 0.00015) * buyValue
        val gst = 0.18 * (brokerage + exch + sebi)
        val total = brokerage + stt + exch + sebi + stamp + gst
        return ChargesBreakdown(
            brokerage = round2(brokerage),
            stt = round2(stt),
            exchange = round2(exch),
            sebi = round2(sebi),
            stamp = round2(stamp),
            gst = round2(gst),
            total = round2(total)
        )
    }

    fun round2(v: Double): Double = round(v * 100.0) / 100.0

    fun inr(x: Double): String {
        val neg = x < 0
        val formatted = "%.2f".format(abs(x))
        val parts = formatted.split(".")
        val whole = parts[0]
        val frac = parts[1]
        val grouped = groupIndianDigits(whole)
        return (if (neg) "-" else "") + "₹" + grouped + "." + frac
    }

    fun money(x: Double): String {
        val s = inr(x)
        return if (s.endsWith(".00")) s.dropLast(3) else s
    }

    fun crore(rupees: Double?, signed: Boolean = false): String {
        if (rupees == null) return "-"
        val cr = rupees / 1e7
        val neg = cr < 0
        val whole = "%.0f".format(abs(cr))
        val grouped = groupIndianDigits(whole)
        val prefix = when {
            neg -> "-"
            signed && cr > 0 -> "+"
            else -> ""
        }
        return "$prefix$grouped"
    }

    private fun groupIndianDigits(whole: String): String {
        if (whole.length <= 3) return whole
        val tail = whole.takeLast(3)
        var head = whole.dropLast(3)
        val groups = mutableListOf<String>()
        while (head.length > 2) {
            groups.add(0, head.takeLast(2))
            head = head.dropLast(2)
        }
        if (head.isNotEmpty()) groups.add(0, head)
        return (groups + tail).joinToString(",")
    }

    fun parseSymbol(text: String?, exchange: String? = null): Pair<String, String> {
        var s = (text ?: "").trim().uppercase()
        var ex = exchange?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (":" in s) {
            val (pre, sym) = s.split(":", limit = 2)
            s = sym.trim()
            if (pre == "NSE" || pre == "BSE") ex = pre
        }
        if (s.endsWith(".NS")) {
            s = s.dropLast(3)
            ex = "NSE"
        } else if (s.endsWith(".BO")) {
            s = s.dropLast(3)
            ex = "BSE"
        }
        if (Regex("^\\d{6}$").matches(s)) {
            ex = "BSE"
        }
        require(Regex("^[A-Z0-9][A-Z0-9&\\-]{0,19}$").matches(s)) {
            "Expected an NSE/BSE symbol like RELIANCE, M&M, or BAJAJ-AUTO, got '$text'"
        }
        val finalEx = ex ?: "NSE"
        require(finalEx == "NSE" || finalEx == "BSE") {
            "Exchange must be NSE or BSE, got '$finalEx'"
        }
        return s to finalEx
    }

    fun yahooSymbol(sym: String, exchange: String): String =
        if (exchange.uppercase() == "BSE") "$sym.BO" else "$sym.NS"

    fun parseClockMinutes(text: String?, what: String = "time"): Int? {
        val t = (text ?: "").trim()
        val m = Regex("^(\\d{1,2}):(\\d{2})\\s*(?:([AaPp])\\.?\\s*[Mm]\\.?)?\\s*(?:IST)?$").matchEntire(t)
            ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        val ap = m.groupValues[3].lowercase()
        if (min > 59 || h > 23) return null
        if (ap == "p" && h != 12) h += 12
        else if (ap == "a" && h == 12) h = 0
        else if (ap.isEmpty() && h in 1..8) h += 12
        return h * 60 + min
    }

    fun clockString(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun resolveFlatBy(value: String?, rules: SessionRules): Pair<String, String?> {
        val v = (value ?: "auto").trim()
        if (v.equals("auto", ignoreCase = true) || v.isEmpty()) {
            return rules.flatByDefault to null
        }
        if (!Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(v)) {
            return rules.flatByDefault to "flat_by '$v' is not HH:MM: using ${rules.flatByDefault}"
        }
        if (v >= rules.squareOff) {
            return rules.flatByDefault to "flat_by $v is at or after Zerodha's ${rules.squareOff} MIS square-off: clamped to ${rules.flatByDefault}"
        }
        return v to null
    }

    fun sha256Hex16(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
