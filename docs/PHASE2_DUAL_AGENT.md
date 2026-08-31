# Phase 2 — Dual Agent Shell

## RUKUS
Phone-control agent. Owns screen reading, semantic clicking, text entry, gestures, app launching, device controls, and bounded privileged actions.

## MUTINY
App/game builder agent. Owns project planning, Android app generation, 2D/3D game workflows, build analysis, APK production, and handing finished builds to RUKUS for install/test.

## Shared architecture
Both identities use the same core runtime and safety system. Identity changes presentation, specialized tools, workspace state, and behavior—not the trusted execution boundary.

## Added in this phase
- Bottom navigation between RUKUS and MUTINY workspaces
- MUTINY persona and dedicated builder screen
- Shared AgentKernel capability routing
- ReadScreen and ClickText semantic device actions
- Working TypeText adapter through Accessibility
- Safety classification for new semantic actions

## Next gate
1. Structured command parser/tool protocol
2. RUKUS command console and action journal
3. MUTINY persistent project workspace
4. Sandboxed project writer/build worker
5. First generated Compose APK
6. RUKUS install/launch/test handoff
