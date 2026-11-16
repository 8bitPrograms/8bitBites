# Water planning and supplement-day extras

The app separates the base goal, optional protein-powder extra and optional
creatine extra. These are planning targets. They do not record any water as drunk.

## Profile estimate

Settings > Water can adjust the base when the saved age, sex, height or weight
changes. Existing saved custom goals remain in custom mode until the user enables
profile mode. An incomplete or unsupported profile keeps the custom base.

For adult profiles (18–80 years, 35–250 kg, 120–230 cm), the app uses:

    resting kcal = 10 × weight kg + 6.25 × height cm − 5 × age + sex term
    sex term = +5 for male, −161 for female
    base drinks goal ml = round to 50 ml(resting kcal × 1.4 × 0.8)

The Mifflin–St Jeor equation estimates resting energy. The 1.4 activity factor,
1 ml/kcal water approximation and 20% food-water allowance form an **app planning
heuristic**. Their combination is not a validated individual fluid prescription.
A 30-year-old, 70 kg, 175 cm male profile gives a starting goal of 1,850 ml.

The estimate is independent of a calorie restriction/weight-loss target.
Exercise, heat, pregnancy, lactation, illness and prescribed fluid limits are
not modelled. The goal remains editable and can be disabled or overridden.

Sources and limits:

- [Mifflin et al., original resting-energy study](https://pubmed.ncbi.nlm.nih.gov/2305711/).
- [Institute of Medicine, Water chapter](https://www.nationalacademies.org/read/10925/chapter/6):
  total water includes drinks and food; a wide range of intakes can maintain hydration.
- [Comparison of weight- and energy-based water formulas](https://pubmed.ncbi.nlm.nih.gov/22275331/):
  formulas produce different estimates, so no exact need is claimed here.

## What creatine and protein powder contribute

Research does not provide a validated litres-per-gram extra-water equation for
either supplement. Creatine can increase body water, especially during initial
loading; reported short-term retention is not a recurring daily drinking-water
dose. The app does not convert that retained volume into a compulsory daily extra.

- [ISSN creatine position stand](https://pmc.ncbi.nlm.nih.gov/articles/PMC5469049/).
- [Creatine questions and misconceptions review](https://pmc.ncbi.nlm.nih.gov/articles/PMC7871530/).
- [Controlled dietary-protein and hydration study](https://pubmed.ncbi.nlm.nih.gov/16567155/):
  a small study of total dietary protein does not establish a powder-specific
  extra-litre rule.

### Protein powder specifically

Protein metabolism produces urea, whose renal excretion requires water. The
macronutrients section of the Institute of Medicine chapter explains this
physiology, but the theoretical excretion volume is not a universal extra-drink
prescription. Ordinary dietary protein already contributes to that load, and a
powder can either add to or replace another protein source.

In the small 2006 crossover study above, five men consumed different total-protein
diets. Fluid intake and fluid balance were unaffected, while some blood/urine
markers changed. The sample is too small to establish a general dose equation,
and it did not test a powder-specific extra-litre requirement. Consequently, the
app does not turn powder weight, protein grams or a target into a compulsory
extra-water dose. Optional personal extras remain adjustable and off by default.

The supplement card shows actual recorded protein grams separately from powder
weight, alongside carbohydrate and sugar grams. These use the meal's stored
nutrition snapshot; they are not another addition to the food diary's totals.

**Supplement-day extras default to zero.** A user who wants a personal extra plan
can enter its volume alongside the corresponding daily gram target. For each day:

    extra ml = chosen extra at target × min(actual known grams / target grams, 1)

For a 30 g powder target with a chosen 1,000 ml extra, recording 15 g activates
500 ml; recording no powder activates no powder extra. Each day's extra is capped
at the selected amount. This is configurable planning arithmetic, not a claim
about physiological water requirements or the volume used to mix a shake.

Linked meal/supplement records count once. Unknown scoop weights do not invent
grams or activate extra water. Deleting a recorded dose removes its contribution.

## Display and records

Base vessels have solid outlines. Active extras have dashed bottle outlines,
with protein powder and creatine labelled separately. The app uses the selected
serving size for the base; extra bottles represent 1 litre each, with partial fills.
The water widget uses an adaptive bottle scale to fit its available space.

Actual water fills the base first, then the planned powder and creatine sections.
Changing a target never changes the amount actually logged. Nothing is added to
water history merely because a supplement was recorded.

The plus button adds the chosen serving; custom ml/L amounts, deletion and undo
remain available. Water is stored in the private Room database. It is not exported
to Health Connect in this release.
