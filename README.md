# Your Study
Native Android (Kotlin + Jetpack Compose, Material 3 Expressive). Open this folder in Android Studio, let Gradle sync, run on a device with Android 12+ (API 31+).

First run: grant Display over other apps, enable the "Your Study" accessibility service, and set Your Study as the default home app. Then pick a time + essential apps and tap Start focus.

## Updates

The app checks the public `adsky165ytgamer/YourStudy` GitHub Releases repository through the MATH1 API. A successful check with no newer release is different from an API or network error; errors are retained as a retry/offline state rather than being reported as up to date. When a release is available, the app displays the publisher's plain-text notes, downloads the HTTPS GitHub asset off the main thread, verifies the optional SHA-256, validates the package and version code, and hands the file to Android's package installer for user confirmation.

Release authors must publish exactly one APK and include the MATH1 metadata block. See [RELEASING.md](RELEASING.md) for the required format and generation helper.
