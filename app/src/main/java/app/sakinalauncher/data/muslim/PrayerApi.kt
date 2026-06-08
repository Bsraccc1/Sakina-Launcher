package app.sakinalauncher.data.muslim

import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
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
}

object PrayerApiClient {
    private const val MAX_RETRIES = 3

    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var lastError: IOException? = null
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful || response.code in 400..499) return@Interceptor response
                response.close()
                lastError = IOException("HTTP ${response.code}")
            } catch (error: IOException) {
                lastError = error
            }
            attempt++
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(400L * attempt)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        throw lastError ?: IOException("Request failed")
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(retryInterceptor)
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
)

data class AladhanTimingsResponse(
    val code: Int?,
    val status: String?,
    val data: AladhanTimingDataDto?,
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
)

data class AladhanMetaDto(
    val timezone: String?,
    val method: AladhanMethodDto?,
)

data class AladhanMethodDto(
    val id: Int?,
    val name: String?,
)
