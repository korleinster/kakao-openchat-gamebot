# Security toolchain update — 2026-10-30

Update Kotlin Android plugin 2.0.21 to 2.4.21 and Gradle 8.9 to 8.14.4.
Pin the official Gradle distribution SHA-256 consistently in the wrapper and
bootstrap lock. Migrate the removed kotlinOptions JVM target setting to
compilerOptions while retaining Java 17, AGP 8.7.3 and Android SDK levels.
Keep all existing application features, package/version metadata and signing
configuration. The snapshot feature base is commit 8acd8f4.

Validation: 24 unit tests pass; lint reports zero errors and eight warnings.
74 tasks complete successfully, including app and instrumentation-test APK
assembly. The existing project debug keystore is reused only in an ignored
local build folder. No key or generated APK is committed.

This branch is prepared separately because the main project chat is actively
working with the connected device. The original checkout and device have not
been modified by this security build. Instrumentation tests are compiled but
not executed; phone installation and on-device acceptance remain pending.
