# Privacy

Tempo has no account, analytics SDK, advertising SDK, cloud sync, or direct
network permission. It reads heart-rate broadcasts from a sensor you select.

Stored locally in Android app-private preferences:

- Last sensor Bluetooth address for reconnecting.
- Zone ranges, workout templates, prompt/import drafts, and audio settings.
- Active-session checkpoints and session history, including timestamps,
  sensor address, and heart-rate/zone summary metrics.

These preferences are not separately encrypted by Tempo. Device security matters;
a rooted device, an authorized debugger, or a compromised phone may expose them.
Development APKs are debuggable and should not be treated as production releases.
Automatic backup and device-transfer exclusions are configured for app preferences.

Clipboard access occurs when you tap Copy or Paste. Copying a prompt exposes its
contents to the system clipboard; pasting it into WHOOP AI or another service
shares it with that service under its policies. Tempo does not directly connect
to an AI service. Android text-to-speech is supplied by your selected engine;
its processing and network behavior depend on your device and engine settings.
Use an offline voice if you want speech processing to remain on-device.

History entries and custom workouts can be deleted individually. Clear app storage
in Android Settings to remove all local Tempo data. Clipboard contents and data
you shared with another app must be cleared there separately.

Bluetooth broadcasting is not proof of sensor identity or medical accuracy.
Choose your own sensor and avoid sharing screenshots or diagnostics containing
health data or device identifiers. Spoken coaching can be heard by people nearby.
