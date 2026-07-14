package app.sakinalauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class BoundWidget(
    val appWidgetId: Int,
    val providerFlattened: String,
    /** 0 = use provider / default height. */
    val heightDp: Int = 0,
    /** 0 = full container width. Otherwise explicit width in dp. */
    val widthDp: Int = 0,
) {
    fun providerComponent(): ComponentName? =
        runCatching { ComponentName.unflattenFromString(providerFlattened) }.getOrNull()
}

/**
 * On-device persistence for AppWidgets hosted in the Productive panel.
 * No network; JSON in a dedicated SharedPreferences file.
 */
class ProductiveWidgetStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    @Volatile
    private var cache: List<BoundWidget>? = null

    init {
        // One-time: drop host-forced sizes from older builds that ignored provider
        // minWidth/minHeight (double-scaled px-as-dp). Next inflate uses provider defaults.
        if (!prefs.getBoolean(KEY_SIZE_PROVIDER_V2, false)) {
            val migrated = decode(prefs.getString(KEY_WIDGETS, null)).map {
                it.copy(widthDp = 0, heightDp = 0)
            }
            cache = migrated
            prefs.edit {
                putString(KEY_WIDGETS, encode(migrated))
                putBoolean(KEY_SIZE_PROVIDER_V2, true)
            }
        }
    }

    fun getWidgets(): List<BoundWidget> {
        cache?.let { return it }
        val decoded = decode(prefs.getString(KEY_WIDGETS, null))
        cache = decoded
        return decoded
    }

    fun setWidgets(widgets: List<BoundWidget>) {
        val next = widgets.distinctBy { it.appWidgetId }
        cache = next
        prefs.edit { putString(KEY_WIDGETS, encode(next)) }
    }

    fun addWidget(widget: BoundWidget) {
        val without = getWidgets().filterNot { it.appWidgetId == widget.appWidgetId }
        setWidgets(without + widget)
    }

    fun removeWidget(appWidgetId: Int): Boolean {
        val current = getWidgets()
        val next = current.filterNot { it.appWidgetId == appWidgetId }
        if (next.size == current.size) return false
        setWidgets(next)
        return true
    }

    fun updateSize(appWidgetId: Int, widthDp: Int, heightDp: Int): Boolean {
        var updated = false
        val next = getWidgets().map { widget ->
            if (widget.appWidgetId == appWidgetId) {
                updated = true
                widget.copy(
                    widthDp = widthDp.coerceAtLeast(0),
                    heightDp = heightDp.coerceAtLeast(0),
                )
            } else {
                widget
            }
        }
        if (updated) setWidgets(next)
        return updated
    }

    fun updateHeight(appWidgetId: Int, heightDp: Int): Boolean {
        var updated = false
        val next = getWidgets().map { widget ->
            if (widget.appWidgetId == appWidgetId) {
                updated = true
                widget.copy(heightDp = heightDp.coerceAtLeast(0))
            } else {
                widget
            }
        }
        if (updated) setWidgets(next)
        return updated
    }

    companion object {
        private const val PREFS_FILENAME = "app.sakinalauncher.productive_widgets"
        private const val KEY_WIDGETS = "BOUND_WIDGETS"
        private const val KEY_SIZE_PROVIDER_V2 = "SIZE_FROM_PROVIDER_V2"

        fun encode(widgets: List<BoundWidget>): String {
            val array = JSONArray()
            widgets.forEach { widget ->
                array.put(
                    JSONObject()
                        .put("id", widget.appWidgetId)
                        .put("provider", widget.providerFlattened)
                        .put("heightDp", widget.heightDp)
                        .put("widthDp", widget.widthDp),
                )
            }
            return array.toString()
        }

        fun decode(payload: String?): List<BoundWidget> {
            return runCatching {
                val array = JSONArray(payload ?: "[]")
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val id = item.optInt("id", -1)
                        val provider = item.optString("provider")
                        if (id < 0 || provider.isBlank()) continue
                        add(
                            BoundWidget(
                                appWidgetId = id,
                                providerFlattened = provider,
                                heightDp = item.optInt("heightDp", 0).coerceAtLeast(0),
                                widthDp = item.optInt("widthDp", 0).coerceAtLeast(0),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
