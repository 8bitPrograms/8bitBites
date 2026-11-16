# Home-screen widgets

Add widgets from Settings > Home screen widgets or the Android launcher's picker.

- **Quick add:** a single row of three large Food / selected AI provider / Photo
  targets. Its default target is 3 × 1 cells, subject to launcher cell sizing.
- **Daily nutrition:** a compact 4 × 2 target with energy, goal, water progress
  and actions. Enlarging it adds a calorie progress ring and three macro columns;
  the actions remain at the bottom.
- **Water tracker:** a compact 3 × 2 target with amount, goal, progress and three
  distinct drink amounts. A taller card shows large solid base bottles, dashed
  active supplement extras and the goal breakdown. The first drink amount uses
  the serving selected in Settings. Drinks are recorded without opening the app.

The layout uses the allocated height instead of leaving a large empty tail.
Launchers control actual cell dimensions; existing widget sizes stay under user
control. New default sizes apply when adding a widget again.

On Android 12+, widgets follow the app's chosen background and accent, including
AMOLED black. Buttons use visible filled surfaces and provider icons are tinted
for contrast. Android 9–11 retain their system light/dark resource palette.
The widgets use locally bundled Inter and the selected app language. Picker
preview images come from the actual widget renderer using fictional sample data.

Water buttons enqueue a durable background write. Each tap has a unique record
ID; retrying the same write does not add a duplicate drink. The record uses the
tap timestamp and current local day. UI and widgets share the hydration totals.

Updates follow food, water, supplement and preference changes. Periodic refresh
and date/time-zone events also refresh cards. Android scheduling can delay a
background refresh, so exact midnight delivery is not guaranteed.

Host tests inflate real RemoteViews, check control bounds at small/large sizes,
theme colors, provider icons and idempotent water writes. An Android 9 inflation
check covers the fallback path. See BUILD-VERIFICATION.md for physical-device
checks and their limits.
