package app.olauncher.data.muslim

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class PrayerTimeStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    var provider: PrayerProvider
        get() = PrayerProvider.fromId(prefs.getString(KEY_PROVIDER, PrayerProvider.KEMENAG.id))
        set(value) = prefs.edit { putString(KEY_PROVIDER, value.id) }

    var cityQuery: String
        get() = prefs.getString(KEY_CITY_QUERY, DEFAULT_CITY_QUERY).orEmpty().ifBlank { DEFAULT_CITY_QUERY }
        set(value) = prefs.edit { putString(KEY_CITY_QUERY, value.trim().ifBlank { DEFAULT_CITY_QUERY }) }

    var cityId: String
        get() = prefs.getString(KEY_CITY_ID, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_CITY_ID, value) }

    var cityLabel: String
        get() = prefs.getString(KEY_CITY_LABEL, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_CITY_LABEL, value) }

    var autoDetectLocation: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT_LOCATION, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_DETECT_LOCATION, value) }

    var globalLocationLabel: String
        get() = prefs.getString(KEY_GLOBAL_LOCATION_LABEL, DEFAULT_GLOBAL_LOCATION_LABEL).orEmpty()
            .ifBlank { DEFAULT_GLOBAL_LOCATION_LABEL }
        set(value) = prefs.edit {
            putString(KEY_GLOBAL_LOCATION_LABEL, value.trim().ifBlank { DEFAULT_GLOBAL_LOCATION_LABEL })
        }

    var globalCountry: String
        get() = prefs.getString(KEY_GLOBAL_COUNTRY, DEFAULT_GLOBAL_COUNTRY).orEmpty()
            .ifBlank { DEFAULT_GLOBAL_COUNTRY }
        set(value) = prefs.edit {
            putString(KEY_GLOBAL_COUNTRY, value.trim().ifBlank { DEFAULT_GLOBAL_COUNTRY })
        }

    var globalLatitude: Double
        get() = Double.fromBits(prefs.getLong(KEY_GLOBAL_LATITUDE, DEFAULT_GLOBAL_LATITUDE.toBits()))
        set(value) = prefs.edit { putLong(KEY_GLOBAL_LATITUDE, value.toBits()) }

    var globalLongitude: Double
        get() = Double.fromBits(prefs.getLong(KEY_GLOBAL_LONGITUDE, DEFAULT_GLOBAL_LONGITUDE.toBits()))
        set(value) = prefs.edit { putLong(KEY_GLOBAL_LONGITUDE, value.toBits()) }

    var globalTimeZoneId: String
        get() = prefs.getString(KEY_GLOBAL_TIME_ZONE_ID, DEFAULT_GLOBAL_TIME_ZONE_ID).orEmpty()
            .ifBlank { DEFAULT_GLOBAL_TIME_ZONE_ID }
        set(value) = prefs.edit {
            putString(KEY_GLOBAL_TIME_ZONE_ID, value.trim().ifBlank { DEFAULT_GLOBAL_TIME_ZONE_ID })
        }

    var globalMethod: Int
        get() = prefs.getInt(KEY_GLOBAL_METHOD, DEFAULT_GLOBAL_METHOD)
        set(value) = prefs.edit { putInt(KEY_GLOBAL_METHOD, value) }

    val activeCacheKey: String
        get() = when (provider) {
            PrayerProvider.KEMENAG -> "${provider.id}:${cityId.ifBlank { cityQuery }}"
            PrayerProvider.GLOBAL -> "${provider.id}:$globalLatitude:$globalLongitude:$globalMethod:$globalTimeZoneId"
        }

    fun getCachedSchedule(): PrayerSchedule? {
        return decodeSchedule(prefs.getString(KEY_LAST_SCHEDULE, null))
            ?.takeIf { it.provider == provider && it.cacheKey == activeCacheKey && it.isFetchedToday() }
    }

    fun saveSchedule(schedule: PrayerSchedule) {
        prefs.edit { putString(KEY_LAST_SCHEDULE, encodeSchedule(schedule)) }
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
                times = times,
            )
        }.getOrNull()
    }

    companion object {
        private const val PREFS_FILENAME = "app.olauncher.muslim_center"
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
        private const val KEY_LAST_SCHEDULE = "MUSLIM_CENTER_LAST_SCHEDULE"
        private const val DEFAULT_CITY_QUERY = "jakarta"
        private const val DEFAULT_GLOBAL_LOCATION_LABEL = "Mecca"
        private const val DEFAULT_GLOBAL_COUNTRY = "Saudi Arabia"
        private const val DEFAULT_GLOBAL_LATITUDE = 21.4225
        private const val DEFAULT_GLOBAL_LONGITUDE = 39.8262
        private const val DEFAULT_GLOBAL_TIME_ZONE_ID = "Asia/Riyadh"
        private const val DEFAULT_GLOBAL_METHOD = 3
    }
}
