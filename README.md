# Dynamic Island

Native Kotlin Android Dynamic Island overlay for Motorola G45 5G and other Android phones.

## Features
- Expandable AMOLED floating pill
- Notification previews (Notification Access required)
- Media controls for active notification-backed sessions
- Charging and battery status
- In-app music volume slider
- Incoming-call state indicator
- No analytics, ads, accounts, or Internet permission

## Build
Every push to main builds a debug APK using GitHub Actions. Open **Actions → Build Android APK → latest run → Artifacts → DynamicIsland-debug-apk** and download the ZIP. Extract it and install the APK.

## Setup
1. Install the APK and open Dynamic Island.
2. Allow Display over other apps.
3. Enable Dynamic Island Notification Access.
4. Grant phone permission for call-state indicators.
5. Tap Start Dynamic Island.

Android shows a persistent foreground-service notification while the overlay is active.

Notification text and media metadata are processed locally. The app does not request Internet access.
