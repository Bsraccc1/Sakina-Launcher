package app.sakinalauncher.data.muslim

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accuracy and robustness of the offline prayer time calculation.
 *
 * Reference values are real published schedules, fetched while writing these
 * tests and pasted verbatim:
 *  * Jakarta (KOTA JAKARTA, id 1301): Bimas Islam Kemenag RI, via
 *    https://api.myquran.com/v2/sholat/jadwal/1301/2026/<month>
 *  * Makkah: Umm al-Qura, via
 *    https://api.aladhan.com/v1/timings/<date>?latitude=21.4225&longitude=39.8262&method=4
 *
 * Tolerance is 1 minute for Jakarta (the schedule the app ships for Indonesia)
 * and 2 minutes for Makkah, where Umm al-Qura applies its own rounding.
 */
class PrayerTimeCalculatorTest {

    private val jakartaZone = TimeZone.getTimeZone("Asia/Jakarta")
    private val jakartaLat = -6.1751
    private val jakartaLng = 106.8650

    private val makkahZone = TimeZone.getTimeZone("Asia/Riyadh")
    private val makkahLat = 21.4225
    private val makkahLng = 39.8262

    private fun minutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun assertClose(expected: String, actual: String, toleranceMinutes: Int, label: String) {
        val delta = minutes(actual) - minutes(expected)
        assertTrue(
            "$label expected ~$expected but was $actual (off by $delta min)",
            kotlin.math.abs(delta) <= toleranceMinutes,
        )
    }

    @Test
    fun jakarta_matchesBimasIslamWithinOneMinute() {
        // date to (subuh, terbit, dzuhur, ashar, maghrib, isya)
        val reference = mapOf(
            Triple(2026, 1, 15) to listOf("04:27", "05:45", "12:05", "15:30", "18:18", "19:33"),
            Triple(2026, 6, 1) to listOf("04:36", "05:54", "11:54", "15:15", "17:47", "19:01"),
            Triple(2026, 6, 15) to listOf("04:39", "05:57", "11:57", "15:18", "17:49", "19:04"),
            Triple(2026, 12, 15) to listOf("04:10", "05:30", "11:51", "15:17", "18:05", "19:21"),
        )

        reference.forEach { (date, expected) ->
            val (year, month, day) = date
            val times = PrayerTimeCalculator.timesFor(
                year = year,
                month = month,
                day = day,
                latitude = jakartaLat,
                longitude = jakartaLng,
                timeZone = jakartaZone,
                method = PrayerTimeCalculator.Method.KEMENAG,
            )
            val label = "$year-$month-$day"
            assertClose(expected[0], times.fajr(), 1, "$label fajr")
            assertClose(expected[1], times.sunrise(), 1, "$label sunrise")
            assertClose(expected[2], times.dhuhr(), 1, "$label dhuhr")
            assertClose(expected[3], times.asr(), 1, "$label asr")
            assertClose(expected[4], times.maghrib(), 1, "$label maghrib")
            assertClose(expected[5], times.isha(), 1, "$label isha")
        }
    }

    @Test
    fun makkah_matchesUmmAlQura() {
        // Umm al-Qura: Isha is a fixed 90 minutes after Maghrib.
        val june = PrayerTimeCalculator.timesFor(
            year = 2026, month = 6, day = 1,
            latitude = makkahLat, longitude = makkahLng, timeZone = makkahZone,
            method = PrayerTimeCalculator.Method.UMM_AL_QURA,
        )
        assertClose("04:11", june.fajr(), 2, "makkah jun fajr")
        assertClose("05:38", june.sunrise(), 2, "makkah jun sunrise")
        assertClose("12:19", june.dhuhr(), 2, "makkah jun dhuhr")
        assertClose("15:35", june.asr(), 2, "makkah jun asr")
        assertClose("18:59", june.maghrib(), 2, "makkah jun maghrib")
        assertClose("20:29", june.isha(), 2, "makkah jun isha")

        val december = PrayerTimeCalculator.timesFor(
            year = 2026, month = 12, day = 15,
            latitude = makkahLat, longitude = makkahLng, timeZone = makkahZone,
            method = PrayerTimeCalculator.Method.UMM_AL_QURA,
        )
        assertClose("05:29", december.fajr(), 2, "makkah dec fajr")
        assertClose("12:16", december.dhuhr(), 2, "makkah dec dhuhr")
        assertClose("15:20", december.asr(), 2, "makkah dec asr")
        assertClose("17:41", december.maghrib(), 2, "makkah dec maghrib")
        assertClose("19:11", december.isha(), 2, "makkah dec isha")
    }

    @Test
    fun ummAlQura_ishaIsNinetyMinutesAfterMaghrib() {
        val times = PrayerTimeCalculator.timesFor(
            year = 2026, month = 6, day = 1,
            latitude = makkahLat, longitude = makkahLng, timeZone = makkahZone,
            method = PrayerTimeCalculator.Method.UMM_AL_QURA,
        )
        assertEquals(90, times.ishaMinutes - times.maghribMinutes)
    }

    @Test
    fun prayerOrderHoldsForAFullYearAtManyLatitudes() {
        val zone = TimeZone.getTimeZone("UTC")
        listOf(0.0, -6.2, 21.4, 51.5).forEach { latitude ->
            val cursor = Calendar.getInstance(zone).apply {
                clear()
                set(2026, Calendar.JANUARY, 1, 12, 0, 0)
            }
            repeat(365) {
                val times = PrayerTimeCalculator.timesFor(
                    year = cursor.get(Calendar.YEAR),
                    month = cursor.get(Calendar.MONTH) + 1,
                    day = cursor.get(Calendar.DAY_OF_MONTH),
                    latitude = latitude,
                    longitude = 0.0,
                    timeZone = zone,
                )
                val label = "lat=$latitude day=${cursor.get(Calendar.DAY_OF_YEAR)}"
                assertTrue("$label fajr<sunrise", times.fajrMinutes < times.sunriseMinutes)
                assertTrue("$label sunrise<dhuhr", times.sunriseMinutes < times.dhuhrMinutes)
                assertTrue("$label dhuhr<asr", times.dhuhrMinutes < times.asrMinutes)
                assertTrue("$label asr<maghrib", times.asrMinutes < times.maghribMinutes)
                assertTrue("$label maghrib<isha", times.maghribMinutes < times.ishaMinutes)
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    @Test
    fun extremeLatitudesStayFiniteAllYear() {
        val zone = TimeZone.getTimeZone("UTC")
        listOf(64.0, 78.0, -70.0).forEach { latitude ->
            val cursor = Calendar.getInstance(zone).apply {
                clear()
                set(2026, Calendar.JANUARY, 1, 12, 0, 0)
            }
            repeat(365) {
                val times = PrayerTimeCalculator.timesFor(
                    year = cursor.get(Calendar.YEAR),
                    month = cursor.get(Calendar.MONTH) + 1,
                    day = cursor.get(Calendar.DAY_OF_MONTH),
                    latitude = latitude,
                    longitude = 0.0,
                    timeZone = zone,
                )
                val all = listOf(
                    times.fajrMinutes, times.sunriseMinutes, times.dhuhrMinutes,
                    times.asrMinutes, times.maghribMinutes, times.ishaMinutes,
                )
                all.forEach { minute ->
                    assertTrue("lat=$latitude produced $minute", minute in 0 until 24 * 60)
                }
                times.toPrayerTimes().forEach { prayer ->
                    assertTrue("blank time at lat=$latitude", prayer.time.isNotBlank())
                    assertTrue("unparsable ${prayer.time}", prayer.minuteOfDay != Int.MAX_VALUE)
                }
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    @Test
    fun oneYearOfSchedulesNeedsNoNetwork() {
        val start = Calendar.getInstance(jakartaZone).apply {
            clear()
            set(2026, Calendar.JANUARY, 1, 12, 0, 0)
        }.timeInMillis

        val year = PrayerTimeCalculator.timesForRange(
            startMillis = start,
            days = 365,
            latitude = jakartaLat,
            longitude = jakartaLng,
            timeZone = jakartaZone,
            method = PrayerTimeCalculator.Method.KEMENAG,
        )

        assertEquals(365, year.size)
        assertTrue(year.all { it.toPrayerTimes().size == 5 })
        assertFalse("Jakarta must never need the polar fallback", year.any { it.usedHighLatitudeFallback })
        // Distinct days must produce distinct schedules — a constant answer would
        // mean the date was being ignored.
        assertTrue(year.map { it.fajrMinutes }.distinct().size > 20)
    }

    @Test
    fun hanafiAsrIsLaterThanStandard() {
        val standard = PrayerTimeCalculator.timesFor(
            year = 2026, month = 6, day = 1,
            latitude = jakartaLat, longitude = jakartaLng, timeZone = jakartaZone,
            asrMethod = PrayerTimeCalculator.AsrMethod.STANDARD,
        )
        val hanafi = PrayerTimeCalculator.timesFor(
            year = 2026, month = 6, day = 1,
            latitude = jakartaLat, longitude = jakartaLng, timeZone = jakartaZone,
            asrMethod = PrayerTimeCalculator.AsrMethod.HANAFI,
        )
        assertTrue(
            "Hanafi Asr ${hanafi.asr()} should fall after standard ${standard.asr()}",
            hanafi.asrMinutes > standard.asrMinutes,
        )
        assertTrue(hanafi.asrMinutes < hanafi.maghribMinutes)
    }

    @Test
    fun kemenagFajrIsEarlierThanMwl() {
        // MABIMS uses a 20 degree Fajr angle against MWL's 18, so Subuh comes sooner.
        val kemenag = PrayerTimeCalculator.timesFor(
            year = 2026, month = 6, day = 1,
            latitude = jakartaLat, longitude = jakartaLng, timeZone = jakartaZone,
            method = PrayerTimeCalculator.Method.KEMENAG,
        )
        val mwl = PrayerTimeCalculator.timesFor(
            year = 2026, month = 6, day = 1,
            latitude = jakartaLat, longitude = jakartaLng, timeZone = jakartaZone,
            method = PrayerTimeCalculator.Method.MWL,
        )
        assertTrue(kemenag.fajrMinutes < mwl.fajrMinutes)
    }

    @Test
    fun methodAndAsrIdsRoundTrip() {
        PrayerTimeCalculator.Method.entries.forEach {
            assertEquals(it, PrayerTimeCalculator.Method.fromId(it.id))
        }
        PrayerTimeCalculator.AsrMethod.entries.forEach {
            assertEquals(it, PrayerTimeCalculator.AsrMethod.fromId(it.id))
        }
        assertEquals(PrayerTimeCalculator.Method.MWL, PrayerTimeCalculator.Method.fromId("nonsense"))
        assertEquals(PrayerTimeCalculator.AsrMethod.STANDARD, PrayerTimeCalculator.AsrMethod.fromId(null))
    }

    @Test
    fun timesAreFormattedAsZeroPaddedClockTimes() {
        val times = PrayerTimeCalculator.timesFor(
            year = 2026, month = 6, day = 1,
            latitude = jakartaLat, longitude = jakartaLng, timeZone = jakartaZone,
            method = PrayerTimeCalculator.Method.KEMENAG,
        )
        listOf(times.fajr(), times.sunrise(), times.dhuhr(), times.asr(), times.maghrib(), times.isha())
            .forEach { value ->
                assertTrue("badly formatted: $value", Regex("^\\d{2}:\\d{2}$").matches(value))
            }
    }

    @Test
    fun daylightSavingIsHonoured() {
        // London: 1 June is BST (UTC+1), 1 January is GMT. If the DST offset were
        // ignored the June times would be an hour off.
        val london = TimeZone.getTimeZone("Europe/London")
        val summer = PrayerTimeCalculator.timesFor(
            year = 2026, month = 6, day = 1,
            latitude = 51.5074, longitude = -0.1278, timeZone = london,
        )
        // Solar noon in London in June sits around 13:00 local because of BST.
        assertTrue(
            "summer dhuhr was ${summer.dhuhr()}",
            summer.dhuhrMinutes in minutes("12:45")..minutes("13:15"),
        )
        val winter = PrayerTimeCalculator.timesFor(
            year = 2026, month = 1, day = 1,
            latitude = 51.5074, longitude = -0.1278, timeZone = london,
        )
        assertTrue(
            "winter dhuhr was ${winter.dhuhr()}",
            winter.dhuhrMinutes in minutes("11:45")..minutes("12:15"),
        )
    }
}
