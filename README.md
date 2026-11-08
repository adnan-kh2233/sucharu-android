# Sucharu Android App

Native Android customer app for the Sucharu home-services platform. It shares the Firebase-backed API used by the customer website, but this Android app has its own repository and Git history.

## Build

Open this folder in Android Studio, let Gradle sync, then build the debug app:

```powershell
./gradlew.bat assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk` and is excluded from Git.

## Firebase API

The backend source and setup guide are maintained with the website project. Configure the Gradle property `sucharuApiBaseUrl` for the environment where the app will run. For local development on a USB-connected phone, run `adb reverse tcp:5000 tcp:5000` and use `http://127.0.0.1:5000/api`.

The app uses Firebase Authentication tokens with the shared API and stores profile details, saved service address, and bookings in the shared Firebase backend. Do not commit local signing keys, service-account credentials, or machine-specific SDK settings.
