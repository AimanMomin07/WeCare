# WeCare

WeCare is a Jetpack Compose Android appointment booking demo with Firebase Authentication and Firebase Realtime Database connectivity status.

## Build

Open this project in Android Studio or run:

```powershell
.\gradlew.bat app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Firebase setup

The app expects `app/google-services.json` for the Firebase project. Enable **Email/Password** under Firebase Console → Authentication → Sign-in method to use registration and sign-in.

The home screen displays Realtime Database connection status. Appointment entries and profile phone/date-of-birth fields are currently stored locally on the device; they are not synchronized to Realtime Database.
