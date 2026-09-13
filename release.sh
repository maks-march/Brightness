#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

fail_handler() {
  local code=$?
  echo "ERROR: release.sh failed at line ${BASH_LINENO[0]}: ${BASH_COMMAND}" >&2
  return "$code"
}
trap fail_handler ERR

VERSION_NAME="${1:-}"
if [[ -z "$VERSION_NAME" || ! "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: ./release.sh <versionName> [versionCode]" >&2
  echo "Example: ./release.sh 1.1.0" >&2
  exit 1
fi

CURRENT_CODE="$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -1)"
if [[ -z "$CURRENT_CODE" ]]; then
  echo "Could not find versionCode in app/build.gradle.kts" >&2
  exit 1
fi
VERSION_CODE="${2:-$((CURRENT_CODE + 1))}"
if [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]]; then
  echo "versionCode must be an integer" >&2
  exit 1
fi

# Prefer a compatible JDK 17 from ~/.jdks for this script only.
if [[ -d "${HOME:-}/.jdks" ]]; then
  while IFS= read -r JAVA_CANDIDATE; do
    [[ -x "$JAVA_CANDIDATE" || -f "$JAVA_CANDIDATE" ]] || continue
    CANDIDATE_MAJOR="$("$JAVA_CANDIDATE" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1 || true)"
    if [[ "$CANDIDATE_MAJOR" == "17" ]]; then
      JAVA_HOME="$(cd "$(dirname "$JAVA_CANDIDATE")/.." && pwd)"
      export JAVA_HOME
      export PATH="$JAVA_HOME/bin:$PATH"
      echo "Using project JDK: $JAVA_HOME"
      break
    fi
  done < <(find "$HOME/.jdks" -type f \( -name java -o -name java.exe \) -path '*/bin/*' 2>/dev/null | sort)
fi

JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1 || true)"
if [[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -lt 17 ]]; then
  echo "JDK 17 or newer is required. Current Java: $JAVA_MAJOR" >&2
  exit 1
fi
if [[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -gt 22 ]]; then
  echo "This project uses Gradle 8.9 and AGP 8.7.3. Use JDK 17 (recommended) or JDK 21/22, not JDK $JAVA_MAJOR." >&2
  exit 1
fi

PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="$(command -v python3)"
elif command -v python >/dev/null 2>&1; then
  PYTHON_BIN="$(command -v python)"
fi
if [[ -z "$PYTHON_BIN" ]]; then
  echo "Python 3 was not found. Install Python or use release.ps1 on Windows." >&2
  exit 1
fi

BUILD_FILE="app/build.gradle.kts"
VERSION_FILE="version.json"
BACKUP_DIR="$(mktemp -d)"
cp "$BUILD_FILE" "$BACKUP_DIR/build.gradle.kts"
cp "$VERSION_FILE" "$BACKUP_DIR/version.json"
SUCCESS=0
restore_on_exit() {
  local exit_code=$?
  if [[ "$SUCCESS" != "1" ]]; then
    cp "$BACKUP_DIR/build.gradle.kts" "$BUILD_FILE" || true
    cp "$BACKUP_DIR/version.json" "$VERSION_FILE" || true
    echo "Release preparation failed; version files were restored." >&2
  fi
  rm -rf "$BACKUP_DIR" || true
  return "$exit_code"
}
trap restore_on_exit EXIT

if ! "$PYTHON_BIN" - "$VERSION_CODE" "$VERSION_NAME" <<'PY'
from pathlib import Path
import re, sys
code, name = sys.argv[1:]
path = Path("app/build.gradle.kts")
text = path.read_text(encoding="utf-8")
text, count_code = re.subn(r'versionCode = \d+', f'versionCode = {code}', text, count=1)
text, count_name = re.subn(r'versionName = "[^"]+"', f'versionName = "{name}"', text, count=1)
if count_code != 1 or count_name != 1:
    raise SystemExit("Could not update versionCode/versionName")
path.write_text(text, encoding="utf-8")
Path("version.json").write_text(
    '{\n  "versionCode": ' + code + ',\n  "versionName": "' + name + '",\n  "notes": "See the GitHub release notes."\n}\n',
    encoding="utf-8"
)
PY
then
  echo "ERROR: Python could not update the version files." >&2
  exit 1
fi

echo "Version prepared: $VERSION_NAME ($VERSION_CODE)"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" && -n "${LOCALAPPDATA:-}" ]]; then
  SDK_ROOT="$LOCALAPPDATA/Android/Sdk"
fi
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT" ]]; then
  echo "Android SDK was not found. Set ANDROID_SDK_ROOT or install it through Android Studio SDK Manager." >&2
  exit 1
fi
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"

# local.properties is machine-specific and is ignored by Git.
SDK_FOR_GRADLE="${SDK_ROOT//\\//}"
if [[ -f local.properties ]]; then
  if grep -q '^sdk.dir=' local.properties; then
    sed -i "s#^sdk.dir=.*#sdk.dir=$SDK_FOR_GRADLE#" local.properties
  else
    printf '\nsdk.dir=%s\n' "$SDK_FOR_GRADLE" >> local.properties
  fi
else
  printf 'sdk.dir=%s\n' "$SDK_FOR_GRADLE" > local.properties
fi
echo "Using Android SDK: $SDK_ROOT"

if [[ -x ./gradlew ]]; then
  GRADLEW=(./gradlew)
else
  GRADLEW=(bash ./gradlew)
fi
"${GRADLEW[@]}" clean assembleRelease

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$RELEASE_APK" ]]; then
  echo "Release APK was not produced: $RELEASE_APK" >&2
  exit 1
fi
mkdir -p apk
cp "$RELEASE_APK" "apk/BrightnessControl.apk"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" && -n "${LOCALAPPDATA:-}" ]]; then
  SDK_ROOT="$LOCALAPPDATA/Android/Sdk"
fi
APKSIGNER=""
AAPT2=""
if [[ -d "$SDK_ROOT/build-tools" ]]; then
  APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner | sort -V | tail -1 || true)"
  AAPT2="$(find "$SDK_ROOT/build-tools" -type f -name aapt2 | sort -V | tail -1 || true)"
fi
if [[ -n "$APKSIGNER" ]]; then
  "$APKSIGNER" verify --verbose apk/BrightnessControl.apk
else
  echo "Warning: apksigner was not found; APK signature was not checked."
fi
if [[ -n "$AAPT2" ]]; then
  BADGING="$("$AAPT2" dump badging apk/BrightnessControl.apk)"
  if grep -q "application-debuggable='true'" <<<"$BADGING"; then
    echo "The APK is debuggable; release build is invalid." >&2
    exit 1
  fi
fi

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum apk/BrightnessControl.apk
else
  shasum -a 256 apk/BrightnessControl.apk
fi

SUCCESS=1
echo "Release $VERSION_NAME ($VERSION_CODE) is ready in apk/BrightnessControl.apk"
echo "Commit apk/BrightnessControl.apk and version.json to the configured GitHub repository."
