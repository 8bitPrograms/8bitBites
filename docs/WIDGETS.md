# Home-screen widgets

Add widgets from Settings > Home screen widgets or the Android launcher's widget picker.

- **Water tracker:** current day's consumed amount and combined goal, solid base
  bottles, dashed active extras, and three distinct quick amounts. The first amount
  uses the serving selected in Settings. Buttons log water without opening the app.
- **Daily nutrition:** energy progress, macros and optional targets, water progress,
  a quick-water button and food/AI/camera shortcuts.
- **Quick add:** profile, food, AI and camera shortcuts in a compact card.

Widgets adapt to resizing, support light/dark system colours, and use the selected
app language. Compact cards hide illustration/details to retain the action buttons.
Tap a water card to open today's water tracker.

Water buttons enqueue a durable background write. Each tap has a unique record ID;
retrying the same write does not add a duplicate drink. The record uses the tap's
timestamp and the current local day. UI, widget and supplement totals use the same
daily-goal calculation.

Updates follow food, water, supplement and preference changes. Periodic refresh and
date/time-zone events also refresh the cards. Android/launcher scheduling can delay
background refresh, so exact midnight delivery is not guaranteed.

Host tests apply actual RemoteViews, check visible totals and button bounds, verify
idempotent water writes and render small/normal previews. Physical-device launcher,
tap scheduling and battery-restriction behaviour still need device validation.
