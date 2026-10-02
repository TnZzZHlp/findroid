# Signed release APK workflow

`Signed Release APKs` runs on every push to `main`, or manually from the Actions
page on `main`. It builds the phone and TV Libre Release variants with JDK 21
and the Android SDK versions declared in `buildSrc/src/main/kotlin/Versions.kt`.
It does not publish a GitHub Release or upload to Google Play.

## Repository secrets

Configure these under Settings > Secrets and variables > Actions in the repository:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | Base64 encoding of the existing `release-signing.p12` |
| `SIGNING_KEY_ALIAS` | Existing release key alias |
| `SIGNING_STORE_PASSWORD` | Existing keystore password |
| `SIGNING_KEY_PASSWORD` | Existing key password |
| `SIGNING_CERT_SHA256` | Expected release certificate SHA-256, 64 hex characters without colons |

Keep the keystore and `.env` ignored and backed up securely. Do not commit them,
print secret values, enable shell tracing, or replace the release key. The
workflow uses separate secret names from the existing tag-publishing workflow;
its `FINDROID_*` secrets are not changed.

To configure the secrets from the existing local signing files without printing
credentials, use Bash from the repository root after authenticating `gh`:

```bash
set -euo pipefail
set +x
for file in .env release-signing.p12; do
    test -f "$file"
    git check-ignore -q "$file"
done
set -a
source .env
set +a
: "${SIGNING_KEYSTORE_PATH:?}" "${SIGNING_KEY_ALIAS:?}"
: "${SIGNING_STORE_PASSWORD:?}" "${SIGNING_KEY_PASSWORD:?}"
test -f "$SIGNING_KEYSTORE_PATH"
repo="TnZzZHlp/findroid"
# GitHub secrets have a 48 KB limit; Base64 increases the keystore size.
test "$(base64 -w 0 "$SIGNING_KEYSTORE_PATH" | wc -c)" -le 49152
fingerprint=$(
    keytool -exportcert -keystore "$SIGNING_KEYSTORE_PATH" \
        -storepass:env SIGNING_STORE_PASSWORD -alias "$SIGNING_KEY_ALIAS" \
        | openssl x509 -inform DER -noout -fingerprint -sha256 \
        | cut -d= -f2 | tr -d ':' | tr '[:upper:]' '[:lower:]'
)
[[ "$fingerprint" =~ ^[a-f0-9]{64}$ ]]
base64 -w 0 "$SIGNING_KEYSTORE_PATH" | gh secret set SIGNING_KEYSTORE_BASE64 --repo "$repo"
printf '%s' "$SIGNING_KEY_ALIAS" | gh secret set SIGNING_KEY_ALIAS --repo "$repo"
printf '%s' "$SIGNING_STORE_PASSWORD" | gh secret set SIGNING_STORE_PASSWORD --repo "$repo"
printf '%s' "$SIGNING_KEY_PASSWORD" | gh secret set SIGNING_KEY_PASSWORD --repo "$repo"
printf '%s' "$fingerprint" | gh secret set SIGNING_CERT_SHA256 --repo "$repo"
unset SIGNING_KEY_ALIAS SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD fingerprint
```

## Outputs and verification

The workflow preserves unsigned APKs and writes `-signed.apk` files for
`arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86` in each app variant. Every APK is
verified with `apksigner verify --verbose --print-certs`, including a check
against `SIGNING_CERT_SHA256`. A missing APK, failed verification, or mismatched
certificate fails the job before artifact upload; no debug-key fallback is used.

Two artifacts, `phone-libre-release-signed-<commit>` and
`tv-libre-release-signed-<commit>`, contain the four signed APKs for their variant
and their public certificate verification reports. They are retained for 14 days
and are available on the workflow run's Actions page. Neither signing credentials
nor the keystore are uploaded. The temporary keystore is removed after signing,
including a failed signing step.

The workflow uses pinned action revisions, read-only repository permissions, and
no pull-request trigger. Only trusted changes should be merged into `main`:
anyone who can modify a secret-bearing workflow can potentially misuse its keys.
