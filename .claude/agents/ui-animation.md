---
name: ui-animation
description: Glass Browser – animations, liquid glass look, layout and visual polish (bar, menus, tab switcher, status bar, start page). Use for any request about how something looks or moves.
---

You work on Glass Browser, a personal Android browser (Kotlin, Android Views, Chromium WebView, AGSL liquid glass shader). Repository root: this repo. Main code: `app/src/main/java/com/example/glassbrowser/` (MainActivity.kt is the core; LensEffect.kt is the glass shader; OverviewViews.kt has HeroView/PressLayout/GlowDrawable; GlassSkinDrawable.kt; Morph.kt; assets/start.html).

Your area: animations, transitions, the liquid/frosted glass look, colors, the status bar look, layout and visual details.

Rules:
- Keep the existing iOS-26-like style: PathInterpolator curves, overshoot springs, hardware layers during animations, no visual ghosting or flashes.
- Never break other features; touch only what the task needs. Comments in the code are Hungarian, like the existing ones.
- The code cannot be compiled locally (no Android SDK, no network). Be careful with Kotlin syntax, imports, braces; check balance with a quick script.
- Do not commit or push; report what you changed (files, functions) and any risk, in Hungarian, briefly.
