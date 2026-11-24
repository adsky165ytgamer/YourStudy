# YourStudy release process

YourStudy releases are consumed by the public MATH1 update-check API at `https://math1.adsky165ytgamer.workers.dev`. MATH1 only considers published GitHub Releases in this public repository.

## Required release shape

Each release intended for app updates must contain exactly one non-empty `.apk` asset. The asset must be uploaded to the GitHub Release, not merely committed to the source tree. Do not attach a second APK such as a debug build or an unsigned build.

The release body must contain human-readable notes followed by one MATH1 metadata block:

```text
<!-- math1
{
  "versionName": "5.0.0",
  "versionCode": 5,
  "minSdk": 31,
  "compileSdk": 36,
  "targetSdk": 36,
  "channel": "stable",
  "sha256": "<64 lowercase hex characters>"
}
-->
```

`versionCode` must increase monotonically. MATH1 orders updates only by `versionCode`; `versionName` is descriptive. The `minSdk` value is checked against the device's Android API level. A release containing the exact marker `<!-- math1:block -->` is intentionally ignored.

## Generate release metadata

After building and signing the single release APK, run:

```bash
python3 tools/generate-math1-release-notes.py \
  /path/to/YourStudy-release.apk \
  --version-name 5.0.0 \
  --version-code 5 \
  --min-sdk 31 \
  --compile-sdk 36 \
  --target-sdk 36 \
  --channel stable \
  --notes "YourStudy 5.0.0: planner, wellbeing, pairing, and live update improvements."
```

Copy the complete output into the GitHub Release body and upload only that APK. The script computes the SHA-256 from the exact uploaded file.

## Verify before publishing

Check that the repository is public, the release is not a draft, the tag points to the intended `main` commit, the APK is signed with the official `yourstudy` keystore, and the release contains exactly one APK. The Android client also verifies HTTPS, package identity, version code, and the optional MATH1 SHA-256 before opening Android's package installer.

Stable builds query MATH1's `stable` result. Beta builds may explicitly opt in to the `beta` result; alpha releases are never offered. MATH1 errors, including rate limits and GitHub outages, are not treated as “up to date.”

## Version 6

```bash
python3 tools/generate-math1-release-notes.py \
  /path/to/YourStudy-v6-release-signed.apk \
  --version-name 6 --version-code 8 --min-sdk 31 --compile-sdk 36 --target-sdk 36 \
  --channel stable \
  --notes "YourStudy 6: custom break length, focus music with dimmed cover, Quick Settings tile, manual update check."
```

Tag the release `v6`, upload only the signed APK, then put its SHA-256 into `control-plane/control.json` (`appUpdate.sha256`) before setting `appUpdate.enabled` to `true`. The app also checks that the APK is signed with the official certificate (SHA-256 `1cd0da51dbb06bf33a1714490005eddb17ad0e43e199d009f85939c84716025c`) before opening the installer.
