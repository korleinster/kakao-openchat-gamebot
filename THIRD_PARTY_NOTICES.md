# Third-party notices

## Selective source adaptation

- Project: bssm-oss/kakao-talk-auto-bot
- Repository: https://github.com/bssm-oss/kakao-talk-auto-bot
- Reviewed commit: a10b27b46984b8b85712bf84bf452596f607f5c7
- License: MIT
- Copyright: Copyright (c) 2026 kakao-talk-auto-bot contributors
- Preserved permission text: licenses/kakao-talk-auto-bot-MIT.txt

NotificationBridge.kt and SessionTransport.kt contain adapted portions of the reviewed notification/reply transport. Exact original paths, hashes, commit and changes are recorded in upstream.lock.json. Original copyright and permission notices are retained in source headers, this repository, and the in-app license screen.

## Dependencies and assets

The debug runtime dependency graph contains Kotlin standard library 2.0.21 and JetBrains annotations 13.0, both Apache-2.0. The Apache license is preserved in licenses/Kotlin-Apache-2.0.txt and the in-app license screen. The Gradle wrapper is Apache-2.0 build tooling. AndroidX test/JUnit dependencies are used only by test artifacts, not the main app. No AI model weights or upstream icon assets are included. A project's top-level MIT license does not replace dependency licenses or service terms.

The no-license lee775/KakaoBot-Android repository was reviewed as a candidate but its executable code is not incorporated.
