# 8bitBites 0.5.1-dev — verification

Checked on **2026-11-16**, after recovering the 0.5.0 development source and
preparing the official `8bitPrograms/8bitBites` repository.

## Scope

The application label, widgets, project name, update endpoints and documentation
use **8bitBites**. The Android package remains `org.eightbit.bite`; private
storage names, Room migrations and stable food IDs are retained. The public
catalogue is still version **0.4.0**, SQLite schema **1**.

The source is versionCode **7**, versionName **0.5.1-dev**. Bundled patch notes
have `highlights=false`. The remote app manifest remains at the previously
packaged 0.5.0-dev (code 6); this publication does not claim a signed APK release.

## Checks completed

On Windows, with JDK 17, Android SDK platform 36, build-tools 35.0.0 and the
included Gradle 8.13 wrapper:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 scripts/check_catalogue.py
python3 -m unittest discover -s scripts -p 'test_*.py' -v
python3 scripts/build_localizations.py
```

- **50 tests passed, with no failures or skips:** 32 core JVM tests, seven
  Room/preferences/migration tests, six Compose interaction/render tests, and
  five Python catalogue publisher tests.
- Debug assembly and lint succeeded. Lint reports **0 errors and 63 warnings**;
  the remaining warnings are not represented as fixed.
- Translation regeneration produced no changes to the staged resources or
  lookup files. All eight UI languages are included.
- Catalogue checksum, size, schema, release metadata, row count and allowed
  tables passed validation: **2,193 foods**, including 260 ETİ entries.
- The convenience copy and immutable 0.4.0 snapshot match. The APK bundles those
  exact SQLite bytes. Earlier 0.1.0 and 0.2.0 snapshots are included unchanged.
- APK ZIP integrity, package/version/SDK metadata, the **8bitBites** application
  label and version-7 bundled release notes passed inspection.
- Android `apksigner verify` and `zipalign -c -P 16 4` passed. All 14 packaged
  native libraries have ELF LOAD alignment of at least 16 KB; LiteRT-LM itself
  is available for arm64-v8a and x86_64.
- Relative documentation links resolve. Authored files pass the staged
  whitespace check; original third-party license notices retain upstream
  whitespace. Credentials, private keys, local SDK configuration, APKs and
  build caches are excluded from the published source.

The app tests use Robolectric SDK 34 on the host. They do not establish
physical-device behaviour. The initial restricted-network build could not fetch
an Android test dependency; the complete run succeeded after dependency access
was available.

The first GitHub workflow stopped before tests because the hosted image did not
provide `sdkmanager` on its command path. The workflow now installs the Android
command-line tools and required SDK packages explicitly with setup-android,
rather than relying on tools preinstalled in the runner image.

## Locally built APK and signing

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Bytes: `94624243`
- SHA-256: `80fa25c1ce3d889517de4a53fe67bbb5f1cad2c806e2a733f7eb46befce58af4`
- Certificate SHA-256:
  `6d8f32f359514de208eed8170437b0702ee50147bc68cd36602a3024f03eb53c`
- Bundled database SHA-256:
  `1e73e8d5dc8759348d2796d45bec0d3ce85e2719fd7145b420d28cbcca30d10e`

This APK uses the local Android development key. Its certificate differs from
the 0.5.0 development artifact recorded in
[the historical report](docs/verification/0.5.0.md). It is therefore **not an
in-place upgrade for that artifact**. Keeping the same package name preserves
the upgrade path only when the APK is also signed with the existing key.
Do not uninstall an existing app to bypass this mismatch: uninstalling removes
its private diary. The original signing identity is needed for a distributable
upgrade; it is not part of the repository.

## Remaining validation

No physical phone or emulator was used for this publication. Upgrade
installation, camera, AI inference, live provider requests, background model
downloads, launcher widgets and Health Connect still require device testing.
The food catalogue, host tests and a successfully built APK do not establish
those results.

App and catalogue update URLs now target the official repository. A signed APK
release remains separate from publishing the source and public catalogue.
See [the release guide](docs/RELEASING.md) and
[the historical verification records](docs/verification).
