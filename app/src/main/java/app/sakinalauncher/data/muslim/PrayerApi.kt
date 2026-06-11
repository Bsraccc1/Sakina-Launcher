package app.sakinalauncher.data.muslim

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface PrayerApi {
    @GET("sholat/kabkota/semua")
    suspend fun allCities(): PrayerCityResponse

    @GET("sholat/kabkota/cari/{keyword}")
    suspend fun searchCities(@Path("keyword") keyword: String): PrayerCityResponse

    @GET("sholat/jadwal/{cityId}/today")
    suspend fun todaySchedule(
        @Path("cityId") cityId: String,
        @Query("tz") timeZoneId: String,
    ): PrayerScheduleResponse

    @GET("sholat/jadwal/{cityId}/{year}/{month}")
    suspend fun getMonthlyKemenagSchedule(
        @Path("cityId") cityId: String,
        @Path("year") year: Int,
        @Path("month") month: Int,
        @Query("tz") timeZoneId: String,
    ): PrayerMonthlyResponse
}

interface AladhanApi {
    @GET("timings/{date}")
    suspend fun timings(
        @Path("date") date: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int,
        @Query("timezonestring") timeZoneId: String,
    ): AladhanTimingsResponse

    @GET("calendar/{year}/{month}")
    suspend fun getAladhanCalendar(
        @Path("year") year: Int,
        @Path("month") month: Int,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int,
        @Query("timezonestring") timeZoneId: String,
    ): AladhanCalendarResponse
}

object PrayerApiClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val api: PrayerApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.myquran.com/v3/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PrayerApi::class.java)
    }

    val aladhanApi: AladhanApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.aladhan.com/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AladhanApi::class.java)
    }
}

data class PrayerCityResponse(
    val status: Boolean,
    val message: String?,
    val data: List<PrayerCityDto>?,
)

data class PrayerCityDto(
    val id: String,
    @SerializedName("lokasi")
    val location: String,
)

data class PrayerScheduleResponse(
    val status: Boolean,
    val message: String?,
    val data: PrayerScheduleDataDto?,
)

data class PrayerScheduleDataDto(
    val id: String?,
    @SerializedName("kabko")
    val city: String?,
    @SerializedName("prov")
    val province: String?,
    @SerializedName("jadwal")
    val schedules: Map<String, PrayerTimesDto>?,
)

data class PrayerTimesDto(
    @SerializedName("tanggal")
    val dateLabel: String?,
    val subuh: String?,
    val dzuhur: String?,
    val ashar: String?,
    val maghrib: String?,
    val isya: String?,
    val date: String?,
)

data class PrayerMonthlyResponse(
    val status: Boolean,
    val message: String?,
    val data: PrayerMonthlyDataDto?,
)

data class PrayerMonthlyDataDto(
    val id: String?,
    @SerializedName("kabko")
    val city: String?,
    @SerializedName("prov")
    val province: String?,
    @SerializedName("jadwal")
    val schedules: List<PrayerTimesDto>?,
)

data class AladhanTimingsResponse(
    val code: Int?,
    val status: String?,
    val data: AladhanTimingDataDto?,
)

data class AladhanCalendarResponse(
    val code: Int?,
    val status: String?,
    val data: List<AladhanTimingDataDto>?,
)

data class AladhanTimingDataDto(
    val timings: AladhanTimesDto?,
    val date: AladhanDateDto?,
    val meta: AladhanMetaDto?,
)

data class AladhanTimesDto(
    @SerializedName("Fajr")
    val fajr: String?,
    @SerializedName("Dhuhr")
    val dhuhr: String?,
    @SerializedName("Asr")
    val asr: String?,
    @SerializedName("Maghrib")
    val maghrib: String?,
    @SerializedName("Isha")
    val isha: String?,
)

data class AladhanDateDto(
    val readable: String?,
    val gregorian: AladhanGregorianDto?,
)

data class AladhanGregorianDto(
    val date: String?,
)

data class AladhanMetaDto(
    val timezone: String?,
    val method: AladhanMethodDto?,
)

data class AladhanMethodDto(
    val id: Int?,
    val name: String?,
)
