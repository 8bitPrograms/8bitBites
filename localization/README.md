# UI translations

English is the default. The language selector and Android per-app language
settings offer English, Turkish, German, French, Spanish, Italian, Portuguese
and Japanese.

`tr.json` defines the canonical English source keys, and every language JSON
contains a translation for each canonical key. Run
`python3 scripts/build_localizations.py` after editing them to regenerate Android
resources, the app locale configuration, English template matchers and Kotlin
resource IDs. Positional placeholders must match the source key exactly.

The `ai(en, tr)` helper also resolves non-Turkish strings through these resources.
Food labels, brand/model names, license notices and messages returned by external
services keep their original wording. Offline food-term translation remains
English/Turkish and is separate from the application language.
