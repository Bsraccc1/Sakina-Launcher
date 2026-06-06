package app.sakinalauncher.data.muslim

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class PrayerProvider(val id: String) {
    KEMENAG("kemenag"),
    GLOBAL("global");

    companion object {
        fun fromId(value: String?): PrayerProvider {
            return entries.firstOrNull { it.id == value } ?: KEMENAG
        }
    }
}

enum class PrayerName {
    FAJR,
    DHUHR,
    ASR,
    MAGHRIB,
    ISHA;

    companion object {
        fun fromStoredName(value: String): PrayerName? {
            return when (value.uppercase(Locale.US)) {
                "FAJR", "SUBUH" -> FAJR
                "DHUHR", "DZUHUR", "ZUHUR", "ZUHR" -> DHUHR
                "ASR", "ASHAR" -> ASR
                "MAGHRIB" -> MAGHRIB
                "ISHA", "ISYA" -> ISHA
                else -> null
            }
        }
    }
}

data class PrayerTime(
    val name: PrayerName,
    val time: String,
) {
    val minuteOfDay: Int
        get() {
            val parts = time.split(":")
            if (parts.size != 2) return Int.MAX_VALUE
            val hour = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
            val minute = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
            return hour * 60 + minute
        }
}

data class PrayerCity(
    val id: String,
    val label: String,
)

data class PrayerSchedule(
    val city: String,
    val province: String,
    val dateLabel: String,
    val fetchedAtMillis: Long,
    val times: List<PrayerTime>,
    val source: String = "Bimas Islam Kemenag RI",
    val provider: PrayerProvider = PrayerProvider.KEMENAG,
    val timeZoneId: String = "Asia/Jakarta",
    val cacheKey: String = "",
) {
    fun nextPrayer(now: Calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId))): PrayerTime {
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return times.firstOrNull { it.minuteOfDay >= nowMinutes } ?: times.first()
    }

    fun updatedLabel(locale: Locale = Locale.getDefault()): String {
        val format = SimpleDateFormat("HH:mm", locale)
        return format.format(fetchedAtMillis)
    }

    fun isFetchedToday(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val zone = runCatching { TimeZone.getTimeZone(timeZoneId) }.getOrDefault(TimeZone.getDefault())
        val fetched = Calendar.getInstance(zone).apply { timeInMillis = fetchedAtMillis }
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        return fetched.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            fetched.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    }
}

sealed class PrayerScheduleResult {
    data class Fresh(val schedule: PrayerSchedule) : PrayerScheduleResult()
    data class Cached(val schedule: PrayerSchedule, val error: String) : PrayerScheduleResult()
    data class Error(val message: String) : PrayerScheduleResult()
}
