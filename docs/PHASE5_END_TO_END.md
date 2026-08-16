# Phase 5 — End-to-End Proof

## Goal

Prove the MUTINY -> build backend -> APK -> RUKUS smoke-test chain without pretending desktop Gradle can safely run inside the RUKUS APK process.

## Build runtime decision

RUKUS uses a build-backend abstraction. The preferred phone-local backend is an AndroidIDE/Termux-style worker because it can provide JDK, Gradle, Android SDK tools, and an Android-compatible aapt2 binary. The RUKUS app remains the control plane and does not expose arbitrary model-supplied shell execution.

The repository also includes a CI proof lane using JDK 17, Android SDK 35, Build Tools 34.0.0, Gradle 8.9, and the current AGP 8.7.x project configuration.

## RUKUS smoke test

A successful MUTINY build creates a handoff object. RUKUS converts that handoff into a smoke-test plan:

1. install APK through an approved installer path
2. launch package
3. capture initial accessibility snapshot
4. detect immediate launch/crash failure
5. report observations back to MUTINY

## Security boundaries

- no arbitrary model-provided shell text
- fixed build tasks only
- materialized files remain path-confined
- worker availability is explicit
- unavailable worker paths fail closed

## Actions proof trigger

This documentation touch intentionally retriggers the Phase 5 push workflow so the current head is assembled by GitHub Actions.
