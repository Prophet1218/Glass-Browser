package com.example.glassbrowser

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager

/**
 * A felület nyelve (magyar / angol). A szövegek a kódban magyarul vannak; angol nyelvnél ez a
 * szótár fordítja le őket. A [tree] és a [prefs] egy már megjelenített felület összes feliratát
 * átírja (oda-vissza is), így a nyelv váltásához nem kell újraindítani a böngészőt.
 */
object L {
    var en = false
        private set

    /** A beállított nyelv betöltése ("app_language": "hu" vagy "en"). */
    fun init(ctx: Context) {
        en = code(ctx) == "en"
    }

    /**
     * The chosen language; until the user picks one it follows the phone's language: Hungarian
     * phones get Hungarian, every other (unsupported) language falls back to English.
     */
    fun code(ctx: Context): String {
        val p = PreferenceManager.getDefaultSharedPreferences(ctx)
        p.getString("app_language", null)?.let { if (it == "hu" || it == "en") return it }
        val sys = android.content.res.Resources.getSystem().configuration.locales[0]?.language.orEmpty()
        return if (sys == "hu") "hu" else "en"
    }

    /** A szöveg az aktuális nyelven (a forrás mindig a magyar szöveg). */
    fun t(s: String): String = if (en) EN[s] ?: s else s

    /** Bármelyik nyelvű felirat átírása az aktuális nyelvre (ismeretlen szöveg változatlan marad). */
    fun any(s: CharSequence?): CharSequence? {
        if (s == null) return null
        val k = s.toString()
        return if (en) EN[k] ?: s else HU[k] ?: s
    }

    /** Egy nézetfa összes feliratának (szöveg, súgószöveg, leírás) átírása. */
    fun tree(v: View) {
        if (v is TextView) {
            any(v.text)?.let { if (it !== v.text) v.text = it }
            any(v.hint)?.let { if (it !== v.hint) v.hint = it }
        }
        v.contentDescription?.let { d -> any(d)?.let { if (it !== d) v.contentDescription = it } }
        if (v is ViewGroup) for (i in 0 until v.childCount) tree(v.getChildAt(i))
    }

    /** A beállítások képernyő összes címe, leírása és listaeleme. */
    fun prefs(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val p: Preference = group.getPreference(i)
            p.title = any(p.title)
            if (p.summaryProvider == null) p.summary = any(p.summary)
            if (p is ListPreference) {
                p.entries = p.entries?.map { any(it) ?: it }?.toTypedArray()
                p.dialogTitle = any(p.dialogTitle)
            }
            if (p is PreferenceGroup) prefs(p)
        }
    }

    private val EN: Map<String, String> = mapOf(
        // keresősáv, lapváltó, menü
        "Keresés vagy webcím" to "Search or type URL",
        "Vissza" to "Back",
        "Frissítés" to "Refresh",
        "Újratöltés" to "Reload",
        "Lapok" to "Tabs",
        "Menü" to "Menu",
        "Lap bezárása" to "Close tab",
        "Beállítások" to "Settings",
        "Új lap" to "New tab",
        "Előre" to "Forward",
        "Megosztás" to "Share",
        "Könyvjelző hozzáadása" to "Add bookmark",
        "Könyvjelző eltávolítása" to "Remove bookmark",
        "Könyvjelző eltávolítva" to "Bookmark removed",
        "Könyvjelző mentve" to "Bookmark saved",
        "Könyvjelző törölve" to "Bookmark deleted",
        "Könyvjelzők" to "Bookmarks",
        "Előzmények" to "History",
        "Találatok" to "Results",
        "Asztali oldal" to "Desktop site",
        "Oldal fordítása" to "Translate page",
        "Eredeti oldal" to "Show original",
        "Kezdőlap" to "Home",
        "Nincs találat az előzményekben." to "No matches in history.",
        "Még nincs könyvjelző. Egy oldalt a ⋯ menü „Könyvjelző hozzáadása” pontjával menthetsz el." to
            "No bookmarks yet. Save a page with “Add bookmark” in the ⋯ menu.",
        "Írj be egy webcímet vagy keresőszót." to "Type a web address or search term.",
        "Letöltés…" to "Downloading…",
        "A letöltés nem sikerült" to "Download failed",
        // csempék
        "Csempe szerkesztése" to "Edit shortcut",
        "Új csempe" to "New shortcut",
        "Csempék visszaállítása" to "Reset shortcuts",
        "A kezdőlapon újra a leggyakrabban látogatott oldalaid jelennek meg." to
            "The start page will show your most visited sites again.",
        "Név" to "Name",
        "Webcím (pl. telex.hu)" to "Web address (e.g. example.com)",
        "Mentés" to "Save",
        "Mégse" to "Cancel",
        "Törlés" to "Delete",
        "Másolás" to "Copy",
        "A böngészőből letöltött fájlok itt jelennek meg." to "Files you download in the browser show up here.",
        "Visszaállítás" to "Reset",
        "Igen" to "Yes",
        // beállítások: kategóriák és elemek
        "Megjelenés" to "Appearance",
        "Általános" to "General",
        "Reklámszűrés és kiegészítők" to "Ad blocking and add-ons",
        "Keresősáv" to "Search bar",
        "Állapotsor" to "Status bar",
        "Üveghatás" to "Glass effect",
        "Animációk és érintés" to "Animations and touch",
        "Nyelv" to "Language",
        "A felület nyelve" to "App language",
        "Magyar" to "Hungarian",
        "Fordítás célnyelve" to "Translate pages into",
        "Német" to "German",
        "Francia" to "French",
        "Spanyol" to "Spanish",
        "Olasz" to "Italian",
        "Román" to "Romanian",
        "Szlovák" to "Slovak",
        "Lengyel" to "Polish",
        "Ukrán" to "Ukrainian",
        "Orosz" to "Russian",
        "Téma (világos / sötét)" to "Theme (light / dark)",
        "A telefon beállítását követi" to "Follow system",
        "Világos" to "Light",
        "Sötét" to "Dark",
        "Weboldalak sötétítése" to "Darken websites",
        "Sötét módban a világos weboldalakat is sötétre színezi (Android 13+)." to
            "In dark mode, light websites are darkened too (Android 13+).",
        "Alkalmazásikon" to "App icon",
        "Ikon átállítva. A kezdőképernyőn néhány másodperc múlva frissül." to
            "Icon changed. The home screen updates in a few seconds.",
        "Az ikont nem sikerült átállítani" to "Couldn't change the icon",
        "Sima" to "Flat",
        "Folyékony üveg" to "Liquid glass",
        "Matt üveg" to "Frosted glass",
        "kék" to "blue",
        "zöld" to "green",
        "rózsaszín" to "pink",
        "lila" to "purple",
        "narancs" to "orange",
        "grafit" to "graphite",
        "Üveg megjelenés" to "Glass appearance",
        "Keresősáv stílusa" to "Search bar style",
        "Lebegő (liquid glass kapszulák)" to "Floating (liquid glass capsules)",
        "Rögzített, alul (Chrome-szerű)" to "Docked, bottom (Chrome-like)",
        "Rögzített, felül (Chrome-szerű)" to "Docked, top (Chrome-like)",
        "Üveg típusa" to "Glass type",
        "Folyékony üveg (fénytörés)" to "Liquid glass (refraction)",
        "Matt üveg (erős elmosás, fénytörés nélkül)" to "Frosted glass (strong blur, no refraction)",
        "Kikapcsolva (tömör felületek, üveg nélkül)" to "Off (solid surfaces, no glass)",
        "Rögzített keresősávnál az üveg ki van kapcsolva." to "Glass is off with a docked search bar.",
        "Keresősáv színe" to "Search bar color",
        "Keresősáv színének erőssége" to "Search bar color strength",
        "0 = színtelen üveg" to "0 = colorless glass",
        "Üveg sötétítése" to "Glass darkening",
        "A keresősáv, a menü és a javaslatok üvegének sötétítése." to
            "Darkens the glass of the search bar, the menu and the suggestions.",
        "Elmosás" to "Blur",
        "0 = nincs elmosás" to "0 = no blur",
        "Fénytörés magassága" to "Refraction height",
        "Fénytörés eltolása" to "Refraction offset",
        "Színszóródás" to "Dispersion",
        "Körív az üvegen" to "Glass rim",
        "Vékony fényív az üvegfelületek felső és alsó szélén." to
            "A thin highlight on the top and bottom edges of glass surfaces.",
        "Domború (3D) üveg" to "Convex (3D) glass",
        "Az üvegfelületek domborúnak, vastagnak látszanak: fénycsillanás a peremen, erősebb fénytörés a széleken." to
            "Glass surfaces look convex and thick: a glint along the edge, stronger refraction near the edges.",
        "Felső (állapotsor) hatás" to "Top (status bar) effect",
        "Kikapcsolva az állapotsor mögött egyszínű háttér van, a telefon világos vagy sötét módja szerint." to
            "When off, the status bar has a plain background matching light or dark mode.",
        "Állapotsor stílusa" to "Status bar style",
        "Színsáv (az oldal nem mozdul)" to "Color band (page stays put)",
        "Átúszó (elmosva csúszik az állapotsor mögé)" to "Flow (page slides blurred under the status bar)",
        "Felső elmosás erőssége" to "Top blur strength",
        "Alapértelmezett üvegbeállítások visszaállítása" to "Reset glass settings",
        "Az üveg típusa, színe, az elmosás, a fénytörés és a színszóródás visszaáll az eredeti értékre." to
            "Glass type, color, blur, refraction and dispersion return to their defaults.",
        "Üvegbeállítások visszaállítva" to "Glass settings reset",
        "Érintés" to "Touch",
        "Rugózó érintés" to "Bouncy touch",
        "A keresősáv és a gombok érintésre összemennek, majd visszapattannak." to
            "The search bar and buttons shrink when touched, then bounce back.",
        "Rugózás mértéke" to "Bounce amount",
        "Rezgő visszajelzés" to "Haptic feedback",
        "Rövid rezgés a keresősáv és a gombok érintésekor." to
            "A short vibration when touching the search bar and buttons.",
        "Rezgés erőssége" to "Vibration strength",
        "Keresés és kezdőlap" to "Search and start page",
        "Keresőmotor" to "Search engine",
        "Keresési javaslatok" to "Search suggestions",
        "Gépelés közben a keresőmotor javaslatai is megjelennek az előzmények mellett. Amit beírsz, az ehhez elmegy a keresőmotornak." to
            "While typing, the search engine's suggestions appear next to your history. What you type is sent to the search engine for this.",
        "Kezdőoldal" to "Start page",
        "Kezdőoldal webcíme" to "Start page address",
        "Üresen hagyva a beépített kezdőlap nyílik meg. Új lapnál is ez töltődik be." to
            "Leave empty for the built-in start page. New tabs open this too.",
        "Weboldalak" to "Websites",
        "Linkek megnyitása alkalmazásban" to "Open links in apps",
        "Ha egy linkhez van telepített alkalmazás (YouTube, Reddit, X, Facebook…), a link abban nyílik meg. A címsorba beírt címek mindig a böngészőben maradnak." to
            "If an app is installed for a link (YouTube, Reddit, X, Facebook…), the link opens there. Addresses typed into the address bar always stay in the browser.",
        "Számítógépes nézetet kér az oldalaktól." to "Requests the desktop version of websites.",
        "Adatvédelem" to "Privacy",
        "Harmadik féltől származó sütik" to "Third-party cookies",
        "Szükséges a Google-bejelentkezéshez és sok beágyazott tartalomhoz." to
            "Needed for Google sign-in and lots of embedded content.",
        "Böngészési adatok törlése" to "Clear browsing data",
        "Sütik, gyorsítótár, helyi tárolók és előzmények." to "Cookies, cache, local storage and history.",
        "A sütik, a gyorsítótár, a helyi tárolók és az előzmények törlődnek." to
            "Cookies, cache, local storage and history will be deleted.",
        "Adatok törölve" to "Data cleared",
        "Kiegészítők" to "Add-ons",
        "Reklámblokkoló" to "Ad blocker",
        "A szűrőlistán szereplő hirdetés- és követőszerverek kéréseit letiltja." to
            "Blocks requests to ad and tracker servers on the filter list.",
        "Szűrőlista forrása" to "Filter list source",
        "Szűrőlista webcíme (hosts vagy domainlista)" to "Filter list address (hosts or domain list)",
        "Szűrőlista frissítése" to "Update filter list",
        "Nincs megadva szűrőlista-cím" to "No filter list address set",
        "Felhasználói szkriptek" to "User scripts",
        "Saját JavaScript-kiegészítők, amelyek a megadott oldalakon lefutnak (pl. elemek elrejtése, sötétítés, automatizálás)." to
            "Your own JavaScript add-ons that run on chosen sites (e.g. hiding elements, darkening, automation).",
        "A szkriptek az oldal betöltése után futnak le. Koppints egy szkriptre a szerkesztéshez." to
            "Scripts run after the page loads. Tap a script to edit it.",
        "Új szkript" to "New script",
        "＋  Új szkript" to "＋  New script",
        "Szkript szerkesztése" to "Edit script",
        "Hol fusson? (pl. youtube.com – üresen mindenhol)" to "Where should it run? (e.g. youtube.com – empty for everywhere)",
        "JavaScript kód" to "JavaScript code",
        "Bekapcsolva" to "Enabled",
        "(névtelen)" to "(unnamed)",
        "Google-fiók" to "Google account",
        "Bejelentkezés Google-fiókkal" to "Sign in with Google",
        "Megnyitja a Google bejelentkezési oldalát egy új lapon. A bejelentkezés megmarad." to
            "Opens the Google sign-in page in a new tab. You stay signed in.",
        "Megnyitja a Google bejelentkezési oldalát itt, a beállításokban. A bejelentkezés megmarad." to
            "Opens the Google sign-in page right here in the settings. You stay signed in.",
        "Animációk sebessége" to "Animation speed",
        "100 = normál, kisebb = lassabb, nagyobb = gyorsabb" to "100 = normal, lower = slower, higher = faster",
        "Be vagy jelentkezve a Google-fiókodba." to "You're signed in to your Google account.",
        "Nem vagy bejelentkezve." to "You're not signed in.",
        "Kijelentkezés" to "Sign out",
        "Minden süti törlődik, így minden oldalról kijelentkeztet." to
            "All cookies are deleted, so this signs you out of every site.",
        "Ez az összes weboldalról kijelentkeztet, mert minden süti törlődik. Folytatod?" to
            "This signs you out of every website because all cookies are deleted. Continue?",
        "Kijelentkeztél" to "Signed out",
        "Mentett jelszavak" to "Saved passwords",
        "A Google-fiókodba mentett jelszavak megnyitása (passwords.google.com)." to
            "Open the passwords saved in your Google account (passwords.google.com).",
        "Google-előzmények" to "Google history",
        "A Google-fiókodban tárolt keresési és böngészési előzmények (myactivity.google.com)." to
            "Search and browsing history stored in your Google account (myactivity.google.com).",
        "Jelszavak automatikus kitöltése" to "Password autofill",
        "Ha a telefonon a Google az automatikus kitöltő, a mentett jelszavaidat a bejelentkezési oldalakon felajánlja. Itt választhatod ki." to
            "If Google is your phone's autofill service, it offers your saved passwords on sign-in pages. Choose it here.",
        "Keresd a telefon beállításaiban: Jelszavak és automatikus kitöltés" to
            "Look in the phone settings: Passwords & autofill",
        "Mit lehet szinkronizálni?" to "What can be synced?",
        "A Chrome szinkronizálását (könyvjelzők, megnyitott lapok, Chrome-előzmények) a Google csak a saját böngészőjének engedi, más böngésző nem tud rácsatlakozni. A fenti pontokkal a jelszavaid és a Google-előzményeid így is elérhetők; az itt megnyitott oldalak előzményei csak ezen a telefonon tárolódnak." to
            "Google only allows Chrome sync (bookmarks, open tabs, Chrome history) in its own browser; other browsers can't connect to it. With the items above your passwords and Google history are still available; the history of pages opened here is stored only on this phone.",
        "Állapot" to "Status",
        "Térkép" to "Maps",
        "Legutóbb megnyitott" to "Recently opened",
        "Javaslatok" to "Suggestions",
        "Váltás erre a lapra" to "Switch to tab",
        "Webcím kimásolva" to "Address copied",
        "Vágólapra másolva" to "Copied to clipboard",
        "Letöltések" to "Downloads",
        "Mappa" to "Folder",
        "Letöltés elindult" to "Download started",
        "Ez a letöltés nem támogatott" to "This download isn't supported",
        "Letöltések mappa megnyitása" to "Open Downloads folder",
        "Még nincs letöltés." to "No downloads yet.",
        "Szüneteltetve" to "Paused",
        "Nincs alkalmazás a fájl megnyitásához" to "No app can open this file",
        "Átlátszó (a szöveg az oldalhoz igazodik)" to "Clear (text adapts to the page)",
        "Privát böngészés" to "Private browsing",
        "Üveg árnyalata" to "Glass tone",
        "Rendszer szerint (világos / sötét)" to "Follow system (light / dark)",
        "Nem sikerült lefordítani" to "Couldn't translate",
        "Újra" to "Retry",
        "Eredeti" to "Original",
        "Oldal lefordítva" to "Page translated",
        "Fordítás…" to "Translating…",
        "Lefordítod az oldalt?" to "Translate page?",
        "Visszavonás" to "Undo",
        "Fordítás" to "Translate",
        "Fordítás felajánlása" to "Offer to translate",
        "Idegen nyelvű oldalon felajánlja a fordítást, mint a Chrome." to "Offers to translate pages in other languages, like Chrome.",
        "Lapváltó elrendezése" to "Tab switcher layout",
        "Rács (2 oszlop)" to "Grid (2 columns)",
        "Halom (egymásra rakott lapok)" to "Stack (overlapping tabs)",
        "Lap bezárási animációja" to "Tab close animation",
        "Kirepülés elfordulva" to "Fly out with a twist",
        "Összezsugorodás" to "Shrink",
        "Elhalványulás elmosódva" to "Blur and fade",
        "Elhalványulás oldalra" to "Fade to the side",
        "Kilökés felfelé" to "Toss up",
        "Összecsukódás" to "Fold away"
    )

    private val HU: Map<String, String> = EN.entries.associate { (k, v) -> v to k }
}
