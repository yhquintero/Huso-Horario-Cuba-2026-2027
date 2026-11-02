#!/usr/bin/env bash
# Crea (o actualiza) la release de GitHub con el APK y sus checksums.
# Se ejecuta en CI al empujar un tag vX.Y.Z, con GH_TOKEN y contents: write.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

version="$(sed -n 's/.*versionName "\(.*\)".*/\1/p' android/app/build.gradle | head -1)"
[[ -n "$version" ]] || { echo "ERROR: no se pudo leer versionName" >&2; exit 1; }

tag="${GITHUB_REF_NAME:-v${version}}"
if [[ "$tag" != "v${version}" ]]; then
  echo "ERROR: el tag $tag no coincide con versionName $version de build.gradle" >&2
  exit 1
fi

apk="dist/CubaHora2027-${tag}.apk"
[[ -f "$apk" ]] || { echo "ERROR: falta $apk" >&2; exit 1; }
[[ -f dist/SHA256SUMS ]] || { echo "ERROR: falta dist/SHA256SUMS" >&2; exit 1; }
[[ -f dist/README.md ]] || { echo "ERROR: falta dist/README.md" >&2; exit 1; }

notes="android/RELEASE_NOTES.md"
[[ -f "$notes" ]] || { echo "ERROR: falta $notes" >&2; exit 1; }

# Notas finales = notas del proyecto + checksum real del APK publicado.
final_notes="$(mktemp)"
{
  cat "$notes"
  printf '\n## SHA-256 del APK\n\n```\n'
  cat dist/SHA256SUMS
  printf '```\n'
} > "$final_notes"

title="Cuba Hora 2027 ${tag}"

if gh release view "$tag" >/dev/null 2>&1; then
  echo "La release $tag ya existe: se actualizan los adjuntos."
  gh release upload "$tag" "$apk" dist/SHA256SUMS --clobber
  gh release edit "$tag" --notes-file "$final_notes" --title "$title"
else
  echo "Creando la release $tag."
  if git rev-parse -q --verify "refs/tags/${tag}" >/dev/null; then
    gh release create "$tag" --title "$title" --notes-file "$final_notes" \
      "$apk" dist/SHA256SUMS
  else
    gh release create "$tag" --title "$title" --notes-file "$final_notes" \
      --target "${GITHUB_SHA:?}" \
      "$apk" dist/SHA256SUMS
  fi
fi

echo "Release publicada:"
gh release view "$tag" --json url,name,tagName,assets --jq \
  '"\(.name) · \(.url)\n" + ([.assets[] | "  - \(.name) (\(.size) bytes)"] | join("\n"))'
