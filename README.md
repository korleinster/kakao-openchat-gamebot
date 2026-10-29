# Lineage 2M Kakao Open Chat Bot

A dedicated Android test application for Lineage 2M announcements and lightweight open-chat games.

Independently maintained by [korleinster](https://github.com/korleinster), with selected notification/reply transport portions adapted from [bssm-oss/kakao-talk-auto-bot](https://github.com/bssm-oss/kakao-talk-auto-bot). Credit for those portions belongs to the original kakao-talk-auto-bot contributors.

## Current scope

Version `0.1.0-test` processes **only the explicitly paired test conversation `봇테스트`**. Named group rooms and the owner's one-to-one open-chat test room have separate admission rules. This is an owner-device prototype, not a general KakaoTalk integration or a production release.

- Pair a room using a short-lived code and confirm it locally.
- Answer `!핑`, `!도움말`, `!오늘`, `!월보`, `!정기`, `!컬렉션`.
- Play `!주사위 [maximum]` and `!뽑기 name1 name2 ...`.
- Schedule a one-off 30-second reminder with `!알림테스트`.
- Register weekly world-boss/content schedules, five-minute reminders, and a daily briefing at 07:00 Asia/Seoul.
- Register shared collection cycles using an original calendar-month anchor and a reminder offset.
- Persist schedule and delivery state, claim work atomically, and avoid replaying dispatches with unknown outcomes.

No real game schedules or collection dates are bundled. Automatic schedule sending starts disabled. The application has no Internet permission and does not persist messages from other rooms. Android notification access is still a broad OS permission. Normally the application filters by room metadata or the paired identity before content processing. During the five-minute pairing window, a new unnamed one-to-one notification with a stable identity is checked only for the exact pairing code. The owner must confirm the candidate locally. Unmatched content and message history are not stored or processed as commands.

## Verified prototype behavior

On October 29, 2026, the owner confirmed four actual replies (`!핑`, `!주사위 100`, `!뽑기`, and `!알림테스트`) plus the scheduled 30-second notification in the one-to-one test open chat. The phone recorded five accepted requests. The display was turned off before the test; continuous secure-lock/Doze state and precise arrival latency were not measured. Fifteen unit tests and five device database tests passed. Group open chats, long idle periods, reboot recovery, and real game schedules remain pending.

## Development

Pinned stack: Temurin JDK 17.0.21+9, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile/target SDK 35, minimum SDK 26. Owner-device acceptance targets Android test device / Android 13 / One UI 5.1 / KakaoTalk 26.9.1. Other devices are not acceptance-tested.

On macOS ARM64:

```sh
python3 scripts/bootstrap-toolchain.py
scripts/build.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The bootstrap helper verifies pinned official checksums, uses the ignored workspace toolchain folder, and leaves global Java/SDK settings unchanged. It may display the Android SDK package license prompt. The generated standard debug key is for local development only.

On other hosts, configure JDK 17 and Android SDK 35 with Build Tools 34.0.0, set `local.properties`, prepare a debug keystore at `work/toolchain/debug.keystore`, and use `./gradlew`.

## Owner-device setup

```sh
scripts/adb.sh install -r app/build/outputs/apk/debug/app-debug.apk
scripts/adb.sh install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
scripts/adb.sh shell am instrument -w com.korleinster.kakaobot.test/androidx.test.runner.AndroidJUnitRunner
```

Grant notification access to the app explicitly on the phone. Create a room-pairing code in the app, send it from another account to `봇테스트`, confirm the candidate in the app, then enable command responses. Keep KakaoTalk out of the foreground so its message notifications can reach the listener. Verify every test reply in the actual chat.

The source APK is `app/build/outputs/apk/debug/app-debug.apk`. Build output, tool binaries, device screenshots, raw logs and signing keys are ignored by Git.

## Delivery and screen limitations

A successful PendingIntent invocation means the request was accepted, not that the message appeared or was read. Missing or ambiguous room identity blocks sending. Reply capabilities remain in memory; after process loss only active eligible notifications can restore them. Removed notifications do not prove an already cached capability has expired, but every attempt revalidates its bound identity and package. No lifetime guarantee is made.

Background queries and notifications do not require keeping the physical display illuminated. UI work may require the owner to unlock the device; scrcpy supports a physically dark display. Doze, close alarm spacing, permission changes, force-stop, reboot and KakaoTalk changes need separate acceptance tests.

## Documentation


`config/project.json` records planning defaults; the installed app stores its runtime configuration privately on the phone. The source repository is [korleinster/kakao-openchat-gamebot](https://github.com/korleinster/kakao-openchat-gamebot).

## License

Project material is licensed under the **MIT License**; see [LICENSE](LICENSE). Retain applicable copyright and permission notices when redistributing copies or substantial portions.

- New project code: Copyright (c) 2026 korleinster.
- Adapted portions: Copyright (c) 2026 kakao-talk-auto-bot contributors, originally MIT-licensed. The exact upstream license is preserved in [licenses/kakao-talk-auto-bot-MIT.txt](licenses/kakao-talk-auto-bot-MIT.txt).
- Original source: [bssm-oss/kakao-talk-auto-bot at a10b27b46984b8b85712bf84bf452596f607f5c7](https://github.com/bssm-oss/kakao-talk-auto-bot/tree/a10b27b46984b8b85712bf84bf452596f607f5c7). File mappings, original hashes, and modification summaries are in [upstream.lock.json](upstream.lock.json) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Runtime Kotlin/JetBrains components retain their Apache-2.0 license. Full notices and source attribution are included in the app's license screen and in `licenses/`. This independently maintained project is not an official Kakao or NCSOFT product and is not endorsed by the upstream authors.
