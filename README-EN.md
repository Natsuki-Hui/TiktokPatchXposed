# TiktokPatchXposed

[Русский](README.md) | **English**

An LSPosed module for TikTok. It removes ads, unlocks features TikTok only gives to some users, and adds a few useful extras. All settings are inside TikTok itself.

Current version: 4.0.2. Tested on TikTok 47.1.4 from Google Play, Android 16.

## Features

**Feed**
- removes ads: in the feed, on the startup splash, inside videos and in other video lists
- hides LIVE streams and the LIVE button in the top-left corner
- hides videos marked "People you may know"
- shows the seek bar on every video, short ones included

**Comments & profile**
- comment reposts
- the new profile layout with banners
- hidden TikTok features: long-press speed-up, comments to Favorites, audio and photo comments, background playback and more

**Downloads & links**
- downloads without a watermark
- download any video, even if the author turned downloads off
- screenshots and screen recording work everywhere
- clean links: a copied short link like `vt.tiktok.com/…` (it tells TikTok who shared the video) is replaced with the plain video link a second later, and tracking parameters (`_r`, `_t`, `u_code`, `share_link_id` and so on) are removed from long links

**Region**
- SIM country spoofing with around 190 countries. Germany is the default; you can pick another country or turn it off. Recommendations stay in the language you use TikTok in; turn off "Recommendations in my language" to get videos picked for the selected country instead

**Invisible mode** (off by default)
- hide story views: authors won't see you among their viewers
- hide profile visits: turn on TikTok's profile view history to see your visitors without showing up in other people's lists
- hide video views from people you follow: with view history on, they won't see which of their videos you watched
- this is a test feature: TikTok's view reports are confirmed to be cancelled, but how it looks to other people hasn't been confirmed yet

**Streaks**
- auto streaks, off by default, see below

Comment reposts, the new profile and the hidden features are server-side TikTok experiments that the module switches on locally. These and the region take effect after TikTok restarts; the settings screen shows a restart button when needed.

## Where to find the settings

TikTok → Profile → ☰ → Settings and privacy → **TiktokPatchXposed** (the very first row).

The screen looks like TikTok's own settings and follows TikTok's theme (Display → light/dark) and app language. Translations: English, Russian, Ukrainian, German, Spanish, Portuguese, French, Italian, Polish, Turkish and Indonesian. Belarusian and Kazakh use Russian; any other language falls back to English.

The module has no app of its own. The settings button in LSPosed opens the same screen inside TikTok.

## Installation

1. Install the APK and enable the module for TikTok in LSPosed.
2. Force-stop TikTok and open it again.
3. Open TikTok's settings → TiktokPatchXposed.

Updating: install over the old version, settings are kept. Only when coming from 3.x, turn auto streaks on again.

## Compatibility

Older versions had to be adjusted to new class names after every TikTok update. Now, on the first launch of a new TikTok build, the module finds the code it needs by itself (by strings and signatures), which takes a fraction of a second, and remembers the result. New TikTok versions should therefore keep working without a module update. This can't be guaranteed: if something can't be found, that feature is simply skipped.

Both `com.zhiliaoapp.musically` and `com.ss.android.ugc.trill` are supported (the latter hasn't been tested on a device).

The module uses LSPosed's modern API (libxposed 101–102), so newer LSPosed builds no longer mark it as "legacy". Older LSPosed 1.9.x is still supported: the APK keeps the old loading path for it.

## Auto streaks

Turned on manually. The module only renews existing streaks in one-to-one chats, and only when a streak is about to expire. It uses TikTok's `spark_v1` interaction, not a text message. It works while TikTok is open: no alarms and no background services. Every send is recorded first, so the same streak isn't renewed twice in a day. The module pauses a few seconds between chats, as if you were going through them by hand. If TikTok doesn't accept a streak, you'll get a notification so you can check that chat yourself.

## Building

Requires Java 17 and Android SDK 36.

```shell
./gradlew testDebugUnitTest assembleRelease
```

## Disclaimer

Modifying the client and automating actions can lead to account restrictions. Use at your own risk.
