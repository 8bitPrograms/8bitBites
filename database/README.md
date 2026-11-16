# Independently versioned food catalogue

`foods.sqlite` is the current convenience copy. `manifest.json` points to a
specific immutable `versions/<version>/foods.sqlite` snapshot. Data versions
(e.g. 0.1.0) and SQLite schema versions (`PRAGMA user_version`, currently 1)
are different. Application releases have their own integer versionCode.

The app bundles an initial snapshot for offline use. Opening the app checks
`app-update.json` and `database/manifest.json` separately on GitHub's raw endpoint.
No diary, profile, dose or search text is included in these update requests.
The official repository is `8bitPrograms/8bitBites`, branch `main`.
Configure another repository at build time:

```sh
sh gradlew assembleDebug -Pbite.repository=OWNER/REPOSITORY
```

Application update controls open that repository's Releases page. They do not
download or silently install executables. Database-only updates fetch the
immutable SQLite snapshot, verify exact size and SHA-256, check SQLite integrity,
validate required tables, metadata, IDs and nutrient values, and check app/schema
compatibility before replacing the private local catalogue through AtomicFile.
The previous catalogue remains available if download/validation fails. Personal
Room data and historical nutrient snapshots are never replaced by this operation.
Automatic launch checks do not automatically install database updates.

## Publish a new snapshot

```sh
python3 scripts/publish_database.py --csv collection.csv --version 0.4.1 \
  --kind patch --notes-json release-notes.json
```

`release-notes.json` is an object with `en` and `tr` arrays of concise changes.
Use `--kind patch` for ordinary corrections: an update remains available but
does not show a feature modal. Minor/major content changes set `highlights=true`.
The app displays significant changes after installing the app or catalogue;
seen versions are tracked independently. Duplicate source URL/variant pairs are
deduplicated, unknown nutrients stay NULL, per-gram labels are normalized to
100 g, and volume/unknown serving bases are excluded without inventing density.
The manifest records inclusion/exclusion counts. Reviewed future schema changes
must update both publisher and client compatibility checks.

Existing version directories are immutable. Failed publication output is left
for inspection; fix it deliberately rather than reusing a published version.
Never copy the personal Room database, diary, API keys or user profile into this
directory. See NOTICE.md for accuracy limitations and source rights. OFF/USDA
results stay separate from this community collection; their licenses still apply.

## Current import

The current 0.4.0 snapshot is the supplied `8bitbite_foods_0.4.0.sqlite` unchanged:
2,193 usable food rows, including 260 ETİ foods, plus research metadata that the app does not treat as
food or nutrition records. Its SHA-256 is
`1e73e8d5dc8759348d2796d45bec0d3ce85e2719fd7145b420d28cbcca30d10e`.
All 636 food IDs from 0.2.0 are retained; 1,557 foods have been added. The earlier
0.1.0 (419 foods) and 0.2.0 (636 foods) snapshots remain immutable. Required source
credits and applicable licenses remain in effect; see NOTICE.md.

The Settings > Updates screen accepts a newer compatible SQLite file directly.
Integrity, schema, release metadata, row counts and nutrient values are checked
before atomic installation. Reinstalling an APK is not required. An APK with a
newer bundled catalogue also upgrades the catalogue on first open, while keeping
a separately installed newer catalogue intact. Personal diary data stays separate.

## Public endpoints

- [Current manifest](https://raw.githubusercontent.com/8bitPrograms/8bitBites/main/database/manifest.json)
- [Current convenience copy](https://raw.githubusercontent.com/8bitPrograms/8bitBites/main/database/foods.sqlite)
- [Immutable 0.4.0 snapshot](https://raw.githubusercontent.com/8bitPrograms/8bitBites/main/database/versions/0.4.0/foods.sqlite)
- [App release manifest](https://raw.githubusercontent.com/8bitPrograms/8bitBites/main/app-update.json)

The app downloads the immutable path selected by the manifest, not the convenience
copy. Publish the manifest, matching current copy and new version directory in
the same commit. Do not rewrite earlier snapshots or store these SQLite files as
Git LFS pointers: the updater needs the actual SQLite bytes at the raw URL.

## Publisher input

The publisher accepts a UTF-8 CSV with these column names. The first five are
needed to identify and normalise a usable label; nutrient cells can be blank.

| CSV column | Meaning |
| --- | --- |
| `Product` | Product name |
| `Source URL` | HTTPS source reference |
| `Basis unit` | `g`; volume-based labels are excluded |
| `Basis qty` | Positive mass represented by the label values |
| Nutrient column(s) | At least one reported value is required |
| `Brand`, `Store`, `Flavor / variant` | Optional attribution/identity fields |
| `Energy kcal` | Energy in kcal for the stated basis |
| `Protein (g)`, `Carbohydrate (g)`, `Fat (g)` | Macronutrient grams |
| `Fiber (g)`, `Sugars (g)`, `Sodium (g)` | Other nutrient grams; sodium is not salt |
| `QC status`, `QC note` | Optional review notes |

`release-notes.json` can contain, for example:

```json
{"en": ["Corrected reviewed product labels."], "tr": ["İncelenen ürün etiketleri düzeltildi."]}
```

Before committing, run:

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
python3 scripts/check_catalogue.py
```
