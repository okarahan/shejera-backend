# CV fixtures — Step-by-step recognition tests

## Step 1: Box detection only

Put the source chart image here (replace if you want another chart):

```
src/test/resources/cv/fixtures/step1-source.png
```

(or `step1-source.jpg`)

A sample is already present so the test can run immediately.

Then run:

```bash
./gradlew test --tests com.shejera.importing.cv.steps.BoxDetectionStepTest
```

Artifacts (boxes-only image + JSON + validation) are written to:

```
build/cv-artifacts/step1/
  boxes-only.png
  boxes-overlay-on-source.png   # JSON rectangles drawn on the original image
  boxes.json
  boxes-validation.json         # automatic edge/color match checks
```

Step 1 finds person boxes only (positions/sizes). Watermark, connector lines,
and text are ignored for this step.

## Step 2: Arrow geometry only (no box linking)

Uses step 1 boxes **only** to erase cards on the source image.
Detects each arrow as **start** + **tip** (arrowhead). Matching arrows → boxes
is step 3.

```bash
./gradlew test --tests com.shejera.importing.cv.steps.ArrowDetectionStepTest
```

```
build/cv-artifacts/step2/
  arrows-only.png    # black arrows on white
  arrows-debug.png   # start (blue) → tip (green) + box outlines
  arrows.json
```

Example `arrows.json` entry:

```json
{
  "id": 1,
  "startX": 3100,
  "startY": 400,
  "tipX": 1680,
  "tipY": 480,
  "straightLength": 1420.5
}
```

Also: `arrows-overlay-on-source.png` — arrows on the original chart.

## Step 3: Link arrows → parent tree

For each non-root box: nearest arrow tip → child; nearest box to arrow start
(above the child) → `parentId`.

```bash
./gradlew test --tests com.shejera.importing.cv.steps.TreeLinkStepTest
```

```
build/cv-artifacts/step3/
  tree.json
  tree-overlay-on-source.png
```

Example node:

```json
{ "id": 1, "parentId": 0, "childIds": [3, 4], "arrowId": 1 }
```

## Step 4: Sex from left gender bar

Samples ~4px on the left edge of each box (blue → `M`, pink → `F`) and
enriches the step-3 tree.

```bash
./gradlew test --tests com.shejera.importing.cv.steps.SexEnrichmentStepTest
```

```
build/cv-artifacts/step4/
  tree-with-sex.json
  sex-overlay-on-source.png
```

```json
{ "id": 1, "parentId": 0, "childIds": [3, 4], "arrowId": 1, "sex": "F" }
```

## Step 5: First-line name OCR

Crops the top band of each box (skips gender bar), OCR as a single line,
drops `-` placeholders, stores `name` on each tree node.

```bash
./gradlew test --tests com.shejera.importing.cv.steps.NameOcrStepTest
```

```
build/cv-artifacts/step5/
  tree-with-names.json
  names-overlay-on-source.png
```

```json
{ "id": 1, "parentId": 0, "sex": "F", "name": "MERYEM KARAHAN" }
```

## Step 6: Relationship line (skip wrap)

Looks at card line 2 (relationship under the name). If it wraps to line 3,
that wrap is skipped. Text is optional / low priority for the tree.

```bash
./gradlew test --tests com.shejera.importing.cv.steps.RelationshipSkipStepTest
```

```
build/cv-artifacts/step6/
  tree-with-roles.json
  roles-overlay-on-source.png
```

## Step 7: DT / ÖT dates

Line format `DT:D.M.YYYY ÖT:DD.MM.YYYY` — day/month 1 or 2 digits; `-` = missing.
Stored as zero-padded `birthDate` / `deathDate` when possible.

```bash
./gradlew test --tests com.shejera.importing.cv.steps.DatesOcrStepTest
```

```
build/cv-artifacts/step7/
  tree-with-dates.json
  dates-overlay-on-source.png
```

## Step 8: Doğum Yeri (birth place)

Last line: `Doğum Yeri:X` — everything after the label is the place (no space
required after `:`). Multi-word places are allowed.

```bash
./gradlew test --tests com.shejera.importing.cv.steps.BirthPlaceOcrStepTest
```

```
build/cv-artifacts/step8/
  tree-with-places.json
  places-overlay-on-source.png
```

