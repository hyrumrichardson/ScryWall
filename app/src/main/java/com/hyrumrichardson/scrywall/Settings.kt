package com.hyrumrichardson.scrywall

import android.app.WallpaperManager
import android.content.Context

/** Where cards come from: a Scryfall search or a public Moxfield deck. */
enum class Source(val label: String) {
    SCRYFALL("Scryfall"),
    MOXFIELD("Moxfield");

    suspend fun sample(input: String): Sample = when (this) {
        SCRYFALL -> Scryfall.sample(input)
        MOXFIELD -> Moxfield.sample(input)
    }
}

enum class ImageStyle(val label: String) {
    ART("Art only"),
    CARD("Full card"),
}

enum class ScaleMode(val label: String) {
    FILL("Fill"),
    FIT("Fit"),
    FIT_BLUR("Fit + blur"),
    STRETCH("Stretch"),
    CENTER("Center"),
}

enum class WallTarget(val label: String, val flags: Int) {
    HOME("Home screen", WallpaperManager.FLAG_SYSTEM),
    LOCK("Lock screen", WallpaperManager.FLAG_LOCK),
    BOTH("Both", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

enum class Interval(val label: String, val minutes: Long) {
    NEVER("Don't change", 0),
    M15("15 min", 15),
    M30("30 min", 30),
    H1("1 hour", 60),
    H3("3 hours", 180),
    H6("6 hours", 360),
    H12("12 hours", 720),
    D1("Daily", 1440),
    W1("Weekly", 10080);

    /** "every 15 min", "daily", ... for use in sentences. */
    val phrase: String
        get() = when (this) {
            NEVER -> "never"
            D1 -> "daily"
            W1 -> "weekly"
            else -> "every $label"
        }
}

/** Saved settings, shared by the screen and the background worker. */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("scrywall", Context.MODE_PRIVATE)

    var query: String
        get() = sp.getString("query", "") ?: ""
        set(v) = sp.edit().putString("query", v).apply()

    /** Moxfield deck link, kept separately so switching sources doesn't lose the search. */
    var deck: String
        get() = sp.getString("deck", "") ?: ""
        set(v) = sp.edit().putString("deck", v).apply()

    var source: Source
        get() = enumOr(sp.getString("source", null), Source.SCRYFALL)
        set(v) = sp.edit().putString("source", v.name).apply()

    var style: ImageStyle
        get() = enumOr(sp.getString("style", null), ImageStyle.ART)
        set(v) = sp.edit().putString("style", v.name).apply()

    var scale: ScaleMode
        get() = enumOr(sp.getString("scale", null), ScaleMode.FILL)
        set(v) = sp.edit().putString("scale", v.name).apply()

    var target: WallTarget
        get() = enumOr(sp.getString("target", null), WallTarget.BOTH)
        set(v) = sp.edit().putString("target", v.name).apply()

    var interval: Interval
        get() = enumOr(sp.getString("interval", null), Interval.D1)
        set(v) = sp.edit().putString("interval", v.name).apply()

    /** The query or deck link the rotation actually uses (set when the user taps "Set wallpaper"). */
    var activeQuery: String
        get() = sp.getString("activeQuery", "") ?: ""
        set(v) = sp.edit().putString("activeQuery", v).apply()

    var activeSource: Source
        get() = enumOr(sp.getString("activeSource", null), Source.SCRYFALL)
        set(v) = sp.edit().putString("activeSource", v.name).apply()

    /** Name of the active Moxfield deck, refreshed each time the wallpaper changes. */
    var activeDeckName: String
        get() = sp.getString("activeDeckName", "") ?: ""
        set(v) = sp.edit().putString("activeDeckName", v).apply()

    var rotating: Boolean
        get() = sp.getBoolean("rotating", false)
        set(v) = sp.edit().putBoolean("rotating", v).apply()

    var lastCard: String
        get() = sp.getString("lastCard", "") ?: ""
        set(v) = sp.edit().putString("lastCard", v).apply()

    var lastChanged: Long
        get() = sp.getLong("lastChanged", 0L)
        set(v) = sp.edit().putLong("lastChanged", v).apply()

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
