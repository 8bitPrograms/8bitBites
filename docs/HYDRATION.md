# Water tracking and optional reference

The water goal is a user-selected tracking target. It is never a limit on
recording another drink and is never silently changed by an age, profile,
calorie, protein-powder or creatine update. Reaching it does not disable logging.
Drinks are recorded by volume; powder mass is not treated as water volume.

For adults with age and sex entered, Settings can display a starting reference:
2,000 ml of drinks for male profiles and 1,600 ml for female profiles. These are
an explicit app estimate of 80% of EFSA's adult total-water reference values
(2.5 L and 2.0 L respectively), assuming 20% comes from food. Actual food-water
contributions vary. The original reference includes food moisture and all drinks,
and is not an individual prescription or a required plain-water intake.

This suggestion uses age/sex references. Weight, height, a calorie target and
supplement grams alone do not establish an individualized water requirement.
No extra ml-per-gram rule is applied for creatine or protein powder. This is a
deliberate limit of the suggestion, rather than a claim of personal prediction.
For profiles under 18 or without the required reference inputs, only the custom
goal is offered. No medical or supplement dosage target is generated.

Sources:

- EFSA, *Scientific Opinion on Dietary Reference Values for water*:
  https://www.efsa.europa.eu/en/efsajournal/pub/1459
- EFSA, *Dietary Reference Values for nutrients — Summary report*, water section:
  https://www.efsa.europa.eu/sites/default/files/2017_09_DRVs_summary_report.pdf
- International Society of Sports Nutrition position stand on creatine:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC5469049/

The screen uses glasses for servings below 700 ml and bottles for servings of
700 ml or more. A 2,000 ml goal with a 100 ml unit produces 20 glasses. The final
vessel may be partially filled when the goal is not a multiple of the unit.
Changing the serving changes future additions and the visualization, not past
logged volumes. Undo removes the most recently added entry from that session;
individual historical entries can be deleted separately. Every date uses local
day boundaries, including daylight-saving transitions.

Water data is stored in the private app database. This release continues to
export nutrition records only to Health Connect; it does not request hydration
permissions or export water records.
