package com.hyrumrichardson.scrywall

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    val screen: Pair<Int, Int> = WallpaperRenderer.screenSize(app)

    var source by mutableStateOf(prefs.source); private set
    /** The Scryfall search or Moxfield deck link, depending on [source]. */
    var query by mutableStateOf(if (prefs.source == Source.MOXFIELD) prefs.deck else prefs.query)
    var deckName by mutableStateOf<String?>(null); private set
    var searching by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var total by mutableIntStateOf(0); private set
    var samples by mutableStateOf<List<Card>>(emptyList()); private set
    var selected by mutableIntStateOf(0); private set

    var style by mutableStateOf(prefs.style); private set
    var scale by mutableStateOf(prefs.scale); private set
    var target by mutableStateOf(prefs.target); private set
    var interval by mutableStateOf(prefs.interval); private set

    var preview by mutableStateOf<ImageBitmap?>(null); private set
    var previewLoading by mutableStateOf(false); private set

    var applying by mutableStateOf(false); private set
    var rotating by mutableStateOf(prefs.rotating); private set
    var activeQuery by mutableStateOf(prefs.activeQuery); private set
    var activeSource by mutableStateOf(prefs.activeSource); private set
    var activeDeckName by mutableStateOf(prefs.activeDeckName); private set
    var activeInterval by mutableStateOf(prefs.interval); private set
    var lastCard by mutableStateOf(prefs.lastCard); private set
    var lastChanged by mutableLongStateOf(prefs.lastChanged); private set
    var message by mutableStateOf<String?>(null); private set

    private val sourceCache = mutableMapOf<String, Bitmap>()
    private var previewJob: Job? = null

    private var searchJob: Job? = null

    fun updateSource(v: Source) {
        if (v == source) return
        saveQuery(query.trim())
        source = v
        prefs.source = v
        query = if (v == Source.MOXFIELD) prefs.deck else prefs.query
        // Results from the other source no longer apply.
        searchJob?.cancel()
        previewJob?.cancel()
        searching = false
        previewLoading = false
        samples = emptyList()
        total = 0
        deckName = null
        preview = null
        error = null
    }

    private fun saveQuery(q: String) {
        if (source == Source.MOXFIELD) prefs.deck = q else prefs.query = q
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        saveQuery(q)
        val src = source
        searchJob = viewModelScope.launch {
            searching = true
            error = null
            try {
                val result = src.sample(q)
                samples = result.cards
                total = result.total
                deckName = result.deckName
                selected = 0
                refreshPreview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceException) {
                error = e.message
            } catch (e: Exception) {
                error = "Couldn't reach ${src.label}. Check your connection and try again."
            } finally {
                if (isActive) searching = false
            }
        }
    }

    fun select(index: Int) {
        if (index == selected) return
        selected = index
        refreshPreview()
    }

    fun updateStyle(v: ImageStyle) { style = v; prefs.style = v; refreshPreview() }
    fun updateScale(v: ScaleMode) { scale = v; prefs.scale = v; refreshPreview() }
    fun updateTarget(v: WallTarget) { target = v; prefs.target = v }
    fun updateInterval(v: Interval) { interval = v; prefs.interval = v }

    private fun refreshPreview() {
        val card = samples.getOrNull(selected) ?: return
        val url = card.imageUrl(style) ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            previewLoading = true
            try {
                val src = sourceCache[url] ?: Scryfall.downloadBitmap(url).also { sourceCache[url] = it }
                val (w, h) = screen
                // Preview at ~400px tall: plenty for a thumbnail, cheap to redraw.
                val k = 400f / h
                val bmp = withContext(Dispatchers.Default) {
                    WallpaperRenderer.render(src, w, h, scale, k)
                }
                preview = bmp.asImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                preview = null
            } finally {
                // A cancelled job must not hide the spinner of the job that replaced it.
                if (isActive) previewLoading = false
            }
        }
    }

    fun apply() {
        val q = query.trim()
        if (q.isEmpty() || applying) return
        saveQuery(q)
        prefs.activeQuery = q
        prefs.activeSource = source
        // Name of the loaded deck for now; changeNow() refreshes it from Moxfield.
        prefs.activeDeckName = if (source == Source.MOXFIELD) deckName.orEmpty() else ""
        val src = source
        viewModelScope.launch {
            applying = true
            message = null
            try {
                val name = WallpaperSetter.changeNow(getApplication())
                WallpaperWorker.schedule(getApplication(), interval)
                val willRotate = interval != Interval.NEVER
                prefs.rotating = willRotate
                rotating = willRotate
                activeQuery = q
                activeSource = src
                activeDeckName = prefs.activeDeckName
                activeInterval = interval
                lastCard = name
                lastChanged = prefs.lastChanged
                message = "Wallpaper set to $name."
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceException) {
                message = e.message
            } catch (e: Exception) {
                message = "Couldn't set the wallpaper: ${e.message ?: "unknown error"}"
            } finally {
                applying = false
            }
        }
    }

    fun changeNow() {
        if (applying) return
        viewModelScope.launch {
            applying = true
            message = null
            try {
                val name = WallpaperSetter.changeNow(getApplication())
                lastCard = name
                lastChanged = prefs.lastChanged
                activeDeckName = prefs.activeDeckName
                message = "Wallpaper set to $name."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Couldn't change the wallpaper."
            } finally {
                applying = false
            }
        }
    }

    fun stopRotating() {
        WallpaperWorker.cancel(getApplication())
        prefs.rotating = false
        rotating = false
        message = "Automatic changes stopped. Your current wallpaper stays."
    }

    override fun onCleared() {
        sourceCache.clear()
    }
}
