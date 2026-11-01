#!/usr/bin/env bash
# Compila el APK de release y lo deja en android/app/build/outputs/apk/release/.
#
# Requiere un JDK 17 y el Android SDK (compileSdk 34). En GitHub Actions los
# dos vienen preinstalados. Uso local:
#   export ANDROID_HOME=$HOME/Android/Sdk
#   ./android/scripts/build-apk.sh
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root/android"

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  for candidate in "$HOME/Android/Sdk" "/usr/lib/android-sdk" "/opt/android-sdk"; do
    if [[ -d "$candidate" ]]; then
      export ANDROID_HOME="$candidate"
      break
    fi
  done
fi
if [[ -z "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ERROR: define ANDROID_HOME con la ruta del Android SDK." >&2
  exit 1
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [[ ! -f local.properties ]]; then
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
fi

# Firma opcional por variables de entorno: base64 del almacén de claves.
if [[ -n "${CUBA_KEYSTORE_BASE64:-}" && -z "${CUBA_KEYSTORE_PATH:-}" ]]; then
  keystore_tmp="$(mktemp -d)/cuba-release.p12"
  printf '%s' "$CUBA_KEYSTORE_BASE64" | base64 --decode > "$keystore_tmp"
  export CUBA_KEYSTORE_PATH="$keystore_tmp"
fi

gradle_bin="${GRADLE_BIN:-gradle}"
if ! command -v "$gradle_bin" >/dev/null 2>&1; then
  echo "ERROR: no se encontró Gradle en PATH. Instala Gradle 8.9 o superior." >&2
  exit 1
fi

"$gradle_bin" --no-daemon --stacktrace :app:assembleRelease

apk="$root/android/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk" ]]; then
  echo "ERROR: no se generó $apk" >&2
  exit 1
fi
echo "APK: $apk"
