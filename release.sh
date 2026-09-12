#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

VERSION_NAME="${1:?Usage: ./release.sh <versionName> [versionCode]}"
CURRENT_CODE="$(grep -o 'versionCode = [0-9]*' app/build.gradle.kts | head -1 | awk '{print $3}')"
VERSION_CODE="${2:-$((CURRENT_CODE + 1))}"

if [[ ! "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must look like 1.2.3" >&2
  exit 1
fi
if [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]]; then
  echo "versionCode must be an integer" >&2
  exit 1
fi

python3 - "$VERSION_CODE" "$VERSION_NAME" <<'PY'
from pathlib import Path
import re, sys
code, name = sys.argv[1:]
path = Path("app/build.gradle.kts")
text = path.read_text()
text = re.sub(r'versionCode = \d+', f'versionCode = {code}', text, count=1)
text = re.sub(r'versionName = "[^"]+"', f'versionName = "{name}"', text, count=1)
path.write_text(text)
Path("version.json").write_text(
    '{\n  "versionCode": ' + code + ',\n  "versionName": "' + name + '",\n  "notes": "See the GitHub release notes."\n}\n'
)
PY

./gradlew clean assembleRelease
mkdir -p apk
cp app/build/outputs/apk/release/app-release.apk "apk/BrightnessControl.apk"

if command -v apksigner >/dev/null 2>&1; then
  apksigner verify --verbose "apk/BrightnessControl.apk"
else
  echo "Warning: apksigner is not in PATH; Android Studio can verify the APK."
fi
if command -v aapt >/dev/null 2>&1; then
  BADGING="$(aapt dump badging "apk/BrightnessControl.apk")"
  grep -q "application-debuggable='false'" <<<"$BADGING" || {
    echo "The APK appears to be debuggable." >&2
    exit 1
  }
fi

sha256sum "apk/BrightnessControl.apk"
echo "Release $VERSION_NAME ($VERSION_CODE) is ready in apk/BrightnessControl.apk"
echo "Commit apk/BrightnessControl.apk and version.json to the configured GitHub repository."
