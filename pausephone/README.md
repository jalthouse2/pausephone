# [pause]phone

*take a pause. be present.*

An Android app for using your phone on purpose. Before an app or website you've chosen opens, a cloud of tiny dots breathes out and back in for a few seconds, with a quote underneath. Then you decide: go in, or not now.

## What it does

- **Pause before opening.** 0–10 seconds of breathing before a chosen app or website opens.
- **Breath length.** 3–8 seconds each way.
- **Opens per day.** 1–20, or no limit. When they're used up, the app rests until tomorrow.
- **Session length.** 5 minutes to 4 hours, or no limit. When the time is up, the breathing screen comes back.
- **A breather along the way.** One breath with the dot cloud after 5–60 minutes in an app.
- **Blocking.** Block an app or site completely, or only at night (22:00 to 07:00).
- **Unlock pause.** A few seconds of breathing each time you unlock the phone.
- **Today.** How many times you paused, went in, let it go, and took a breather.

Everything stays on the phone. There is no account, no network access and no analytics.

## Build and run

1. Install [Android Studio](https://developer.android.com/studio) (Koala or newer).
2. **File › Open** and choose this folder. Let Gradle sync; it downloads Gradle 8.7 and the Android libraries the first time.
3. Plug in an Android phone (Android 8.0 or newer) with USB debugging on, or start an emulator, and press **Run**.
4. In the app, tap **Begin**, then **Open settings** and turn on **[pause]phone** under Accessibility (on some phones it's under *Downloaded apps* or *Installed services*).
5. Tap the settings icon, then **Add an app** or **Add a website**, and set its sliders.

From the command line: `./gradlew assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.

## How it works

| Part | File |
| --- | --- |
| Rules, settings and daily counts (SharedPreferences) | `data/Store.kt` |
| Open sessions and the 2-minute grace period | `data/Sessions.kt` |
| Quotes under the breathing | `data/Quotes.kt` |
| Watches the foreground app and browser address bar | `service/PauseService.kt` |
| The breathing screen (all modes) | `PauseActivity.kt` |
| The dot cloud animation | `ui/DotCloud.kt` |
| Today, Apps and sites, rule sliders, unlock pause | `MainActivity.kt` |
| Colours, Manrope type, ensō logo, shared controls | `ui/Theme.kt`, `ui/Components.kt` |

Android has no public way to intercept an app launch, so [pause]phone uses an **accessibility service**. When the app in front changes to one with a rule, the service opens the breathing screen on top of it. "Open" closes the breathing screen and reveals the app underneath; "Not now" goes to the home screen. For websites, the service reads the address bar of supported browsers: Chrome, Firefox, Samsung Internet, Edge, Brave, Opera and DuckDuckGo.

If you leave an app and come back within 2 minutes, it's the same session and doesn't count as a new open.

## Known limits

- **Google Play.** Play restricts accessibility services to apps whose main purpose is accessibility, so publishing there would need a review argument or a different approach. Installing it yourself, or through F-Droid, is fine.
- **Browser updates** sometimes rename the address bar's view id. If a browser stops being recognised, update its entry in `PauseService.BROWSERS`.
- **Night hours** are fixed at 22:00 to 07:00 for now (`Store.nightStart` / `nightEnd`).
- **Battery savers** on some phones (Xiaomi, Huawei, OnePlus) stop accessibility services. If pauses stop appearing, exempt [pause]phone from battery optimisation.
- Sessions live in memory, so if Android kills the app, the next open starts a fresh pause.

## Credits

Type: [Manrope](https://github.com/googlefonts/manrope), SIL Open Font License (see `FONT_LICENSE_Manrope.txt`).
Quotes: Thich Nhat Hanh, Marcus Aurelius, Seneca and Simone Weil, plus a few lines written for the app.
