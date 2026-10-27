# Release signing

Haven's GitHub `v0.1.4` release notes disclose that its APK was signed with a local
debug keystore for testing. That key is not included in this repository, and a
debug signing key is not a reliable update identity for future releases.

The SHA-256 certificate digest of the published `v0.1.4` APK is
`269d7b79cfb9760d4bf9e404f377c01877cf02ec29feeba3f7fb3d1a5c90aa20`.
This identifies that APK's Android Debug certificate; it does not establish a
stable production signing identity for later releases.

Version `0.1.5` is signed with the new Haven release key. Its certificate SHA-256
is `3f041588698bcd386a9f9bc0ab06ef0cf6b32f226e1cb53510a48bbb3f6cfef5`.
This key does not match the published `v0.1.4` APK. Users upgrading from `v0.1.4`
must uninstall it before installing `v0.1.5`; Android will reject an in-place
update signed by a different certificate. Future releases must keep using this
new key to preserve updates from `v0.1.5` onward.

The private release keystore is stored outside Git at `release/haven-release.jks`.
Keep it backed up securely and configure `release/keystore.properties` with:

```properties
storeFile=haven-release.jks
storePassword=...
keyAlias=haven
keyPassword=...
```

Place the keystore at `release/haven-release.jks`. Both the directory and the
properties file are ignored by Git. Do not put credentials in CI logs or commit
them. A release build now fails at packaging time if the signing properties are
missing; debug builds do not need the release key.

Before publishing, verify the APK and record the certificate fingerprint in the
GitHub release description:

```sh
apksigner verify --print-certs app-release.apk
# or
scripts/print-apk-certificate.sh app-release.apk
```

Publish the full SHA-256 certificate digest and state which APK it belongs to.
Users can run the same command on a downloaded APK and compare the digest. Keep
the keystore backed up securely; losing it prevents in-place updates. The
published `v0.1.4` APK uses a different certificate from `v0.1.5`, so users on
`v0.1.4` must uninstall before installing `v0.1.5`. Future releases must continue
using the new private key to allow in-place updates from `v0.1.5` onward.
