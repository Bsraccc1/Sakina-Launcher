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
    private val store: PrayerScheduleStore,
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
        // Lay down a year of offline days for the new location before touching the
        // network, so the schedule survives even if this is the last time online.
        warmOfflineYear()
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
        warmOfflineYear()
        getOrFetchToday(forceRefresh = true)
    }

    suspend fun refreshToday(): PrayerScheduleResult = getOrFetchToday(forceRefresh = true)

    /**
     * Adopt a coordinate straight from the device's location, without asking any
     * server to name it first. This is what lets auto-detect work on a phone that
     * has GPS but no data: the coordinate alone is enough to compute the schedule.
     *
     * [label] is only cosmetic (reverse geocoding may fail); the maths uses the
     * coordinate.
     */
    suspend fun adoptDetectedLocation(
        label: String,
        country: String,
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
    ): PrayerScheduleResult = withContext(ioDispatcher) {
        // Inside Indonesia the published Bimas Islam schedule is what people expect
        // to see, so prefer the named city when we can resolve one; otherwise fall
        // back to computing from the raw coordinate.
        val indonesianCity = PrayerOfflineLocations.match(label)
            ?.takeIf { it.timeZoneId.startsWith("Asia/Jakarta") ||
                it.timeZoneId.startsWith("Asia/Makassar") ||
                it.timeZoneId.startsWith("Asia/Jayapura") }

        if (indonesianCity != null) {
            store.provider = PrayerProvider.KEMENAG
            store.cityLabel = label
            store.cityQuery = label
        } else {
            store.provider = PrayerProvider.GLOBAL
            store.globalLocationLabel = label
            store.globalCountry = country
            store.globalLatitude = latitude
            store.globalLongitude = longitude
            store.globalTimeZoneId = timeZoneId
        }
        warmOfflineYear()
        getOrFetchToday(forceRefresh = true)
    }

    /**
     * Write a year of locally computed schedules to disk. Called after a location is
     * chosen so the user is covered even if the launcher is never online again.
     * Cheap: no network, pure arithmetic, and the store writes one batch.
     */
    suspend fun warmOfflineYear(days: Int = 365): Int = withContext(ioDispatcher) {
        val schedules = scheduleForYear(days = days)
        if (schedules.isEmpty()) return@withContext 0
        store.saveSchedules(schedules.first().cacheKey, schedules)
        schedules.size
    }

    suspend fun getOrFetchToday(forceRefresh: Boolean = false): PrayerScheduleResult = withContext(ioDispatcher) {
        val timeZoneId = activeTimeZoneId()
        val today = todayYmd(timeZoneId)
        if (!forceRefresh) {
            activeCacheKeysForRead().firstNotNullOfOrNull { cacheKey ->
                if (store.isCacheFreshForDate(cacheKey, today, PrayerCache.CACHE_TTL_MILLIS)) {
                    store.getCachedScheduleForDate(cacheKey, today)
                } else {
                    null
                }
            }?.let { cached ->
                return@withContext PrayerScheduleResult.Cached(cached, "")
            }
        }
        val cacheKey = activeCacheKeyForWrite()
        val now = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId))
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        return@withContext refreshMonthBatch(year, month, timeZoneId, cacheKey, today)
    }

    suspend fun loadCachedToday(): PrayerSchedule? = withContext(ioDispatcher) {
        val timeZoneId = activeTimeZoneId()
        val today = todayYmd(timeZoneId)
        store.getCachedScheduleForDate(store.activeCacheKey, today)
            ?: computedScheduleFor(System.currentTimeMillis())
    }

    fun cachedSchedule(): PrayerSchedule? = store.getCachedSchedule() ?: computedScheduleFor(System.currentTimeMillis())

    /**
     * True once we know where the user is — either a picked city or a detected
     * coordinate. From that moment on prayer times are always available, network
     * or not, because they can be computed locally.
     */
    val hasLocation: Boolean
        get() = when (store.provider) {
            PrayerProvider.KEMENAG -> store.cityLabel.isNotBlank() || store.cityQuery.isNotBlank()
            PrayerProvider.GLOBAL -> store.globalLatitude != 0.0 || store.globalLongitude != 0.0
        }

    /**
     * Schedule for any date, computed on the device. This is the guarantee the user
     * asked for: install the launcher, pick (or auto-detect) a location once, and
     * the schedule works forever — a year ahead, abroad, in aeroplane mode.
     *
     * Returns null only when there is genuinely no location to compute for.
     */
    fun scheduleFor(dateMillis: Long): PrayerSchedule? = computedScheduleFor(dateMillis)

    /**
     * A full year of schedules starting at [startMillis], all computed locally.
     * No network, no per-day API calls.
     */
    fun scheduleForYear(startMillis: Long = System.currentTimeMillis(), days: Int = 365): List<PrayerSchedule> {
        val place = activePlace() ?: return emptyList()
        val zone = TimeZone.getTimeZone(place.timeZoneId)
        val cursor = Calendar.getInstance(zone).apply {
            timeInMillis = startMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return buildList(days) {
            repeat(days) {
                computedScheduleFor(cursor.timeInMillis)?.let { add(it) }
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    /** Where the user is, as coordinates the calculator can use. */
    private fun activePlace(): PrayerOfflineLocations.Place? = when (store.provider) {
        PrayerProvider.GLOBAL -> {
            if (store.globalLatitude == 0.0 && store.globalLongitude == 0.0) {
                null
            } else {
                PrayerOfflineLocations.Place(
                    name = store.globalLocationLabel,
                    latitude = store.globalLatitude,
                    longitude = store.globalLongitude,
                    timeZoneId = store.globalTimeZoneId.ifBlank { TimeZone.getDefault().id },
                )
            }
        }

        PrayerProvider.KEMENAG -> {
            val label = store.cityLabel.ifBlank { store.cityQuery }
            if (label.isBlank()) null else PrayerOfflineLocations.matchOrJakarta(label)
        }
    }

    /** Calculation convention that matches the active provider. */
    private fun activeMethod(): PrayerTimeCalculator.Method = when (store.provider) {
        PrayerProvider.KEMENAG -> PrayerTimeCalculator.Method.KEMENAG
        PrayerProvider.GLOBAL -> when (store.globalMethod) {
            4 -> PrayerTimeCalculator.Method.UMM_AL_QURA
            5 -> PrayerTimeCalculator.Method.EGYPTIAN
            2 -> PrayerTimeCalculator.Method.ISNA
            else -> PrayerTimeCalculator.Method.MWL
        }
    }

    private fun computedScheduleFor(dateMillis: Long): PrayerSchedule? {
        val place = activePlace() ?: return null
        val zone = runCatching { TimeZone.getTimeZone(place.timeZoneId) }.getOrDefault(TimeZone.getDefault())
        val method = activeMethod()
        val times = PrayerTimeCalculator.timesFor(
            dateMillis = dateMillis,
            latitude = place.latitude,
            longitude = place.longitude,
            timeZone = zone,
            method = method,
        )
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = dateMillis }
        val ymdFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
        val labelFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.getDefault()).apply { timeZone = zone }
        val cityLabel = when (store.provider) {
            PrayerProvider.KEMENAG -> store.cityLabel.ifBlank { store.cityQuery }
            PrayerProvider.GLOBAL -> store.globalLocationLabel
        }
        val province = when (store.provider) {
            PrayerProvider.KEMENAG -> ""
            PrayerProvider.GLOBAL -> store.globalCountry
        }
        return PrayerSchedule(
            city = cityLabel,
            province = province,
            dateLabel = labelFormat.format(calendar.time),
            // Computed schedules are always current for their date, so stamp them
            // now: the UI's "is this today's data" check then passes offline too.
            fetchedAtMillis = System.currentTimeMillis(),
            times = times.toPrayerTimes(),
            source = method.label,
            provider = store.provider,
            timeZoneId = place.timeZoneId,
            cacheKey = store.activeCacheKey,
            dateYmd = ymdFormat.format(calendar.time),
        )
    }

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

    private suspend fun activeCacheKeyForWrite(): String {
        if (store.provider == PrayerProvider.KEMENAG && store.cityId.isBlank()) {
            ensureKemenagCityId()
        }
        return store.activeCacheKey
    }

    private fun activeCacheKeysForRead(): List<String> {
        if (store.provider != PrayerProvider.KEMENAG) return listOf(store.activeCacheKey)
        val currentKey = store.activeCacheKey
        val queryKey = "${PrayerProvider.KEMENAG.id}:${store.cityQuery}"
        return listOf(currentKey, queryKey).distinct()
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

    /**
     * Network failed. Prefer a cached day, then fall back to computing the day on
     * the device. [PrayerScheduleResult.Error] is only for the one case that
     * genuinely cannot be solved offline: we do not know where the user is.
     */
    private fun fallbackForToday(error: String, cacheKey: String, today: String): PrayerScheduleResult {
        store.getCachedScheduleForDate(cacheKey, today)?.let {
            return PrayerScheduleResult.Cached(it, error)
        }
        computedScheduleFor(System.currentTimeMillis())?.let {
            // Not an error state for the user: this schedule is correct, it just
            // came from the device's own calculation instead of the network.
            return PrayerScheduleResult.Fresh(it)
        }
        val cached = store.getCachedSchedule() ?: store.getStaleCachedSchedule()
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
            dateYmd = times.date.orEmpty().ifBlank { kemenagDateLabelYmd(times.dateLabel) },
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

    private fun kemenagDateLabelYmd(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            val rawDate = value.substringAfter(",").trim()
            val source = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val target = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            target.format(source.parse(rawDate)!!)
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
        private const val MAX_FETCH_ATTEMPTS = 2
        private const val BACKOFF_BASE_MS = 300L

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
