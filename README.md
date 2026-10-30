# Chatbot (채팅봇)

An Android app for independently configured messenger rooms, recurring reminders, and lightweight commands.

## Product

Version `0.3.0` replaces room roles with independent feature checkboxes:

- Repeating reminders: weekdays, time, and 0–1440 minutes of advance notice.
- Cycle reminders: original calendar anchor, 1–24 month interval, and advance days.
- Daily summary: room-specific time, default 07:00 Asia/Seoul, and optional empty-day notices.
- Dice and candidate draws, independently enabled.

Each room also has one of five reply styles: boyfriend, girlfriend, playful kid, elder, or a concise assistant. Previewing or checking a feature does not save it; use the room's save button. Style templates preserve names, dates, numbers, and random results.

The app has Rooms, Reminders, and Settings screens, light/dark themes, system time/date pickers, and accessible native controls. Existing room bindings and schedules are retained. Earlier combined/announcement/games roles migrate to equivalent feature selections. The previous game-specific categories remain readable while the app uses generic reminder names. Legacy commands remain compatibility aliases; current help shows `!핑`, `!도움말`, enabled reminder queries, `!주사위`, and `!뽑기`.

## Room setup and sending

The phone's KakaoTalk account must already participate in the conversation. Add a room, send its five-minute pairing code from another account, confirm the candidate locally, and enable that room's bot. Adding an app profile does not create or join a KakaoTalk room. Up to 20 profiles can be registered; large-room-count capacity has not been acceptance-tested.

Configure reminder features and definitions, then enable scheduled sending separately. Adding a feature does not silently turn on existing schedules. Incoming notifications are routed by confirmed conversation identity, independently of the settings screen's selected room. A new pairing attempt affects that profile's binding and pending deliveries; already confirmed other rooms retain their settings.

Reply capabilities stay in memory. Missing identity, conflicting bindings, unavailable reply actions, or required permissions block sending. A successful PendingIntent invocation records request acceptance; actual arrival must be checked in the conversation. Unknown dispatch outcomes are not automatically replayed. Notification structure, device idle state, force-stop, reboot, or messenger changes can require a fresh room notification.

## Privacy and permissions

The in-app disclosure and affirmative consent gate precede notification content processing and sending, including when Android access was granted in an older version. Consent can be withdrawn. The app has no Internet, contacts, location, or broad storage permission and uses no advertising or analytics SDK. It processes KakaoTalk notifications locally and sends configured replies/reminders through KakaoTalk to that conversation's participants.

Room settings and reminder definitions remain until deleted. Completed delivery records older than seven days and deduplication hashes older than one day are pruned when the app runs. Recent processing logs are capped at 100. Room deletion and full local-data deletion are available in the app. Android app-data backup is disabled. See the [privacy policy](docs/privacy-policy.md).

Exact reminders use user-granted `SCHEDULE_EXACT_ALARM`; `USE_EXACT_ALARM` is not declared. Alarm and idle constraints are shared across the app's rooms. Precise delivery is not guaranteed.

## Development

Pinned stack: JDK 17.0.21+9, Gradle 8.13, AGP 8.11.1, Kotlin 2.2.21, compile/target SDK 36, Build Tools 35.0.0, minimum SDK 26. The runtime includes AndroidX Activity 1.11.0 for lifecycle-aware back navigation and edge-to-edge handling.

On macOS ARM64:

```sh
python3 scripts/bootstrap-toolchain.py
scripts/build.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest bundleRelease lintRelease
```

The bootstrap verifies pinned official checksums and uses the ignored local toolchain directory. Android tools display any required package-license prompt. On other hosts, provide JDK 17 and Android SDK 36/Build Tools 35.0.0, configure `local.properties`, and use `./gradlew`.

The build helper keeps project caches and intermediates in a project-specific temporary directory and mirrors final outputs/reports to `app/build/`. Optional `BOT_JAVA_HOME`, `BOT_ANDROID_HOME`, `BOT_GRADLE_USER_HOME`, and `BOT_GRADLE_EXECUTABLE` support tools outside synchronized folders. Direct Gradle builds use normal locations unless configured explicitly. Local build tools, keys, device screenshots, raw evidence, and generated APK/AAB files are ignored by Git.

Outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- Release bundle: `app/build/outputs/bundle/release/app-release.aab`.

The default release bundle is unsigned. To sign for upload, configure the ignored root `signing.properties` with your protected upload key's `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Release never falls back to the standard debug key.

## Verification and release preparation

Version 0.3.0 passed 35 unit tests, 19 product checks on the Android 13 owner device, and 18 product checks on an Android 16 emulator. These include overlapping cases across environments. Fresh real-recipient messaging verification is pending. The checks cover feature combinations, tone payload preservation, scheduling boundaries, consent state, migration, deletion, and room isolation. Explicit owner-device tests use opt-in arguments and disposable profiles; normal runs do not change real bindings. Keep the device unlocked for foreground UI tests. Debug-only preparation helpers are in the separate test APK and are not exported endpoints in the main app.

A prototype's short real-device messaging result does not establish long-term Doze/reboot reliability. Current per-version evidence is maintained in [implementation status](docs/10-implementation-status.md) and [product redesign](docs/12-product-redesign.md).

Google Play preparation includes target API 36, a release bundle, privacy/consent/deletion flows, and Korean listing copy in `distribution/ko-KR/`. Actual submission still requires the owner's developer account, public support email, upload signing, Console declarations, review access material, and applicable closed testing. See the [release preparation record](docs/13-play-release.md). No store submission or approval has been performed.


## License and provenance

Independently maintained by [korleinster](https://github.com/korleinster). Selected notification/reply transport portions are adapted from [bssm-oss/kakao-talk-auto-bot](https://github.com/bssm-oss/kakao-talk-auto-bot) at commit [a10b27b46984b8b85712bf84bf452596f607f5c7](https://github.com/bssm-oss/kakao-talk-auto-bot/tree/a10b27b46984b8b85712bf84bf452596f607f5c7).

The project is MIT-licensed; retain applicable copyright and permission notices. New code: Copyright (c) 2026 korleinster. Adapted portions: Copyright (c) 2026 kakao-talk-auto-bot contributors. The exact original [MIT license](licenses/kakao-talk-auto-bot-MIT.txt), root [LICENSE](LICENSE), [source mappings](upstream.lock.json), [third-party notices](THIRD_PARTY_NOTICES.md), and in-app license text preserve attribution. Runtime libraries retain their Apache-2.0 licenses; resolved modules are recorded in [runtime-components.json](licenses/runtime-components.json).

The app is independently developed and is not an official Kakao application or an endorsed integration.
