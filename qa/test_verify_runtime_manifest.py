#!/usr/bin/env python3
import unittest

from verify_runtime_manifest import validate_manifest

BASE = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.ruckus.agent">
  <uses-permission android:name="android.permission.WRITE_SETTINGS" />
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

PROFILE_RECEIVER = '''<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:permission="android.permission.DUMP" android:enabled="true" android:exported="true">
  <intent-filter><action android:name="androidx.profileinstaller.action.INSTALL_PROFILE" /></intent-filter>
  <intent-filter><action android:name="androidx.profileinstaller.action.SKIP_FILE" /></intent-filter>
  <intent-filter><action android:name="androidx.profileinstaller.action.SAVE_PROFILE" /></intent-filter>
  <intent-filter><action android:name="androidx.profileinstaller.action.BENCHMARK_OPERATION" /></intent-filter>
</receiver>'''


class RuntimeManifestContractTest(unittest.TestCase):
    def assertRejected(self, xml: str, fragment: str):
        with self.assertRaisesRegex(ValueError, fragment):
            validate_manifest(xml)

    def with_profile_receiver(self, xml: str = BASE):
        return xml.replace('</application>', PROFILE_RECEIVER + '</application>')

    def test_valid_manifest_passes(self):
        validate_manifest(BASE)

    def test_unexpected_requested_permission_fails_closed(self):
        extra = '<uses-permission android:name="android.permission.INTERNET" />'
        self.assertRejected(
            BASE.replace('<application>', extra + '<application>'),
            "unexpected requested permission set",
        )

    def test_required_write_settings_permission_cannot_disappear(self):
        self.assertRejected(
            BASE.replace('<uses-permission android:name="android.permission.WRITE_SETTINGS" />', ''),
            "unexpected requested permission set",
        )

    def test_legacy_sdk23_permission_alias_is_still_part_of_allowlist(self):
        manifest = BASE.replace(
            '<uses-permission android:name="android.permission.WRITE_SETTINGS" />',
            '<uses-permission-sdk-23 android:name="android.permission.WRITE_SETTINGS" />',
        )
        validate_manifest(manifest)

    def test_packaged_qualified_accessibility_resource_passes(self):
        validate_manifest(BASE.replace(
            '@xml/ruckus_accessibility_service',
            '@com.ruckus.agent:xml/ruckus_accessibility_service',
        ))

    def test_compiled_accessibility_resource_id_passes(self):
        validate_manifest(BASE.replace('@xml/ruckus_accessibility_service', '@0x7f120001'))

    def test_apkanalyzer_ref_resource_id_passes(self):
        validate_manifest(BASE.replace('@xml/ruckus_accessibility_service', '@ref/0x7f0b0000'))

    def test_protected_profile_installer_receiver_passes(self):
        validate_manifest(self.with_profile_receiver())

    def test_profile_installer_receiver_must_keep_dump_permission(self):
        manifest = self.with_profile_receiver().replace(
            'android:permission="android.permission.DUMP"',
            'android:permission="android.permission.INTERNET"',
        )
        self.assertRejected(manifest, "must remain protected by android.permission.DUMP")

    def test_profile_installer_receiver_actions_cannot_drift(self):
        manifest = self.with_profile_receiver().replace(
            '<intent-filter><action android:name="androidx.profileinstaller.action.BENCHMARK_OPERATION" /></intent-filter>',
            '',
        )
        self.assertRejected(manifest, "intent actions changed unexpectedly")

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

    def test_compose_preview_activity_must_not_ship_exported(self):
        extra = '<activity android:name="androidx.compose.ui.tooling.PreviewActivity" android:exported="true" />'
        self.assertRejected(BASE.replace('</application>', extra + '</application>'), "unexpected exported component set")

    def test_duplicate_privileged_service_fails_closed(self):
        service = '''<service android:name="com.ruckus.agent.control.RuckusAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="false" />'''
        self.assertRejected(BASE.replace('</application>', service + '</application>'), "expected exactly one service")


if __name__ == "__main__":
    unittest.main()
