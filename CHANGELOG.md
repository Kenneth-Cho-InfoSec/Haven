0.1.5
===

- Updated the Android build to API 37, AGP 9.4.1, Gradle 9.8.2, and current AndroidX libraries.
- Replaced in-process local broadcasts with lifecycle-aware shared UI state and events.
- Hardened activity restart behavior so an incoming external intent is never replayed.
- Modernized activity results, package-manager flags, media thumbnails, and media scanning APIs.
- Added regression coverage for file-extension handling and sanitized activity restart intents.
- Added package visibility for Haven's cross-profile app-management workflow. `QUERY_ALL_PACKAGES` and `MANAGE_EXTERNAL_STORAGE` require distribution-policy review before Google Play publishing.
- Added a default-off system-picker option for user-selected files across profiles, plus managed app restrictions editing for supported manifest schema types.
- Deferred hiding Haven inside the Work Profile until provisioning completes, replaced the Android framework context menu with an anchored popup, and added provisioning diagnostics.
- Updated widget allowlisting for Android 17, report failed app-operation launches instead of dropping callbacks, and document release certificate verification.
- Corrected the Indonesian resource directory and removed blank Telugu overrides.

0.1.4
===

- Hardened cross-profile IPC against untrusted work-profile applications.
- Protected the exported cross-profile activity with a signature-level permission.
- Hardened authentication key initialization, action binding, timestamp validation, and signature comparison.

0.1.3
===

- Added Gradle release signing from the local ignored release keystore properties.
- Quieted the File Shuttle document provider unchecked AIDL collection warning.

1.9.1 (445)
===

- Hotfix crashes below Android 11.

1.9
===

- Updated targetSDK to 34 (Android 14) with compatibility fixes.
- More reliable delayed freezing using AlarmManager (thanks parmaster84).
- Support for cross-profile interactions allowlisting (e.g. for Gboard).
- Removed "Fake Camera" feature as it has not been supported since R.
- Version displayed within the app has now been changed to also reflect the exact Git commit when the app is built.
- File Shuttle no longer appends ".null" or ".bin" suffixes unnecessarily. This should make it work much better with file managers such as Material Files.
- File Shuttle now triggers media scanning much more robustly. Media files (pictures, videos, etc.) copied into the work profile should now show up much quicker in gallery apps.
- Added a fake NFC payment service to workaround a bug in Android that prevents payment apps inside the work profile from being used if none is present in the main profile.
- Fixed unintuitive colors of navigation icons under dark mode.

1.8
===

- Updated targetSDK to 33 (Android 13) with compatibility fixes.
- UI style revamp with Material You support on Android 12+.

1.7
===

- Revamped the initial setup process to include a full setup guide for better clarity and less confusion.
- Upgraded targetSDK to 31 (Android 12) with compatibility fixes.
- Upgraded dependencies.
- Translation updates thanks to our wonderful community.

1.6
===

- Start of in-repo changelogs
- Add support for Android 11 (c147377, 3852bd, and more) (__Note__: For now, File Shuttle is not available in the version on Google Play due to policy reasons, as they will not be allowing apps with All Files permission before 2021.)
- Haven can no longer be installed to external storage (removable SD cards) (9a6777 by Camilio Alejo)
- Allow more browsable intents to be passed across work / main profile boundary (43444b by Camilio Alejo)
- A new shortcut to Documents UI is available in the three-dot menu of Haven, because on Pixels the Google Files app may not be able to open File Shuttle correctly (a121ee)
- You can now choose to block or allow cross-profile contact access via a settings option (749ad1)
- Thanks to translators participating in our Weblate instance (https://weblate.typeblog.net), Haven is now available in more languages. You can now contribute translations easier than ever by using the Weblate interface.
