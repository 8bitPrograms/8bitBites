# Contributing to 8bitBites

Bug reports, translations, interface improvements and reviewed food-label
corrections are welcome. Open an issue describing the problem or proposed
behaviour, then send a focused pull request against `main`.

## Development setup

Follow [BUILDING.md](docs/BUILDING.md). Use Kotlin/Compose for the Android UI and
keep reusable calculations in `core`. Preserve `org.eightbit.bite`, private
storage names, stable food IDs and existing Room migrations.

Run the checks relevant to your change:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 -m unittest discover -s scripts -p 'test_*.py' -v
python3 scripts/check_catalogue.py
```

For new behaviour, add tests that exercise user-visible outcomes or data
contracts. For UI changes, check a small screen, long text, dark mode and the
affected languages. Document any physical-device checks that were not run.

## Translations

Edit `localization/<language>.json` rather than generated string resources.
English keys are canonical; preserve positional placeholders such as `%1$s`.
Run `python3 scripts/build_localizations.py` and commit the generated resources,
lookup map and locale configuration. Widget labels live in each locale's
`widgets.xml`. See [localization/README.md](localization/README.md).

## Food data

Keep unknown nutrients as `NULL`; a missing value is not zero. Report values
per 100 g, use grams for sodium and preserve source attribution. Include a
product label or other reviewable reference for a correction. Do not guess a
serving weight, density or unreported nutrient.

Published `database/versions/` snapshots are immutable. Publish corrections as
a new data version using [the database guide](database/README.md). Personal
diaries, profiles, photos and API keys do not belong in the public catalogue.

## Pull requests

Explain the concrete problem, resulting behaviour and checks performed. Keep
unrelated changes separate. Never commit credentials, signing keys, private
exports, model weights, SDKs, caches or build output. The existing ignore rules
cover local SDK configuration and keystores; inspect your staged diff as well.

Contributions to application code use GPL-3.0-or-later. Retain third-party
license notices and do not change the licensing of external content.
