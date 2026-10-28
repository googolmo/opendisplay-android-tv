# List available project tasks.
default:
    @just --list

# Build the debug APK.
debug:
    ./gradlew assembleDebug

# Install the debug APK on a connected Android device or emulator.
install-debug:
    ./gradlew installDebug

# Generate a timestamped Play upload key under ~/projects/android/kp/.
keygen:
    ./scripts/generate-play-upload-key.sh

# Build a signed Android App Bundle for Google Play.
release key_env:
    ./scripts/build-play-release.sh bundle "{{key_env}}"

# Build a signed APK for direct install or testing.
sign key_env:
    ./scripts/build-play-release.sh apk "{{key_env}}"

# Build both signed release artifacts.
release-both key_env:
    ./scripts/build-play-release.sh both "{{key_env}}"

# Show the configured Android signing variants.
signing-report:
    ./gradlew signingReport

# Remove generated build outputs.
clean:
    ./gradlew clean
