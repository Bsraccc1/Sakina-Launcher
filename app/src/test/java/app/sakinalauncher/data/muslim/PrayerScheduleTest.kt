package app.sakinalauncher.data.muslim

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class PrayerScheduleTest {
    private val schedule = PrayerSchedule(
        city = "KOTA JAKARTA",
        province = "DKI JAKARTA",
        dateLabel = "Sabtu, 06/06/2026",
        fetchedAtMillis = 0L,
        times = listOf(
            PrayerTime(PrayerName.FAJR, "04:37"),
            PrayerTime(PrayerName.DHUHR, "11:55"),
            PrayerTime(PrayerName.ASR, "15:16"),
            PrayerTime(PrayerName.MAGHRIB, "17:48"),
            PrayerTime(PrayerName.ISHA, "19:02"),
        )
    )

    @Test
    fun nextPrayerReturnsUpcomingPrayer() {
        assertEquals(PrayerName.ASR, schedule.nextPrayer(clockAt(12, 10)).name)
    }

    @Test
    fun nextPrayerKeepsPrayerAtExactStartTime() {
        assertEquals(PrayerName.DHUHR, schedule.nextPrayer(clockAt(11, 55)).name)
    }

    @Test
    fun nextPrayerWrapsToFirstPrayerAfterLastPrayer() {
        assertEquals(PrayerName.FAJR, schedule.nextPrayer(clockAt(20, 0)).name)
    }

    private fun clockAt(hour: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
    }
}
