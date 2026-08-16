# Phase 3 — Intelligence Loop

Phase 3 turns the dual-agent shell into an executable workflow.

## RUKUS

- Natural-language command protocol
- Intent router
- Typed-action execution through DeviceController
- Initial commands: read screen, click visible text, type text, Back, Home, brightness, media volume
- Unsupported requests fail closed instead of inventing shell commands

## MUTINY

- Persistent ProjectRecord model
- ProjectStore backed by app-private SharedPreferences
- App / 2D game / 3D game project records survive app restarts
- ManifestGenerator creates concrete file manifests and pipeline tasks
- Project Vault surfaces recent projects in the MUTINY workspace

## Shared handoff

MUTINY pipeline terminates in `handoff_to_rukus`. The next gate will materialize the generated manifest into a sandboxed project workspace, build the debug APK in a controlled worker, then give the resulting APK to RUKUS for install/launch/test.

## Safety rule

The language layer never emits arbitrary shell text. Natural-language requests resolve to known typed actions or remain unexecuted.
