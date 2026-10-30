# Third-party notices

## Selective source adaptation

- Original project: [bssm-oss/kakao-talk-auto-bot](https://github.com/bssm-oss/kakao-talk-auto-bot)
- Original copyright holder: kakao-talk-auto-bot contributors
- Source commit: [a10b27b46984b8b85712bf84bf452596f607f5c7](https://github.com/bssm-oss/kakao-talk-auto-bot/tree/a10b27b46984b8b85712bf84bf452596f607f5c7)
- License: MIT
- Copyright: Copyright (c) 2026 kakao-talk-auto-bot contributors
- Preserved permission text: [upstream MIT license](licenses/kakao-talk-auto-bot-MIT.txt), copied without modification from the [pinned original](https://github.com/bssm-oss/kakao-talk-auto-bot/blob/a10b27b46984b8b85712bf84bf452596f607f5c7/LICENSE)

NotificationBridge.kt and SessionTransport.kt contain adapted portions of the reviewed notification/reply transport. Exact original paths, hashes, commit and changes are recorded in upstream.lock.json. Original copyright and permission notices are retained in source headers, this repository, and the in-app license screen.

| Local file | Original source at the pinned commit |
| --- | --- |
| [NotificationBridge.kt](app/src/main/java/com/korleinster/kakaobot/NotificationBridge.kt) | [NotificationListener.kt](https://github.com/bssm-oss/kakao-talk-auto-bot/blob/a10b27b46984b8b85712bf84bf452596f607f5c7/app/src/main/java/com/example/chatbotchichi/NotificationListener.kt) |
| [SessionTransport.kt](app/src/main/java/com/korleinster/kakaobot/SessionTransport.kt) | [SessionManager.kt](https://github.com/bssm-oss/kakao-talk-auto-bot/blob/a10b27b46984b8b85712bf84bf452596f607f5c7/app/src/main/java/com/example/chatbotchichi/SessionManager.kt), [SessionReplier.kt](https://github.com/bssm-oss/kakao-talk-auto-bot/blob/a10b27b46984b8b85712bf84bf452596f607f5c7/app/src/main/java/com/example/chatbotchichi/SessionReplier.kt) |

Changes include explicit room pairing, one-to-one notification admission, hashed conversation identity, package validation, recovery handling, and durable delivery state. This is an independently maintained adaptation; the upstream authors do not maintain or endorse this project. New project code is credited to korleinster. When redistributing copied or substantially included MIT code, retain the applicable copyright and permission notices. Source links document provenance; they do not replace the license text.

## Dependencies and assets

The release runtime graph includes Kotlin standard library 2.2.21, JetBrains annotations 23.0.0, AndroidX Activity 1.11.0 and its Core/Lifecycle/SavedState companions, Kotlin coroutines 1.7.3, and Guava's listenablefuture 1.0. Their Maven POM license declarations are Apache-2.0. The resolved module inventory is in [licenses/runtime-components.json](licenses/runtime-components.json); full Apache-2.0 text and any packaged NOTICE text are preserved in the app's license screen. AndroidX sources are maintained by the Android Open Source Project; Kotlin/coroutines/annotations by JetBrains and contributors; Guava by Google and contributors. These components retain their own notices and are not relicensed by the project's MIT license.

The Gradle wrapper is Apache-2.0 build tooling. AndroidX test/JUnit dependencies belong only to test artifacts. The app icon is project-created vector artwork. No AI model weights or upstream icon assets are included.

The no-license lee775/KakaoBot-Android repository was reviewed as a candidate but its executable code is not incorporated.
