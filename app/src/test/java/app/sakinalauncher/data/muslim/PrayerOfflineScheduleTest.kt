package app.sakinalauncher.data.muslim

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The offline guarantee: once a location is known, prayer times must be available
 * for any date without a network, forever. These tests use APIs that always throw,
 * so anything that passes here provably did not touch the network.
 */
class PrayerOfflineScheduleTest {

    private fun repository(store: PrayerScheduleStore) = PrayerTimeRepository(
        kemenagApi = DeadPrayerApi(),
        aladhanApi = DeadAladhanApi(),
        store = store,
    )

    @Test
    fun withoutLocation_thereIsNothingToCompute() {
        val store = MemoryStore(cityQuery = "", cityLabel = "")
        val repository = repository(store)

        assertFalse(repository.hasLocation)
        assertNull(repository.scheduleFor(System.currentTimeMillis()))
    }

    @Test
    fun pickedCity_hasScheduleWithNoNetwork() {
        val store = MemoryStore(cityQuery = "jakarta", cityLabel = "KOTA JAKARTA")
        val repository = repository(store)

        assertTrue(repository.hasLocation)
        val schedule = repository.scheduleFor(System.currentTimeMillis())
        assertNotNull(schedule)
        assertEquals(5, schedule!!.times.size)
        assertEquals("Asia/Jakarta", schedule.timeZoneId)
        assertTrue(schedule.times.all { it.time.matches(Regex("^\\d{2}:\\d{2}$")) })
    }

    @Test
    fun scheduleIsAvailableAYearAhead() {
        val store = MemoryStore(cityQuery = "jakarta", cityLabel = "KOTA JAKARTA")
        val repository = repository(store)

        val cursor = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
        listOf(1, 30, 100, 200, 364, 365).forEach { daysAhead ->
            val target = (cursor.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, daysAhead) }
            val schedule = repository.scheduleFor(target.timeInMillis)
            assertNotNull("no schedule $daysAhead days ahead", schedule)
            assertEquals(5, schedule!!.times.size)
        }
    }

    @Test
    fun aWholeYearCanBeGeneratedAndCached() = runBlocking {
        val store = MemoryStore(cityQuery = "jakarta", cityLabel = "KOTA JAKARTA")
        val repository = repository(store)

        val written = repository.warmOfflineYear(days = 365)

        assertEquals(365, written)
        // Distinct calendar days, all stored, none duplicated.
        assertEquals(365, store.storedDates().size)
    }

    @Test
    fun offlineTodayIsServedAsFreshNotError() = runBlocking {
        val store = MemoryStore(cityQuery = "jakarta", cityLabel = "KOTA JAKARTA")
        val repository = repository(store)

        // Every API call throws, i.e. the phone is offline.
        val result = repository.getOrFetchToday(forceRefresh = true)

        assertTrue(
            "offline with a known location must not be an error, got $result",
            result is PrayerScheduleResult.Fresh || result is PrayerScheduleResult.Cached,
        )
        val schedule = when (result) {
            is PrayerScheduleResult.Fresh -> result.schedule
            is PrayerScheduleResult.Cached -> result.schedule
            is PrayerScheduleResult.Error -> null
        }
        assertNotNull(schedule)
        assertEquals(5, schedule!!.times.size)
        assertTrue("computed schedule must count as today's data", schedule.isFetchedToday())
    }

    @Test
    fun offlineWithoutLocationIsTheOnlyErrorCase() = runBlocking {
        val store = MemoryStore(cityQuery = "", cityLabel = "")
        val repository = repository(store)

        val result = repository.getOrFetchToday(forceRefresh = true)

        assertTrue("expected Error, got $result", result is PrayerScheduleResult.Error)
    }

    @Test
    fun cachedScheduleFallsBackToComputation() {
        val store = MemoryStore(cityQuery = "jakarta", cityLabel = "KOTA JAKARTA")
        val repository = repository(store)

        // Nothing has ever been cached, yet the card can still be painted.
        assertNotNull(repository.cachedSchedule())
    }

    @Test
    fun travellingAbroadStillResolves() {
        val store = MemoryStore(cityQuery = "", cityLabel = "").apply {
            provider = PrayerProvider.GLOBAL
            globalLocationLabel = "Makkah"
            globalCountry = "Saudi Arabia"
            globalLatitude = 21.4225
            globalLongitude = 39.8262
            globalTimeZoneId = "Asia/Riyadh"
            globalMethod = 4
        }
        val repository = repository(store)

        assertTrue(repository.hasLocation)
        val schedule = repository.scheduleFor(System.currentTimeMillis())
        assertNotNull(schedule)
        assertEquals("Asia/Riyadh", schedule!!.timeZoneId)
        assertEquals("Umm al-Qura", schedule.source)
    }

    @Test
    fun kemenagCityLabelsResolveToCoordinates() {
        listOf(
            "KOTA JAKARTA" to "Asia/Jakarta",
            "KAB. BANDUNG" to "Asia/Jakarta",
            "KOTA SURABAYA" to "Asia/Jakarta",
            "KOTA MAKASSAR" to "Asia/Makassar",
            "KOTA DENPASAR" to "Asia/Makassar",
            "KOTA JAYAPURA" to "Asia/Jayapura",
            "KOTA AMBON" to "Asia/Jayapura",
        ).forEach { (label, zone) ->
            val place = PrayerOfflineLocations.match(label)
            assertNotNull("unmatched label: $label", place)
            assertEquals("wrong zone for $label", zone, place!!.timeZoneId)
        }
    }

    @Test
    fun unknownCityFallsBackToJakartaRatherThanFailing() {
        val place = PrayerOfflineLocations.matchOrJakarta("KAB. SOMEWHERE UNKNOWN")
        assertEquals("JAKARTA", place.name)
    }

    @Test
    fun specificCityWinsOverBroaderName() {
        // "BANDAR LAMPUNG" must not be shadowed by a shorter partial match.
        val place = PrayerOfflineLocations.match("KOTA BANDAR LAMPUNG")
        assertEquals("BANDAR LAMPUNG", place?.name)
    }

    // ---------------------------------------------------------------- test doubles

    /** Every call fails: proves the code under test never depended on the network. */
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

    private class MemoryStore(
        cityQuery: String,
        cityLabel: String,
    ) : PrayerScheduleStore {
        private val schedules = linkedMapOf<String, PrayerSchedule>()

        override var provider: PrayerProvider = PrayerProvider.KEMENAG
        override var cityQuery: String = cityQuery
        override var cityId: String = ""
        override var cityLabel: String = cityLabel
        override var autoDetectLocation: Boolean = false
        override var globalLocationLabel: String = ""
        override var globalCountry: String = ""
        override var globalLatitude: Double = 0.0
        override var globalLongitude: Double = 0.0
        override var globalTimeZoneId: String = ""
        override var globalMethod: Int = 3
        override val activeCacheKey: String
            get() = when (provider) {
                PrayerProvider.KEMENAG -> "${provider.id}:${cityId.ifBlank { cityQuery }}"
                PrayerProvider.GLOBAL -> "${provider.id}:$globalLatitude:$globalLongitude"
            }

        fun storedDates(): Set<String> = schedules.values.map { it.dateYmd }.toSet()

        override fun getCachedSchedule(): PrayerSchedule? = null

        override fun getStaleCachedSchedule(): PrayerSchedule? = null

        override fun saveSchedule(schedule: PrayerSchedule) = saveSchedules(schedule.cacheKey, listOf(schedule))

        override fun saveSchedules(cacheKey: String, schedules: List<PrayerSchedule>) {
            schedules.forEach { this.schedules["$cacheKey:${it.dateYmd}"] = it }
        }

        override fun getCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule? = null

        override fun getStaleCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule? = null

        override fun isCacheFreshForDate(cacheKey: String, dateYmd: String, ttlMillis: Long): Boolean = false
    }
}
