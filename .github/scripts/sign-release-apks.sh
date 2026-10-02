#!/usr/bin/env bash
set -euo pipefail
set +x

: "${SIGNING_KEYSTORE_PATH:?Missing signing keystore path}"
: "${SIGNING_KEY_ALIAS:?Missing signing key alias}"
: "${SIGNING_STORE_PASSWORD:?Missing signing store password}"
: "${SIGNING_KEY_PASSWORD:?Missing signing key password}"
: "${SIGNING_CERT_SHA256:?Missing expected signing certificate fingerprint}"

signer="${1:?Usage: bash .github/scripts/sign-release-apks.sh /path/to/apksigner}"
test -x "$signer"
test -f "$SIGNING_KEYSTORE_PATH"
expected_fingerprint="${SIGNING_CERT_SHA256,,}"
if [[ ! "$expected_fingerprint" =~ ^[a-f0-9]{64}$ ]]; then
    echo "Expected certificate SHA-256 must be 64 hexadecimal characters" >&2
    exit 1
fi

for variant in phone tv; do
    for abi in arm64-v8a armeabi-v7a x86_64 x86; do
        apk="app/$variant/build/outputs/apk/libre/release/$variant-libre-$abi-release-unsigned.apk"
        if [[ ! -s "$apk" ]]; then
            echo "Missing or empty unsigned APK: $apk" >&2
            exit 1
        fi
        signed="${apk%-unsigned.apk}-signed.apk"
        report="${signed%.apk}-verification.txt"
        "$signer" sign \
            --ks "$SIGNING_KEYSTORE_PATH" \
            --ks-key-alias "$SIGNING_KEY_ALIAS" \
            --ks-pass env:SIGNING_STORE_PASSWORD \
            --key-pass env:SIGNING_KEY_PASSWORD \
            --out "$signed" "$apk"
        "$signer" verify --verbose --print-certs "$signed" > "$report"
        mapfile -t fingerprints < <(
            awk '/certificate SHA-256 digest:/ { print tolower($NF) }' "$report"
        )
        if [[ ${#fingerprints[@]} -ne 1 || "${fingerprints[0]:-}" != "$expected_fingerprint" ]]; then
            echo "Release certificate mismatch: $signed" >&2
            exit 1
        fi
        echo "Verified: $signed"
    done
done

echo "All 8 release APKs use the expected signing certificate."
