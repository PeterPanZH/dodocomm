# Security Policy

## Supported versions

Security fixes are shipped in the latest GitHub Release. Please update through the
in-app updater or from the Releases page.

## Reporting a vulnerability

Please use **GitHub Private Vulnerability Reporting / Security Advisories** on
this repository. Do not open a public issue for a vulnerability that could put
users at risk. Include the affected version, reproduction steps and impact.

## Security model

- **No secrets in source**: the app has no API keys, tokens or server credentials.
  The only persisted secrets are the user's own Bark server URL/Device Key in app
  private SharedPreferences, which never leave the device except as requests to
  the Bark server configured by the user.
- **No analytics, ads or third-party telemetry**. The only optional outbound
  request besides Bark is the update manifest check, which fetches
  `latest.json` from GitHub Releases.
- **Signing material is never committed**: `keystore/`, `keystore.properties`,
  `local.properties`, build outputs and verification evidence are gitignored.
  GitHub Actions signs releases using encrypted repository secrets; the current
  release certificate is `CN=DodoComm, O=DodoComm Contributors`.
- **Update integrity**: in-app updates only accept a higher `versionCode`, verify
  the SHA-256 and file size from `latest.json`, verify the APK signing certificate
  matches the running app, and then hand the APK to the system installer. There is
  no silent installation and no self-modifying code.
- **SMS privacy**: the app only registers for new-SMS broadcasts. It does not
  request access to existing SMS or contacts. Messages are stored locally (max
  500 records) and sent only to the Bark endpoint entered by the user.
- **Least privilege**: permissions are limited to receiving SMS, notifications,
  the optional phone state (for dual-SIM labels) and installing an update after an
  explicit user action. No accessibility, overlay, audio or device-admin tricks.

## Release integrity

Releases are built by GitHub Actions from this repository and published with:
- `DodoComm-<version>.apk`
- `SHA256SUMS.txt`
- `latest.json` (in-app updater manifest)

Verify a download with `sha256sum -c SHA256SUMS.txt` and the signer with
`apksigner verify --print-certs DodoComm-<version>.apk`.
