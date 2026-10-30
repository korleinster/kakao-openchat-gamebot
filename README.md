# Kakao Open Chat Room Bot

An Android test application for room-specific announcements and lightweight open-chat games, including Lineage 2M schedules.

Independently maintained by [korleinster](https://github.com/korleinster), with selected notification/reply transport portions adapted from [bssm-oss/kakao-talk-auto-bot](https://github.com/bssm-oss/kakao-talk-auto-bot). Credit for those portions belongs to the original kakao-talk-auto-bot contributors.

## Current scope

Version `0.2.0-test` manages **multiple independently paired conversations**, with a role, schedules, delivery records, and response controls for each room. Add a room in the app, choose its role, and create a five-minute pairing code. Re-pairing resets only that room; other confirmed rooms keep their settings. One pairing operation is active at a time, with up to 20 registered profiles in this test version. This is an owner-device prototype, not a general KakaoTalk integration or a production release.

- Pair each room using a short-lived code and confirm it locally. The phone’s KakaoTalk account must already participate in the conversation; adding an app profile does not create or join a KakaoTalk room.
- Choose a room role: game announcements, games, or both. Help and allowed commands follow that role. Switching to games disables that room’s automatic schedules; switching back requires enabling them again.
- See notification permission, listener connection, pairing progress, reply readiness, and last room notification separately; receive state-change updates while the app is open.
- Answer `!핑`, `!도움말`, `!오늘`, `!월보`, `!정기`, `!컬렉션`.
- Play `!주사위 [maximum]` and `!뽑기 name1 name2 ...`.
- Schedule a one-off 30-second reminder with `!알림테스트`.
- Register weekly world-boss/content schedules, five-minute reminders, and a daily briefing at 07:00 Asia/Seoul.
- Register shared collection cycles using an original calendar-month anchor and a reminder offset.
- Persist schedule and delivery state, claim work atomically, and avoid replaying dispatches with unknown outcomes.

No real game schedules or collection dates are bundled. Automatic schedule sending starts disabled. The application has no Internet permission and does not persist messages from unpaired rooms. Android notification access is still a broad OS permission. Normally the application filters by a structured conversation title or the paired identity before content processing. KakaoTalk can omit the room name even in group notifications, and its title can contain the sender's name. During the five-minute pairing window, only the latest message of a new notification with stable identity is checked for the exact code, unless an explicit structured room title contradicts the configured room. The owner must verify where they sent the code and confirm the candidate locally. Unmatched content and older messages are not stored or processed as commands.

## Verified prototype behavior

The initial `0.1.0-test` check on October 29, 2026 confirmed four actual replies (`!핑`, `!주사위 100`, `!뽑기`, and `!알림테스트`) plus the scheduled 30-second notification in the one-to-one test open chat. The phone recorded five accepted requests. The display was turned off before the test; continuous secure-lock/Doze state and precise arrival latency were not measured. Fifteen unit tests and five device database tests passed at that stage.

Version `0.1.1-test` was also verified in a separately configured group open chat: the owner saw the pairing state update without reopening the app, then confirmed the ping reply, test acknowledgement, and scheduled notification. The phone's accepted-request count increased from five to eight. The display was turned off during this follow-up test. Twenty-four unit tests and nine device database/settings/UI/package-visibility checks passed; a separate debug-only pairing preparation helper was also exercised. Long idle periods, reboot recovery, and real game schedules remain pending.

Version `0.2.0-test` passes 29 unit tests, 11 isolated device database/settings tests, and lint. Installation preserved the existing room binding and response settings. On October 30, 2026, concurrent operation was checked in an existing group chat (combined role) and a second one-to-one test chat (games role): games help and dice replies, no reply to the games room’s schedule command, and a ping reply in the existing group. Accepted-request deltas were two and one respectively, with no pending, failed, or unknown deliveries. Room selection, role application, and preservation of the other room’s settings were checked on the phone. The display was turned off for the message test. The owner reported all results as normal, but the current run had no new scheduled-notification record, so that path is being rechecked rather than counted as verified. Long-running multiple-group acceptance, 20-room capacity, and Doze/reboot reliability remain pending.

## Development

Pinned stack: Temurin JDK 17.0.21+9, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile/target SDK 35, minimum SDK 26. Compatibility varies by Android and KakaoTalk version.

On macOS ARM64:

```sh
python3 scripts/bootstrap-toolchain.py
scripts/build.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The bootstrap helper verifies pinned official checksums, uses the ignored workspace toolchain folder, and leaves global Java/SDK settings unchanged. It may display the Android SDK package license prompt. The generated standard debug key is for local development only. `scripts/build.sh` keeps generated intermediates in a project-specific temporary directory to avoid duplicate generated files in synchronized checkouts, then mirrors final artifacts and reports to `app/build/`. Direct `./gradlew` builds retain Gradle's normal output location unless `-PbotBuildRoot` is supplied.

On other hosts, configure JDK 17 and Android SDK 35 with Build Tools 34.0.0, set `local.properties`, prepare a debug keystore at `work/toolchain/debug.keystore`, and use `./gradlew`.

## Owner-device setup

```sh
scripts/adb.sh install -r app/build/outputs/apk/debug/app-debug.apk
scripts/adb.sh install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
scripts/adb.sh shell am instrument -w com.korleinster.kakaobot.test/androidx.test.runner.AndroidJUnitRunner
```

Default device tests use disposable databases and settings files. Live tests are explicitly opted in: `pair_test_room` can create a new real binding attempt for foreground UI validation, and `prepare_pairing_room` can prepare an owner-authorized code in a debug build without accessing the screen. They reset the existing binding and pending deliveries. Neither adds an exported control endpoint to the main app.

Grant notification access to the app explicitly on the phone. Use “새 방 추가” to register another room, enter its name and role, send the generated code from another account to that conversation, confirm the candidate in the app, then enable its command responses. Use “관리할 방 선택” to change the room shown in settings. Incoming notifications are routed by their confirmed conversation identity, independently of the selected settings screen. Keep the bot app open to watch its countdown and state updates; keep KakaoTalk out of the foreground so its message notifications can reach the listener. Verify every test reply in the actual chat. Notification access being allowed does not itself mean a room has been paired, and cached reply information is not a live Kakao server connection check.

The source APK is `app/build/outputs/apk/debug/app-debug.apk`. Build output, tool binaries, device screenshots, raw logs and signing keys are ignored by Git.

## Room isolation

Each new profile uses a local UUID, separate settings, and a separate SQLite database. Existing `bot-settings` and `bot.db` remain the first profile, with the combined role as the default. Choose a role and press “선택한 역할 적용” to save it. Changing a role preserves the binding and schedule definitions, cancels that room’s pending deliveries, and recalculates eligible future work. The earliest wake-up across all rooms drives one app alarm. Android’s app-level idle restrictions still apply across rooms. A conversation already bound to another profile cannot be paired again; ambiguous identity blocks the affected profiles.

The role presets control the existing command modules. Natural-language AI personas, custom modules, per-member administrator authorization, and long-running multi-room acceptance are separate future work. See `docs/11-multi-room-roles.md` for requirements and validation status.

## Delivery and screen limitations

A successful PendingIntent invocation means the request was accepted, not that the message appeared or was read. Missing or ambiguous room identity blocks sending. Reply capabilities remain in memory; after process loss only active eligible notifications can restore them. Removed notifications do not prove an already cached capability has expired, but every attempt revalidates its bound identity and package. No lifetime guarantee is made.

On listener recovery, a pairing candidate may be recovered only from the latest message of a notification posted after the current code was created and before that code expires; older commands are not replayed. If a valid code arrives without a usable Kakao-owned text reply action, the app shows that failure instead of continuing to say it is waiting for the code. Free-form text input and KakaoTalk intent ownership remain required; file-only inputs are rejected. The manifest declares visibility for `com.kakao.talk` so Android package visibility does not hide the creator information needed for that ownership check.

Background queries and notifications do not require keeping the physical display illuminated. UI work may require the owner to unlock the device; scrcpy supports a physically dark display. Doze, close alarm spacing, permission changes, force-stop, reboot and KakaoTalk changes need separate acceptance tests.

## Documentation


`config/project.json` records planning defaults; the installed app stores its runtime configuration privately on the phone. The source repository is [korleinster/kakao-openchat-gamebot](https://github.com/korleinster/kakao-openchat-gamebot).

## License

Project material is licensed under the **MIT License**; see [LICENSE](LICENSE). Retain applicable copyright and permission notices when redistributing copies or substantial portions.

- New project code: Copyright (c) 2026 korleinster.
- Adapted portions: Copyright (c) 2026 kakao-talk-auto-bot contributors, originally MIT-licensed. The exact upstream license is preserved in [licenses/kakao-talk-auto-bot-MIT.txt](licenses/kakao-talk-auto-bot-MIT.txt).
- Original source: [bssm-oss/kakao-talk-auto-bot at a10b27b46984b8b85712bf84bf452596f607f5c7](https://github.com/bssm-oss/kakao-talk-auto-bot/tree/a10b27b46984b8b85712bf84bf452596f607f5c7). File mappings, original hashes, and modification summaries are in [upstream.lock.json](upstream.lock.json) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Runtime Kotlin/JetBrains components retain their Apache-2.0 license. Full notices and source attribution are included in the app's license screen and in `licenses/`. This independently maintained project is not an official Kakao or NCSOFT product and is not endorsed by the upstream authors.
