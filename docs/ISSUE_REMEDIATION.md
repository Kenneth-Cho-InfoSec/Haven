# Open issue remediation

This records the implementation state of the 16 open GitHub issues reviewed on
2026-10-27. It pairs each issue with the source files changed and the remaining
evidence needed before calling a device-specific report resolved.

## Product decisions

- **#3 — Internet blocking:** do not add a built-in VPN firewall. Recommend
  closing the feature request as not planned unless it is narrowed to a
  specific, well-bounded use case.
- **#5 — Shizuku:** do not add generic Shizuku support. Recommend closing the
  request unless a concrete Haven operation that needs Shizuku is proposed.
- **#9 — Device Owner plus Profile Owner:** keep Haven's current personal-device
  managed-profile flow as Profile Owner-only. Do not add a combined owner mode.
  Reconsider company-owned work-profile enrollment only as a separate product
  mode with its own provisioning path.

## Issue-by-issue status

### #1 — Provisioning on customized Chinese ROMs

**Implemented:** `SetupWizardActivity` now collects the Android build, managed
user support, provisioning handler, provisioning-allowed state, device-policy
role holder, and optional SetupWizard partner-provider status. The failed setup
screen can copy these details. This gives ROM reports concrete compatibility
data without pretending Haven can replace missing system provisioning services.

**Still needed:** a repro and copied report from the affected ROM before adding
any vendor-specific workaround.

### #3 — Revoke internet access or use an allowlist

**Decision: do not ship a Haven firewall.** A correct work-profile internet
blocker would need to own a `VpnService`, handle traffic and recovery, and define
what happens when lockdown or another VPN is already in use. Android allows
only one active VPN service per user or profile; a failed or competing VPN can
interrupt work-profile connectivity. That cost and failure mode are too large
for a broad toggle in this app. Recommend closing the request as not planned.
Android references: [VPN guide](https://developer.android.com/develop/connectivity/vpn),
[DevicePolicyManager always-on VPN](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setAlwaysOnVpnPackage(android.content.ComponentName,java.lang.String,boolean)).

### #4 — Android 9 crash opening app actions

**Implemented:** `AppListFragment` now opens the app actions with an anchored
`PopupMenu` instead of the framework long-press context-menu dialog that appears
in the reported stack trace. Existing app operations remain in the same handler.

**Still needed:** confirm the menu on an Android 9 device; the available API 37
emulator did not complete boot in the previous build session.

### #5 — Shizuku support

**Decision: do not add generic Shizuku support.** Haven's current job is creating
and managing a work profile through Android's DPC APIs. “Support Shizuku apps”
does not identify a Haven operation to implement, and arbitrary app support
would require a separate integration and security model for each operation.
Recommend closing the request unless a specific task and its security boundary
are proposed.

### #6 and #15 — Managed app / enterprise policies

**Implemented:** `IHavenService.aidl` and `HavenService` expose the work-profile
DPC application-restrictions API. `AppListFragment` reads the target app's
declared restrictions and edits booleans, choices, multi-select values,
integers, and strings. The editor preserves existing keys while saving.
Restrictions are shown only for installed, visible work-profile apps.

Nested bundle and bundle-array restrictions are reported as unsupported by the
editor. Apps without an `android.content.APP_RESTRICTIONS` schema cannot be
edited here. Android reference:
[DevicePolicyManager application restrictions](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setApplicationRestrictions(android.content.ComponentName,java.lang.String,android.os.Bundle)).

### #7 — Possible profile escape

**Current safeguards retained:** `AndroidManifest.xml` declares the exported
cross-profile activity with a signature-level permission, and
`AuthenticationUtility` binds HMAC signatures to the action, timestamp, and
single-use nonce. Bootstrap key acceptance still relies on the activity
permission protecting the forwarded request.

**Still needed:** a security test on supported Android versions must confirm how
that permission is enforced by the system's cross-profile intent forwarder. The
report did not include a proof of concept, so the issue should not be marked
resolved solely from source inspection.

### #9 — Device Owner and Profile Owner together

**Decision: keep Haven's current Profile Owner-only BYOD flow.** Do not add
Device Owner enrollment or a simultaneous Device Owner/Profile Owner mode.
Android's company-owned work-profile mode uses a separate enrollment path and
Profile Owner controls with selected device-wide policies; it is a different
product mode from adding Device Owner access to Haven's existing personal
work-profile setup. Revisit only if Haven adopts company-owned devices as a
product use case. Android references:
[work profiles on company-owned devices](https://developers.google.com/android/work/requirements/work-profile-corporate),
[DevicePolicyManager owner APIs](https://developer.android.com/reference/android/app/admin/DevicePolicyManager),
[Android device-management modes](https://source.android.com/docs/devices/admin).

### #11 — Clone freezes and uninstall failures

**Implemented:** `HavenService` now returns a cancelled result when the activity
proxy needed to start PackageInstaller is unavailable or disconnects, instead
of leaving the caller without a completion callback. `AppListFragment` reports
that cancellation to the user.

**Still needed:** the attached log contains only a framework
`Performing pause of activity that is not resumed` exception for `MainActivity`;
it does not include PackageInstaller status or Haven's install/uninstall call
path. Reproduce on the reported Motorola/Android 15 build with a complete
logcat before attributing that exception to the reported freeze.

### #13 — LineageOS 23.2 with microG provisioning

**Implemented:** the failed-setup diagnostic copy action reports the configured
role holder and whether its package is installed. The attached report's
ManagedProvisioning log says its configured role holder is missing and offline
provisioning is disabled; it also reports a missing SetupWizard partner
provider. Those are system provisioning dependencies, not a microG-specific
failure that Haven can repair in its app code.

**Still needed:** retry with the diagnostic copy output and a current provisioning
log after the ROM's role-holder configuration is corrected or changed.

### #14 — Setup hangs after Haven disables itself

**Implemented:** `Utility.enforceWorkProfilePolicies` completes filters and
enables the profile before `DummyActivity` schedules launcher hiding five
seconds after finalization. `hideMainActivityInManagedProfile` uses
`DONT_KILL_APP`. This moves the package-component change out of the critical
provisioning sequence described in the report.

**Still needed:** verify on HyperOS 4 / API 37; the issue reporter has already
reported a successful manual device verification of this fix.

### #16 — HyperOS cloned apps missing from Haven

**Implemented earlier:** `AndroidManifest.xml` declares `QUERY_ALL_PACKAGES`,
and `HavenService` enumerates installed applications using package-manager
flags. Haven's app list is limited to the Android user/profile queried by each
service; it cannot promise visibility into a vendor's separate dual-app space.

**Still needed:** determine whether the missing apps are installed in Haven's
managed profile or in Xiaomi's separate clone space, then capture package-list
diagnostics from that device. No vendor-specific filter change is justified by
the screenshots alone.

### #17 — Signing certificate fingerprint

**Implemented:** `docs/RELEASING.md` records the SHA-256 signer digest measured
from the published `v0.1.4` APK and explains that it is an Android Debug
certificate. `scripts/print-apk-certificate.sh` prints the signer for any APK.
`app/build.gradle` now rejects a release packaging task without configured
release signing properties.

**Still needed:** configure and retain the private signing key used for future
releases. Verify it matches the published `v0.1.4` certificate before promising
seamless updates; otherwise users must uninstall before switching signers.

### #18 — Better file selection between profiles

**Implemented:** `SettingsFragment`, `SettingsManager`, `LocalStorageManager`,
and `Utility` add a default-off setting for Android's `ACTION_PICK_IMAGES`,
`ACTION_GET_CONTENT`, and `ACTION_OPEN_DOCUMENT` across both profile directions.
The user still chooses each file in a system picker. This is separate from
File Shuttle's DocumentsProvider. Based on
[Shelter PR #321](https://github.com/PeterCxy/Shelter/pull/321).

### #19 — Work-profile app widgets

**Implemented earlier:** the work-app menu already allowlists widget providers.
`HavenService` now uses API 37's set-based widget-provider API while retaining
the incremental API for older Android versions. The allowlist makes profile
widgets available to the parent profile's widget host.

### #20 — Connected app experiences

**Implemented earlier:** the work-app menu allowlists packages through
`DevicePolicyManager.setCrossProfilePackages`; its label now says “Allow
Connected App Requests.” This makes an app eligible to request user consent for
cross-profile communication. It does not merge app data or silently authorize
all cross-profile interactions. Android reference:
[cross-profile package policy](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setCrossProfilePackages(android.content.ComponentName,java.util.Set)).

## Remaining validation

The source changes are ready for a local debug build. The following items still
depend on a device, signing key, or security review:

- Android 9 popup menu and clone/uninstall flows.
- HyperOS app enumeration and provisioning finalization.
- LineageOS provisioning role-holder behavior.
- Android cross-profile activity permission enforcement for issue #7.
- A complete Motorola logcat for issue #11.
- Stable release signing-key continuity for issue #17.
- Device-level widget, picker, and connected-app consent behavior.
- A scoped Shizuku use case and a VPN prototype decision.
