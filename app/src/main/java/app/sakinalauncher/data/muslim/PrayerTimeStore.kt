package app.sakinalauncher.data.muslim

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

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

    /**
     * Identity of the location a stored schedule belongs to.
     *
     * Derived here rather than left to implementors: the rule was re-implemented in the
     * real store and in three test doubles, and one double disagreed with production
     * (it omitted `globalMethod` and `globalTimeZoneId`), so a test could pass against a
     * key shape that never occurs on a device.
     *
     * Load-bearing caveat for callers: for [PrayerProvider.KEMENAG] the resolved
     * [cityId] takes precedence over [cityQuery], so once an id is on disk this key does
     * **not** change when the city label does. Do not use "the key changed" as a proxy
     * for "the user moved" — compare the resolved [PrayerOfflineLocations.Place] instead.
     * That confusion served one city's prayer times under another city's name.
     */
    val activeCacheKey: String
        get() = when (provider) {
            PrayerProvider.KEMENAG -> "${provider.id}:${cityId.ifBlank { cityQuery }}"
            PrayerProvider.GLOBAL ->
                "${provider.id}:$globalLatitude:$globalLongitude:$globalMethod:$globalTimeZoneId"
        }

    fun getCachedSchedule(): PrayerSchedule?
    fun getStaleCachedSchedule(): PrayerSchedule?
    fun saveSchedule(schedule: PrayerSchedule)
    fun saveSchedules(cacheKey: String, schedules: List<PrayerSchedule>)
    fun getCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule?
    fun isCacheFreshForDate(cacheKey: String, dateYmd: String, ttlMillis: Long): Boolean
}

class PrayerTimeStore(context: Context) : PrayerScheduleStore {
    private val prefs = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    /**
     * Formatter and decoded-schedule caches. Extracted so their concurrency contract is
     * pinned by [PrayerScheduleCacheTest]: this store is shared between the main thread
     * and `Dispatchers.IO`, and both caches replaced per-call construction that was
     * thread-confined by accident.
     */
    private val cache = PrayerScheduleCache(MAX_CACHED_DATES)

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

    /**
     * On by default: a fresh install should resolve the user's own city as soon as
     * location permission allows, instead of silently sitting on the Jakarta
     * default until they find this switch in Settings.
     */
    override var autoDetectLocation: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT_LOCATION, true)
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

    override fun getCachedSchedule(): PrayerSchedule? {
        val today = todayYmd(currentTimeZoneId())
        return getCachedScheduleForDate(activeCacheKey, today)
            ?.takeIf { it.provider == provider && it.isFetchedToday() }
    }

    override fun getStaleCachedSchedule(): PrayerSchedule? {
        val today = todayYmd(currentTimeZoneId())
        return getCachedScheduleForDate(activeCacheKey, today)
            ?.takeIf { it.provider == provider }
    }

    override fun saveSchedule(schedule: PrayerSchedule) {
        saveSchedules(schedule.cacheKey.ifBlank { activeCacheKey }, listOf(schedule))
    }

    override fun saveSchedules(cacheKey: String, schedules: List<PrayerSchedule>) {
        if (schedules.isEmpty()) return
        purgeOtherCacheKeys(cacheKey)
        val stored = HashMap<String, PrayerSchedule>(schedules.size)
        prefs.edit {
            schedules.forEach { schedule ->
                val ymd = schedule.dateYmd.ifBlank { dateYmdFor(schedule) }
                if (ymd.isNotBlank()) {
                    val entry = schedule.copy(dateYmd = ymd)
                    val key = scheduleKey(cacheKey, ymd)
                    putString(key, encodeSchedule(entry))
                    stored[key] = entry
                }
            }
        }
        // Keep the decode cache in step, or the next read serves the old copy.
        cache.putAll(stored)
        prefs.edit { putString(KEY_ACTIVE_CACHE_KEY, cacheKey) }
        enforceDateCap(cacheKey)
    }

    override fun getCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule? {
        return readSchedule(scheduleKey(cacheKey, dateYmd))
    }

    /** Memoized decode — a cache read used to re-parse a ~450 byte JSON document. */
    private fun readSchedule(key: String): PrayerSchedule? {
        cache.get(key)?.let { return it }
        val decoded = decodeSchedule(prefs.getString(key, null)) ?: return null
        cache.put(key, decoded)
        return decoded
    }

    override fun isCacheFreshForDate(cacheKey: String, dateYmd: String, ttlMillis: Long): Boolean {
        val schedule = getCachedScheduleForDate(cacheKey, dateYmd) ?: return false
        return System.currentTimeMillis() - schedule.fetchedAtMillis <= ttlMillis
    }

    private fun scheduleKey(cacheKey: String, dateYmd: String): String {
        return "$KEY_SCHEDULE_PREFIX$cacheKey:$dateYmd"
    }

    private fun currentTimeZoneId(): String {
        return when (provider) {
            PrayerProvider.GLOBAL -> globalTimeZoneId
            PrayerProvider.KEMENAG -> getCachedScheduleForDate(activeCacheKey, todayYmd("Asia/Jakarta"))
                ?.timeZoneId ?: "Asia/Jakarta"
        }
    }

    private fun todayYmd(timeZoneId: String): String {
        return cache.formatYmd(timeZoneId, System.currentTimeMillis())
    }

    private fun dateYmdFor(schedule: PrayerSchedule): String {
        return cache.formatYmd(schedule.timeZoneId, schedule.fetchedAtMillis)
    }

    private fun purgeOtherCacheKeys(cacheKey: String) {
        val previous = prefs.getString(KEY_ACTIVE_CACHE_KEY, null)
        if (previous == null || previous == cacheKey) return
        val stalePrefix = "$KEY_SCHEDULE_PREFIX$previous:"
        val stale = prefs.all.keys.filter { it.startsWith(stalePrefix) }
        prefs.edit { stale.forEach { remove(it) } }
        cache.removeAll(stale)
    }

    /**
     * Trim the cache to [MAX_CACHED_DATES] entries, keeping the ones that will actually
     * be read.
     *
     * Keys sort chronologically, so the previous `subList(0, size - cap)` dropped the
     * *earliest* dates — after a year-ahead warm that meant keeping only the twelve
     * months out and deleting **today**. Every read then missed, `isCacheFreshForDate`
     * always returned false, and the TTL protected nothing: each open refetched.
     *
     * Today and the near future are what the UI asks for, so past dates go first and
     * only then the far end of the future.
     */
    private fun enforceDateCap(cacheKey: String) {
        val prefix = "$KEY_SCHEDULE_PREFIX$cacheKey:"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }.sorted()
        if (keys.size <= MAX_CACHED_DATES) return

        val today = todayYmd(currentTimeZoneId())
        val past = keys.filter { it.removePrefix(prefix) < today }
        val todayOnward = keys.filter { it.removePrefix(prefix) >= today }

        val toRemove = mutableListOf<String>()
        // Past days are never read back; drop them all before touching the future.
        toRemove += past
        if (todayOnward.size > MAX_CACHED_DATES) {
            toRemove += todayOnward.subList(MAX_CACHED_DATES, todayOnward.size)
        } else {
            // Room left over — keep the most recent past days to fill the window.
            val spare = MAX_CACHED_DATES - todayOnward.size
            if (spare > 0 && past.isNotEmpty()) {
                toRemove -= past.takeLast(spare.coerceAtMost(past.size)).toSet()
            }
        }
        if (toRemove.isEmpty()) return
        prefs.edit { toRemove.forEach { remove(it) } }
        cache.removeAll(toRemove)
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
