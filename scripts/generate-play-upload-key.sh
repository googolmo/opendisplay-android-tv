#!/usr/bin/env bash
set -euo pipefail
set +x

key_dir="${OPENDISPLAY_KEY_DIR:-$HOME/projects/android/kp}"
mkdir -p "$key_dir"
chmod 700 "$key_dir"

timestamp="$(date '+%Y%m%d-%H%M%S')"
base="opendisplay-upload-$timestamp"
keystore="$key_dir/$base.jks"
env_file="$key_dir/$base.env"
key_alias="opendisplay-upload"

if [[ -e "$keystore" || -e "$env_file" ]]; then
    printf 'Refusing to overwrite an existing key or env file.\n' >&2
    exit 1
fi

if ! command -v keytool >/dev/null 2>&1 || ! command -v openssl >/dev/null 2>&1; then
    printf 'Install a JDK (keytool) and OpenSSL, then run this script again.\n' >&2
    exit 1
fi

umask 077
upload_password="$(openssl rand -hex 32)"

keytool -genkeypair -noprompt \
    -alias "$key_alias" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname 'CN=OpenDisplay Android Upload, OU=Android, O=OpenDisplay, C=JP' \
    -keystore "$keystore" \
    -storepass "$upload_password" \
    -keypass "$upload_password"

{
    printf "export OPENDISPLAY_UPLOAD_KEYSTORE='%s'\n" "$keystore"
    printf "export OPENDISPLAY_UPLOAD_KEY_ALIAS='%s'\n" "$key_alias"
    printf "export OPENDISPLAY_UPLOAD_STORE_PASSWORD='%s'\n" "$upload_password"
    printf "export OPENDISPLAY_UPLOAD_KEY_PASSWORD='%s'\n" "$upload_password"
} > "$env_file"
chmod 600 "$keystore" "$env_file"
keytool -list -keystore "$keystore" -alias "$key_alias" -storepass "$upload_password" >/dev/null
unset upload_password

printf 'Created upload keystore: %s\n' "$keystore"
printf 'Private build credentials: %s\n' "$env_file"
printf 'Build a Play upload bundle with:\n  ./scripts/build-play-release.sh bundle "%s"\n' "$env_file"
