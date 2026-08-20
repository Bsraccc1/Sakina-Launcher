package app.sakinalauncher.data.muslim

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline prayer time calculation.
 *
 * The launcher must never be unable to show prayer times just because the phone
 * has no signal, so the times are computed locally from the sun's position for
 * any date and any coordinate. The network is only ever a nicety.
 *
 * Algorithm: standard low-precision solar position (Meeus / US Naval Observatory
 * almanac form) -> declination + equation of time -> solar transit for Dhuhr,
 * hour angles for the remaining prayers.
 *
 * Deliberately built on [Calendar]/[TimeZone] rather than java.time so it works
 * on minSdk 24 without core library desugaring.
 *
 * Accuracy, verified against published tables (see PrayerTimeCalculatorTest):
 *  * Jakarta, all 365 days of 2026 vs Bimas Islam Kemenag: within 1 minute for
 *    every prayer.
 *  * Makkah vs Umm al-Qura: within 1 minute.
 */
object PrayerTimeCalculator {

    /** Angles and Isha rules of the well-known calculation conventions. */
    enum class Method(
        val id: String,
        val label: String,
        val fajrAngle: Double,
        val ishaAngle: Double,
        /** When > 0, Isha is a fixed interval after Maghrib instead of an angle. */
        val ishaMinutesAfterMaghrib: Int = 0,
        /** Kemenag publishes times with a safety margin ("ihtiyati") baked in. */
        val ihtiyati: Ihtiyati = Ihtiyati.NONE,
    ) {
        /** Muslim World League — the sensible worldwide default. */
        MWL("mwl", "Muslim World League", 18.0, 17.0),

        /** Indonesia (Kemenag / Bimas Islam, MABIMS angles + published margins). */
        KEMENAG("kemenag", "Kemenag RI (MABIMS)", 20.0, 18.0, ihtiyati = Ihtiyati.KEMENAG),

        /** Saudi Arabia: Isha is 90 minutes after Maghrib. */
        UMM_AL_QURA("ummalqura", "Umm al-Qura", 18.5, 0.0, ishaMinutesAfterMaghrib = 90),

        /** Egyptian General Authority of Survey. */
        EGYPTIAN("egyptian", "Egyptian General Authority", 19.5, 17.5),

        /** Islamic Society of North America. */
        ISNA("isna", "ISNA", 15.0, 15.0),
        ;

        companion object {
            fun fromId(value: String?): Method =
                entries.firstOrNull { it.id == value } ?: MWL
        }
    }

    /**
     * Per-prayer safety margin in minutes. Kemenag's published schedule is not the
     * raw astronomical time: it is rounded up with a small margin so that praying
     * exactly on the printed minute is always inside the valid window.
     */
    data class Ihtiyati(
        val fajr: Int,
        val sunrise: Int,
        val dhuhr: Int,
        val asr: Int,
        val maghrib: Int,
        val isha: Int,
    ) {
        companion object {
            val NONE = Ihtiyati(0, 0, 0, 0, 0, 0)

            /**
             * Reproduces the Bimas Islam tables to within one minute for every prayer
             * on all 365 days of 2026 (checked against api.myquran.com id 1301).
             * Sunrise is the one time pushed *earlier*: it ends Fajr, so a safety
             * margin there has to shorten the window, not extend it.
             */
            val KEMENAG = Ihtiyati(fajr = 2, sunrise = -3, dhuhr = 3, asr = 2, maghrib = 3, isha = 2)
        }
    }

    /** Shadow length factor for Asr. */
    enum class AsrMethod(val id: String, val shadowFactor: Double) {
        /** Shafi'i, Maliki, Hanbali: shadow equals object length. */
        STANDARD("standard", 1.0),

        /** Hanafi: shadow equals twice the object length. */
        HANAFI("hanafi", 2.0),
        ;

        companion object {
            fun fromId(value: String?): AsrMethod =
                entries.firstOrNull { it.id == value } ?: STANDARD
        }
    }

    /**
     * How a prayer at an extreme latitude was resolved when the sun never reaches
     * the required twilight angle (polar summer / winter).
     */
    enum class HighLatitudeRule(val id: String) {
        /** Split the night into sevenths — the common, conservative choice. */
        ONE_SEVENTH_NIGHT("one_seventh"),

        /** Divide the night by the twilight angle. */
        ANGLE_BASED("angle_based"),

        /** Half of the night. */
        MIDNIGHT("midnight"),
        ;

        companion object {
            fun fromId(value: String?): HighLatitudeRule =
                entries.firstOrNull { it.id == value } ?: ONE_SEVENTH_NIGHT
        }
    }

    /**
     * Prayer times for one day, as minutes after local midnight. Always finite:
     * extreme latitudes fall back to [HighLatitudeRule] instead of producing NaN.
     */
    data class DayTimes(
        val fajrMinutes: Int,
        val sunriseMinutes: Int,
        val dhuhrMinutes: Int,
        val asrMinutes: Int,
        val maghribMinutes: Int,
        val ishaMinutes: Int,
        /** True when any prayer needed a high-latitude fallback. */
        val usedHighLatitudeFallback: Boolean,
    ) {
        fun fajr(): String = formatMinutes(fajrMinutes)
        fun sunrise(): String = formatMinutes(sunriseMinutes)
        fun dhuhr(): String = formatMinutes(dhuhrMinutes)
        fun asr(): String = formatMinutes(asrMinutes)
        fun maghrib(): String = formatMinutes(maghribMinutes)
        fun isha(): String = formatMinutes(ishaMinutes)

        /** In the order the day runs, ready for [PrayerSchedule]. */
        fun toPrayerTimes(): List<PrayerTime> = listOf(
            PrayerTime(PrayerName.FAJR, fajr()),
            PrayerTime(PrayerName.DHUHR, dhuhr()),
            PrayerTime(PrayerName.ASR, asr()),
            PrayerTime(PrayerName.MAGHRIB, maghrib()),
            PrayerTime(PrayerName.ISHA, isha()),
        )
    }

    /**
     * Prayer times for [year]/[month]/[day] (month is 1-based) at [latitude] /
     * [longitude], expressed in [timeZone].
     *
     * @param elevationMeters observer height, used for the horizon dip.
     */
    fun timesFor(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        method: Method = Method.MWL,
        asrMethod: AsrMethod = AsrMethod.STANDARD,
        highLatitudeRule: HighLatitudeRule = HighLatitudeRule.ONE_SEVENTH_NIGHT,
        elevationMeters: Double = 0.0,
    ): DayTimes {
        val utcOffsetHours = utcOffsetHours(year, month, day, timeZone)
        // Evaluate the sun at local noon, not at 00:00 UTC, so the declination used
        // is the one that actually applies to this location's day.
        val julianDay = julianDay(year, month, day) - longitude / (15.0 * 24.0)
        val declination = sunDeclination(julianDay)
        val equationOfTime = equationOfTime(julianDay)

        val solarNoon = 12.0 + utcOffsetHours - longitude / 15.0 - equationOfTime
        val horizonDip = 0.833 + 0.0347 * kotlin.math.sqrt(elevationMeters.coerceAtLeast(0.0))

        val sunriseHa = hourAngle(-horizonDip, latitude, declination)
        val fajrHa = hourAngle(-method.fajrAngle, latitude, declination)
        val ishaHa = if (method.ishaMinutesAfterMaghrib > 0) {
            null
        } else {
            hourAngle(-method.ishaAngle, latitude, declination)
        }

        // Asr: the sun's altitude where a shadow reaches the mazhab's factor.
        val asrAltitude = toDegrees(
            atan(1.0 / (asrMethod.shadowFactor + tan(toRadians(abs(latitude - declination)))))
        )
        val asrHa = hourAngle(asrAltitude, latitude, declination)

        var fallback = false

        // Sunrise/sunset can genuinely not happen inside the polar circles. Use the
        // solar-noon-relative bounds of a civil day so the ordering still holds.
        val sunriseHours: Double
        val maghribHours: Double
        if (sunriseHa != null) {
            sunriseHours = solarNoon - sunriseHa
            maghribHours = solarNoon + sunriseHa
        } else {
            fallback = true
            val polarDay = isPolarDay(latitude, declination)
            // Polar day: treat the sun as just grazing the horizon; polar night: a
            // nominal short day. Either way the times stay ordered and finite.
            val halfDay = if (polarDay) 11.5 else 0.5
            sunriseHours = solarNoon - halfDay
            maghribHours = solarNoon + halfDay
        }

        val nightLength = 24.0 - (maghribHours - sunriseHours)

        val fajrHours = if (fajrHa != null) {
            solarNoon - fajrHa
        } else {
            fallback = true
            sunriseHours - nightPortion(highLatitudeRule, method.fajrAngle, nightLength)
        }

        val ishaHours = when {
            method.ishaMinutesAfterMaghrib > 0 ->
                maghribHours + method.ishaMinutesAfterMaghrib / 60.0
            ishaHa != null -> solarNoon + ishaHa
            else -> {
                fallback = true
                maghribHours + nightPortion(highLatitudeRule, method.ishaAngle, nightLength)
            }
        }

        val asrHours = if (asrHa != null) {
            solarNoon + asrHa
        } else {
            fallback = true
            // Sun never gets that low: put Asr midway between Dhuhr and Maghrib.
            (solarNoon + maghribHours) / 2.0
        }

        val iht = method.ihtiyati
        return DayTimes(
            fajrMinutes = roundUpMinutes(fajrHours, iht.fajr),
            sunriseMinutes = roundUpMinutes(sunriseHours, iht.sunrise),
            dhuhrMinutes = roundUpMinutes(solarNoon, iht.dhuhr),
            asrMinutes = roundUpMinutes(asrHours, iht.asr),
            maghribMinutes = roundUpMinutes(maghribHours, iht.maghrib),
            ishaMinutes = roundUpMinutes(ishaHours, iht.isha),
            usedHighLatitudeFallback = fallback,
        )
    }

    /** Convenience overload for a timestamp. */
    fun timesFor(
        dateMillis: Long,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        method: Method = Method.MWL,
        asrMethod: AsrMethod = AsrMethod.STANDARD,
        highLatitudeRule: HighLatitudeRule = HighLatitudeRule.ONE_SEVENTH_NIGHT,
        elevationMeters: Double = 0.0,
    ): DayTimes {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = dateMillis }
        return timesFor(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            latitude = latitude,
            longitude = longitude,
            timeZone = timeZone,
            method = method,
            asrMethod = asrMethod,
            highLatitudeRule = highLatitudeRule,
            elevationMeters = elevationMeters,
        )
    }

    /**
     * [days] consecutive days of prayer times starting at [startMillis]. This is
     * what makes a year of offline schedule possible: 365 days cost one call and
     * no network at all.
     */
    fun timesForRange(
        startMillis: Long,
        days: Int,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        method: Method = Method.MWL,
        asrMethod: AsrMethod = AsrMethod.STANDARD,
        highLatitudeRule: HighLatitudeRule = HighLatitudeRule.ONE_SEVENTH_NIGHT,
        elevationMeters: Double = 0.0,
    ): List<DayTimes> {
        if (days <= 0) return emptyList()
        val cursor = Calendar.getInstance(timeZone).apply {
            timeInMillis = startMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return buildList(days) {
            repeat(days) {
                add(
                    timesFor(
                        year = cursor.get(Calendar.YEAR),
                        month = cursor.get(Calendar.MONTH) + 1,
                        day = cursor.get(Calendar.DAY_OF_MONTH),
                        latitude = latitude,
                        longitude = longitude,
                        timeZone = timeZone,
                        method = method,
                        asrMethod = asrMethod,
                        highLatitudeRule = highLatitudeRule,
                        elevationMeters = elevationMeters,
                    )
                )
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    // ---------------------------------------------------------------- internals

    /** Julian day for 00:00 UT of the given civil date. */
    internal fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    internal fun sunDeclination(julianDay: Double): Double {
        val d = julianDay - 2451545.0
        val meanAnomaly = normalizeAngle(357.529 + 0.98560028 * d)
        val meanLongitude = normalizeAngle(280.459 + 0.98564736 * d)
        val eclipticLongitude = normalizeAngle(
            meanLongitude + 1.915 * sin(toRadians(meanAnomaly)) + 0.020 * sin(toRadians(2 * meanAnomaly))
        )
        val obliquity = 23.439 - 0.00000036 * d
        return toDegrees(asin(sin(toRadians(obliquity)) * sin(toRadians(eclipticLongitude))))
    }

    internal fun equationOfTime(julianDay: Double): Double {
        val d = julianDay - 2451545.0
        val meanAnomaly = normalizeAngle(357.529 + 0.98560028 * d)
        val meanLongitude = normalizeAngle(280.459 + 0.98564736 * d)
        val eclipticLongitude = normalizeAngle(
            meanLongitude + 1.915 * sin(toRadians(meanAnomaly)) + 0.020 * sin(toRadians(2 * meanAnomaly))
        )
        val obliquity = 23.439 - 0.00000036 * d
        val rightAscension = normalizeHours(
            toDegrees(
                atan2(
                    cos(toRadians(obliquity)) * sin(toRadians(eclipticLongitude)),
                    cos(toRadians(eclipticLongitude)),
                )
            ) / 15.0
        )
        return meanLongitude / 15.0 - rightAscension
    }

    /**
     * Hours from solar noon at which the sun sits at [altitude] degrees, or null
     * when the sun never reaches it on that day at that latitude.
     */
    internal fun hourAngle(altitude: Double, latitude: Double, declination: Double): Double? {
        val numerator = sin(toRadians(altitude)) - sin(toRadians(latitude)) * sin(toRadians(declination))
        val denominator = cos(toRadians(latitude)) * cos(toRadians(declination))
        if (denominator == 0.0) return null
        val cosine = numerator / denominator
        if (cosine > 1.0 || cosine < -1.0) return null
        return toDegrees(acos(cosine)) / 15.0
    }

    private fun isPolarDay(latitude: Double, declination: Double): Boolean =
        (latitude >= 0 && declination > 0) || (latitude < 0 && declination < 0)

    private fun nightPortion(rule: HighLatitudeRule, angle: Double, nightLength: Double): Double =
        when (rule) {
            HighLatitudeRule.ONE_SEVENTH_NIGHT -> nightLength / 7.0
            HighLatitudeRule.ANGLE_BASED -> nightLength * (angle.coerceAtLeast(1.0) / 60.0)
            HighLatitudeRule.MIDNIGHT -> nightLength / 2.0
        }

    /**
     * Published tables round *up* to the next whole minute: praying one second
     * before the true time would be invalid, one second after is fine.
     */
    private fun roundUpMinutes(hours: Double, marginMinutes: Int): Int {
        val total = ceil(hours * 60.0 - 1e-9).toInt() + marginMinutes
        val dayMinutes = 24 * 60
        return ((total % dayMinutes) + dayMinutes) % dayMinutes
    }

    /** Offset including DST for that particular date, not just the zone's raw offset. */
    private fun utcOffsetHours(year: Int, month: Int, day: Int, timeZone: TimeZone): Double {
        val calendar = GregorianCalendar(timeZone).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }
        return timeZone.getOffset(calendar.timeInMillis) / 3_600_000.0
    }

    private fun normalizeAngle(degrees: Double): Double = degrees - 360.0 * floor(degrees / 360.0)

    private fun normalizeHours(hours: Double): Double = hours - 24.0 * floor(hours / 24.0)

    private fun toRadians(degrees: Double): Double = degrees * Math.PI / 180.0

    private fun toDegrees(radians: Double): Double = radians * 180.0 / Math.PI

    internal fun formatMinutes(minuteOfDay: Int): String {
        val dayMinutes = 24 * 60
        val normalized = ((minuteOfDay % dayMinutes) + dayMinutes) % dayMinutes
        val hour = normalized / 60
        val minute = normalized % 60
        return buildString {
            if (hour < 10) append('0')
            append(hour)
            append(':')
            if (minute < 10) append('0')
            append(minute)
        }
    }
}
