package app.sakinalauncher.data.muslim

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The in-memory caches behind [PrayerTimeStore]: `yyyy-MM-dd` formatters keyed by time
 * zone, and decoded schedules keyed by preference key.
 *
 * Deliberately free of Android dependencies, like [app.sakinalauncher.data.WidgetSizeMath],
 * so the concurrency rules are covered by plain JVM unit tests. One store instance is
 * shared between the main thread (the Muslim Center paints from cache during
 * `onViewCreated`) and `Dispatchers.IO` (fetch and save), so every entry point here is
 * reachable from two threads at once.
 *
 * Both caches are unsafe unsynchronised, and neither failure is loud:
 *  - [SimpleDateFormat] keeps parse state in a shared `Calendar`, so concurrent
 *    [formatYmd] calls return *each other's* dates — a schedule filed under the wrong day
 *    rather than an exception.
 *  - An access-ordered [LinkedHashMap] relinks on `get`, making a read a structural
 *    modification; concurrent access corrupts the link list and eviction stops firing.
 *
 * Every method therefore holds [lock]. Contention is irrelevant: these are microsecond
 * map operations, not I/O.
 *
 * @param maxEntries how many decoded schedules to retain; matches the store's own
 *   retention window, since anything beyond it is evicted from disk anyway.
 */
class PrayerScheduleCache(private val maxEntries: Int) {

    private val lock = Any()

    private val formatters = HashMap<String, SimpleDateFormat>(4)

    private val decoded = object : LinkedHashMap<String, PrayerSchedule>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, PrayerSchedule>): Boolean =
            size > maxEntries
    }

    /**
     * Formats [millis] as `yyyy-MM-dd` in [timeZoneId], reusing one formatter per zone.
     * Formatting happens inside the lock, not just the lookup — handing the formatter out
     * and calling `format` on it afterwards is the bug this class exists to prevent.
     */
    fun formatYmd(timeZoneId: String, millis: Long): String = synchronized(lock) {
        formatters.getOrPut(timeZoneId) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = runCatching { TimeZone.getTimeZone(timeZoneId) }
                    .getOrDefault(TimeZone.getDefault())
            }
        }.format(millis)
    }

    fun get(key: String): PrayerSchedule? = synchronized(lock) { decoded[key] }

    fun put(key: String, schedule: PrayerSchedule) = synchronized(lock) {
        decoded[key] = schedule
        Unit
    }

    fun putAll(entries: Map<String, PrayerSchedule>) = synchronized(lock) {
        decoded.putAll(entries)
    }

    fun removeAll(keys: Collection<String>) = synchronized(lock) {
        keys.forEach { decoded.remove(it) }
    }

    /** Retained entry count. Exposed for tests asserting the cap holds. */
    fun size(): Int = synchronized(lock) { decoded.size }
}
