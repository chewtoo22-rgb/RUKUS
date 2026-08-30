#!/usr/bin/env python3
import unittest

from verify_runtime_manifest import validate_manifest

BASE = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.ruckus.agent">
  <application>
    <provider android:name="rikka.shizuku.ShizukuProvider" android:authorities="com.ruckus.agent.shizuku" android:exported="true" android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
    <service android:name="com.ruckus.agent.control.RuckusAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="false">
      <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>
      <meta-data android:name="android.accessibilityservice" android:resource="@xml/ruckus_accessibility_service" />
    </service>
    <activity android:name="com.ruckus.agent.MainActivity" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
    </activity>
  </application>
</manifest>'''


class RuntimeManifestContractTest(unittest.TestCase):
    def assertRejected(self, xml: str, fragment: str):
        with self.assertRaisesRegex(ValueError, fragment):
            validate_manifest(xml)

    def test_valid_manifest_passes(self):
        validate_manifest(BASE)

    def test_accessibility_service_cannot_be_exported(self):
        self.assertRejected(BASE.replace('android:exported="false"', 'android:exported="true"', 1), "accessibility service must be exported=false")

    def test_accessibility_service_must_keep_bind_permission(self):
        self.assertRejected(BASE.replace('android.permission.BIND_ACCESSIBILITY_SERVICE', 'android.permission.INTERNET'), "must require BIND_ACCESSIBILITY_SERVICE")

    def test_accessibility_metadata_cannot_drift(self):
        self.assertRejected(BASE.replace('@xml/ruckus_accessibility_service', '@xml/other'), "must reference")

    def test_launcher_must_be_exported(self):
        self.assertRejected(BASE.replace('android:name="com.ruckus.agent.MainActivity" android:exported="true"', 'android:name="com.ruckus.agent.MainActivity" android:exported="false"'), "MainActivity must be exported=true")

    def test_shizuku_authority_is_application_scoped(self):
        self.assertRejected(BASE.replace('com.ruckus.agent.shizuku', 'com.example.shizuku'), "authority")

    def test_unexpected_exported_component_fails_closed(self):
        extra = '<receiver android:name="com.ruckus.agent.LeakyReceiver" android:exported="true" />'
        self.assertRejected(BASE.replace('</application>', extra + '</application>'), "unexpected exported component set")

    def test_duplicate_privileged_service_fails_closed(self):
        service = '''<service android:name="com.ruckus.agent.control.RuckusAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="false" />'''
        self.assertRejected(BASE.replace('</application>', service + '</application>'), "expected exactly one service")


if __name__ == "__main__":
    unittest.main()
