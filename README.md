# AirCursor Complete 1.0

Android camera-based hand cursor prototype with a foreground camera service, system overlay, MediaPipe hand tracking, and Accessibility gestures.

## Intended features
- Camera-based single-hand tracking using MediaPipe's 21 landmarks.
- Floating cursor overlay above other apps.
- Index finger points the cursor.
- Thumb-index pinch and release triggers a tap.
- Hold pinch to begin drag; move while pinched; release to end drag.
- Two-finger vertical movement attempts scroll gestures.
- User-adjustable sensitivity, smoothing, and inference rate.
- On-device processing; frames are not sent to a server.

## Device tuning
Default profile is conservative for mid-range Android devices:
- 480x360 camera analysis target (CameraX may negotiate another supported size).
- One tracked hand.
- 15 inference FPS default; configurable from 8 to 24.
- KEEP_ONLY_LATEST frame strategy.
- Smoothed cursor motion.
The actual speed, heat, and battery use must be measured on the target phone. These settings do not guarantee a specific FPS.

## GitHub build
1. Create a repository named `AirCursor`.
2. Upload the contents of this project folder to the repository root.
3. Push to `main` or run **Build AirCursor Complete APK** from Actions.
4. Download artifact **AirCursor-Complete-APKs**.
5. Debug APK is `app-debug.apk`. Release APK is unsigned and cannot be installed as-is until signed.

## First launch
1. Install the debug APK.
2. Open AirCursor and grant camera permission.
3. Grant "Display over other apps".
4. Enable AirCursor under Android Settings > Accessibility > Installed/Downloaded apps.
5. Return to AirCursor and press Start.
6. Keep the persistent notification visible while tracking is active.
7. Press Stop to close the camera and remove the cursor.

## Important Android limitations
- Camera foreground services are subject to Android's while-in-use restrictions. Start tracking from the visible app, not automatically at boot.
- Android/OEM battery management may stop the service. Exempt AirCursor from battery optimization if the OS offers that setting.
- Accessibility gesture injection requires explicit user enablement and may be restricted by some apps or secure screens.
- The overlay is intentionally not touchable so it does not block normal taps.
- Scroll gesture recognition is heuristic and needs device testing/tuning.
- A production release should be signed with a private keystore and tested across Android versions.
- This is a substantial prototype, not a certified Apple Vision Pro-equivalent product.

## Privacy
Camera frames are analyzed locally by MediaPipe. This project contains no analytics or upload endpoint. Camera access is active only while tracking is running.
