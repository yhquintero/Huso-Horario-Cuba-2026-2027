#!/usr/bin/env bash
# Comprueba el APK generado, publica su SHA-256 y deja una copia en dist/.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

apk="${1:-android/app/build/outputs/apk/release/app-release.apk}"
[[ -f "$apk" ]] || { echo "ERROR: no existe $apk" >&2; exit 1; }

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk" && -d "$sdk" ]] || { echo "ERROR: define ANDROID_HOME" >&2; exit 1; }
build_tools="$(ls -d "$sdk"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
[[ -n "$build_tools" ]] || { echo "ERROR: no hay build-tools en el SDK" >&2; exit 1; }
aapt2="${build_tools}aapt2"
apksigner="${build_tools}apksigner"
[[ -x "$aapt2" ]] || { echo "ERROR: falta aapt2 en $build_tools" >&2; exit 1; }
[[ -x "$apksigner" ]] || { echo "ERROR: falta apksigner en $build_tools" >&2; exit 1; }

version="$(sed -n 's/.*versionName "\(.*\)".*/\1/p' android/app/build.gradle | head -1)"
[[ -n "$version" ]] || { echo "ERROR: no se pudo leer versionName" >&2; exit 1; }
version_code="$(sed -n 's/.*versionCode \([0-9][0-9]*\).*/\1/p' android/app/build.gradle | head -1)"
[[ -n "$version_code" ]] || { echo "ERROR: no se pudo leer versionCode" >&2; exit 1; }

echo "== badging =="
echo "aapt2: $aapt2"
"$aapt2" dump badging "$apk" > /tmp/badging.txt
grep -E "^(package|sdkVersion|targetSdkVersion|application-label)" /tmp/badging.txt || true
if grep -q "uses-permission: name='android.permission.INTERNET'" /tmp/badging.txt; then
  echo "ERROR: el APK pide INTERNET; la app debe funcionar sin red." >&2
  exit 1
fi
if ! grep -q "package: name='com.yhquintero.cubahora2027' versionCode='$version_code' versionName='$version'" /tmp/badging.txt; then
  echo "ERROR: applicationId o versión inesperados en el APK." >&2
  grep "^package:" /tmp/badging.txt >&2
  exit 1
fi
if ! grep -Eq "(min)?[Ss]dkVersion:'21'" /tmp/badging.txt; then
  echo "ERROR: minSdk inesperado (se esperaba 21)." >&2
  grep -i "sdkversion" /tmp/badging.txt >&2 || true
  exit 1
fi

echo "== manifiesto compilado =="
"$aapt2" dump xmltree --file AndroidManifest.xml "$apk" > /tmp/manifest-tree.txt
if grep -q 'usesCleartextTraffic.*true' /tmp/manifest-tree.txt; then
  echo "ERROR: el APK permite tráfico en claro." >&2
  exit 1
fi
for component in MainActivity GuideActivity BootReceiver TransitionReceiver; do
  if ! grep -qE "\.(cubahora2027\.)?$component\"" /tmp/manifest-tree.txt; then
    echo "ERROR: el manifiesto no declara $component." >&2
    grep -E "E: (activity|receiver)|android:name" /tmp/manifest-tree.txt | head -20 >&2 || true
    exit 1
  fi
done

echo "== firma =="
"$apksigner" verify --print-certs "$apk" > /tmp/signature.txt
grep -E "SHA-256|Verified using" /tmp/signature.txt | head -8 || true

# Extrae la huella SHA-256 del certificado sin depender del formato exacto
# de apksigner (puede venir con o sin "Signer #1:", con o sin dos puntos).
signer="$(python3 - <<'PY_HUELLA'
import re

text = open("/tmp/signature.txt", encoding="utf-8", errors="replace").read()
for match in re.finditer(r"SHA-256 digest(?![a-z])([0-9A-Fa-f:\s]{64,})", text):
    digits = re.sub(r"[^0-9A-Fa-f]", "", match.group(1))
    if len(digits) >= 64:
        print(digits[:64].upper())
        break
PY_HUELLA
)"
expected="$(tr -d '[:space:]:' < android/expected-signer.txt | tr '[:lower:]' '[:upper:]')"
echo "firma del APK : $signer"
echo "firma esperada: $expected"
if [[ -z "$signer" || "$signer" != "$expected" ]]; then
  echo "ERROR: la firma del APK no coincide con android/expected-signer.txt" >&2
  exit 1
fi
grep -E "^Verified using v[123] scheme" /tmp/signature.txt || true

echo "== publicación =="
python3 "$root/tools/publish_dist.py" --apk "$apk" --signer-sha256 "$signer"
python3 "$root/tools/verify_release_hashes.py"
