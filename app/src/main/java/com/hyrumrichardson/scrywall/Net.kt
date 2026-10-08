package com.hyrumrichardson.scrywall

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared HTTP client. Scryfall rejects OkHttp's default User-Agent, so every request gets ours. */
object Net {
    const val USER_AGENT = "ScryWall/1.0 (Android wallpaper app)"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()
}

class ScryWallApp : Application(), ImageLoaderFactory {
    // Coil's thumbnails go through the same client so they carry our User-Agent too.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Net.http)
        .crossfade(true)
        .build()
}
