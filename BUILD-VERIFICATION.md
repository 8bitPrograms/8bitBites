# 8bitBites 0.6.1-dev — local verification

Checked on **2026-11-16**. This version was built on the local Windows computer.
GitHub Actions was not used; its workflow now runs only by manual dispatch.

## Changes verified

- Supplement-card totals and individual intakes show only consumed protein,
  carbohydrates and sugars. The stored meal snapshot counts once, and missing
  values stay unknown. A Turkish render checks 30 g powder / 24 g protein,
  3 g carbohydrate and 1 g sugar without adding nutrition a second time.
- Resumable Android jobs declare a 64 KB minimum network chunk, capped by the
  remaining size. Network waiting and system deferral are distinguished. The
  first received bytes are reported immediately and displayed in B/KB/MB.
- Profile-based, adjustable base water planning using age, sex, height and weight.
  The calculation is explicitly an estimate; see [HYDRATION.md](docs/HYDRATION.md).
- User-selected supplement-day extras activate from actual known intake. Linked
  meal/supplement records count once; unknown scoop weights add no invented grams.
  Extras default to zero because research establishes no litres-per-gram rule.
- Solid base vessels and separately labelled dashed extra bottles. Removing a
  dose removes its planned extra without changing actual water history.
- New direct-action water widget, improved daily/quick widgets, compact layouts,
  refreshed water/supplement totals and date/time-zone refresh handling.
- Streamed, verified model downloads with valid partial-chunk continuation,
  pause/disconnect handling, assigned Android job networks, bounded retries and
  explicit connection/network waiting states.

## Local checks

**73 JVM/app host tests passed, with no failures or skips:**

- 41 core JVM tests, including seven HTTP transfer/recovery tests.
- 32 app host tests: three daily hydration/deduplication tests, seven
  Room/preferences/migration tests, four supplement-nutrition tests, nine Compose
  interaction/render tests, five download-start/progress tests and four
  RemoteViews/direct-water-write tests.

Debug assembly and Android lint succeeded with JDK 17, Android platform 36,
build-tools 35.0.0 and Gradle 8.13. Lint has **0 errors and 76 warnings**.
No lint baseline or new suppression was introduced to make the run pass.

The widget renders were inspected at normal and compact sizes. A clipped footer
found in the first render was fixed; final tests also check absolute button bounds.
The app dashboard, Japanese dashboard and active supplement-extra water view were
rendered. Main new water/download text is available in English and Turkish;
existing additional languages retain their translations and use English fallback
for explanatory text not yet translated.

The public catalogue is unchanged: version 0.4.0, schema 1, 2,193 foods. The
packaged catalogue SHA-256 matches the previously checked catalogue. Translation
generation, relative documentation links and authored-file whitespace were checked.
The unchanged Python publisher checks are recorded in the
[0.6.0 report](docs/verification/0.6.0.md).

A real public Qwen request in the preceding 0.6.0 work returned HTTP 200, and a request resuming at byte
65,536 returned the exact expected HTTP 206 range. Only small samples were read.
Gated Gemma endpoints returned the expected HTTP 401 without a token. Host transfer
tests cover interruption/resume, ranged chunks, servers ignoring ranges, redirect
token isolation, invalid ranges, bad hashes and gated/CDN error classification.

## Local development APK — publication deferred

- File: artifacts/8bitBites-0.6.1-dev.apk
- Application ID: org.eightbit.bite
- Version: 0.6.1-dev, versionCode 9
- Bytes: 96043007
- SHA-256: 19824b0db59c469f8c0648507d560550b52b9b83361907e382ce54d29c63bb6a
- Certificate SHA-256:
  8e73b40f0ab8b1bb784f8a7aa74d9930fb02ec77356b329e04a20b2a1e54560c
- Bundled database SHA-256:
  1e73e8d5dc8759348d2796d45bec0d3ce85e2719fd7145b420d28cbcca30d10e

The local distributable was re-signed with the recovered original development
identity. Android signature verification and 16 KB ZIP alignment passed. This
certificate matches the historical 0.5.0 development APK. The keystore is outside
the repository and is not included in source or release assets.
ZIP integrity, packaged version notes and the bundled catalogue checksum passed.
The 14 native libraries are byte-identical to the checked 0.6.0 artifact.

No GitHub APK release was published. App-update metadata remains at the last
published APK version until publication is requested.

The standard Gradle debug output uses the local default key; it is not the
distributed artifact listed above. The short-lived 0.5.1 verification build also
used that other key; see [its historical report](docs/verification/0.5.1.md).

## Limits

No Android device was connected. Matching certificate/package/version and host
tests do not prove a real upgrade. Physical-phone installation, launcher actions,
Android background scheduling, complete multi-GB model downloads, local inference,
camera and Health Connect still need device validation.

The download changes address concrete transport, waiting-state and recovery paths.
Small live range requests and simulated interrupted transfers do not establish
that a complete model has downloaded and run on a user's phone.
