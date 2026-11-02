#!/usr/bin/env python3
"""Publica el APK compilado en ``dist/`` con sus checksums y su ficha.

Escribe tres archivos versionados en el repositorio:

* ``dist/CubaHora2027-v<versión>.apk``: copia del APK firmado;
* ``dist/SHA256SUMS``: formato ``sha256sum -c``;
* ``dist/README.md``: tamaño, versión, huella de firma e instrucciones.

La versión se lee de ``android/app/build.gradle`` y no se acepta ninguna otra.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = ROOT / "android" / "app" / "build.gradle"
DIST = ROOT / "dist"
EXPECTED_SIGNER = ROOT / "android" / "expected-signer.txt"


def fail(message: str) -> "NoReturn":  # type: ignore[valid-type]
    print("FAIL  " + message, file=sys.stderr)
    raise SystemExit(1)


def gradle_version() -> tuple[str, str]:
    source = BUILD_GRADLE.read_text(encoding="utf-8")
    version_name = re.search(r'versionName\s+"([^"]+)"', source)
    version_code = re.search(r"versionCode\s+(\d+)", source)
    if not version_name or not version_code:
        fail("no se pudo leer versionName/versionCode de build.gradle")
    return version_name.group(1), version_code.group(1)


def format_fingerprint(hex_digest: str) -> str:
    return ":".join(hex_digest[i:i + 2] for i in range(0, len(hex_digest), 2))


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--signer-sha256", required=True)
    args = parser.parse_args(argv[1:])

    if not args.apk.is_file():
        fail("no existe el APK indicado: %s" % args.apk)

    signer = args.signer_sha256.replace(":", "").strip().upper()
    if not re.fullmatch(r"[0-9A-F]{64}", signer):
        fail("la huella del firmante no es un SHA-256 válido")
    expected = EXPECTED_SIGNER.read_text(encoding="utf-8").strip().upper()
    if signer != expected:
        fail("la huella del firmante no coincide con android/expected-signer.txt")

    version_name, version_code = gradle_version()

    DIST.mkdir(parents=True, exist_ok=True)
    for stale in DIST.glob("CubaHora2027-*.apk"):
        stale.unlink()

    target = DIST / ("CubaHora2027-v%s.apk" % version_name)
    shutil.copyfile(args.apk, target)

    data = target.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    size = len(data)

    (DIST / "SHA256SUMS").write_text(
        "%s  %s\n" % (digest, target.name), encoding="utf-8")

    readme = build_readme(target.name, version_name, version_code, size, digest, signer)
    (DIST / "README.md").write_text(readme, encoding="utf-8")

    print("OK    %s (%d bytes)" % (target.relative_to(ROOT), size))
    print("      SHA-256  %s" % digest.upper())
    print("      firma    %s" % format_fingerprint(signer))
    return 0


def build_readme(name: str, version_name: str, version_code: str,
                 size: int, digest: str, signer: str) -> str:
    return f"""# APK publicado

Copia instalable de **Cuba Hora 2027** para Android, generada por
`.github/workflows/build-apk.yml` a partir del código de este repositorio.

> [!IMPORTANT]
> Proyecto independiente y no oficial. No está afiliado a Microsoft, a Samsung
> ni a una institución del Gobierno de Cuba. El calendario que aplica es el del
> perfil publicado en este repositorio (2026 en UTC−04:00 y cambio a UTC−05:00
> el 7 de noviembre de 2027 a la 01:00) y no constituye una certificación
> oficial. Comprueba la normativa vigente antes de usarlo.

## Archivo

| Archivo | Versión | Tamaño | SHA-256 |
|---|---|---:|---|
| `{name}` | {version_name} (versionCode {version_code}) | {size:,} bytes | `{digest.upper()}` |

- **Android mínimo:** 5.0 (API 21). Probado con el flujo de compilación para
  API 34; funciona en Samsung Galaxy S22 y en otros teléfonos con Android 5.0
  o superior.
- **Firma:** SHA-256 del certificado
  `{format_fingerprint(signer)}` (identidad pública del proyecto, ver
  `android/keystore/README.md`).
- **Permisos peligrosos:** ninguno. No pide INTERNET y no puede leer datos
  personales. El cambio automático de zona horaria requiere un permiso
  opcional que solo se concede por ADB.

## Instalación

1. Descarga `{name}` en el teléfono.
2. Abre el archivo; si Android lo bloquea, permite «Instalar aplicaciones
   desconocidas» para la app con la que lo abriste (Archivos o Chrome).
3. Acepta el aviso de Play Protect y termina la instalación.

También se publica como adjunto en la release de GitHub del repositorio, con
este mismo `SHA256SUMS`.

## Comprobación

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs {name}
```
"""


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
