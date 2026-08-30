#!/usr/bin/env python3
"""Fail-closed validation for RUKUS's final merged Android manifest.

This consumes `apkanalyzer manifest print` output from the built APK, so it
validates the package users will install rather than only the source manifest.
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
APP_ID = "com.ruckus.agent"
ACCESSIBILITY_SERVICE = f"{APP_ID}.control.RuckusAccessibilityService"
MAIN_ACTIVITY = f"{APP_ID}.MainActivity"
SHIZUKU_PROVIDER = "rikka.shizuku.ShizukuProvider"


def attr(node: ET.Element, name: str) -> str | None:
    return node.get(ANDROID + name)


def canonical_component(name: str | None) -> str | None:
    if not name:
        return name
    if name.startswith("."):
        return APP_ID + name
    if "." not in name:
        return f"{APP_ID}.{name}"
    return name


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def component_by_name(application: ET.Element, tag: str, name: str) -> ET.Element:
    matches = [
        node
        for node in application.findall(tag)
        if canonical_component(attr(node, "name")) == name
    ]
    require(len(matches) == 1, f"expected exactly one {tag} {name}, found {len(matches)}")
    return matches[0]


def has_intent_action(node: ET.Element, action_name: str) -> bool:
    return any(
        attr(action, "name") == action_name
        for intent_filter in node.findall("intent-filter")
        for action in intent_filter.findall("action")
    )


def has_launcher_filter(node: ET.Element) -> bool:
    for intent_filter in node.findall("intent-filter"):
        actions = {attr(action, "name") for action in intent_filter.findall("action")}
        categories = {attr(cat, "name") for cat in intent_filter.findall("category")}
        if (
            "android.intent.action.MAIN" in actions
            and "android.intent.category.LAUNCHER" in categories
        ):
            return True
    return False


def validate_manifest(xml_text: str) -> None:
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as exc:
        raise ValueError(f"manifest XML is malformed: {exc}") from exc

    require(root.tag == "manifest", "root element must be <manifest>")
    package_name = root.get("package")
    if package_name is not None:
        require(package_name == APP_ID, f"manifest package must be {APP_ID}, got {package_name}")

    applications = root.findall("application")
    require(len(applications) == 1, f"expected exactly one application, found {len(applications)}")
    application = applications[0]

    service = component_by_name(application, "service", ACCESSIBILITY_SERVICE)
    require(attr(service, "exported") == "false", "accessibility service must be exported=false")
    require(
        attr(service, "permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "accessibility service must require BIND_ACCESSIBILITY_SERVICE",
    )
    require(
        has_intent_action(service, "android.accessibilityservice.AccessibilityService"),
        "accessibility service intent action missing",
    )
    metadata = [
        node
        for node in service.findall("meta-data")
        if attr(node, "name") == "android.accessibilityservice"
    ]
    require(len(metadata) == 1, "accessibility service metadata must appear exactly once")
    require(
        attr(metadata[0], "resource") == "@xml/ruckus_accessibility_service",
        "accessibility service metadata must reference @xml/ruckus_accessibility_service",
    )

    activity = component_by_name(application, "activity", MAIN_ACTIVITY)
    require(attr(activity, "exported") == "true", "MainActivity must be exported=true")
    require(has_launcher_filter(activity), "MainActivity MAIN/LAUNCHER intent filter missing")

    provider = component_by_name(application, "provider", SHIZUKU_PROVIDER)
    require(attr(provider, "exported") == "true", "ShizukuProvider must be exported=true")
    require(
        attr(provider, "authorities") == f"{APP_ID}.shizuku",
        "ShizukuProvider authority must be bound to the RUKUS applicationId",
    )
    require(
        attr(provider, "permission") == "android.permission.INTERACT_ACROSS_USERS_FULL",
        "ShizukuProvider must retain INTERACT_ACROSS_USERS_FULL protection",
    )

    exported_components: set[str] = set()
    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for node in application.findall(tag):
            if attr(node, "exported") == "true":
                name = canonical_component(attr(node, "name")) or "<unnamed>"
                exported_components.add(f"{tag}:{name}")

    expected_exports = {
        f"activity:{MAIN_ACTIVITY}",
        f"provider:{SHIZUKU_PROVIDER}",
    }
    require(
        exported_components == expected_exports,
        "unexpected exported component set: " + ", ".join(sorted(exported_components)),
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", help="XML file produced by `apkanalyzer manifest print`")
    args = parser.parse_args()
    try:
        with open(args.manifest, "r", encoding="utf-8") as handle:
            validate_manifest(handle.read())
    except (OSError, ValueError) as exc:
        print(f"APK RUNTIME MANIFEST: FAIL: {exc}", file=sys.stderr)
        return 1
    print("APK RUNTIME MANIFEST: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
