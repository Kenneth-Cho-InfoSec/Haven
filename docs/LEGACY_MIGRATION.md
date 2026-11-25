# Moving a 0.1.x work profile to Haven 1.0

Haven 1.0 uses a new application ID (`com.arcturus.haven`). Installing it does not
update `io.github.kennethchoinfosec.haven` 0.1.x, and the existing work profile stays
owned by the old package. Without a migration path, users have to delete the work
profile, losing every app and its data, before they can use isolation in 1.0.

The `legacy-bridge` module builds a small 0.1.6 APK under the old application ID. It
updates the existing 0.1.x installation in place and hands the work profile over to
`com.arcturus.haven` with `DevicePolicyManager.transferOwnership()` (API 28+).

## How it works

1. The user installs `com.arcturus.haven` in the personal profile and does **not**
   set up isolation in it.
2. The user updates the old Haven to the 0.1.6 bridge. The update applies to the copy
   in the personal profile and to the profile-owner copy inside the work profile.
   The device admin receiver keeps its 0.1.x class name, so the bridge stays the
   profile owner.
3. Opening the bridge in the personal profile checks that the new app is installed and
   opens the bridge inside the work profile (`CrossProfileApps.startMainActivity`).
4. Inside the work profile, the bridge:
   - adds `com.arcturus.haven` to the profile with `installExistingPackage()` if needed,
   - clears the 0.1.x cross-profile intent filters,
   - calls `transferOwnership()` to `com.arcturus.haven/.receivers.HavenDeviceAdminReceiver`,
   - starts the new app once inside the profile.
5. `com.arcturus.haven` declares `<support-transfer-ownership/>`. In
   `onTransferOwnershipComplete()`, and again when it is started inside the profile,
   it applies the same policies as a freshly provisioned profile and hides its
   launcher entry.
6. In the personal profile, `MainActivity` checks `Utility.isWorkProfileAvailable()`
   before starting the setup wizard, so the transferred profile is picked up instead of
   provisioning a new one.

Apps, app data, frozen state and user restrictions in the profile are kept. Settings,
freeze lists and launcher shortcuts from 0.1.x are not carried over. After the move,
the user uninstalls the old Haven from the personal profile and from the Work tab.

## Releasing the bridge

The bridge must be signed with the same key as the published 0.1.5 APK (certificate
SHA-256 `3f041588698bcd386a9f9bc0ab06ef0cf6b32f226e1cb53510a48bbb3f6cfef5`). Android
refuses the in-place update otherwise.

Create `release/legacy-keystore.properties` (ignored by Git):

```properties
storeFile=haven-legacy-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then build and verify:

```sh
./gradlew :legacy-bridge:assembleRelease
scripts/print-apk-certificate.sh legacy-bridge/build/outputs/apk/release/legacy-bridge-release.apk
```

If 0.1.x was also distributed through an F-Droid repository, publish the bridge there
under the same application ID so those users receive it as a normal update.

## Testing

1. Install the 0.1.5 release APK and set up a work profile with a few apps.
2. Install a `com.arcturus.haven` build that contains `<support-transfer-ownership/>`.
3. Install the bridge over 0.1.5 (`adb install -r`), open it and follow the prompts.
4. Check that `adb shell dumpsys device_policy` lists `com.arcturus.haven` as the
   profile owner, the work apps still have their data, and the new Haven in the
   personal profile opens its app list without starting setup.
