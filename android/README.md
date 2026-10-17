# Tally Android widget

This Android companion adds a Home Screen widget for the current month's spend. It opens the Tally web app and keeps the widget total on this phone. It does not sync data with other devices.

## Move existing data from the PWA

1. In the existing Tally app, open **Settings → Your data → Download full backup**.
2. Install and open the Android companion, then tap **Import a backup from your current Tally** and choose the downloaded JSON file.
3. Add the widget from the Android launcher: long press the Home Screen, choose **Widgets**, then select **Tally**.
4. Add or edit spends in the Android companion app. The widget refreshes when Tally saves changes.

The PWA and Android companion keep separate local copies. Changes made in the PWA after importing won't appear in the widget; export and reimport a backup to transfer later changes.

## Download the APK

After the Android build finishes, download the **Tally-Android-Widget** artifact from the latest **Build Android widget app** run in the repository's Actions tab. The artifact is available for 30 days.

## Build locally

Install JDK 17, Android SDK platform 35, and Gradle 8.9, then run `gradle -p android assembleDebug --no-daemon`.

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
