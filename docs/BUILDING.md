# Build and test

## Toolchain

| Component | Project requirement |
| --- | --- |
| Java | JDK 17 |
| Gradle | 8.13, through the included wrapper |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.21 |
| Android SDK | Platform 36, build-tools 35.0.0 |
| Minimum Android version | API 28 / Android 9 |
| Python | 3.9+ for the standard-library maintenance scripts |

Open the repository in Android Studio and install the required SDK packages.
Set `JAVA_HOME` to your JDK 17 installation. Use `ANDROID_HOME` for the SDK or
let Android Studio create an ignored `local.properties` with `sdk.dir`.

## Commands

Linux/macOS:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 scripts/check_catalogue.py
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Windows PowerShell:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python scripts/check_catalogue.py
python -m unittest discover -s scripts -p 'test_*.py' -v
```

If `gradlew` is not executable after manually extracting an archive, run
`sh gradlew ...` or restore its executable bit. Git checkouts preserve it.

The APK is `app/build/outputs/apk/debug/app-debug.apk`. App test reports are in
`app/build/reports/tests/testDebugUnitTest/`; core reports are in
`core/build/reports/tests/test/`. The lint report is under `app/build/reports/`.
The first test run downloads Robolectric Android artifacts and requires network
access. App tests use Robolectric SDK 34; production compile/targetSdk is 36.

CI runs the same checks on Ubuntu and retains the debug APK and reports as
short-lived workflow artifacts. These artifacts use a CI development signing
key; they are not a stable signed release channel.

## Project layout

- `app/`: Compose UI, Room, CameraX, Health Connect, model runtime and widgets.
- `core/`: calculations, catalogue compatibility, AI parsing and download rules.
- `database/`: public SQLite catalogue, manifests and immutable data versions.
- `localization/`: editable translation dictionaries.
- `scripts/`: catalogue publication/checks and resource generation.
- `docs/`: behaviour, privacy, release and historical verification notes.

Regenerate translations with `python3 scripts/build_localizations.py`. Room
schema exports belong in `app/schemas/`; add a migration rather than resetting
the private database. Catalogue schema and Room schema are separate contracts.

## Repository and signing configuration

The default update repository is `8bitPrograms/8bitBites`, branch `main`. A fork
can override it with `-Pbite.repository=OWNER/REPOSITORY`. The internal property
name and Android package `org.eightbit.bite` are retained for compatibility.

Development builds use the local Android debug key. Updating an installed APK
requires the same application ID and signing certificate plus a higher
versionCode. The display-name change alone does not require a data migration.
Do not uninstall an existing installation simply to bypass a signature mismatch:
uninstalling removes its private diary. See [RELEASING.md](RELEASING.md).
