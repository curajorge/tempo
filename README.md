# Tempo — zone trainer

An Android zone-training prototype powered by WHOOP's Bluetooth Low Energy
heart-rate broadcast. It reads the standard BLE Heart Rate Service (`0x180D`)
and Heart Rate Measurement characteristic (`0x2A37`).

Tempo is an independent app. Not affiliated with or endorsed by WHOOP.
WHOOP references describe device compatibility and the manual AI copy/paste workflow,
not an official partnership. Branding and public-release wording have not received
legal clearance or WHOOP approval.

The app has two live visual modes sharing one workout engine:

- **Drive** — calibrated zone dial, live marker, target band, and effort cues.
- **Pulse** — 60-second live HR trace with a shaded target range.

It includes a 90-second test workout, a 20-minute Zone 2 workout, a 30-minute
progressive workout, editable zone BPM ranges, spoken phase changes, and live
in-zone, average, and peak metrics.

## Test flow

1. In the WHOOP app, open Device Settings and enable **Heart Rate Broadcast**.
2. Open **Tempo** and grant the Nearby devices permission.
3. Tap **Sensor** to search.
4. Tap the WHOOP device in the results and wait for the BPM value.
5. Choose a workout, adjust **Zones** if needed, then tap **Start workout**.

The prototype works while its screen is open and keeps the display awake during
training. Background tracking and workout history are not implemented yet.
Leaving the screen or losing the live signal pauses the workout.
Sensor selection is explicit; only the last successfully streaming sensor
is remembered for reconnect on launch. A received HR packet is required before
the app reports a live connection. Search, connection, discovery, subscription,
and missing-signal failures have separate status messages.
Use **Search again** to release a stuck connection and scan afresh.
Initial zone settings are examples; **Zones** saves your explicit BPM ranges.

## Workout library and forms

Tap **Workouts** in the header to open the library. Saved plans appear above
the included workouts. Open a saved plan to use it, edit its name/notes and
stage names/durations/zones, duplicate it, or delete it with confirmation.
Included plans can be duplicated but not overwritten or deleted.
Library changes are persisted before the screen reports success.
The editor validates the same constraints as JSON imports.

AI prompt, import, preview, library, editing, and zones use the same custom
dark form sheet with labeled inputs, scrollable content, and full-width actions.
The keyboard resizes the form; imported text and prompt drafts remain local.

The 0.6 Android run passes 32 checks including actual disk writes, editing,
duplication, targeted deletion, stale-record protection, imports, and navigation.
Persistence checks use dedicated isolated_check_ preferences, not the user library.

## Stage navigation

Use **Previous**, **Next**, or **Restart** below the current stage.
Tap any stage in the list to jump directly to it. Each navigation starts that
stage's timer at its full duration and preserves session metrics. A running
session continues; a paused session stays paused. Before starting, choose any
stage and then press Start. After completion, selecting a stage reopens the
session paused so you can resume it. Reset still clears the entire session.
Skipping the last stage uses a Finish confirmation.

## Create workouts with WHOOP AI

Open the workout picker and select **WHOOP AI**, or scroll to **Create with WHOOP AI**.
Enter your goal and copy the generated prompt into WHOOP AI.
Return to **Import WHOOP AI response**, paste its JSON or complete JSON code block,
review every stage with its actual BPM range, and choose **Save workout**.
The imported workout is selected immediately and remains in the local library
after restarting the app. Incomplete import text is kept as a local draft.
Clipboard access happens only when tapping Copy or Paste.

The version-1 format is:

```json
{
  "format": "tempo-workout",
  "version": 1,
  "name": "Aerobic base",
  "notes": "Keep the effort steady.",
  "stages": [
    {"name": "Warm-up", "seconds": 300, "zone": 1},
    {"name": "Steady", "seconds": 1200, "zone": 2},
    {"name": "Cool-down", "seconds": 300, "zone": 1}
  ]
}
```

Only fixed-duration stages are supported. Repeats must be expanded.
Validation allows 1–100 stages, whole seconds 1–21600 per stage,
zones 1–5, and at most 24 hours overall. Invalid input stays editable.
Exact duplicate imports are rejected. Notes are displayed during review.
The library supports up to 100 imports.

**Zones** now accepts explicit, ascending, nonoverlapping BPM ranges copied
from WHOOP. The same persisted ranges drive the prompt, import preview,
workout coaching, and gauge. Imported JSON never changes the user's zones.

## Verification

The 0.3 build was tested on the connected OnePlus with the user's WHOOP MG:
discovery, subscription, and successive real BPM readings were confirmed in
the app log. The Drive layout was inspected on the actual phone.
Eight standalone Java packet tests cover unsigned 8/16-bit values and malformed
packets (tests/HeartRatePacketTest.java).

The 0.4 app and Android test APK compile successfully. Fifteen local JSON checks
pass, covering raw/fenced JSON, persistence serialization, malformed input,
invalid zones/durations, and unsupported versions. The Android runner additionally
checks generated prompts and persistence in the test APK's own sandbox:

```sh
adb shell am instrument -w com.jcm.whoopheartratepoc.test/com.jcm.whoopheartratepoc.ImportChecks
```

The 0.4 Android runner and screen checks require a connected phone.

## Local build

The project targets Android API 35 and uses package
`com.jcm.whoopheartratepoc`, keeping it isolated from other installed apps.
