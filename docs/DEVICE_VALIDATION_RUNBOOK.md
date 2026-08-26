# RUKUS Device Validation Runbook

Purpose: validate Executor V1 on a real Android device without weakening the fail-closed safety model.

## Preconditions

- Build from a commit with green `RUKUS Android` CI.
- Record APK SHA-256 and source commit.
- Confirm Accessibility Service and any Shizuku/Rish dependency state before testing privileged actions.
- Start with non-destructive commands only.
- Keep a second control path available to stop the app/service if behavior diverges.

## Evidence to capture

For each case record: source commit, device model, Android version, RUKUS build, goal text, parsed typed actions, confirmation requirement, pre-action observation, post-action observation, completion evidence, result, and any logs/screenshots needed to reproduce a failure.

## Gate A: admission and parsing

1. Bounded benign goal produces the expected typed action list.
2. Unsupported request fails closed with zero executable actions.
3. Overlong input fails atomically with rejection evidence.
4. Malformed/control-character input fails closed.
5. No rejected plan exposes a confirmation path or stale executable actions.

## Gate B: observation integrity

1. Foreground package identity is captured correctly.
2. Focused editor and actionable nodes remain observable on a large accessibility tree.
3. Sensitive text/content descriptions are redacted from stored observation evidence.
4. Empty/ambiguous foreground-package evidence cannot satisfy completion.
5. Screen changes unrelated to the requested command do not count as success.

## Gate C: deterministic execution

Run individually and verify exact completion evidence for:

- Home
- Back
- click-by-text on an unambiguous visible target
- type text into a focused non-sensitive editor
- brightness adjustment within supported bounds
- media-volume adjustment within supported bounds

For each action, intentionally create at least one mismatch and confirm RUKUS refuses to mark the task complete.

## Gate D: privileged boundary

Only after A-C pass:

1. Verify the exact approved privileged command is reflected in completion evidence.
2. Confirm acknowledgement for a different command cannot satisfy the active request.
3. Remove/interrupt the privileged backend and verify the request fails closed.
4. Restore the backend and verify recovery requires fresh evidence rather than stale completion state.

## Gate E: interruption and resume

Exercise interruption at these points:

- before confirmation
- after confirmation but before execution
- during a multi-action plan
- after an action but before final observation
- after process/app restart

Resume only when the stored checkpoint is valid, bounded, and consistent with the current observation. Malformed, stale, or out-of-range checkpoints must fail closed.

## Gate F: stale-evidence rejection

1. Complete a task successfully.
2. Change foreground app/state.
3. Attempt to reuse the earlier completion evidence.
4. Require RUKUS to obtain fresh observation and verification before presenting `VERIFIED COMPLETE` again.

## Stop conditions

Stop testing and mark the build blocked if any of the following occurs:

- rejected goal produces an executable action
- sensitive observation data is retained unredacted
- completion is asserted without exact post-action evidence
- privileged action runs without the required confirmation/authorization boundary
- stale evidence is accepted after state changes
- the app loops actions after interruption/recovery
- the only way to pass a case is to weaken a safety assertion

## Exit criteria

Executor V1 is device-validation green only when every applicable gate above passes on the exact tested APK. CI success is necessary but not a substitute for device evidence.
