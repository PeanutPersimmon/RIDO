# RIDO

RIDO is an Android app for planning events and tasks, recording activities and sleep, and reviewing time through Today, Plan, and Statistics. It includes categories, tags, reminders, a sleep home-screen widget, voice actions, and local backup and restore.

The project uses Kotlin, Jetpack Compose, Room, DataStore, Glance, WorkManager, and Gradle. It is under active development; this repository contains source code and tests rather than a published release.

## Local build

Install JDK 21 and the Android SDK, then provide the SDK path through your environment or an untracked `local.properties`. Run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Do not commit `local.properties`, signing material, credentials, user data, or build outputs.
