# Changelog

App versions and public food-catalogue versions are independent.

## 0.10.0-dev — 2026-11-16

- Show catalogue search and Favorites only inside Add food. Recorded Meals is a
  main destination. Food widgets open the picker; new custom foods continue to
  portion entry.
- Add AMOLED Black, Charcoal, Midnight, Forest and Dusk backgrounds and a separate
  accent preference. Keep light/system, Material You and unlocked extra palettes.
  Bundle Inter locally with its SIL OFL notice for consistent readable type.
- Strengthen dark-theme surface contrast and message fills; keep AMOLED backgrounds black.
- Rebuild widgets with compact defaults, bottom actions, expanding calorie rings
  and bottle graphics. Follow the app theme on Android 12+ and tint provider icons.
- Simplify chat while keeping the app’s bottom navigation and standard app bar.
  Keep history as a compact sheet, place attachments behind + and replace Send
  with Stop during requests. Avoid repeating saved meal acknowledgments.

## 0.8.0-dev — 2026-11-16

- Let AI interpret meals/photos and estimate portions and nutrition. Approve the
  summary to save directly, without database matching. Keep AI-estimate source
  labels, unknown values, portion scaling and confirmation/retry deduplication.
- Save local conversations, submitted photos and pending meal summaries. Reopen
  or delete them from history; keep account/provider bindings when continuing.
- Preserve confirmed diary entries when deleting chat history.

## 0.7.1-dev — 2026-11-16

- Apply the app-bar/navigation bounds to embedded chat and consume their insets;
  keep the composer visible above navigation and the keyboard.
- Keep browser sign-in in an app-owned session with a temporary foreground
  connection notification; clean it up on success, cancellation and timeout.
- Ignore idle/dropped browser callback connections instead of losing the pending
  authorization. Show browser/verification/model-loading progress separately.
- Reuse one account connection across screens and retain a verified identity when
  loading the account's models fails. Add credential-free connection stage logs.
- Add a mobile-sized browser return page with the app icon, localized text,
  light/dark appearance and a large return button.

## 0.7.0-dev — 2026-11-16

- Move all AI connection controls into Settings: on-device, OpenAI-compatible
  HTTPS server and ChatGPT account/plan access.
- Add the documented open-source ChatGPT sign-in flow with browser authorization,
  PKCE/state/nonce checks, verified identity, protected account storage, refresh,
  sign-out, account-specific model discovery and completed Responses streams.
- Add conversation bubbles, photo attachments, meal corrections and a separate
  review sheet. Diary nutrition still comes only from confirmed database foods.
- Use the selected provider's icon/name in navigation and the quick widget.
- Move recorded Meals and Favorites into sections under Foods; retain the You
  overview, water, supplements and trends. Existing diary/model data is unchanged.
- Build and check locally. Real-account sign-in and S25 inference remain pending.

## 0.6.2-dev — 2026-11-16

- Move native local-model execution into a private bound-service process so
  worker failures can be reported without directly crashing the diary process.
- Run one synchronous JNI request per worker and end the worker on timeout or
  cancellation, avoiding cleanup while an asynchronous native request is active.
- Show load/preparation/recognition stages and add copyable local diagnostics
  with device, Android, memory, model and failure-stage information.
- Recover unfinished requests without automatic retries. Meal text, photos,
  responses and credentials are excluded from the diagnostic record.
- Clarify that Gemma's Hugging Face token is for download access. The pinned
  Qwen file already matches the upstream template-fixed artifact; it is unchanged.

## 0.6.1-dev — 2026-11-16

- Show consumed protein, carbohydrates and sugars in the supplement card, with
  daily, product and intake totals from the recorded meal snapshot.
- Keep linked meals/intakes deduplicated and unreported nutrient values unknown.
- Document protein-powder hydration evidence and the absence of a universal
  extra-water dose equation.
- Declare small resumable chunks to Android's download scheduler, distinguish
  system deferral from network waiting and display the first received bytes.
- Build and verify locally; APK publication remains deferred.

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
