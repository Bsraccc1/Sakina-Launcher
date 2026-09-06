package app.sakinalauncher.data.muslim

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Auto-detect must follow the user when they physically move.
 *
 * [PrayerTimeRepository.adoptDetectedLocation] decides whether to re-warm the offline
 * cache by comparing [PrayerScheduleStore.activeCacheKey] before and after it writes the
 * detected place. For the Kemenag provider that key is built from `cityId` first and only
 * falls back to `cityQuery`, so a stale `cityId` from a previously picked city makes the
 * key identical no matter where the phone actually is.
 */
class PrayerRelocationTest {

    @Test
    fun detectingANewCityChangesTheCacheKey() = runBlocking {
        // The user picked Jakarta by hand at some point, so an id is on disk.
        val store = MemoryStore().apply {
            provider = PrayerProvider.KEMENAG
            cityId = "1301"
            cityLabel = "KOTA JAKARTA"
            cityQuery = "KOTA JAKARTA"
        }
        val repository = repository(store)
        val jakartaKey = store.activeCacheKey

        // They then travel to Surabaya and auto-detect resolves it.
        repository.adoptDetectedLocation(
            label = "KOTA SURABAYA",
            country = "Indonesia",
            latitude = -7.2575,
            longitude = 112.7521,
            timeZoneId = "Asia/Jakarta",
        )

        assertEquals("KOTA SURABAYA", store.cityLabel)
        assertNotEquals(
            "moving to a new city must change the cache key, or the old city's schedule is served",
            jakartaKey,
            store.activeCacheKey,
        )
    }

    @Test
    fun relocatingWarmsTheNewLocationOffline() = runBlocking {
        val store = MemoryStore().apply {
            provider = PrayerProvider.KEMENAG
            cityId = "1301"
            cityLabel = "KOTA JAKARTA"
            cityQuery = "KOTA JAKARTA"
        }
        val repository = repository(store)

        repository.adoptDetectedLocation(
            label = "KOTA SURABAYA",
            country = "Indonesia",
            latitude = -7.2575,
            longitude = 112.7521,
            timeZoneId = "Asia/Jakarta",
        )

        // Every API here throws, so anything written came from on-device computation.
        assertEquals(
            "a relocation must lay down offline days for the new place",
            1,
            store.savedCacheKeys.size,
        )
        assertEquals(store.activeCacheKey, store.savedCacheKeys.single())
    }

    @Test
    fun detectingTheSameCityDoesNotRewarm() = runBlocking {
        val store = MemoryStore().apply {
            provider = PrayerProvider.KEMENAG
            cityId = "1301"
            cityLabel = "KOTA JAKARTA"
            cityQuery = "KOTA JAKARTA"
        }
        val repository = repository(store)

        repository.adoptDetectedLocation(
            label = "KOTA JAKARTA",
            country = "Indonesia",
            latitude = -6.1751,
            longitude = 106.8650,
            timeZoneId = "Asia/Jakarta",
        )

        // Opening the Muslim Center on the same spot must stay cheap: no re-warm.
        assertEquals(emptyList<String>(), store.savedCacheKeys)
        assertEquals("1301", store.cityId)
    }

    private fun repository(store: PrayerScheduleStore) = PrayerTimeRepository(
        kemenagApi = DeadPrayerApi(),
        aladhanApi = DeadAladhanApi(),
        store = store,
    )

    // ---------------------------------------------------------------- test doubles

    /** Every call fails: anything that passes provably never touched the network. */
    private class DeadPrayerApi : PrayerApi {
        override suspend fun allCities(): PrayerCityResponse = error("offline")
        override suspend fun searchCities(keyword: String): PrayerCityResponse = error("offline")
        override suspend fun todaySchedule(cityId: String, timeZoneId: String): PrayerScheduleResponse =
            error("offline")

        override suspend fun getMonthlyKemenagSchedule(
            cityId: String,
            year: Int,
            month: Int,
            timeZoneId: String,
        ): PrayerMonthlyResponse = error("offline")
    }

    private class DeadAladhanApi : AladhanApi {
        override suspend fun timings(
            date: String,
            latitude: Double,
            longitude: Double,
            method: Int,
            timeZoneId: String,
        ): AladhanTimingsResponse = error("offline")

        override suspend fun getAladhanCalendar(
            year: Int,
            month: Int,
            latitude: Double,
            longitude: Double,
            method: Int,
            timeZoneId: String,
        ): AladhanCalendarResponse = error("offline")
    }

    private class MemoryStore : PrayerScheduleStore {
        private val schedules = linkedMapOf<String, PrayerSchedule>()

        override var provider: PrayerProvider = PrayerProvider.KEMENAG
        override var cityQuery: String = ""
        override var cityId: String = ""
        override var cityLabel: String = ""
        override var autoDetectLocation: Boolean = true
        override var globalLocationLabel: String = ""
        override var globalCountry: String = ""
        override var globalLatitude: Double = 0.0
        override var globalLongitude: Double = 0.0
        override var globalTimeZoneId: String = ""
        override var globalMethod: Int = 3

        val savedCacheKeys = mutableListOf<String>()

        override fun getCachedSchedule(): PrayerSchedule? = null

        override fun getStaleCachedSchedule(): PrayerSchedule? = null

        override fun saveSchedule(schedule: PrayerSchedule) =
            saveSchedules(schedule.cacheKey, listOf(schedule))

        override fun saveSchedules(cacheKey: String, schedules: List<PrayerSchedule>) {
            savedCacheKeys.add(cacheKey)
            schedules.forEach { this.schedules["$cacheKey:${it.dateYmd}"] = it }
        }

        override fun getCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule? =
            schedules["$cacheKey:$dateYmd"]

        override fun isCacheFreshForDate(cacheKey: String, dateYmd: String, ttlMillis: Long): Boolean {
            val schedule = getCachedScheduleForDate(cacheKey, dateYmd) ?: return false
            return System.currentTimeMillis() - schedule.fetchedAtMillis <= ttlMillis
        }
    }
}
