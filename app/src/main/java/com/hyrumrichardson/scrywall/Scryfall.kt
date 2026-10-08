package com.hyrumrichardson.scrywall

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ScryfallException(message: String) : Exception(message)

data class Card(
    val name: String,
    val artUrl: String?,
    val cardUrl: String?,
    val thumbUrl: String?,
) {
    fun imageUrl(style: ImageStyle): String? = when (style) {
        ImageStyle.ART -> artUrl ?: cardUrl
        ImageStyle.CARD -> cardUrl ?: artUrl
    }

    fun thumbnail(style: ImageStyle): String? = when (style) {
        ImageStyle.ART -> artUrl ?: thumbUrl
        ImageStyle.CARD -> thumbUrl ?: cardUrl
    }
}

data class Sample(val total: Int, val cards: List<Card>)

/** Minimal client for the public Scryfall API (https://scryfall.com/docs/api). */
object Scryfall {
    private const val BASE = "https://api.scryfall.com"
    private const val PAGE_SIZE = 175

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Up to [count] random cards from the search, plus the total number of matches. */
    suspend fun sample(query: String, count: Int = 5): Sample = withContext(Dispatchers.IO) {
        val first = searchPage(query, 1)
        val total = first.optInt("total_cards", 0)
        val pool = parseCards(first).toMutableList()

        // For big searches, also pull one random later page so the preview isn't
        // always drawn from the first 175 results.
        val pages = (total + PAGE_SIZE - 1) / PAGE_SIZE
        if (pages > 1) {
            val page = Random.nextInt(2, pages + 1)
            delay(120) // Scryfall asks for 50-100 ms between requests
            runCatching { pool += parseCards(searchPage(query, page)) }
        }
        if (pool.isEmpty()) throw ScryfallException("None of the matching cards have images.")
        Sample(total, pool.distinctBy { it.artUrl ?: it.cardUrl }.shuffled().take(count))
    }

    /**
     * One random result from the same search the preview uses, so options like
     * unique:prints and the art-level de-duplication apply here too.
     * (Scryfall's /cards/random endpoint ignores those and returns one printing per card.)
     */
    suspend fun random(query: String): Card = withContext(Dispatchers.IO) {
        val first = searchPage(query, 1)
        val total = first.optInt("total_cards", 0)
        val firstData = first.optJSONArray("data")
        if (total <= 0 || firstData == null || firstData.length() == 0) {
            throw ScryfallException("Your search didn't match any cards.")
        }
        repeat(5) { attempt ->
            val index = Random.nextInt(total)
            val page = index / PAGE_SIZE + 1
            val data = if (page == 1) {
                firstData
            } else {
                delay(120) // Scryfall asks for 50-100 ms between requests
                runCatching { searchPage(query, page).optJSONArray("data") }.getOrNull()
            }
            if (data != null && data.length() > 0) {
                val card = data.optJSONObject(minOf(index % PAGE_SIZE, data.length() - 1))
                card?.let(::parseCard)?.let { return@withContext it }
            }
            if (attempt > 0) delay(120)
        }
        throw ScryfallException("Couldn't find a card with an image for this search.")
    }

    suspend fun downloadBitmap(url: String): Bitmap = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Image download failed (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("Empty image response")
            BitmapFactory.decodeStream(body.byteStream())
                ?: throw IOException("Couldn't decode image")
        }
    }

    private fun searchPage(query: String, page: Int): JSONObject {
        val url = "$BASE/cards/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("unique", "art")
            .addQueryParameter("page", page.toString())
            .build()
        return getJson(url)
    }

    private fun getJson(url: HttpUrl): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val json = try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw IOException("Unexpected response from Scryfall (HTTP ${resp.code})")
            }
            if (json.optString("object") == "error") {
                throw ScryfallException(json.optString("details", "Scryfall returned an error."))
            }
            if (!resp.isSuccessful) throw IOException("Scryfall request failed (HTTP ${resp.code})")
            return json
        }
    }

    private fun parseCards(list: JSONObject): List<Card> {
        val data = list.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i -> data.optJSONObject(i)?.let(::parseCard) }
    }

    private fun parseCard(o: JSONObject): Card? {
        val status = o.optString("image_status")
        if (status == "missing" || status == "placeholder") return null
        val uris = o.optJSONObject("image_uris")
            ?: o.optJSONArray("card_faces")?.optJSONObject(0)?.optJSONObject("image_uris")
            ?: return null
        fun str(key: String) = uris.optString(key).takeIf { it.isNotBlank() }
        val card = Card(
            name = o.optString("name"),
            artUrl = str("art_crop"),
            cardUrl = str("png") ?: str("large") ?: str("normal"),
            thumbUrl = str("normal") ?: str("small"),
        )
        return card.takeIf { it.artUrl != null || it.cardUrl != null }
    }

    private const val USER_AGENT = "ScryWall/1.0 (Android wallpaper app)"
}
