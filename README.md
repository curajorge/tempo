# Tempo — zone trainer

An experimental Android zone trainer using Bluetooth Low Energy
heart-rate broadcasts. It reads the standard BLE Heart Rate Service (`0x180D`)
and Heart Rate Measurement characteristic (`0x2A37`).

Tempo is an independent app. Not affiliated with or endorsed by WHOOP.
WHOOP references describe device compatibility and the manual AI copy/paste workflow,
not an official partnership. Branding and public-release wording have not received
legal clearance or WHOOP approval.

The app has two live visual modes sharing one workout engine:

- **Drive** — calibrated zone dial, live marker, target band, and effort cues.
- **Pulse** — 60-second live HR trace with a shaded target range.

The app uses a dark Classic theme. **Mute audio / Unmute audio** is available near the top of the
training screen. Master mute stops current speech and suppresses all subsequent
Tempo speech, including connection alerts, without pausing the workout or changing
the phone's volume. **Settings** saves master mute, stage announcements, zone
guidance, and 30/45/60-second reminder spacing independently. Muting overrides
the individual announcement settings, including during screen-off sessions.

It includes a 90-second test workout, a 20-minute Zone 2 workout, a 30-minute
progressive workout, editable zone BPM ranges, spoken phase changes, and live
in-zone, average, and peak metrics.

## Test flow

1. In the WHOOP app, open Device Settings and enable **Heart Rate Broadcast**.
2. Open **Tempo** and grant the Nearby devices permission.
3. Tap **Sensor** to search.
4. Tap the WHOOP device in the results and wait for the BPM value.
5. Choose a workout, adjust **Zones** if needed, then tap **Start workout**.

Active sessions run in a `connectedDevice` foreground service, with a training
notification and a session-scoped partial wake lock. Locking the screen or
leaving the Activity does not pause the workout. Notification actions provide
Pause/Resume, Next, and Stop & save; enable notifications to use these controls.
Stage announcements and optional zone cues use Android text-to-speech. Zone cues
require 8 seconds of sustained deviation beyond a 2 BPM tolerance and have a
configurable 30/45/60-second cooldown. Android TTS and output volume must be configured.

The timer pauses after a disconnect or 6 seconds without a valid HR reading.
Reconnection uses the last selected sensor with bounded retry backoff. A dropout
of up to 30 seconds can resume automatically; longer drops and restored sessions
require explicit Resume. Manual pauses remain paused. Missing time is not replayed.
Sessions checkpoint every 5 seconds and on explicit controls. After process death
or reboot, reopen Tempo to restore the checkpoint paused (up to 5 seconds of
recent progress may be lost). Force-stop does not automatically restart tracking.

History is private on-device storage: completed/stopped sessions, weighted average
and peak HR, time in target, time per zone, and planned versus actual stage time.
Stopping saves partial progress. Deleting a history entry requires confirmation
and does not delete its workout template. No account, cloud sync or export yet.
Phone battery-management policies and audio routing still require real-device
testing; a foreground service is not a guarantee against user/system termination.
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

The Android test runner checks actual disk writes, editing, duplication, targeted
deletion, stale-record protection, imports, deterministic session timing, signal
loss, recovery, and idempotent history saves. Test storage uses isolated preferences.
Persistence checks use dedicated isolated_check_ preferences, not the user library.

## Stage navigation

Use **Previous**, **Next**, or **Restart** below the current stage.
Tap any stage in the list to jump directly to it. Each navigation starts that
stage's timer at its full duration and preserves session metrics. A running
session continues; a paused session stays paused. Before starting, choose any
stage and then press Start. After completion, selecting a stage prepares a new
session; the completed session remains in History. Reset during a workout asks
to stop and save partial progress rather than silently discarding it.
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

BLE discovery, subscription, and live BPM have been tested with a WHOOP MG
and an Android phone. Other standard BLE Heart Rate monitors may work but have
not been verified. ANT+-only sensors and Apple Watch are not supported.
Eight standalone Java packet tests cover unsigned 8/16-bit values and malformed
packets (tests/HeartRatePacketTest.java).

The 0.9.0 Android runner passed 51 checks on a physical phone before this
publication cleanup. These cover imports, isolated persistence, session timing,
recovery, history, and audio preferences. This is not an end-to-end guarantee of
screen-off audio or Bluetooth reliability across phones. Run the device suite with:

```sh
./gradlew connectedDebugAndroidTest
```

The Android runner requires a connected phone or emulator; BLE testing requires
a physical phone and broadcasting sensor.

## Local build

The project targets Android API 35 and uses package
`com.jcm.whoopheartratepoc`, keeping it isolated from other installed apps.

Requirements: JDK 17, Android SDK Platform 35, and Android SDK Platform Tools.
Use Android Studio to select your SDK or set `ANDROID_HOME` to your SDK directory.
Do not commit `local.properties`, which contains machine-specific SDK paths.

```sh
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 8.0 (API 26) or later is required. Initial builds download Gradle and
Android build dependencies. Debug APKs are for development, not signed releases.

## Privacy, security, and contributing

This is an experimental fitness tool, not a medical device. Review suggested
workouts and set appropriate zones yourself; sensor readings can be inaccurate.
See [Privacy](PRIVACY.md), [Security](SECURITY.md), and
[Contributing](CONTRIBUTING.md). Do not include health records, sensor addresses,
credentials, or unredacted phone logs in issues or pull requests.

## License

Current project code and original artwork are source-available under the
[PolyForm Noncommercial License 1.0.0](LICENSE). Personal and other qualifying
noncommercial use, modification, and redistribution are allowed; commercial use
requires a [separate license](COMMERCIAL_LICENSE.md) from the copyright holder.
This is not an OSI-approved open-source license.

Commit `35af5fe` and earlier were published under MIT. Rights already received
under that license are not withdrawn by this change.

Bundled Geist fonts have their own SIL Open Font License; see
[third-party notices](THIRD_PARTY_NOTICES.md). Trademark rights are not granted.
