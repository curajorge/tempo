# Contributing

Small, focused pull requests are welcome. Discuss major behavior changes first.
See the README for the JDK/SDK setup and build commands.

Before submitting:

1. Run `./gradlew assembleDebug assembleDebugAndroidTest lintDebug`.
2. Run `./gradlew connectedDebugAndroidTest` with a device or emulator attached.
3. For BLE or coaching changes, also test a real sensor, disconnect/reconnect,
   manual pause, screen-off behavior, and muted speech.
4. Use synthetic workouts and sensor addresses in tests. Never commit signing
   keys, `.env` files, local SDK paths, APKs, phone dumps, or personal health data.

Preserve the existing Android application ID so upgrades retain local data.
Keep tests isolated from real user preferences. State what was and was not tested
in your pull request. Contributions are under the project's MIT license;
third-party assets must retain their own required notices.
