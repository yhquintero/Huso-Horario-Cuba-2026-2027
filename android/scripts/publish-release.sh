#!/usr/bin/env bash
# Crea (o actualiza) la release de GitHub con el APK y sus checksums.
# Requiere GH_TOKEN con permiso contents: write y dist/ con los archivos.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

version="$(sed -n 's/.*versionName "\(.*\)".*/\1/p' android/app/build.gradle | head -1)"
[[ -n "$version" ]] || { echo "ERROR: no se pudo leer versionName" >&2; exit 1; }
tag="v${version}"

[[ -f "dist/CubaHora2027-${tag}.apk" ]] || { echo "ERROR: falta el APK en dist/" >&2; exit 1; }
[[ -f dist/SHA256SUMS ]] || { echo "ERROR: falta dist/SHA256SUMS" >&2; exit 1; }

notes="android/RELEASE_NOTES.md"
[[ -f "$notes" ]] || { echo "ERROR: falta $notes" >&2; exit 1; }

if gh release view "$tag" >/dev/null 2>&1; then
  echo "La release $tag ya existe: se actualizan los adjuntos."
  gh release upload "$tag" dist/CubaHora2027-"${tag}".apk dist/SHA256SUMS --clobber
  gh release edit "$tag" --notes-file "$notes" --title "Cuba Hora 2027 ${tag}"
else
  echo "Creando la release $tag."
  gh release create "$tag" \
    --title "Cuba Hora 2027 ${tag}" \
    --notes-file "$notes" \
    --target "$GITHUB_SHA" \
    dist/CubaHora2027-"${tag}".apk dist/SHA256SUMS
fi

echo "Release: $tag"
gh release view "$tag" --json url,name,tagName --jq '"\(.name) · \(.url)"'
