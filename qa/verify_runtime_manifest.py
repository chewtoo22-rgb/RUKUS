#!/usr/bin/env python3
"""Fail-closed validation for RUKUS's final merged Android manifest."""
from __future__ import annotations
import argparse
import re
import sys
import xml.etree.ElementTree as ET
ANDROID = "{http://schemas.android.com/apk/res/android}"
APP_ID = "com.ruckus.agent"
ACCESSIBILITY_SERVICE = f"{APP_ID}.control.RuckusAccessibilityService"
MAIN_ACTIVITY = f"{APP_ID}.MainActivity"
SHIZUKU_PROVIDER = "rikka.shizuku.ShizukuProvider"
RESOURCE_ID = re.compile(r"^@(?:0x)?[0-9a-fA-F]+$")
ACCESSIBILITY_RESOURCE = re.compile(r"^@(?:(?:com\.ruckus\.agent):)?xml/ruckus_accessibility_service$")
def attr(node, name): return node.get(ANDROID + name)
def canonical_component(name):
    if not name: return name
    if name.startswith("."): return APP_ID + name
    if "." not in name: return f"{APP_ID}.{name}"
    return name
def require(condition, message):
    if not condition: raise ValueError(message)
def component_by_name(application, tag, name):
    matches=[node for node in application.findall(tag) if canonical_component(attr(node,"name"))==name]
    require(len(matches)==1,f"expected exactly one {tag} {name}, found {len(matches)}")
    return matches[0]
def has_intent_action(node, action_name):
    return any(attr(a,"name")==action_name for f in node.findall("intent-filter") for a in f.findall("action"))
def has_launcher_filter(node):
    for f in node.findall("intent-filter"):
        actions={attr(a,"name") for a in f.findall("action")}; categories={attr(c,"name") for c in f.findall("category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories: return True
    return False
def valid_accessibility_resource(value):
    if not value:
        return False
    return bool(ACCESSIBILITY_RESOURCE.fullmatch(value) or RESOURCE_ID.fullmatch(value))
def validate_manifest(xml_text):
    try: root=ET.fromstring(xml_text)
    except ET.ParseError as exc: raise ValueError(f"manifest XML is malformed: {exc}") from exc
    require(root.tag=="manifest","root element must be <manifest>")
    package_name=root.get("package")
    if package_name is not None: require(package_name==APP_ID,f"manifest package must be {APP_ID}, got {package_name}")
    apps=root.findall("application"); require(len(apps)==1,f"expected exactly one application, found {len(apps)}"); app=apps[0]
    service=component_by_name(app,"service",ACCESSIBILITY_SERVICE)
    require(attr(service,"exported")=="false","accessibility service must be exported=false")
    require(attr(service,"permission")=="android.permission.BIND_ACCESSIBILITY_SERVICE","accessibility service must require BIND_ACCESSIBILITY_SERVICE")
    require(has_intent_action(service,"android.accessibilityservice.AccessibilityService"),"accessibility service intent action missing")
    metadata=[n for n in service.findall("meta-data") if attr(n,"name")=="android.accessibilityservice"]
    require(len(metadata)==1,"accessibility service metadata must appear exactly once")
    resource=attr(metadata[0],"resource")
    require(valid_accessibility_resource(resource),f"accessibility service metadata must reference its packaged XML resource, got {resource!r}")
    activity=component_by_name(app,"activity",MAIN_ACTIVITY); require(attr(activity,"exported")=="true","MainActivity must be exported=true"); require(has_launcher_filter(activity),"MainActivity MAIN/LAUNCHER intent filter missing")
    provider=component_by_name(app,"provider",SHIZUKU_PROVIDER); require(attr(provider,"exported")=="true","ShizukuProvider must be exported=true"); require(attr(provider,"authorities")==f"{APP_ID}.shizuku","ShizukuProvider authority must be bound to the RUKUS applicationId"); require(attr(provider,"permission")=="android.permission.INTERACT_ACROSS_USERS_FULL","ShizukuProvider must retain INTERACT_ACROSS_USERS_FULL protection")
    exports=set()
    for tag in ("activity","activity-alias","service","receiver","provider"):
        for node in app.findall(tag):
            if attr(node,"exported")=="true": exports.add(f"{tag}:{canonical_component(attr(node,'name')) or '<unnamed>'}")
    expected={f"activity:{MAIN_ACTIVITY}",f"provider:{SHIZUKU_PROVIDER}"}; require(exports==expected,"unexpected exported component set: "+", ".join(sorted(exports)))
def main():
    p=argparse.ArgumentParser(); p.add_argument("manifest"); args=p.parse_args()
    try:
        with open(args.manifest,"r",encoding="utf-8") as h: validate_manifest(h.read())
    except (OSError,ValueError) as exc: print(f"APK RUNTIME MANIFEST: FAIL: {exc}",file=sys.stderr); return 1
    print("APK RUNTIME MANIFEST: PASS"); return 0
if __name__=="__main__": raise SystemExit(main())
