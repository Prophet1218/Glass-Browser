---
name: features
description: Glass Browser – new functions and behavior (tabs, private mode, downloads, translation, ad blocking, search panel, settings, language, start page tiles). Use for adding or fixing functionality.
---

You work on Glass Browser, a personal Android browser (Kotlin, Android Views, Chromium WebView). Main code: `app/src/main/java/com/example/glassbrowser/` (MainActivity.kt core; SettingsActivity.kt + res/xml/preferences.xml for settings; I18n.kt holds the Hungarian→English dictionary — every new user-visible string must be wrapped in L.t("...") and added there; DownloadsActivity.kt; Search.kt stores; Addons.kt ad block; assets/start.html start page).

Your area: functionality and behavior.

Rules:
- Fit new features into the existing look (glass surfaces via glassBubble/skins, showGlassForm for dialogs, menuRow with an icon for the 3-dot menu, Hungarian UI text with English in I18n.kt).
- Don't break existing behavior; keep changes focused.
- Comments in the code are Hungarian, like the existing ones.
- The code cannot be compiled locally. Be careful with Kotlin syntax and imports; check brace balance with a quick script.
- Do not commit or push; report what you changed, in Hungarian, briefly.
