# Changelog

App versions and public food-catalogue versions are independent.

## 0.6.0-dev — 2026-11-16

- Add an adjustable water-planning estimate based on age, sex, height and weight.
- Separate the base from optional, user-defined supplement-day extras; activate
  extras from actual known intake and display them as dashed bottles.
- Add a water widget with direct drink buttons, redesign daily/quick widgets,
  and refresh after water/supplement changes and date/time-zone events.
- Stream and validate resumable model chunks, use the Android job network,
  interrupt paused connections and expose network waiting/retry controls.
- Restore the original development signing identity for the distributed APK.
- Make GitHub Actions manual-only; this version was built and checked locally.

## 0.5.1-dev — 2026-11-16

- Rename the app, widgets and project to **8bitBites**.
- Use the official `8bitPrograms/8bitBites` repository for version checks,
  catalogue downloads, release links and provider identification.
- Add English/Turkish project documentation, development and privacy guides.
- Make generated resources and catalogue publication explicitly UTF-8 on Windows.
- Include Android interaction/migration tests in CI alongside core tests,
  catalogue checks, assembly and lint.
- Preserve the Android package, private storage and unchanged catalogue 0.4.0.
  This patch does not introduce another feature popup.

This rename patch first published the development source and versioned catalogue.

## 0.5.0-dev — 2026-11-16

- Add custom water portions, glasses/bottles, date history and undo.
- Combine the diary, supplements and trends under the profile-photo tab.
- Add food favourites, remembered meals and a meal-level supplement checkbox.
- Add optional macro, protein-powder and creatine targets and profile fields.
- Add German, French, Spanish, Italian, Portuguese and Japanese alongside
  English and Turkish. Migrate private Room schema 1 to 2 without destructive reset.

## Earlier development

- **0.4.1-dev:** bundle the supplied catalogue 0.4.0 with 2,193 foods and retain
  independent catalogue updates/import.
- Earlier work introduced the Compose diary, Health Connect nutrition writes,
  AI review, model downloads, camera, profile photos, widgets and theme pack.

Historical verification records are kept in [docs/verification](docs/verification).
