#!/usr/bin/env python3
"""Comprueba que ``dist/`` publicado es coherente y no está manipulado.

Verifica que cada APK de ``dist/`` tenga su línea en ``SHA256SUMS`` con el
tamaño y la huella reales, que ``dist/README.md`` publique esos mismos datos y
que la versión coincida con ``android/app/build.gradle``.

Uso:
    python3 tools/verify_release_hashes.py            # verifica
    python3 tools/verify_release_hashes.py --check    # igual (para CI)
"""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
BUILD_GRADLE = ROOT / "android" / "app" / "build.gradle"
EXPECTED_SIGNER = ROOT / "android" / "expected-signer.txt"


def fail(message: str) -> "NoReturn":  # type: ignore[valid-type]
    print("FAIL  " + message, file=sys.stderr)
    raise SystemExit(1)


def gradle_version() -> tuple[str, str]:
    source = BUILD_GRADLE.read_text(encoding="utf-8")
    name = re.search(r'versionName\s+"([^"]+)"', source)
    code = re.search(r"versionCode\s+(\d+)", source)
    if not name or not code:
        fail("no se pudo leer versionName/versionCode de build.gradle")
    return name.group(1), code.group(1)


def main(argv: list[str]) -> int:
    del argv
    apks = sorted(DIST.glob("*.apk")) if DIST.is_dir() else []
    if not apks:
        sums = DIST / "SHA256SUMS"
        if sums.exists():
            fail("hay dist/SHA256SUMS pero ningún APK en dist/")
        print("NOTE  dist/ todavía no publica un APK; nada que verificar.")
        return 0

    sums_path = DIST / "SHA256SUMS"
    readme_path = DIST / "README.md"
    for required in (sums_path, readme_path):
        if not required.is_file():
            fail("falta %s" % required.relative_to(ROOT))

    entries: dict[str, str] = {}
    for line in sums_path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._-]+\.apk)", line)
        if match is None:
            fail("línea con formato inesperado en SHA256SUMS: %r" % line)
        digest, name = match.groups()
        if name in entries:
            fail("SHA256SUMS repite %s" % name)
        entries[name] = digest

    if set(entries) != {apk.name for apk in apks}:
        fail("SHA256SUMS y los APK de dist/ no coinciden")

    readme = readme_path.read_text(encoding="utf-8")
    version_name, version_code = gradle_version()
    signer = EXPECTED_SIGNER.read_text(encoding="utf-8").strip().upper()
    formatted_signer = ":".join(signer[i:i + 2] for i in range(0, 64, 2))

    for apk in apks:
        data = apk.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if entries[apk.name] != digest:
            fail("SHA-256 desactualizado para %s (real: %s)" % (apk.name, digest))
        if apk.name != "CubaHora2027-v%s.apk" % version_name:
            fail("%s no coincide con versionName %s" % (apk.name, version_name))
        row = re.search(
            r"\|\s*`%s`\s*\|\s*([^|]+?)\s*\|\s*([0-9,]+) bytes\s*\|\s*`([0-9A-Fa-f]{64})`\s*\|"
            % re.escape(apk.name), readme)
        if row is None:
            fail("dist/README.md no publica una fila con %s" % apk.name)
        version_cell, size_cell, digest_cell = row.groups()
        if version_cell.strip() != "%s (versionCode %s)" % (version_name, version_code):
            fail("dist/README.md publica una versión distinta de build.gradle")
        if int(size_cell.replace(",", "")) != len(data):
            fail("dist/README.md publica un tamaño distinto para %s" % apk.name)
        if digest_cell.upper() != digest.upper():
            fail("dist/README.md publica un SHA-256 distinto para %s" % apk.name)
        if formatted_signer not in readme:
            fail("dist/README.md no publica la huella de firma esperada")

    print("PASS  dist/: %d APK, checksums, tamaño y versión coherentes" % len(apks))
    print("PASS  dist/README.md publica la huella de firma del proyecto")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
