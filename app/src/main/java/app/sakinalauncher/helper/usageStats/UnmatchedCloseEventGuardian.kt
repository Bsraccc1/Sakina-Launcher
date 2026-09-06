package app.sakinalauncher.helper.usageStats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.util.Log

/**
 * “…a diminutive Guardian who traveled backward through time…”
 *
 * Guards [EventLogWrapper] against Faulty unmatched close events (per
 * [the documentation](https://codeberg.org/fynngodau/usageDirect/wiki/Event-log-wrapper-scenarios))
 * by seeking backwards through time and scanning for the open event.
 *
 * The 24-hour scan is performed **once per `queryStart`** and replayed from memory
 * afterwards. [test] is called from inside [EventLogWrapper]'s event loop, so a fresh
 * `queryEvents` per call meant one multi-hundred-millisecond system query for every
 * unmatched close event on the day — dozens of them on a busy device, all repeating the
 * identical scan.
 */
class UnmatchedCloseEventGuardian(private val usageStatsManager: UsageStatsManager) {

    companion object {
        private const val SCAN_INTERVAL = 1000L * 60 * 60 * 24 // 24 hours

        /** Synthetic marker for DEVICE_STARTUP, which resets every package. */
        private const val TYPE_STARTUP = -1
    }

    private var scannedQueryStart: Long? = null

    /**
     * Per package, the RESUMED/PAUSED events in the scan window, with device startups
     * interleaved so replaying one package's list reproduces the original single pass.
     */
    private var scannedEvents: Map<String, List<Record>> = emptyMap()

    private class Record(val type: Int, val timeStamp: Long)

    /**
     * @param event      Event to validate
     * @param queryStart Timestamp at which original query o
     * @return True if the event is valid, false otherwise
     */
    fun test(event: UsageEvents.Event, queryStart: Long): Boolean {
        val records = recordsFor(queryStart)[event.packageName].orEmpty()

        // Track whether the package is currently in foreground or background
        var open = false // Not open until opened

        for (record in records) {
            when (record.type) {
                // Consider all apps closed after startup according to docs
                TYPE_STARTUP -> open = false

                // see EventLogWrapper
                UsageEvents.Event.ACTIVITY_RESUMED, 4 -> open = true

                UsageEvents.Event.ACTIVITY_PAUSED, 3 -> {
                    if (record.timeStamp != event.timeStamp) {
                        // Don't flip to 'false' if we're looking at the original event itself
                        open = false
                    }
                }
            }
        }

        val result = if (open) "True" else "Faulty"
        Log.d("Guardian", "Scanned for package ${event.packageName} and determined event to be $result")

        // Event is valid if it was previously opened (within SCAN_INTERVAL)
        return open
    }

    /** One system query per [queryStart], then served from memory. */
    private fun recordsFor(queryStart: Long): Map<String, List<Record>> {
        if (scannedQueryStart == queryStart) return scannedEvents

        val events = usageStatsManager.queryEvents(queryStart - SCAN_INTERVAL, queryStart)
        val byPackage = HashMap<String, MutableList<Record>>()
        // Reusable event object for iteration
        val e = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(e)

            if (e.eventType == UsageEvents.Event.DEVICE_STARTUP) {
                // A startup resets every package, so it lands in all lists.
                val marker = Record(TYPE_STARTUP, e.timeStamp)
                byPackage.values.forEach { it.add(marker) }
                continue
            }

            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, 4,
                UsageEvents.Event.ACTIVITY_PAUSED, 3 -> {
                    val pkg = e.packageName ?: continue
                    byPackage.getOrPut(pkg) { mutableListOf() }
                        .add(Record(e.eventType, e.timeStamp))
                }
            }
        }

        scannedQueryStart = queryStart
        scannedEvents = byPackage
        return byPackage
    }
}
