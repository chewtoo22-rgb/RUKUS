# RUKUS Command Center UI

This branch is the first productization pass over the validated Executor V1 command engine.

## Design direction

RUKUS should combine the calm hierarchy of modern AI tools with restrained system-intelligence visuals. The interface is dark graphite, high-contrast, touch-first, and intentionally avoids generic neon sci-fi decoration.

## Home

- Live Device Pulse telemetry: battery, charging state, RAM, CPU load approximation, battery temperature, Android thermal status, storage usage.
- Conversational command composer with quick actions.
- Execution card exposing Understand -> Plan -> Execute -> Verify progression.
- Control Health for Accessibility and Shizuku.
- Resume and exact-action confirmation remain wired to Executor V1.

## Device

A dedicated system-health screen presents telemetry and a stylized device-link view. Sensor availability remains device/kernel dependent; unavailable data is displayed as unavailable rather than fabricated.

## History

Shows the currently persisted task checkpoint, status, progress, and final screen observation without changing TaskSessionStore semantics.

## Settings

The first settings shell exposes presentation toggles and direct Android control-permission entry points. The next pass should persist UI preferences and surface supported executor policies without allowing UI state to weaken safety policy.

## Safety boundary

This redesign does not change DeviceReadyExecutor command interpretation, admission, execution, recovery, confirmation, or completion-evidence behavior. Product UI remains a presentation/control layer around the validated executor.

## Next pass

1. Persist settings with DataStore.
2. Add first-run onboarding/tutorial.
3. Add richer conversation history and expandable execution evidence.
4. Add motion/haptic state language for listening, planning, executing, verifying, blocked, and complete.
5. Add MUTINY workspace entry once its integration boundary is ready.
6. Validate layout and telemetry on physical Samsung devices.
