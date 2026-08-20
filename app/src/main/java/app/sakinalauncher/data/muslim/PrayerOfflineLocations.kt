package app.sakinalauncher.data.muslim

import java.util.Locale

/**
 * Coordinates for the places the launcher can be pinned to, so prayer times can be
 * computed offline even when the user picked a city by name instead of granting
 * location permission.
 *
 * City-level precision is enough: one degree of longitude shifts the schedule by
 * four minutes, and every entry below is the city centre, so the error against a
 * neighbourhood-level position stays well under a minute.
 *
 * Matching is done on the label Kemenag/Bimas Islam returns (e.g. "KOTA JAKARTA",
 * "KAB. BANDUNG"), which is why the keys are bare city names.
 */
object PrayerOfflineLocations {

    data class Place(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val timeZoneId: String,
    )

    /** Fallback when nothing matches: the national reference point. */
    val JAKARTA = Place("JAKARTA", -6.1751, 106.8650, "Asia/Jakarta")

    private val places = listOf(
        // WIB — Sumatra
        Place("BANDA ACEH", 5.5483, 95.3238, "Asia/Jakarta"),
        Place("LHOKSEUMAWE", 5.1801, 97.1507, "Asia/Jakarta"),
        Place("MEDAN", 3.5952, 98.6722, "Asia/Jakarta"),
        Place("PEMATANG SIANTAR", 2.9595, 99.0687, "Asia/Jakarta"),
        Place("PADANG SIDEMPUAN", 1.3735, 99.2681, "Asia/Jakarta"),
        Place("PEKANBARU", 0.5071, 101.4478, "Asia/Jakarta"),
        Place("DUMAI", 1.6667, 101.4500, "Asia/Jakarta"),
        Place("BATAM", 1.0456, 104.0305, "Asia/Jakarta"),
        Place("TANJUNG PINANG", 0.9186, 104.4558, "Asia/Jakarta"),
        Place("PADANG", -0.9471, 100.4172, "Asia/Jakarta"),
        Place("BUKITTINGGI", -0.3055, 100.3691, "Asia/Jakarta"),
        Place("JAMBI", -1.6101, 103.6131, "Asia/Jakarta"),
        Place("PALEMBANG", -2.9761, 104.7754, "Asia/Jakarta"),
        Place("LUBUKLINGGAU", -3.2966, 102.8619, "Asia/Jakarta"),
        Place("BENGKULU", -3.8004, 102.2655, "Asia/Jakarta"),
        Place("PANGKAL PINANG", -2.1316, 106.1169, "Asia/Jakarta"),
        Place("BANDAR LAMPUNG", -5.3971, 105.2668, "Asia/Jakarta"),
        Place("METRO", -5.1131, 105.3067, "Asia/Jakarta"),

        // WIB — Java
        Place("JAKARTA", -6.1751, 106.8650, "Asia/Jakarta"),
        Place("BOGOR", -6.5950, 106.8166, "Asia/Jakarta"),
        Place("DEPOK", -6.4025, 106.7942, "Asia/Jakarta"),
        Place("TANGERANG", -6.1783, 106.6319, "Asia/Jakarta"),
        Place("BEKASI", -6.2383, 106.9756, "Asia/Jakarta"),
        Place("SERANG", -6.1200, 106.1503, "Asia/Jakarta"),
        Place("CILEGON", -6.0025, 106.0114, "Asia/Jakarta"),
        Place("SUKABUMI", -6.9277, 106.9300, "Asia/Jakarta"),
        Place("BANDUNG", -6.9175, 107.6191, "Asia/Jakarta"),
        Place("CIMAHI", -6.8722, 107.5425, "Asia/Jakarta"),
        Place("GARUT", -7.2000, 107.9000, "Asia/Jakarta"),
        Place("TASIKMALAYA", -7.3274, 108.2207, "Asia/Jakarta"),
        Place("CIREBON", -6.7320, 108.5523, "Asia/Jakarta"),
        Place("TEGAL", -6.8694, 109.1402, "Asia/Jakarta"),
        Place("PEKALONGAN", -6.8886, 109.6753, "Asia/Jakarta"),
        Place("SEMARANG", -6.9932, 110.4203, "Asia/Jakarta"),
        Place("SALATIGA", -7.3305, 110.5084, "Asia/Jakarta"),
        Place("SURAKARTA", -7.5755, 110.8243, "Asia/Jakarta"),
        Place("SOLO", -7.5755, 110.8243, "Asia/Jakarta"),
        Place("MAGELANG", -7.4706, 110.2178, "Asia/Jakarta"),
        Place("PURWOKERTO", -7.4249, 109.2397, "Asia/Jakarta"),
        Place("YOGYAKARTA", -7.7956, 110.3695, "Asia/Jakarta"),
        Place("MADIUN", -7.6298, 111.5239, "Asia/Jakarta"),
        Place("KEDIRI", -7.8480, 112.0178, "Asia/Jakarta"),
        Place("BLITAR", -8.0955, 112.1609, "Asia/Jakarta"),
        Place("MALANG", -7.9666, 112.6326, "Asia/Jakarta"),
        Place("BATU", -7.8672, 112.5239, "Asia/Jakarta"),
        Place("SURABAYA", -7.2575, 112.7521, "Asia/Jakarta"),
        Place("SIDOARJO", -7.4478, 112.7183, "Asia/Jakarta"),
        Place("GRESIK", -7.1548, 112.6560, "Asia/Jakarta"),
        Place("MOJOKERTO", -7.4664, 112.4338, "Asia/Jakarta"),
        Place("PASURUAN", -7.6459, 112.9075, "Asia/Jakarta"),
        Place("PROBOLINGGO", -7.7543, 113.2159, "Asia/Jakarta"),
        Place("JEMBER", -8.1845, 113.6681, "Asia/Jakarta"),
        Place("BANYUWANGI", -8.2192, 114.3691, "Asia/Jakarta"),
        Place("MUSI BANYUASIN", -2.6667, 103.7500, "Asia/Jakarta"),
        Place("SEKAYU", -2.8667, 103.8500, "Asia/Jakarta"),

        // WIB — West Kalimantan
        Place("PONTIANAK", -0.0263, 109.3425, "Asia/Jakarta"),
        Place("SINGKAWANG", 0.9061, 108.9861, "Asia/Jakarta"),
        Place("PALANGKARAYA", -2.2100, 113.9213, "Asia/Jakarta"),

        // WITA
        Place("DENPASAR", -8.6705, 115.2126, "Asia/Makassar"),
        Place("MATARAM", -8.5833, 116.1167, "Asia/Makassar"),
        Place("BIMA", -8.4600, 118.7267, "Asia/Makassar"),
        Place("KUPANG", -10.1772, 123.6070, "Asia/Makassar"),
        Place("BANJARMASIN", -3.3194, 114.5908, "Asia/Makassar"),
        Place("BANJARBARU", -3.4572, 114.8114, "Asia/Makassar"),
        Place("BALIKPAPAN", -1.2379, 116.8529, "Asia/Makassar"),
        Place("SAMARINDA", -0.5022, 117.1536, "Asia/Makassar"),
        Place("BONTANG", 0.1324, 117.4900, "Asia/Makassar"),
        Place("TARAKAN", 3.3273, 117.5914, "Asia/Makassar"),
        Place("MAKASSAR", -5.1477, 119.4327, "Asia/Makassar"),
        Place("PARE PARE", -4.0135, 119.6255, "Asia/Makassar"),
        Place("PALOPO", -2.9925, 120.1966, "Asia/Makassar"),
        Place("PALU", -0.8917, 119.8707, "Asia/Makassar"),
        Place("KENDARI", -3.9985, 122.5127, "Asia/Makassar"),
        Place("BAU BAU", -5.4700, 122.6167, "Asia/Makassar"),
        Place("GORONTALO", 0.5435, 123.0568, "Asia/Makassar"),
        Place("MANADO", 1.4748, 124.8421, "Asia/Makassar"),
        Place("BITUNG", 1.4400, 125.1211, "Asia/Makassar"),
        Place("TOMOHON", 1.3120, 124.8388, "Asia/Makassar"),
        Place("MAMUJU", -2.6748, 118.8885, "Asia/Makassar"),

        // WIT
        Place("AMBON", -3.6954, 128.1814, "Asia/Jayapura"),
        Place("TERNATE", 0.7963, 127.3862, "Asia/Jayapura"),
        Place("TIDORE", 0.6833, 127.4333, "Asia/Jayapura"),
        Place("SORONG", -0.8762, 131.2558, "Asia/Jayapura"),
        Place("MANOKWARI", -0.8615, 134.0620, "Asia/Jayapura"),
        Place("JAYAPURA", -2.5337, 140.7181, "Asia/Jayapura"),
        Place("TIMIKA", -4.5426, 136.8869, "Asia/Jayapura"),
        Place("MERAUKE", -8.4932, 140.4018, "Asia/Jayapura"),
        Place("NABIRE", -3.3500, 135.5000, "Asia/Jayapura"),
        Place("WAMENA", -4.0989, 138.9497, "Asia/Jayapura"),

        // Outside Indonesia — common destinations, useful when travelling
        Place("MECCA", 21.4225, 39.8262, "Asia/Riyadh"),
        Place("MAKKAH", 21.4225, 39.8262, "Asia/Riyadh"),
        Place("MEDINA", 24.4672, 39.6111, "Asia/Riyadh"),
        Place("MADINAH", 24.4672, 39.6111, "Asia/Riyadh"),
        Place("JEDDAH", 21.4858, 39.1925, "Asia/Riyadh"),
        Place("RIYADH", 24.7136, 46.6753, "Asia/Riyadh"),
        Place("CAIRO", 30.0444, 31.2357, "Africa/Cairo"),
        Place("KUALA LUMPUR", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
        Place("SINGAPORE", 1.3521, 103.8198, "Asia/Singapore"),
        Place("ISTANBUL", 41.0082, 28.9784, "Europe/Istanbul"),
        Place("DUBAI", 25.2048, 55.2708, "Asia/Dubai"),
        Place("DOHA", 25.2854, 51.5310, "Asia/Qatar"),
        Place("AMMAN", 31.9454, 35.9284, "Asia/Amman"),
        Place("LONDON", 51.5074, -0.1278, "Europe/London"),
    )

    /**
     * Best coordinate match for a free-form city label. Kemenag labels carry
     * prefixes ("KOTA", "KAB.") and sometimes the province, so matching is done on
     * containment in both directions, longest name first to prefer the specific
     * entry ("BANDAR LAMPUNG" over "LAMPUNG").
     */
    fun match(label: String?): Place? {
        val needle = normalize(label ?: return null)
        if (needle.isBlank()) return null
        return places
            .sortedByDescending { it.name.length }
            .firstOrNull { place ->
                val name = place.name
                needle.contains(name) || name.contains(needle)
            }
    }

    /** [match] with the national reference point as a last resort. */
    fun matchOrJakarta(label: String?): Place = match(label) ?: JAKARTA

    private fun normalize(value: String): String {
        var text = value.uppercase(Locale.US)
        listOf("KOTA ADM.", "KOTA ADMINISTRASI", "KABUPATEN", "KAB.", "KOTA", "CITY").forEach {
            text = text.replace(it, " ")
        }
        return text.replace('-', ' ').replace(Regex("[^A-Z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
