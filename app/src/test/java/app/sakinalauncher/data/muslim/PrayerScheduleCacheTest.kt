package app.sakinalauncher.data.muslim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [PrayerScheduleCache] is reached from the main thread (the Muslim Center paints from
 * cache in `onViewCreated`) and from `Dispatchers.IO` (fetch and save) through the same
 * store instance, so these tests pin the concurrency contract that makes that safe.
 *
 * The two caches it holds are both unsafe by default: `SimpleDateFormat` keeps mutable
 * parse state in a shared `Calendar`, and an access-ordered `LinkedHashMap` relinks on
 * `get`, making a read a structural modification. Both were thread-confined by accident
 * before they were cached, which is why no test caught it.
 */
class PrayerScheduleCacheTest {

    private val zone = "Asia/Jakarta"
    private val cap = 14

    // -------------------------------------------------------------- behaviour

    @Test
    fun formatsYmdInTheRequestedZone() {
        val cache = PrayerScheduleCache(cap)
        val millis = 1_757_000_000_000L

        assertEquals(expectedYmd(zone, millis), cache.formatYmd(zone, millis))
        assertEquals(expectedYmd("Asia/Jayapura", millis), cache.formatYmd("Asia/Jayapura", millis))
    }

    @Test
    fun unknownZoneFallsBackInsteadOfThrowing() {
        val cache = PrayerScheduleCache(cap)

        val formatted = cache.formatYmd("Not/AZone", 1_757_000_000_000L)

        assertTrue("expected a yyyy-MM-dd date, got $formatted", formatted.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun reusedFormatterStillHonoursItsZone() {
        val cache = PrayerScheduleCache(cap)
        val millis = 1_757_000_000_000L

        // Second call hits the cached formatter; it must not have drifted.
        repeat(3) { assertEquals(expectedYmd(zone, millis), cache.formatYmd(zone, millis)) }
    }

    @Test
    fun retainsEntriesUpToTheCapAndEvictsBeyondIt() {
        val cache = PrayerScheduleCache(cap)

        repeat(cap + 6) { cache.put("k$it", schedule("2026-09-${it + 1}")) }

        assertEquals(cap, cache.size())
    }

    @Test
    fun removeAllDropsTheGivenKeys() {
        val cache = PrayerScheduleCache(cap)
        cache.putAll((0 until 5).associate { "k$it" to schedule("2026-09-0$it") })

        cache.removeAll(listOf("k1", "k3"))

        assertEquals(3, cache.size())
        assertNull(cache.get("k1"))
        assertNull(cache.get("k3"))
        assertEquals("2026-09-02", cache.get("k2")?.dateYmd)
    }

    // ----------------------------------------------------------- concurrency

    /**
     * Each thread formats its own date through the shared cache and must read that date
     * back. Unsynchronised, threads see each other's dates — a wrong prayer schedule
     * silently attributed to the wrong day.
     */
    @Test
    fun concurrentFormattingNeverReturnsAnotherThreadsDate() {
        val cache = PrayerScheduleCache(cap)
        val threads = 16
        val iterations = 20_000
        val dayMillis = 86_400_000L
        val base = 1_757_000_000_000L
        val expected = (0 until threads).map { expectedYmd(zone, base + it * dayMillis) }

        val wrong = AtomicInteger()
        val sample = java.util.Collections.synchronizedList(mutableListOf<String>())
        val thrown = hammer(threads, iterations) { thread, _ ->
            val actual = cache.formatYmd(zone, base + thread * dayMillis)
            if (actual != expected[thread]) {
                wrong.incrementAndGet()
                if (sample.size < 3) sample.add("thread $thread wanted ${expected[thread]} got $actual")
            }
        }

        assertEquals("threw under concurrency: $thrown", emptyList<String>(), thrown)
        assertEquals("read another thread's date: $sample", 0, wrong.get())
    }

    /**
     * Concurrent reads and writes must leave the map structurally intact and inside its
     * cap. Unsynchronised, the access-ordered relink corrupts the link list and eviction
     * stops firing.
     */
    @Test
    fun concurrentAccessKeepsTheCacheWithinItsCap() {
        val cache = PrayerScheduleCache(cap)
        val threads = 16
        val iterations = 20_000
        repeat(cap) { cache.put("k$it", schedule("2026-09-${it + 1}")) }

        val oversize = AtomicInteger()
        val thrown = hammer(threads, iterations) { thread, i ->
            cache.get("k${(i + thread) % cap}")
            if (i % 3 == 0) cache.put("k${(i * 7 + thread) % (cap * 2)}", schedule("2026-10-01"))
            if (cache.size() > cap) oversize.incrementAndGet()
        }

        assertEquals("threw under concurrency: $thrown", emptyList<String>(), thrown)
        assertEquals("cache exceeded its cap $oversize times", 0, oversize.get())
        assertTrue("final size ${cache.size()} exceeds cap $cap", cache.size() <= cap)
    }

    // --------------------------------------------------------------- helpers

    /** Truth source: a formatter no other thread can touch. */
    private fun expectedYmd(timeZoneId: String, millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone(timeZoneId) }
            .format(millis)

    private fun schedule(dateYmd: String) = PrayerSchedule(
        city = "KOTA JAKARTA",
        province = "",
        dateLabel = dateYmd,
        fetchedAtMillis = 0L,
        times = listOf(PrayerTime(PrayerName.FAJR, "04:36")),
        dateYmd = dateYmd,
    )

    /** Runs [block] on [threads] threads x [iterations], collecting distinct throwables. */
    private fun hammer(threads: Int, iterations: Int, block: (thread: Int, iter: Int) -> Unit): List<String> {
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val thrown = java.util.Collections.synchronizedSet(linkedSetOf<String>())
        repeat(threads) { thread ->
            pool.execute {
                start.await()
                repeat(iterations) { i ->
                    try {
                        block(thread, i)
                    } catch (e: Throwable) {
                        thrown.add("${e.javaClass.simpleName}: ${e.message}")
                    }
                }
                done.countDown()
            }
        }
        start.countDown()
        val finished = done.await(120, TimeUnit.SECONDS)
        pool.shutdownNow()
        if (!finished) thrown.add("TIMEOUT: threads never finished")
        return thrown.toList()
    }
}
