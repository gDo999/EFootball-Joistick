# MVP Status

## Implemented
- [x] Kotlin + Jetpack Compose app shell
- [x] One-click Start flow
- [x] Accessibility Service
- [x] Overlay foreground service
- [x] Automatic Android InputDevice controller detection
- [x] DualSense / DUALSHOCK 4 / Xbox / Generic classification
- [x] Android 14+ controller-to-touch capture for all controller families
- [x] Android 11–13 native fallback for officially supported controller families
- [x] Android 14+ joystick capture through Accessibility MotionEvent sources
- [x] Generic gamepad key capture
- [x] Deterministic 1:1 action mapping
- [x] Multi-touch gesture compositor
- [x] Resolution normalization
- [x] Local screenshot-based CV auto calibration
- [x] Per-resolution calibration persistence
- [x] Automatic calibration retry while HUD appears
- [x] HUD-gated capture: controller remains pass-through in menus and is only consumed after Classic match HUD detection
- [x] Periodic HUD verification releases controller capture when the match HUD disappears
- [x] eFootball foreground detection
- [x] App-private error log + latest error in UI
- [x] No manual profile/mapping editor
- [x] No root / no game injection / no game-file edits / no macros / no bots

## Device validation still required
- [ ] Android Studio Gradle sync with Android SDK 36
- [ ] Physical Android 14/15/16 device test
- [ ] Generic Bluetooth gamepad test
- [ ] Generic USB gamepad test
- [ ] DualSense Android 14+ touch-mapper test / Android 11–13 native fallback test
- [ ] DUALSHOCK 4 Android 14+ touch-mapper test / Android 11–13 native fallback test
- [ ] Xbox Android 14+ touch-mapper test / Android 11–13 native fallback test
- [ ] eFootball Classic HUD calibration on several aspect ratios
- [ ] Confirm coordinate seed against the user's exact current in-game Classic layout
- [ ] Add a dedicated Touch & Flick detector/profile if that control style is required; current CV classifies Classic vs Unknown

## Next engineering step after device test
If CV confidence is weak on a real current eFootball HUD, collect a few screenshots from the target devices and replace the contrast-only detector with a stronger local template/feature detector or optional bundled ML model. Keep it fully on-device and deterministic.
