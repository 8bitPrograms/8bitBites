# Releasing the app and catalogue

## App release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`. A distributable
   upgrade needs a strictly higher versionCode and the existing signing identity.
2. Update `CHANGELOG.md` and the bundled `app-whats-new.json`. Use concise notes
   for each supported language. Set `highlights=false` for ordinary patch changes.
3. Run the checks in [BUILDING.md](BUILDING.md), inspect the resulting APK, and
   test installation and the affected flows on physical Android devices.
4. Sign the release using a key kept outside the repository. Never commit a
   keystore or passwords. Android Studio's signed APK/bundle workflow can supply
   the signing configuration; this repository does not ship production keys.
5. Verify the package, version, certificate, APK signature and 16 KB native-library
   alignment. Record the artifact's SHA-256 and the checks actually performed.
6. Publish a GitHub Release with the verified APK, release notes and checksum.
   Only after the APK is available, advance `app-update.json` on `main` to that
   published version. The app opens the repository's Releases page; it never
   silently installs an executable.

Source version **0.5.1-dev** is the rename/repository patch. The remote app
manifest intentionally still advertises the previously packaged **0.5.0-dev**
(versionCode 6) until a signed 0.5.1 APK is released. CI debug APKs use temporary
signing keys and do not replace the existing development release identity. The
local 0.5.1-dev verification APK also has a different certificate from the earlier
0.5.0 development artifact; see [the signing record](../BUILD-VERIFICATION.md).
It must be signed with the original key before distribution as an in-place upgrade.

The display name is 8bitBites, but `org.eightbit.bite` and private storage names
remain stable. Changing them would create a different app or strand existing
data and is not part of this rename.

## Catalogue-only release

Follow [database/README.md](../database/README.md). Publish a new immutable
snapshot under `database/versions/<version>/` and update the stable manifest and
convenience copy in the same commit. Keep all previously published snapshots.
Run the publisher tests and catalogue integrity check before pushing.

Catalogue content version, SQLite schema version and application versionCode
have different purposes. A schema change needs compatible client validation;
a new catalogue does not automatically require an APK release. Never replace
the private Room database with the public food catalogue.
