# Phase 4 — Builder Execution

Phase 4 turns MUTINY's manifests into a bounded build pipeline.

## Added

- BuildJob state model
- ProjectMaterializer with canonical-path confinement
- fixed Gradle `:app:assembleDebug` execution only
- bounded build log capture
- APK success detection
- RUKUS handoff contract
- BuildCoordinator
- fuller Android project generation: root Gradle, app Gradle, manifest, styles, activity

## Safety boundary

MUTINY does not execute model-provided shell text. BuildExecutor invokes only a fixed Gradle task against the project workspace.

## Current hard dependency

A usable Gradle wrapper/runtime still has to be provisioned into the materialized workspace before a phone-local build can succeed. If `gradlew` is absent, the build fails explicitly rather than falling back to arbitrary commands.

## Next gate

Provision a trusted build runtime, execute the first real debug APK build, then hand that APK to RUKUS for install/launch/smoke-test.
