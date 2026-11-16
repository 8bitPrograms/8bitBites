# Releasing the app and catalogue

## Local app release

Builds are performed locally. GitHub Actions runs only when manually dispatched;
pushing source, tags or release metadata does not start a hosted build.

1. Advance the app versionCode/versionName and bundled release notes. Major user
   features use highlights=true; ordinary corrections do not need another popup.
2. Follow BUILDING.md to run the host tests, catalogue checks, debug assembly and lint.
3. Review the actual UI renders and validate the affected flows on Android devices.
4. Sign the distributable APK with the existing development signing identity.
   Keep the keystore outside the repository. A normal Gradle debug APK may use a
   different local key and must not be assumed to upgrade a previous download.
5. Verify APK signature, package/version, ZIP and native-library alignment. Record
   its SHA-256 and the certificate fingerprint in BUILD-VERIFICATION.md.
6. Publish a GitHub pre-release for development builds, with the APK and checksum.
   Once the APK is available, advance app-update.json on main to that version.

The signed 0.6.0-dev package uses the original development certificate with SHA-256
8e73b40f0ab8b1bb784f8a7aa74d9930fb02ec77356b329e04a20b2a1e54560c.
It matches the historical 0.5.0 development artifact. The Android package remains
org.eightbit.bite, and the Room migration history is retained.

The earlier locally built 0.5.1 verification APK used a different certificate,
recorded in docs/verification/0.5.1.md. A device that installed that particular
artifact has a different signing identity. Never uninstall a diary merely to
bypass a signature mismatch.

Host tests and matching certificates do not prove an actual phone upgrade.
The app opens GitHub Releases; it never silently installs an executable.

## Catalogue-only release

Follow database/README.md. Publish a new immutable snapshot and update the stable
manifest and current copy atomically. Keep old snapshots, stable IDs and source
attribution. Run the catalogue publisher tests and integrity checks before pushing.

Catalogue content version, SQLite schema and app versionCode are independent.
Never publish the private Room diary, personal photos, credentials or signing keys
as part of the public catalogue.
