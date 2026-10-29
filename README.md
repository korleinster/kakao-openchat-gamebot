# Lineage 2M Kakao Open Chat Bot

Independent Android project preparation for scheduled Lineage 2M announcements and lightweight chat games.

## Status

Design draft v0.1. This workspace contains requirements, architecture, delivery and recovery rules, license provenance, proposed data contracts, and a validation plan. It does not contain a runnable Android application yet.

No upstream executable source has been copied. No APK has been built or installed. No KakaoTalk message has been sent. The GitHub remote repository has not been created.

## Confirmed requirements

- Send world-boss and recurring-content reminders five minutes before each event.
- Send a combined daily briefing at 07:00 Asia/Seoul.
- Track several shared timed-collection renewal cycles; real collection data will be supplied later.
- Support lightweight games, starting with a proposed raffle and dice feature.
- Preserve the phone's current USB tethering and existing network configuration.

Automatic sending is disabled in the preparation configuration. No real event schedules or chat-room identifiers are included.

## Documents


Local reference copies:

1. [Product scope](docs/01-product-plan.md)
2. [Announcement rules](docs/02-alert-rules.md)
3. [Transport and recovery](docs/03-transport-recovery.md)
4. [Architecture and data](docs/04-architecture-data.md)
5. [Mini-games](docs/05-minigames.md)
6. [License and provenance](docs/06-license-provenance.md)
7. [Development and validation](docs/07-development-validation.md)
8. [Decisions and open items](docs/08-decisions-status.md)
9. [Android remote setup](docs/09-android-remote-setup.md)

Local documents preserve Notion date/mention markup where applicable. Notion edits and implementation decisions should be reflected in these copies together.

## Development direction

Selectively adapt the MIT-licensed notification and reply transport from `bssm-oss/kakao-talk-auto-bot`, pinned in `upstream.lock.json`. Build schedule calculation, durable delivery coordination, room verification, recovery, and game rules as a dedicated Kotlin application. Local AI models are not part of the proposed first release.

The planned package boundaries are `domain`, `data`, `transport`, `scheduling`, `integration`, and `app`. The first implementation milestone is a verified text reply in a separate test room.

ADB 37.0.1 and scrcpy 5.0.1 are installed in the ignored workspace tools folder. The owner approved USB debugging; device queries, screen capture, ADB touch scrolling and scrcpy video decoding were verified on the Android test device with KakaoTalk 26.9.1. USB functions remain rndis,adb; the checked route and DNS were unchanged and tethering remained active.

Java Runtime, Android SDK and Android Studio are still pending. No Gradle or Android build versions have been selected for this new project yet. See [Android remote setup](docs/09-android-remote-setup.md). Run `scripts/adb.sh` for USB commands or `scripts/scrcpy.sh --no-control` for a read-only mirror. The scrcpy helper uses a USB forward tunnel and disables audio and automatic clipboard synchronization.

## Delivery limitations

A successful PendingIntent invocation is a request outcome, not proof that a message appeared in KakaoTalk. Reply capabilities must be reacquired after process loss when an eligible notification is available. Missing or ambiguous room identity blocks sending. Doze, permissions, and KakaoTalk updates require real-device validation.

## License

New project material is licensed under MIT; see [LICENSE](LICENSE). Preserve upstream attribution and permission notices for any adapted code. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Third-party dependency and service terms are reviewed separately before distribution.
