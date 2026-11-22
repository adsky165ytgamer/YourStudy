# YourStudy

YourStudy is a native Android focus and study companion built with Kotlin, Jetpack Compose, and Material 3. It supports Android 12 and newer (API 31+), with a launcher-safe focus experience, accessibility-assisted app control, device pairing, parent/child coordination, and local voice notes.

## Version 3.0 highlights

- **Focus sessions:** Start timed focus sessions, pause and resume them, enforce selected essential apps, and automatically handle focus breaks and completion states.
- **Launcher-safe behavior:** The focus overlay returns to the real launcher when a break or session completes, with fallback behavior when the launcher intent is unavailable.
- **Parent and child roles:** Choose a self-study, parent, or child role during setup. Parent devices can pair with a child device and review synchronized focus activity.
- **Device pairing:** Pair devices using a QR-based flow and a shared secret. The pairing layer supports parent/child identity, peer state, unpairing, and queued session-log transfer.
- **Session history:** Focus results are stored locally and can be synchronized as peer logs after pairing. Logs include planned duration, focused time, break usage, and completion state.
- **Voice notes:** Capture and manage local study notes, including language metadata, timestamps, favorite state, and whether a note was created during a focus session.
- **Permissions and setup checks:** The app guides users through overlay, accessibility, notification, camera, microphone, Bluetooth, and nearby Wi-Fi permissions as required by the selected workflow.
- **QR and camera support:** QR scanning is available for pairing, with camera support optional at the device-feature level.
- **Release signing:** Release builds support property-based signing with the official YourStudy keystore. Keystore files and passwords are intentionally excluded from source control.

## First run

1. Install the APK on Android 12 or newer.
2. Grant **Display over other apps** permission.
3. Enable the **Your Study accessibility service** when prompted.
4. Grant notifications and nearby-device permissions when using pairing.
5. Grant camera permission for QR pairing and microphone permission for voice notes when requested.
6. Select **Self study**, **Parent**, or **Child** during setup.
7. Choose a focus duration and essential apps, then tap **Start focus**.

For parent/child workflows, complete pairing from the pairing screen by scanning the peer QR code and approving the requested device permissions. A device can be unpaired from setup; locally stored focus history remains available while peer state and queued pairing data are cleared.

## Build from source

Open this folder in Android Studio or use Gradle directly. The project uses Android SDK 36, Java 17-compatible Android compilation, Kotlin 2.1.0, and Gradle 8.11.1.

```bash
./gradlew assembleDebug
```

For a release build, keep the keystore outside the repository and pass signing values through Gradle properties or an equivalent CI secret store:

```bash
./gradlew assembleRelease \
  -PreleaseStoreFile=/secure/path/YourStudy-release.keystore \
  -PreleaseStorePassword="$YOURSTUDY_STORE_PASSWORD" \
  -PreleaseKeyAlias=yourstudy \
  -PreleaseKeyPassword="$YOURSTUDY_KEY_PASSWORD"
```

Never commit the keystore, signing passwords, or generated build outputs. The release application ID is `com.yourstudy.app`.
