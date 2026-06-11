package app.sakinalauncher.data.muslim

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

interface PrayerScheduleStore {
    var provider: PrayerProvider
    var cityQuery: String
    var cityId: String
    var cityLabel: String
    var autoDetectLocation: Boolean
    var globalLocationLabel: String
    var globalCountry: String
    var globalLatitude: Double
    var globalLongitude: Double
    var globalTimeZoneId: String
    var globalMethod: Int
    val activeCacheKey: String

    fun getCachedSchedule(): PrayerSchedule?
    fun getStaleCachedSchedule(): PrayerSchedule?
    fun saveSchedule(schedule: PrayerSchedule)
    fun saveSchedules(cacheKey: String, schedules: List<PrayerSchedule>)
    fun getCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule?
    fun getStaleCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule?
    fun isCacheFreshForDate(cacheKey: String, dateYmd: String, ttlMillis: Long): Boolean
}

class PrayerTimeStore(context: Context) : PrayerScheduleStore {
    private val prefs = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    override var provider: PrayerProvider
        get() = PrayerProvider.fromId(prefs.getString(KEY_PROVIDER, PrayerProvider.KEMENAG.id))
        set(value) = prefs.edit { putString(KEY_PROVIDER, value.id) }

    override var cityQuery: String
        get() = prefs.getString(KEY_CITY_QUERY, DEFAULT_CITY_QUERY).orEmpty().ifBlank { DEFAULT_CITY_QUERY }
        set(value) = prefs.edit { putString(KEY_CITY_QUERY, value.trim().ifBlank { DEFAULT_CITY_QUERY }) }

    override var cityId: String
        get() = prefs.getString(KEY_CITY_ID, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_CITY_ID, value) }

    override var cityLabel: String
        get() = prefs.getString(KEY_CITY_LABEL, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_CITY_LABEL, value) }

    override var autoDetectLocation: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT_LOCATION, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_DETECT_LOCATION, value) }

    override var globalLocationLabel: String
        get() = prefs.getString(KEY_GLOBAL_LOCATION_LABEL, DEFAULT_GLOBAL_LOCATION_LABEL).orEmpty()
            .ifBlank { DEFAULT_GLOBAL_LOCATION_LABEL }
        set(value) = prefs.edit {
            putString(KEY_GLOBAL_LOCATION_LABEL, value.trim().ifBlank { DEFAULT_GLOBAL_LOCATION_LABEL })
        }

    override var globalCountry: String
        get() = prefs.getString(KEY_GLOBAL_COUNTRY, DEFAULT_GLOBAL_COUNTRY).orEmpty()
            .ifBlank { DEFAULT_GLOBAL_COUNTRY }
        set(value) = prefs.edit {
            putString(KEY_GLOBAL_COUNTRY, value.trim().ifBlank { DEFAULT_GLOBAL_COUNTRY })
        }

    override var globalLatitude: Double
        get() = Double.fromBits(prefs.getLong(KEY_GLOBAL_LATITUDE, DEFAULT_GLOBAL_LATITUDE.toBits()))
        set(value) = prefs.edit { putLong(KEY_GLOBAL_LATITUDE, value.toBits()) }

    override var globalLongitude: Double
        get() = Double.fromBits(prefs.getLong(KEY_GLOBAL_LONGITUDE, DEFAULT_GLOBAL_LONGITUDE.toBits()))
        set(value) = prefs.edit { putLong(KEY_GLOBAL_LONGITUDE, value.toBits()) }

    override var globalTimeZoneId: String
        get() = prefs.getString(KEY_GLOBAL_TIME_ZONE_ID, DEFAULT_GLOBAL_TIME_ZONE_ID).orEmpty()
            .ifBlank { DEFAULT_GLOBAL_TIME_ZONE_ID }
        set(value) = prefs.edit {
            putString(KEY_GLOBAL_TIME_ZONE_ID, value.trim().ifBlank { DEFAULT_GLOBAL_TIME_ZONE_ID })
        }

    override var globalMethod: Int
        get() = prefs.getInt(KEY_GLOBAL_METHOD, DEFAULT_GLOBAL_METHOD)
        set(value) = prefs.edit { putInt(KEY_GLOBAL_METHOD, value) }

    override val activeCacheKey: String
        get() = when (provider) {
            PrayerProvider.KEMENAG -> "${provider.id}:${cityId.ifBlank { cityQuery }}"
            PrayerProvider.GLOBAL -> "${provider.id}:$globalLatitude:$globalLongitude:$globalMethod:$globalTimeZoneId"
        }

    override fun getCachedSchedule(): PrayerSchedule? {
        val today = todayYmd(currentTimeZoneId())
        return getCachedScheduleForDate(activeCacheKey, today)
            ?.takeIf { it.provider == provider && it.isFetchedToday() }
    }

    override fun getStaleCachedSchedule(): PrayerSchedule? {
        val today = todayYmd(currentTimeZoneId())
        return getStaleCachedScheduleForDate(activeCacheKey, today)
            ?.takeIf { it.provider == provider }
    }

    override fun saveSchedule(schedule: PrayerSchedule) {
        saveSchedules(schedule.cacheKey.ifBlank { activeCacheKey }, listOf(schedule))
    }

    override fun saveSchedules(cacheKey: String, schedules: List<PrayerSchedule>) {
        if (schedules.isEmpty()) return
        purgeOtherCacheKeys(cacheKey)
        prefs.edit {
            schedules.forEach { schedule ->
                val ymd = schedule.dateYmd.ifBlank { dateYmdFor(schedule) }
                if (ymd.isNotBlank()) {
                    putString(scheduleKey(cacheKey, ymd), encodeSchedule(schedule.copy(dateYmd = ymd)))
                }
            }
        }
        prefs.edit { putString(KEY_ACTIVE_CACHE_KEY, cacheKey) }
        enforceDateCap(cacheKey)
    }

    override fun getCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule? {
        return decodeSchedule(prefs.getString(scheduleKey(cacheKey, dateYmd), null))
    }

    override fun getStaleCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule? {
        return decodeSchedule(prefs.getString(scheduleKey(cacheKey, dateYmd), null))
    }

    override fun isCacheFreshForDate(cacheKey: String, dateYmd: String, ttlMillis: Long): Boolean {
        val schedule = getStaleCachedScheduleForDate(cacheKey, dateYmd) ?: return false
        return System.currentTimeMillis() - schedule.fetchedAtMillis <= ttlMillis
    }

    private fun scheduleKey(cacheKey: String, dateYmd: String): String {
        return "$KEY_SCHEDULE_PREFIX$cacheKey:$dateYmd"
    }

    private fun currentTimeZoneId(): String {
        return when (provider) {
            PrayerProvider.GLOBAL -> globalTimeZoneId
            PrayerProvider.KEMENAG -> getStaleCachedScheduleForDate(activeCacheKey, todayYmd("Asia/Jakarta"))
                ?.timeZoneId ?: "Asia/Jakarta"
        }
    }

    private fun todayYmd(timeZoneId: String): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = runCatching { TimeZone.getTimeZone(timeZoneId) }.getOrDefault(TimeZone.getDefault())
        return format.format(Calendar.getInstance().time)
    }

    private fun dateYmdFor(schedule: PrayerSchedule): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = runCatching { TimeZone.getTimeZone(schedule.timeZoneId) }
            .getOrDefault(TimeZone.getDefault())
        return format.format(Calendar.getInstance().apply { timeInMillis = schedule.fetchedAtMillis }.time)
    }

    private fun purgeOtherCacheKeys(cacheKey: String) {
        val previous = prefs.getString(KEY_ACTIVE_CACHE_KEY, null)
        if (previous == null || previous == cacheKey) return
        val stalePrefix = "$KEY_SCHEDULE_PREFIX$previous:"
        prefs.edit {
            prefs.all.keys.filter { it.startsWith(stalePrefix) }.forEach { remove(it) }
        }
    }

    private fun enforceDateCap(cacheKey: String) {
        val prefix = "$KEY_SCHEDULE_PREFIX$cacheKey:"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }.sorted()
        if (keys.size <= MAX_CACHED_DATES) return
        val toRemove = keys.subList(0, keys.size - MAX_CACHED_DATES)
        prefs.edit { toRemove.forEach { remove(it) } }
    }

    private fun encodeSchedule(schedule: PrayerSchedule): String {
        return JSONObject()
            .put("city", schedule.city)
            .put("province", schedule.province)
            .put("dateLabel", schedule.dateLabel)
            .put("fetchedAtMillis", schedule.fetchedAtMillis)
            .put("source", schedule.source)
            .put("provider", schedule.provider.id)
            .put("timeZoneId", schedule.timeZoneId)
            .put("cacheKey", schedule.cacheKey)
            .put("dateYmd", schedule.dateYmd)
            .put(
                "times",
                JSONArray().apply {
                    schedule.times.forEach { time ->
                        put(JSONObject().put("name", time.name.name).put("time", time.time))
                    }
                }
            )
            .toString()
    }

    private fun decodeSchedule(payload: String?): PrayerSchedule? {
        return runCatching {
            val json = JSONObject(payload ?: return null)
            val timesJson = json.optJSONArray("times") ?: return null
            val times = buildList {
                for (index in 0 until timesJson.length()) {
                    val item = timesJson.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    val time = item.optString("time")
                    val prayerName = PrayerName.fromStoredName(name)
                    if (prayerName != null && time.isNotBlank()) add(PrayerTime(prayerName, time))
                }
            }
            if (times.isEmpty()) return null
            PrayerSchedule(
                city = json.optString("city"),
                province = json.optString("province"),
                dateLabel = json.optString("dateLabel"),
                fetchedAtMillis = json.optLong("fetchedAtMillis", 0L),
                source = json.optString("source", "Bimas Islam Kemenag RI"),
                provider = PrayerProvider.fromId(json.optString("provider", PrayerProvider.KEMENAG.id)),
                timeZoneId = json.optString("timeZoneId", DEFAULT_GLOBAL_TIME_ZONE_ID),
                cacheKey = json.optString("cacheKey", ""),
                dateYmd = json.optString("dateYmd", ""),
                times = times,
            )
        }.getOrNull()
    }

    companion object {
        private const val PREFS_FILENAME = "app.sakinalauncher.muslim_center"
        private const val KEY_PROVIDER = "MUSLIM_CENTER_PROVIDER"
        private const val KEY_CITY_QUERY = "MUSLIM_CENTER_CITY_QUERY"
        private const val KEY_CITY_ID = "MUSLIM_CENTER_CITY_ID"
        private const val KEY_CITY_LABEL = "MUSLIM_CENTER_CITY_LABEL"
        private const val KEY_AUTO_DETECT_LOCATION = "MUSLIM_CENTER_AUTO_DETECT_LOCATION"
        private const val KEY_GLOBAL_LOCATION_LABEL = "MUSLIM_CENTER_GLOBAL_LOCATION_LABEL"
        private const val KEY_GLOBAL_COUNTRY = "MUSLIM_CENTER_GLOBAL_COUNTRY"
        private const val KEY_GLOBAL_LATITUDE = "MUSLIM_CENTER_GLOBAL_LATITUDE"
        private const val KEY_GLOBAL_LONGITUDE = "MUSLIM_CENTER_GLOBAL_LONGITUDE"
        private const val KEY_GLOBAL_TIME_ZONE_ID = "MUSLIM_CENTER_GLOBAL_TIME_ZONE_ID"
        private const val KEY_GLOBAL_METHOD = "MUSLIM_CENTER_GLOBAL_METHOD"
        private const val KEY_SCHEDULE_PREFIX = "schedule:"
        private const val KEY_ACTIVE_CACHE_KEY = "MUSLIM_CENTER_ACTIVE_CACHE_KEY"
        private const val MAX_CACHED_DATES = 14
        private const val DEFAULT_CITY_QUERY = "jakarta"
        private const val DEFAULT_GLOBAL_LOCATION_LABEL = "Mecca"
        private const val DEFAULT_GLOBAL_COUNTRY = "Saudi Arabia"
        private const val DEFAULT_GLOBAL_LATITUDE = 21.4225
        private const val DEFAULT_GLOBAL_LONGITUDE = 39.8262
        private const val DEFAULT_GLOBAL_TIME_ZONE_ID = "Asia/Riyadh"
        private const val DEFAULT_GLOBAL_METHOD = 3
    }
}
