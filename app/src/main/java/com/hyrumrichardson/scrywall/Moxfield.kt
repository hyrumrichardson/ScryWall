package com.hyrumrichardson.scrywall

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

data class Deck(val name: String, val cards: List<Card>)

/** Reads public Moxfield decks. Images come from Scryfall via each card's scryfall_id. */
object Moxfield {
    private const val BASE = "https://api2.moxfield.com/v3/decks/all/"
    private val ID_IN_URL = Regex("""moxfield\.com/decks/([A-Za-z0-9_-]+)""")
    private val BARE_ID = Regex("""^[A-Za-z0-9_-]+$""")

    /** Accepts a deck link (https://moxfield.com/decks/abc123) or just its id. */
    fun deckId(input: String): String? {
        val text = input.trim()
        return ID_IN_URL.find(text)?.groupValues?.get(1) ?: text.takeIf { BARE_ID.matches(it) }
    }

    suspend fun sample(input: String, count: Int = 5): Sample {
        val deck = deck(input)
        return Sample(deck.cards.size, deck.cards.shuffled().take(count), deck.name)
    }

    /** Every distinct printing in the deck, maybeboard excluded. */
    suspend fun deck(input: String): Deck = withContext(Dispatchers.IO) {
        val id = deckId(input) ?: throw SourceException("That doesn't look like a Moxfield deck link.")
        val request = Request.Builder()
            .url(BASE + id)
            .header("Accept", "application/json")
            .build()
        val json = Net.http.newCall(request).execute().use { resp ->
            if (resp.code == 404 || resp.code == 403) {
                throw SourceException("Couldn't find that deck. Make sure it's public or unlisted.")
            }
            if (!resp.isSuccessful) throw IOException("Moxfield request failed (HTTP ${resp.code})")
            try {
                JSONObject(resp.body?.string().orEmpty())
            } catch (e: JSONException) {
                throw IOException("Unexpected response from Moxfield")
            }
        }

        val cards = linkedMapOf<String, Card>()
        val boards = json.optJSONObject("boards")
        boards?.keys()?.forEach { board ->
            if (board == "maybeboard") return@forEach
            val entries = boards.optJSONObject(board)?.optJSONObject("cards") ?: return@forEach
            entries.keys().forEach { key ->
                val card = entries.optJSONObject(key)?.optJSONObject("card") ?: return@forEach
                val sid = card.optString("scryfall_id")
                if (sid.length > 2 && sid !in cards) {
                    cards[sid] = Scryfall.cardFromId(sid, card.optString("name"))
                }
            }
        }
        if (cards.isEmpty()) throw SourceException("That deck doesn't have any cards.")
        Deck(json.optString("name", "Moxfield deck"), cards.values.toList())
    }
}
