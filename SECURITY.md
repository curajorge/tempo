# Security

Tempo is an experimental, pre-1.0 Android app. Security fixes target the latest
`main` branch; there is no supported production release or response-time promise.

## Reporting a vulnerability

Use GitHub's private **Report a vulnerability** option in this repository's
Security tab. Do not post exploitable details, credentials, health data, or
sensor identifiers in public issues. If private reporting is unavailable,
open an issue requesting a private contact without disclosing sensitive details.

Include the affected commit/version, Android version, impact, and a minimal
reproduction using synthetic data. Never include signing keys or full device logs.

## Security boundaries

- Only the launcher activity is exported; the workout service is internal.
- Notification actions use immutable, explicit pending intents.
- Workout imports are data, not executable code, and have size/range limits.
- Data remains in app-private preferences; backup exclusions cover preferences.
- BLE sensor broadcasts are not authenticated by Tempo. Do not treat a reading
  or device name as proof of identity, and do not use Tempo for medical decisions.

Publication checks include history/current-file credential pattern searches,
review of permissions and data handling, and Android build/lint checks. They are
not a professional security audit and cannot guarantee that all issues are absent.

Before distributing a release, review dependencies, test background behavior on
target devices, and use a release build signed with a privately stored key.
