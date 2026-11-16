# 8bitBites 0.10.0-dev — verification

Checked on 2026-11-16 with local Windows build tools. GitHub Actions was not used;
the workflow remains manual-only. The previous report is archived in
[0.8.0](docs/verification/0.8.0.md).

## Resulting behavior

- You / Meals / selected AI provider remain the app's navigation. Chat has one
  standard app bar, compact messages, history sheet and a single composer.
- Catalogue search and Favorites appear only within Add food. Food shortcuts
  open that picker; custom-food creation continues into portion entry.
- Five dark backgrounds include AMOLED black. Accent is selected separately.
  sRGB surface blending keeps message fills and controls visibly separate from
  the background. Inter is bundled locally with its complete SIL OFL notice.
- Widgets use compact default targets. Enlarged layouts use calorie rings,
  macro columns or bottle graphics and keep actions at the bottom. Android 12+
  widgets follow the app palette and tint provider icons; older devices retain
  system light/dark resources. Preview images use fictional renderer fixtures.
- Existing AI approval, account bindings, private history and diary semantics
  remain in place. Receipt links prevent a second visible saved-meal summary.

## Automated checks

**129 host tests passed: 53 core and 76 app. No failures, errors or skips.**

Checks include all built-in dark backgrounds/accents, bubble/background separation,
text contrast, pure-black AMOLED, accent persistence, attachment and Stop actions,
food-picker tabs, history/deletion, existing diary/hydration/model behavior and
actual RemoteViews inflation. Widget controls are checked at small and large
sizes, footer placement and expanded graphics are exercised, and Android 9
inflation covers the fallback path. Water-write retry deduplication remains tested.

Assembly succeeded with JDK 17, Gradle 8.13, platform 36 and build-tools 35.0.0.
Lint completed separately with **0 errors and 90 warnings**. Warnings include
resource/localization and layout recommendations; no baseline, exclusion or
suppression was added. Widget PNG previews were generated from passing renderer
fixtures, then packaged and checked in the final APK.

## Device checks and limits

The APK was installed in place on Samsung S25 (SM-S931B), Android 17 / API 37,
and the installed package reports versionCode 16 / 0.10.0-dev. No application
data was cleared. AMOLED and accent choices were observed persisting during
this session. Earlier device checks established account sign-in, text inference,
explicit meal approval and history restoration, as recorded in the prior report.

The final unlocked 0.10.0 check verified the AMOLED/green message fills and
composer, retained bottom navigation, saved conversation history, Search/Favorites
in Add food and the Meals screen after closing the picker. The existing Water
and Daily nutrition widgets were observed on the Samsung launcher with current
totals, visible icons, expanded graphics and bottom actions. The widget Food
shortcut opened the same picker. No invented meal or unconsumed drink was added.

The original 15-second screen-off timeout was restored and the temporary device
UI dump was removed. These checks do not establish every launcher, battery policy
or background water-tap schedule; those paths also have host coverage.

Phone screenshots and diagnostics remain in ignored local artifacts. They are
not included in the repository.

## Local artifact

- File: artifacts/8bitBites-0.10.0-dev.apk
- Application ID: org.eightbit.bite
- Version: 0.10.0-dev, versionCode 16
- Bytes: 96806189
- SHA-256: 5d1cd354e09868516ae663e0927d276151372bf319900a22c86e61f7ef5c1b00
- Signing certificate SHA-256:
  8e73b40f0ab8b1bb784f8a7aa74d9930fb02ec77356b329e04a20b2a1e54560c
- Catalogue SHA-256:
  1e73e8d5dc8759348d2796d45bec0d3ce85e2719fd7145b420d28cbcca30d10e
- Inter SHA-256:
  29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031

Signature, 16 KB ZIP alignment, ZIP CRC, packaged version notes, preview presence,
font/license and catalogue checks passed. All 14 native libraries are unchanged
from 0.8.0. The Room schema remains version 2. Signing material is not distributed.
No APK release was uploaded to GitHub; public app-update metadata is unchanged.
