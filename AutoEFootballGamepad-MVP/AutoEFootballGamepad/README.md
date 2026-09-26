# Auto eFootball Gamepad — MVP

Android Studio project written in Kotlin + Jetpack Compose.

## What this MVP does

- Detects Android game controllers automatically through `InputDevice` / `InputManager`.
- Identifies common controller families: DualSense, DUALSHOCK 4, Xbox, and Generic gamepads.
- One-button **Start eFootball** flow.
- First-run setup only for:
  - Accessibility Service
  - Draw over other apps (small status overlay)
- Does **not** request Bluetooth runtime permission because it does not scan/pair/manage Bluetooth devices; Android already exposes connected controllers as input devices.
- Detects when `jp.konami.pesam` (eFootball) is foreground.
- On Android 14+ for DualSense / DUALSHOCK 4 / Xbox / Generic controllers:
  - Captures joystick motion and gamepad keys in the Accessibility Service.
  - Converts them to deterministic 1:1 touch gestures, giving Bluetooth and USB controllers the same app path.
  - Uses a bundled adaptive Classic-control preset as the initial seed.
  - Takes a local Accessibility screenshot and runs lightweight computer-vision calibration around expected control regions.
  - Saves calibration per display resolution and reuses it as the next visual seed.
- Multi-touch compositor supports movement plus action-button presses at the same time.
- Simple error log stored in app-private storage.
- No bot logic, combos, macros, gameplay automation, injection, game-file modification, or root.

## Important reality check

As of the current eFootball mobile Controls Manual, KONAMI already lists **DualSense, DUALSHOCK 4, and Xbox Wireless Controller** as supported Bluetooth controllers. Controller use is limited by KONAMI to certain match types and is not supported for menu navigation. Wired behavior can also vary by device OS.

Because the goal of this project is a consistent Bluetooth/USB compatibility layer, **Android 14+ uses the same deterministic touch-mapper path for all detected controller families**. On Android 11–13, known controller families fall back to eFootball's native path because the required global joystick MotionEvent capture API is unavailable.

## Android version

### Android 14+ (API 34+)
The full controller-to-touch MVP path is implemented for all detected controller families. API 34 added AccessibilityService generic `MotionEvent` observation with selectable motion sources, which makes global joystick-axis capture practical without root.

### Android 11–13
The app still detects controllers and can launch eFootball. The touch-mapper is disabled because the same robust global joystick-axis interception API is not available. Input is passed through untouched: DualSense / DUALSHOCK 4 / Xbox can use eFootball's documented native controller support, while Generic controllers are allowed to try the native path but compatibility is not guaranteed by KONAMI. The project does **not** require ADB in its current MVP.

## Input mapping used by the Android 14+ touch mapper

The Android 14+ touch mapper targets the stable four-button Classic layout by position:

| Controller input | Attack | Defence / context | Touch position |
|---|---|---|---|
| Left stick / D-pad | Movement | Movement | Left virtual stick |
| A / Cross | Pass | Switch player | Bottom-left action button |
| B / Circle | Dash | Dash | Bottom-right action button |
| X / Square | Shoot | Tackle-style action | Top-right action button |
| Y / Triangle | Through pass | Pressure-style action | Top-left action button |
| R1 / R2 | Dash | Dash | Bottom-right action button |
| L1 / L2 | Context alias | Switch player | Bottom-left action button |

The game can change action labels between attacking and defending; the fallback maps by **physical screen position**. The CV calibrator refines those physical positions.

## Auto Calibration

`EfootballScreenDetector` is an on-device CV MVP. It:

1. Starts from normalized landscape priors.
2. Searches a local region around the left stick and four action buttons.
3. Scores control-like regions using local luminance/edge contrast.
4. Accepts calibration only when enough physical controls are found.
5. Preserves aliases (`DASH = FACE_EAST`, `SWITCH_PLAYER = FACE_SOUTH`).
6. Saves successful calibration for the current resolution and uses it as a future seed.
7. Keeps verifying the HUD periodically; if the match HUD disappears, controller capture is released automatically until the HUD returns.

It does not upload screenshots and does not require MediaProjection.

### Current limitation

The CV detector is designed around the **Classic** four-button HUD. If KONAMI substantially redesigns the HUD, if the user moves buttons very far from the normal regions, or if a different control style is used, confidence can be low. The service retries automatically while the match HUD is appearing. There is deliberately no manual mapping editor in this MVP.

## Project structure

- `MainActivity.kt` — one-click Compose UI and first-run setup
- `service/EfootballAccessibilityService.kt` — game detection, controller capture policy, calibration orchestration
- `service/OverlayService.kt` — small non-interactive status overlay
- `controller/AutoControllerDetector.kt` — controller discovery and family classification
- `controller/GamepadInputManager.kt` — key/axis normalization
- `mapping/AutomaticMappingEngine.kt` — high-level mapping state
- `mapping/MultiTouchGestureEngine.kt` — concurrent virtual fingers via Accessibility gestures
- `mapping/ResolutionAdapter.kt` — normalized-to-pixel conversion
- `mapping/EfootballPresets.kt` — bundled adaptive Classic seed
- `detection/EfootballScreenDetector.kt` — lightweight local CV
- `calibration/AutoCalibrationManager.kt` — screenshot, confidence, persistence, retries
- `util/ErrorLogger.kt` — app-private log

## Build

Recommended:

- Android Studio with Android SDK 36 installed
- JDK 17 or newer for Android Studio's Gradle JDK
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 30`
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.3.21
- Compose BOM 2026.09.00
- kotlinx.coroutines 1.11.0

Open the project folder in Android Studio, allow Gradle sync, install SDK 36 if prompted, then run on a physical Android device.

## Build verification status

The project was source-reviewed and its XML/resource files were validated in the generation environment. A full Android build was **not** executed there because that environment does not contain an Android SDK and does not have direct network access to download the Gradle distribution/dependencies. Therefore, the first Android Studio sync/build on a real development machine is still required before calling the APK device-tested.

## Permissions / services

The manifest requests only:

- `SYSTEM_ALERT_WINDOW` — for the small status overlay.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — to keep the explicit user-started overlay service alive.
- Accessibility Service binding is granted through Android Settings by the user; it is not a normal runtime permission.

No Bluetooth permission is requested by the MVP because pairing/scanning is outside the app.

## Safety / fair-play scope

This project is intentionally limited to deterministic 1:1 physical-controller-to-touch translation. It has no macro engine, recording/playback, combo scripting, auto-aim, auto-pass, bot, or autonomous gameplay.

KONAMI's eFootball Terms prohibit attempts to gain unfair advantage through cheats, automation software, bots, hacks, mods, or unauthorized third-party software. This project is unofficial; whether a controller mapper is permitted in a particular online mode is ultimately determined by KONAMI. Test conservatively, especially before using it in competitive online play.
