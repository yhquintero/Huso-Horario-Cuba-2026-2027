#!/usr/bin/env bash
# Devuelve a la rama el APK recién compilado y su ficha (dist/).
# Solo se ejecuta en CI, con GH_TOKEN y permiso contents: write.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

[[ -n "${GH_TOKEN:-}" ]] || { echo "ERROR: falta GH_TOKEN" >&2; exit 1; }
[[ -f dist/SHA256SUMS && -f dist/README.md ]] || { echo "ERROR: dist/ incompleto" >&2; exit 1; }

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add -A dist
if git diff --cached --quiet; then
  echo "dist/ ya estaba actualizado; nada que confirmar."
  exit 0
fi

version="$(sed -n 's/.*versionName "\(.*\)".*/\1/p' android/app/build.gradle | head -1)"
git commit -m "Publicar APK v${version} en dist/ [skip ci]"
git push "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" \
  "HEAD:${GITHUB_REF_NAME}"
