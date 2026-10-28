#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'USAGE'
Usage: ./scripts/build-play-release.sh [bundle|apk|both] [key-env-file]

bundle  Build the signed Android App Bundle for Google Play (default).
apk     Build a signed APK for direct install or testing.
both    Build both outputs.

key-env-file defaults to ~/projects/android/kp/open-display-upload-key.env.
Pass the .env path printed by generate-play-upload-key.sh when using a
timestamped keystore.
USAGE
}

artifact="${1:-bundle}"
env_file="${2:-$HOME/projects/android/kp/open-display-upload-key.env}"

case "$artifact" in
    bundle|apk|both) ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
esac

if [[ ! -r "$env_file" ]]; then
    printf 'Cannot read key credentials file: %s\n' "$env_file" >&2
    printf 'Generate a key first, or pass its .env path as the second argument.\n' >&2
    exit 1
fi

# The generated env file contains only shell-safe paths, aliases, and hex passwords.
# shellcheck disable=SC1090
source "$env_file"

for variable in \
    OPENDISPLAY_UPLOAD_KEYSTORE \
    OPENDISPLAY_UPLOAD_KEY_ALIAS \
    OPENDISPLAY_UPLOAD_STORE_PASSWORD \
    OPENDISPLAY_UPLOAD_KEY_PASSWORD; do
    if [[ -z "${!variable:-}" ]]; then
        printf 'Missing %s in %s\n' "$variable" "$env_file" >&2
        exit 1
    fi
done

if [[ ! -f "$OPENDISPLAY_UPLOAD_KEYSTORE" ]]; then
    printf 'Keystore not found: %s\n' "$OPENDISPLAY_UPLOAD_KEYSTORE" >&2
    exit 1
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

case "$artifact" in
    bundle) ./gradlew bundleRelease ;;
    apk) ./gradlew assembleRelease ;;
    both) ./gradlew bundleRelease assembleRelease ;;
esac

case "$artifact" in
    bundle|both)
        printf '\nPlay upload bundle: %s/app/build/outputs/bundle/release/app-release.aab\n' "$project_root"
        ;;
esac
case "$artifact" in
    apk|both)
        printf '\nSigned APK: %s/app/build/outputs/apk/release/app-release.apk\n' "$project_root"
        ;;
esac
