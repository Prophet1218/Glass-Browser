package com.example.glassbrowser

import android.graphics.Bitmap
import android.net.Uri

/** Egy böngészőlap: saját WebView, cím, bélyegkép és betöltési állapot. */
class Tab(val webView: GlassWebView) {
    /** Állandó azonosító: ezen a néven mentjük a lap bélyegképét. */
    var id: Long = System.nanoTime()

    var title: String = ""
    var url: String = MainActivity.START_URL

    /** Újraindításkor visszaállított lap: csak akkor töltjük be, ha kiválasztják. */
    var pendingUrl: String? = null
    var thumbnail: Bitmap? = null
    var favicon: Bitmap? = null
    /** Igaz, amíg egy címsorból indított betöltés tart: ilyenkor nem dobjuk át más alkalmazásba. */
    var stayInBrowser: Boolean = false
    var loading: Boolean = false
    var progress: Int = 0
}

object Urls {
    fun isStart(url: String) = url.startsWith(MainActivity.START_URL)

    fun host(url: String): String = Uri.parse(url).host?.removePrefix("www.") ?: url
}
