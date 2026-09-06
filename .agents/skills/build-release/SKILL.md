---
name: build-release
description: Build and sign Findroid phone and Android TV release APKs. Use for release builds, signed APK requests, or local packaging.
---

# Build signed Findroid releases

Run commands from the repository root. Read `AGENTS.md` first.

## Prerequisites and safety

- JDK 21 and Android SDK matching the current Gradle configuration (see `AGENTS.md`).
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` points to the SDK.
- Reuse the root `release-signing.p12` and `.env`. The latter contains `SIGNING_KEYSTORE_PATH`, `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASSWORD`, and `SIGNING_KEY_PASSWORD`.
- Never print credentials, enable shell tracing, commit either signing file, substitute a debug key, or replace an existing release key.
- If signing material is missing, stop and ask the user. Generate a new key only with explicit permission; a different certificate cannot normally update existing installations.
- Preserve unsigned APKs. Signed outputs must end in `-signed.apk`.

## Build

```bash
./gradlew :app:phone:assembleLibreRelease :app:tv:assembleLibreRelease --console=plain
```

Stop if Gradle fails. Inspect its error before signing anything.

## Sign and verify

Use an installed Android SDK `apksigner`. Run in Bash without `set -x`:

```bash
set -euo pipefail
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
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$sdk"
signer=$(find "$sdk/build-tools" -name apksigner -type f | sort -V | tail -1)
test -x "$signer"
for variant in phone tv; do
    for apk in app/$variant/build/outputs/apk/libre/release/*-unsigned.apk; do
        test -f "$apk"
        signed="${apk%-unsigned.apk}-signed.apk"
        "$signer" sign \
            --ks "$SIGNING_KEYSTORE_PATH" \
            --ks-key-alias "$SIGNING_KEY_ALIAS" \
            --ks-pass env:SIGNING_STORE_PASSWORD \
            --key-pass env:SIGNING_KEY_PASSWORD \
            --out "$signed" "$apk"
        "$signer" verify --verbose --print-certs "$signed" \
            > "${signed%.apk}-verification.txt"
    done
done
unset SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD
```

Confirm all four expected ABIs exist for each variant and all eight certificates match. This check handles differing signer labels across Build Tools versions:

```bash
python3 - <<'PY'
from pathlib import Path
abis = ('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86')
fingerprints = set()
for variant in ('phone', 'tv'):
    directory = Path(f'app/{variant}/build/outputs/apk/libre/release')
    for abi in abis:
        apk = directory / f'{variant}-libre-{abi}-release-signed.apk'
        assert apk.is_file(), apk
        report = apk.with_name(apk.stem + '-verification.txt').read_text()
        assert report.startswith('Verifies'), apk
        hashes = {
            line.split('certificate SHA-256 digest: ', 1)[1]
            for line in report.splitlines()
            if 'certificate SHA-256 digest: ' in line
        }
        assert len(hashes) == 1, apk
        fingerprints.update(hashes)
assert len(fingerprints) == 1, 'Release certificates differ'
print('All 8 APKs verified. Certificate SHA-256:', next(iter(fingerprints)))
PY
```

## Deliver

Report successful build and signature verification, the two output directories, and recommend the `arm64-v8a` APK for modern ARM devices. Mention that `.env` and `release-signing.p12` must be backed up securely for future updates. Do not claim device testing unless performed.
