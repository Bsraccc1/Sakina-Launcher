package app.olauncher.data.muslim

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
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
        refreshToday()
    }

    suspend fun selectGlobalLocation(location: GlobalPrayerLocation): PrayerScheduleResult = withContext(ioDispatcher) {
        store.provider = PrayerProvider.GLOBAL
        store.globalLocationLabel = location.label
        store.globalCountry = location.country
        store.globalLatitude = location.latitude
        store.globalLongitude = location.longitude
        store.globalTimeZoneId = location.timeZoneId
        store.globalMethod = location.method
        refreshToday()
    }

    suspend fun refreshToday(): PrayerScheduleResult = withContext(ioDispatcher) {
        return@withContext when (store.provider) {
            PrayerProvider.KEMENAG -> refreshKemenag()
            PrayerProvider.GLOBAL -> refreshGlobal()
        }
    }

    fun cachedSchedule(): PrayerSchedule? = store.getCachedSchedule()

    private suspend fun refreshKemenag(): PrayerScheduleResult {
        try {
            val cityId = store.cityId.ifBlank {
                val city = kemenagApi.searchCities(store.cityQuery).data?.firstOrNull()
                    ?: return fallback("City not found")
                store.cityId = city.id
                store.cityLabel = city.location
                city.id
            }
            val timeZoneId = inferKemenagTimeZone(store.cityLabel.ifBlank { store.cityQuery })
            val schedule = kemenagApi.todaySchedule(cityId, timeZoneId).toDomain(timeZoneId, store.activeCacheKey)
                ?: return fallback("Prayer schedule not found")
            store.saveSchedule(schedule)
            return PrayerScheduleResult.Fresh(schedule)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return fallback(error.message ?: "Unable to load prayer schedule")
        }
    }

    private suspend fun refreshGlobal(): PrayerScheduleResult {
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
                cacheKey = store.activeCacheKey,
            ) ?: return fallback("Prayer schedule not found")
            store.saveSchedule(schedule)
            return PrayerScheduleResult.Fresh(schedule)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return fallback(error.message ?: "Unable to load prayer schedule")
        }
    }

    private fun fallback(error: String): PrayerScheduleResult {
        val cached = store.getCachedSchedule()
        return if (cached != null) PrayerScheduleResult.Cached(cached, error)
        else PrayerScheduleResult.Error(error)
    }

    private fun PrayerScheduleResponse.toDomain(timeZoneId: String, cacheKey: String): PrayerSchedule? {
        if (!status) return null
        val payload = data ?: return null
        val times = payload.schedules?.values?.firstOrNull() ?: return null
        val prayerTimes = listOfNotNull(
            times.subuh?.let { PrayerTime(PrayerName.FAJR, cleanTime(it)) },
            times.dzuhur?.let { PrayerTime(PrayerName.DHUHR, cleanTime(it)) },
            times.ashar?.let { PrayerTime(PrayerName.ASR, cleanTime(it)) },
            times.maghrib?.let { PrayerTime(PrayerName.MAGHRIB, cleanTime(it)) },
            times.isya?.let { PrayerTime(PrayerName.ISHA, cleanTime(it)) },
        )
        if (prayerTimes.isEmpty()) return null
        return PrayerSchedule(
            city = payload.city.orEmpty(),
            province = payload.province.orEmpty(),
            dateLabel = times.dateLabel.orEmpty(),
            fetchedAtMillis = System.currentTimeMillis(),
            times = prayerTimes,
            source = "Bimas Islam Kemenag RI",
            provider = PrayerProvider.KEMENAG,
            timeZoneId = timeZoneId,
            cacheKey = cacheKey,
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
        val times = payload.timings ?: return null
        val prayerTimes = listOfNotNull(
            times.fajr?.let { PrayerTime(PrayerName.FAJR, cleanTime(it)) },
            times.dhuhr?.let { PrayerTime(PrayerName.DHUHR, cleanTime(it)) },
            times.asr?.let { PrayerTime(PrayerName.ASR, cleanTime(it)) },
            times.maghrib?.let { PrayerTime(PrayerName.MAGHRIB, cleanTime(it)) },
            times.isha?.let { PrayerTime(PrayerName.ISHA, cleanTime(it)) },
        )
        if (prayerTimes.isEmpty()) return null
        return PrayerSchedule(
            city = label,
            province = country,
            dateLabel = payload.date?.readable.orEmpty(),
            fetchedAtMillis = System.currentTimeMillis(),
            times = prayerTimes,
            source = payload.meta?.method?.name?.let { "Aladhan - $it" } ?: "Aladhan",
            provider = PrayerProvider.GLOBAL,
            timeZoneId = payload.meta?.timezone ?: timeZoneId,
            cacheKey = cacheKey,
        )
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
