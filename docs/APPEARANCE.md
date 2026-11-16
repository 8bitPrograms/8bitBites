# Appearance and navigation

**Settings > Appearance > Background** offers Charcoal, AMOLED Black, Midnight,
Forest and Dusk, plus light, system and Material You options. These choices do
not require the extra-palette unlock. New installations use the clean system
theme; existing theme preferences stay intact.

AMOLED Black uses #000000 for the screen background and bottom navigation.
Cards, messages and controls use clearly separated dark surfaces. Filled message
bubbles keep at least 1.8:1 contrast against their dark background, and bubble
text stays at least 4.5:1, across all built-in dark backgrounds and accents.
Accent color is a separate preference: Default, Blue, Green, Purple, Pink or
Orange. It changes buttons, selection highlights and user message bubbles
without changing the chosen background.

This follows the separation between appearance and accent color described in
[ChatGPT's appearance guidance](https://help.openai.com/en/articles/11958281-updating-your-visual-experience-on-chatgpt).
The additional dark palettes and AMOLED option are 8bitBites designs.

The clean/dark themes bundle Inter under SIL OFL 1.1. It is loaded locally and
does not change the phone's system font. Source, checksum and the complete
license are included in assets/fonts and exposed in the app's font credits.

The app retains **You / Meals / selected AI provider** bottom navigation.
Chat uses the same app-bar style as other screens, with history, New chat and
AI settings actions. It has no separate navigation drawer. Attachments are in
the composer + menu. Confirmed meal estimates show one saved marker.

Food search and Favorites are only inside **Add food**. Closing this picker
returns to the diary; choosing a result continues to portion entry. Widget food
shortcuts use the same picker. Creating a personal food continues to its portion
editor. Recorded Meals stays a main destination.
