# Chatbot (채팅봇)

An independently maintained Android notification bot with per-room features, five reply styles, recurring reminders, and optional bidirectional reminder synchronization.

## Features

Each room independently enables repeating reminders, calendar-cycle reminders, a daily summary, dice, and candidate draws. Choose a boyfriend, girlfriend, playful kid, elder, or concise assistant reply style, preview it, then save. Existing room bindings remain isolated.

Repeating reminders support weekdays, time, and advance minutes. Cycle reminders preserve the original calendar anchor across months. Daily summaries default to 07:00 Asia/Seoul. Scheduled sending is a separate local switch; synchronization never turns it on.

## Optional web synchronization

The app ships with **no configured server, room code, account, or credential**. Without setup it works locally. To connect, enter the HTTPS address of a compatible server, its room code and password, and affirm the data-sharing explanation.

Save reminder changes from either the app or the website. The app sends pending changes, checks for remote changes on resume or **Sync now**, and requests background checks approximately every 15 minutes. Android idle/network restrictions can delay background work. This is eventual synchronization, not guaranteed instant delivery.

Offline app edits stay in a durable queue. Concurrent changes preserve the local list and show a conflict; explicitly choose the app or server list. Lost responses reuse a mutation ID to avoid duplicate writes. Disconnecting retains the current local list.

Only reminder definitions, summary settings, versions and acknowledgement metadata are exchanged. Chat contents, conversation identities, reply capabilities, reply styles, bot-enablement switches, and message-delivery records remain local. See the [protocol](docs/sync-protocol.md) and [privacy policy](docs/privacy-policy.md). Website-specific adapters, private operations records, device backups, and connection settings are excluded from this repository.

## Room setup

Your messenger account must already participate in the conversation. Add a room, send its five-minute pairing code from another account, confirm locally, and enable the room's bot. Profiles do not create or join messenger conversations. Missing or ambiguous reply capabilities block sending; after restart a fresh room notification can be required. A successful send request does not prove recipient delivery.

## Build

Pinned stack: JDK 17, Gradle 8.13, AGP 8.11.1, Kotlin 2.2.21, compile/target SDK 36, Build Tools 35.0.0, minimum SDK 26.

```sh
python3 scripts/bootstrap-toolchain.py
scripts/build.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest bundleRelease lintRelease
```

Generated artifacts and protected local toolchain/signing files are ignored. Release defaults to an unsigned AAB; configure an ignored `signing.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword` to use your upload key. Release never falls back to a debug signing key. The helper uses temporary intermediates and mirrors final artifacts to `app/build/`.

Use `-PbotQa=true` for a separate debug test package that does not replace an installed bot. See [release preparation](docs/release-preparation.md). No Play submission or approval is implied by a successful build.

## Support

If this project is useful, [sponsor korleinster on GitHub](https://github.com/sponsors/korleinster). You can also [report an issue](https://github.com/korleinster/kakao-openchat-gamebot/issues); never include connection passwords, tokens, private conversations, or device backups.

## License and provenance

MIT licensed. Copyright (c) 2026 korleinster. Selected notification/reply transport portions are adapted from [bssm-oss/kakao-talk-auto-bot](https://github.com/bssm-oss/kakao-talk-auto-bot) at commit [a10b27b46984b8b85712bf84bf452596f607f5c7](https://github.com/bssm-oss/kakao-talk-auto-bot/tree/a10b27b46984b8b85712bf84bf452596f607f5c7), Copyright (c) 2026 kakao-talk-auto-bot contributors.

Retain the [original MIT notice](licenses/kakao-talk-auto-bot-MIT.txt), [LICENSE](LICENSE), [source mappings](upstream.lock.json), and [third-party notices](THIRD_PARTY_NOTICES.md). Runtime libraries retain their Apache-2.0 notices; resolved components are recorded in [runtime-components.json](licenses/runtime-components.json). This independently developed app is not an official or endorsed messenger integration.
