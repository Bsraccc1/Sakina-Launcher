package app.sakinalauncher.data.muslim

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PrayerTimeRepositoryTest {

    @Test
    fun firstFetchWithBlankCityIdCachesUnderResolvedCityKey() = runBlocking {
        val today = todayYmd()
        val store = MemoryPrayerScheduleStore()
        val api = FakePrayerApi(today)
        val repository = PrayerTimeRepository(
            kemenagApi = api,
            aladhanApi = FakeAladhanApi(),
            store = store,
        )

        val first = repository.getOrFetchToday(forceRefresh = false)
        val second = repository.getOrFetchToday(forceRefresh = false)

        assertTrue(first is PrayerScheduleResult.Fresh)
        assertTrue(second is PrayerScheduleResult.Cached)
        assertEquals("1301", store.cityId)
        assertEquals(listOf("kemenag:1301"), store.savedCacheKeys)
        assertEquals(1, api.searchCalls)
        assertEquals(1, api.monthCalls)
    }

    private fun todayYmd(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(Date())
    }

    private class FakePrayerApi(private val today: String) : PrayerApi {
        var searchCalls = 0
        var monthCalls = 0

        override suspend fun allCities(): PrayerCityResponse {
            return PrayerCityResponse(true, null, listOf(PrayerCityDto("1301", "KOTA JAKARTA")))
        }

        override suspend fun searchCities(keyword: String): PrayerCityResponse {
            searchCalls += 1
            return PrayerCityResponse(true, null, listOf(PrayerCityDto("1301", "KOTA JAKARTA")))
        }

        override suspend fun todaySchedule(cityId: String, timeZoneId: String): PrayerScheduleResponse {
            return PrayerScheduleResponse(
                status = true,
                message = null,
                data = PrayerScheduleDataDto(
                    id = cityId,
                    city = "KOTA JAKARTA",
                    province = "DKI JAKARTA",
                    schedules = mapOf(today to prayerTimes(today)),
                ),
            )
        }

        override suspend fun getMonthlyKemenagSchedule(
            cityId: String,
            year: Int,
            month: Int,
            timeZoneId: String,
        ): PrayerMonthlyResponse {
            monthCalls += 1
            return PrayerMonthlyResponse(
                status = true,
                message = null,
                data = PrayerMonthlyDataDto(
                    id = cityId,
                    city = "KOTA JAKARTA",
                    province = "DKI JAKARTA",
                    schedules = listOf(prayerTimes(today)),
                ),
            )
        }

        private fun prayerTimes(date: String): PrayerTimesDto {
            return PrayerTimesDto(
                dateLabel = "Kamis, ${date.substring(8, 10)}/${date.substring(5, 7)}/${date.substring(0, 4)}",
                subuh = "04:37",
                dzuhur = "11:55",
                ashar = "15:16",
                maghrib = "17:48",
                isya = "19:02",
                date = date,
            )
        }
    }

    private class FakeAladhanApi : AladhanApi {
        override suspend fun timings(
            date: String,
            latitude: Double,
            longitude: Double,
            method: Int,
            timeZoneId: String,
        ): AladhanTimingsResponse = error("Unused")

        override suspend fun getAladhanCalendar(
            year: Int,
            month: Int,
            latitude: Double,
            longitude: Double,
            method: Int,
            timeZoneId: String,
        ): AladhanCalendarResponse = error("Unused")
    }

    private class MemoryPrayerScheduleStore : PrayerScheduleStore {
        private val schedules = linkedMapOf<String, PrayerSchedule>()

        override var provider: PrayerProvider = PrayerProvider.KEMENAG
        override var cityQuery: String = "jakarta"
        override var cityId: String = ""
        override var cityLabel: String = ""
        override var autoDetectLocation: Boolean = false
        override var globalLocationLabel: String = "Mecca"
        override var globalCountry: String = "Saudi Arabia"
        override var globalLatitude: Double = 21.4225
        override var globalLongitude: Double = 39.8262
        override var globalTimeZoneId: String = "Asia/Riyadh"
        override var globalMethod: Int = 3
        override val activeCacheKey: String
            get() = when (provider) {
                PrayerProvider.KEMENAG -> "${provider.id}:${cityId.ifBlank { cityQuery }}"
                PrayerProvider.GLOBAL -> "${provider.id}:$globalLatitude:$globalLongitude:$globalMethod:$globalTimeZoneId"
            }
        val savedCacheKeys = mutableListOf<String>()

        override fun getCachedSchedule(): PrayerSchedule? = schedules.values.lastOrNull()

        override fun getStaleCachedSchedule(): PrayerSchedule? = schedules.values.lastOrNull()

        override fun saveSchedule(schedule: PrayerSchedule) {
            saveSchedules(schedule.cacheKey, listOf(schedule))
        }

        override fun saveSchedules(cacheKey: String, schedules: List<PrayerSchedule>) {
            savedCacheKeys.add(cacheKey)
            schedules.forEach { schedule ->
                this.schedules["$cacheKey:${schedule.dateYmd}"] = schedule.copy(cacheKey = cacheKey)
            }
        }

        override fun getCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule? {
            return schedules["$cacheKey:$dateYmd"]
        }

        override fun getStaleCachedScheduleForDate(cacheKey: String, dateYmd: String): PrayerSchedule? {
            return schedules["$cacheKey:$dateYmd"]
        }

        override fun isCacheFreshForDate(cacheKey: String, dateYmd: String, ttlMillis: Long): Boolean {
            val schedule = getStaleCachedScheduleForDate(cacheKey, dateYmd) ?: return false
            return System.currentTimeMillis() - schedule.fetchedAtMillis <= ttlMillis
        }
    }
}
