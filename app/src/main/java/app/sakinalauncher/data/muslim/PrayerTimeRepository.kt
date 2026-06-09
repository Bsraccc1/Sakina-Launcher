package app.sakinalauncher.data.muslim

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PrayerTimeRepository(
    private val kemenagApi: PrayerApi,
    private val aladhanApi: AladhanApi,
    private val store: PrayerTimeStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun allCities(): List<PrayerCity> = withContext(ioDispatcher) {
        try {
            kemenagApi.allCities().data.orEmpty().map { PrayerCity(it.id, it.location) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchCities(query: String): List<PrayerCity> = withContext(ioDispatcher) {
        try {
            kemenagApi.searchCities(query).data.orEmpty().map { PrayerCity(it.id, it.location) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun selectCity(city: PrayerCity): PrayerScheduleResult = withContext(ioDispatcher) {
        store.provider = PrayerProvider.KEMENAG
        store.cityId = city.id
        store.cityLabel = city.label
        store.cityQuery = city.label
        getOrFetchToday(forceRefresh = true)
    }

    suspend fun selectGlobalLocation(location: GlobalPrayerLocation): PrayerScheduleResult = withContext(ioDispatcher) {
        store.provider = PrayerProvider.GLOBAL
        store.globalLocationLabel = location.label
        store.globalCountry = location.country
        store.globalLatitude = location.latitude
        store.globalLongitude = location.longitude
        store.globalTimeZoneId = location.timeZoneId
        store.globalMethod = location.method
        getOrFetchToday(forceRefresh = true)
    }

    suspend fun refreshToday(): PrayerScheduleResult = getOrFetchToday(forceRefresh = true)

    suspend fun getOrFetchToday(forceRefresh: Boolean = false): PrayerScheduleResult = withContext(ioDispatcher) {
        val timeZoneId = activeTimeZoneId()
        val today = todayYmd(timeZoneId)
        val cacheKey = store.activeCacheKey
        if (!forceRefresh && store.isCacheFreshForDate(cacheKey, today, PrayerCache.CACHE_TTL_MILLIS)) {
            val cached = store.getCachedScheduleForDate(cacheKey, today)
            if (cached != null) return@withContext PrayerScheduleResult.Cached(cached, "")
        }
        val now = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId))
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        return@withContext refreshMonthBatch(year, month, timeZoneId, cacheKey, today)
    }

    suspend fun loadCachedToday(): PrayerSchedule? = withContext(ioDispatcher) {
        val timeZoneId = activeTimeZoneId()
        val today = todayYmd(timeZoneId)
        store.getCachedScheduleForDate(store.activeCacheKey, today)
    }

    fun cachedSchedule(): PrayerSchedule? = store.getCachedSchedule()

    private suspend fun refreshMonthBatch(
        year: Int,
        month: Int,
        timeZoneId: String,
        cacheKey: String,
        today: String,
    ): PrayerScheduleResult {
        return when (store.provider) {
            PrayerProvider.KEMENAG -> refreshKemenagMonth(year, month, timeZoneId, cacheKey, today)
            PrayerProvider.GLOBAL -> refreshGlobalMonth(year, month, timeZoneId, cacheKey, today)
        }
    }

    private suspend fun refreshKemenagMonth(
        year: Int,
        month: Int,
        timeZoneId: String,
        cacheKey: String,
        today: String,
    ): PrayerScheduleResult {
        try {
            val cityId = ensureKemenagCityId() ?: return fallbackForToday("City not found", cacheKey, today)
            val effectiveTz = inferKemenagTimeZone(store.cityLabel.ifBlank { store.cityQuery })
            val response = kemenagApi.getMonthlyKemenagSchedule(cityId, year, month, effectiveTz)
            val schedules = response.toDomainList(effectiveTz, cacheKey)
            if (schedules.isEmpty()) return refreshKemenagSingleDay(cacheKey, today)
            store.saveSchedules(cacheKey, schedules)
            val todays = store.getCachedScheduleForDate(cacheKey, today)
                ?: schedules.firstOrNull { it.dateYmd == today }
                ?: return refreshKemenagSingleDay(cacheKey, today)
            return PrayerScheduleResult.Fresh(todays)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("DEBUG-prayer", "Kemenag month batch failed: ${error.javaClass.simpleName}: ${error.message}", error)
            return refreshKemenagSingleDay(cacheKey, today)
        }
    }

    private suspend fun refreshGlobalMonth(
        year: Int,
        month: Int,
        timeZoneId: String,
        cacheKey: String,
        today: String,
    ): PrayerScheduleResult {
        try {
            val response = aladhanApi.getAladhanCalendar(
                year = year,
                month = month,
                latitude = store.globalLatitude,
                longitude = store.globalLongitude,
                method = store.globalMethod,
                timeZoneId = timeZoneId,
            )
            val schedules = response.toDomainList(
                label = store.globalLocationLabel,
                country = store.globalCountry,
                timeZoneId = timeZoneId,
                cacheKey = cacheKey,
            )
            if (schedules.isEmpty()) return refreshGlobalSingleDay(cacheKey, today)
            store.saveSchedules(cacheKey, schedules)
            val todays = store.getCachedScheduleForDate(cacheKey, today)
                ?: schedules.firstOrNull { it.dateYmd == today }
                ?: return refreshGlobalSingleDay(cacheKey, today)
            return PrayerScheduleResult.Fresh(todays)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("DEBUG-prayer", "Aladhan month batch failed: ${error.javaClass.simpleName}: ${error.message}", error)
            return refreshGlobalSingleDay(cacheKey, today)
        }
    }

    private suspend fun ensureKemenagCityId(): String? {
        val existing = store.cityId
        if (existing.isNotBlank()) return existing
        val city = runCatching { kemenagApi.searchCities(store.cityQuery).data?.firstOrNull() }.getOrNull()
            ?: return null
        store.cityId = city.id
        store.cityLabel = city.location
        return city.id
    }

    private suspend fun refreshKemenagSingleDay(cacheKey: String, today: String): PrayerScheduleResult {
        var lastError = "Unable to load prayer schedule"
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                val cityId = ensureKemenagCityId() ?: return fallbackForToday("City not found", cacheKey, today)
                val timeZoneId = inferKemenagTimeZone(store.cityLabel.ifBlank { store.cityQuery })
                val schedule = kemenagApi.todaySchedule(cityId, timeZoneId).toDomain(timeZoneId, cacheKey)
                    ?: return fallbackForToday("Prayer schedule not found", cacheKey, today)
                store.saveSchedule(schedule)
                return PrayerScheduleResult.Fresh(schedule)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.e("DEBUG-prayer", "Kemenag attempt $attempt failed: ${error.javaClass.simpleName}: ${error.message}", error)
                lastError = error.message ?: lastError
                if (attempt < MAX_FETCH_ATTEMPTS - 1) {
                    delay(BACKOFF_BASE_MS * (attempt + 1))
                }
            }
        }
        return fallbackForToday(lastError, cacheKey, today)
    }

    private suspend fun refreshGlobalSingleDay(cacheKey: String, today: String): PrayerScheduleResult {
        var lastError = "Unable to load prayer schedule"
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                val timeZoneId = store.globalTimeZoneId.ifBlank { TimeZone.getDefault().id }
                val date = apiDate(timeZoneId)
                val schedule = aladhanApi.timings(
                    date = date,
                    latitude = store.globalLatitude,
                    longitude = store.globalLongitude,
                    method = store.globalMethod,
                    timeZoneId = timeZoneId,
                ).toDomain(
                    label = store.globalLocationLabel,
                    country = store.globalCountry,
                    timeZoneId = timeZoneId,
                    cacheKey = cacheKey,
                ) ?: return fallbackForToday("Prayer schedule not found", cacheKey, today)
                store.saveSchedule(schedule)
                return PrayerScheduleResult.Fresh(schedule)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.e("DEBUG-prayer", "Global attempt $attempt failed: ${error.javaClass.simpleName}: ${error.message}", error)
                lastError = error.message ?: lastError
                if (attempt < MAX_FETCH_ATTEMPTS - 1) {
                    delay(BACKOFF_BASE_MS * (attempt + 1))
                }
            }
        }
        return fallbackForToday(lastError, cacheKey, today)
    }

    private fun activeTimeZoneId(): String {
        return when (store.provider) {
            PrayerProvider.KEMENAG -> inferKemenagTimeZone(store.cityLabel.ifBlank { store.cityQuery })
            PrayerProvider.GLOBAL -> store.globalTimeZoneId.ifBlank { TimeZone.getDefault().id }
        }
    }

    private fun todayYmd(timeZoneId: String): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = runCatching { TimeZone.getTimeZone(timeZoneId) }.getOrDefault(TimeZone.getDefault())
        return format.format(Date())
    }

    private fun fallbackForToday(error: String, cacheKey: String, today: String): PrayerScheduleResult {
        val cached = store.getCachedScheduleForDate(cacheKey, today)
            ?: store.getCachedSchedule()
            ?: store.getStaleCachedSchedule()
        return if (cached != null) PrayerScheduleResult.Cached(cached, error)
        else PrayerScheduleResult.Error(error)
    }

    private fun PrayerScheduleResponse.toDomain(timeZoneId: String, cacheKey: String): PrayerSchedule? {
        if (!status) return null
        val payload = data ?: return null
        val times = payload.schedules?.values?.firstOrNull() ?: return null
        return buildKemenagSchedule(payload.city.orEmpty(), payload.province.orEmpty(), times, timeZoneId, cacheKey)
    }

    private fun PrayerMonthlyResponse.toDomainList(timeZoneId: String, cacheKey: String): List<PrayerSchedule> {
        if (!status) return emptyList()
        val payload = data ?: return emptyList()
        val list = payload.schedules ?: return emptyList()
        val city = payload.city.orEmpty()
        val province = payload.province.orEmpty()
        return list.mapNotNull { dto ->
            buildKemenagSchedule(city, province, dto, timeZoneId, cacheKey)
        }
    }

    private fun buildKemenagSchedule(
        city: String,
        province: String,
        times: PrayerTimesDto,
        timeZoneId: String,
        cacheKey: String,
    ): PrayerSchedule? {
        val prayerTimes = listOfNotNull(
            times.subuh?.let { PrayerTime(PrayerName.FAJR, cleanTime(it)) },
            times.dzuhur?.let { PrayerTime(PrayerName.DHUHR, cleanTime(it)) },
            times.ashar?.let { PrayerTime(PrayerName.ASR, cleanTime(it)) },
            times.maghrib?.let { PrayerTime(PrayerName.MAGHRIB, cleanTime(it)) },
            times.isya?.let { PrayerTime(PrayerName.ISHA, cleanTime(it)) },
        )
        if (prayerTimes.isEmpty()) return null
        return PrayerSchedule(
            city = city,
            province = province,
            dateLabel = times.dateLabel.orEmpty(),
            fetchedAtMillis = System.currentTimeMillis(),
            times = prayerTimes,
            source = "Bimas Islam Kemenag RI",
            provider = PrayerProvider.KEMENAG,
            timeZoneId = timeZoneId,
            cacheKey = cacheKey,
            dateYmd = times.date.orEmpty(),
        )
    }

    private fun AladhanTimingsResponse.toDomain(
        label: String,
        country: String,
        timeZoneId: String,
        cacheKey: String,
    ): PrayerSchedule? {
        if (code != 200 && status?.equals("OK", ignoreCase = true) != true) return null
        val payload = data ?: return null
        return buildAladhanSchedule(label, country, payload, timeZoneId, cacheKey)
    }

    private fun AladhanCalendarResponse.toDomainList(
        label: String,
        country: String,
        timeZoneId: String,
        cacheKey: String,
    ): List<PrayerSchedule> {
        val payload = data ?: return emptyList()
        return payload.mapNotNull { day ->
            buildAladhanSchedule(label, country, day, timeZoneId, cacheKey)
        }
    }

    private fun buildAladhanSchedule(
        label: String,
        country: String,
        payload: AladhanTimingDataDto,
        timeZoneId: String,
        cacheKey: String,
    ): PrayerSchedule? {
        val times = payload.timings ?: return null
        val prayerTimes = listOfNotNull(
            times.fajr?.let { PrayerTime(PrayerName.FAJR, cleanTime(it)) },
            times.dhuhr?.let { PrayerTime(PrayerName.DHUHR, cleanTime(it)) },
            times.asr?.let { PrayerTime(PrayerName.ASR, cleanTime(it)) },
            times.maghrib?.let { PrayerTime(PrayerName.MAGHRIB, cleanTime(it)) },
            times.isha?.let { PrayerTime(PrayerName.ISHA, cleanTime(it)) },
        )
        if (prayerTimes.isEmpty()) return null
        val effectiveTz = payload.meta?.timezone ?: timeZoneId
        return PrayerSchedule(
            city = label,
            province = country,
            dateLabel = payload.date?.readable.orEmpty(),
            fetchedAtMillis = System.currentTimeMillis(),
            times = prayerTimes,
            source = payload.meta?.method?.name?.let { "Aladhan - $it" } ?: "Aladhan",
            provider = PrayerProvider.GLOBAL,
            timeZoneId = effectiveTz,
            cacheKey = cacheKey,
            dateYmd = aladhanGregorianYmd(payload.date?.gregorian?.date),
        )
    }

    private fun aladhanGregorianYmd(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            val source = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            val target = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            target.format(source.parse(value)!!)
        }.getOrDefault("")
    }

    private fun apiDate(timeZoneId: String): String {
        val format = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        format.timeZone = TimeZone.getTimeZone(timeZoneId)
        return format.format(Date())
    }

    private fun cleanTime(value: String): String {
        return value.trim().take(5)
    }

    private fun inferKemenagTimeZone(label: String): String {
        val normalized = label.uppercase(Locale.US)
        return when {
            listOf("PAPUA", "MALUKU", "AMBON", "JAYAPURA", "SORONG", "MERAUKE", "TERNATE").any {
                normalized.contains(it)
            } -> "Asia/Jayapura"
            listOf(
                "BALI",
                "NUSA TENGGARA",
                "NTB",
                "NTT",
                "SULAWESI",
                "GORONTALO",
                "KALIMANTAN TIMUR",
                "KALIMANTAN SELATAN",
                "KALIMANTAN UTARA",
                "MAKASSAR",
                "DENPASAR",
                "MATARAM",
                "KUPANG",
                "BALIKPAPAN",
                "SAMARINDA",
                "BANJARMASIN",
                "PALU",
                "MANADO",
                "KENDARI",
            ).any { normalized.contains(it) } -> "Asia/Makassar"
            else -> "Asia/Jakarta"
        }
    }

    companion object {
        private const val MAX_FETCH_ATTEMPTS = 3
        private const val BACKOFF_BASE_MS = 600L

        val globalPresetLocations = listOf(
            GlobalPrayerLocation("Makkah", "Saudi Arabia", 21.4225, 39.8262, "Asia/Riyadh", 4),
            GlobalPrayerLocation("Madinah", "Saudi Arabia", 24.4672, 39.6111, "Asia/Riyadh", 4),
            GlobalPrayerLocation("Kuala Lumpur", "Malaysia", 3.1390, 101.6869, "Asia/Kuala_Lumpur", 17),
            GlobalPrayerLocation("Singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore", 11),
            GlobalPrayerLocation("Dubai", "United Arab Emirates", 25.2048, 55.2708, "Asia/Dubai", 8),
            GlobalPrayerLocation("Istanbul", "Turkey", 41.0082, 28.9784, "Europe/Istanbul", 13),
            GlobalPrayerLocation("London", "United Kingdom", 51.5072, -0.1276, "Europe/London", 3),
            GlobalPrayerLocation("New York", "United States", 40.7128, -74.0060, "America/New_York", 2),
            GlobalPrayerLocation("Tokyo", "Japan", 35.6762, 139.6503, "Asia/Tokyo", 3),
            GlobalPrayerLocation("Cairo", "Egypt", 30.0444, 31.2357, "Africa/Cairo", 5),
        )
    }
}

data class GlobalPrayerLocation(
    val label: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val method: Int = 3,
)
