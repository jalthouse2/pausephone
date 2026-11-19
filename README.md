# [pause]

*take a pause. be present.*

An Android app for using your phone on purpose. Before an app or website you've chosen opens, a cloud of tiny dots breathes in and out for a few seconds, with a quote underneath. Then you decide: go in, or never mind.

## What it does

- **Pause before opening.** 0–10 seconds of breathing before a chosen app or website opens.
- **Breath length.** 3–8 seconds each way.
- **Opens per day.** 1–20, or no limit. When they're used up, the app rests until tomorrow.
- **Session length.** 5 minutes to 4 hours, or no limit. When the time is up, the breathing screen comes back.
- **A breather along the way.** One breath with the dot cloud after 5–60 minutes in an app.
- **Blocking.** Block an app or site completely, or at set times of day that you choose.
- **Unlock pause.** A few seconds of breathing each time you unlock the phone.
- **Going in to…** At the pause you can tap a word for what you're going in for. Today shows what you went in for. You can edit the words.
- **Instead.** After "Never mind", [pause] suggests something else to do from a list you write.
- **Breath you can feel and hear.** A soft tap as each breath begins, and an optional bell as the dots settle into the ensō.
- **Today.** How many times you paused, went in, let it go, and took a breather, plus a few calm sentences about your week. No streaks and no scores.
- **Black version.** Settings › Appearance, and a light or dark breathing screen.
- **Tips.** [Buy me a coffee](https://www.buymeacoffee.com/hX1y1MzNAx) if it helps you.

Everything stays on the phone. There is no account, no network access and no analytics.

## Install on your phone

1. On your Android phone, open **https://github.com/jalthouse2/pausephone/releases/latest/download/pausephone.apk**.
2. Open the downloaded file. If Android asks, allow your browser to install apps.
3. Open **[pause]**, tap **Begin**, then **Open settings** and turn on **[pause]** under Accessibility (on some phones it's under *Downloaded apps* or *Installed services*).
4. Tap the settings icon, then **Add an app** or **Add a website**, and set its sliders.

Every push to `main` rebuilds the app on GitHub (see the Actions tab) and replaces the file at that link. To update, download and install it again; your settings stay.

Building it yourself: open the folder in Android Studio and press Run, or `./gradlew assembleDebug`.

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

Android has no public way to intercept an app launch, so [pause] uses an **accessibility service**. When the app in front changes to one with a rule, the service opens the breathing screen on top of it. "Open" closes the breathing screen and reveals the app underneath; "Never mind" goes to the home screen. For websites, the service reads the address bar of supported browsers: Chrome, Firefox, Samsung Internet, Edge, Brave, Opera and DuckDuckGo.

If you leave an app and come back within 2 minutes, it's the same session and doesn't count as a new open.

## Known limits

- **Google Play.** Play restricts accessibility services to apps whose main purpose is accessibility, so publishing there would need a review argument or a different approach. Installing it yourself, or through F-Droid, is fine.
- **Browser updates** sometimes rename the address bar's view id. If a browser stops being recognised, update its entry in `PauseService.BROWSERS`.
- **Battery savers** on some phones (Xiaomi, Huawei, OnePlus) stop accessibility services. If pauses stop appearing, exempt [pause] from battery optimisation.
- Sessions live in memory, so if Android kills the app, the next open starts a fresh pause.

## Credits

Type: [Manrope](https://github.com/googlefonts/manrope), SIL Open Font License (see `FONT_LICENSE_Manrope.txt`).
Quotes: Thich Nhat Hanh, Marcus Aurelius, Seneca and Simone Weil, plus a few lines written for the app.
