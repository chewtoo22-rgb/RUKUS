# RUCKUS

**You point. I make it happen.**

RUCKUS is an Android-native AI device agent + app/game builder.

## Phase 0 in this scaffold

- Kotlin / Jetpack Compose shell
- AccessibilityService with gesture support
- Device action contract
- Safety classification layer
- Brightness and media-volume control
- App launching / global Back / Home
- Shizuku dependency + provider scaffold
- App/game project specification and builder planning contract
- RUCKUS personality contract

## Next implementation gates

1. Shizuku permission lifecycle and bounded privileged-action adapter
2. Accessibility tree reader and semantic selector engine
3. Text input and scroll/find/click-by-label actions
4. Structured LLM tool protocol
5. Local action journal + undo where possible
6. App Forge worker: create/edit/build Kotlin + Compose projects
7. Game Shop worker: first 2D game template + controller/input layer
8. APK install/launch/test loop
9. Logcat/crash analyzer and repair loop
10. Voice front end

## Safety architecture

RUCKUS does not hand unrestricted shell access to the model. Model output maps into typed actions. Privileged actions require explicit policy approval and are implemented as bounded commands.
