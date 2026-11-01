#!/usr/bin/env python3
"""Genera la copia de la guía web que se empaqueta dentro del APK.

Toma ``index.html``, ``styles.css``, ``script.js`` y ``assets/favicon.svg`` de la
raíz del repositorio y escribe una copia en
``android/app/src/main/assets/site/`` con dos cambios explícitos:

* se elimina la meta etiqueta ``Content-Security-Policy``, porque en Android la
  página se sirve desde ``file:///android_asset`` (origen opaco) y esa política
  bloquearía sus propios estilos y scripts;
* los enlaces relativos a archivos de Windows (``.reg``) y a la documentación
  (``.md``) se convierten en enlaces absolutos al repositorio, de modo que se
  abran en el navegador del teléfono y nunca dentro del WebView.

El resultado es determinista: ejecutar el script dos veces produce los mismos
bytes. Con ``--check`` no escribe nada y falla si la copia versionada no
coincide con lo que se generaría.
"""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "android" / "app" / "src" / "main" / "assets" / "site"
REPO_URL = "https://github.com/yhquintero/Huso-Horario-Cuba-2026-2027"
BLOB_URL = REPO_URL + "/blob/main"

COPY_FILES = (
    ("index.html", "index.html"),
    ("styles.css", "styles.css"),
    ("script.js", "script.js"),
    ("assets/favicon.svg", "assets/favicon.svg"),
)

CSP_PATTERN = re.compile(r"[ \t]*<meta\s+http-equiv=\"Content-Security-Policy\"[^>]*>\s*\n", re.IGNORECASE)
WINDOWS_FILE_PATTERN = re.compile(r'href="(\.reg/[^"]+\.reg)"')
MARKDOWN_PATTERN = re.compile(r'href="((?:[A-Za-z0-9_.-]+/)?[A-Za-z0-9_.-]+\.md)"')


def transform_index(source: str) -> str:
    if "Content-Security-Policy" not in source:
        raise SystemExit("index.html no contiene la meta Content-Security-Policy esperada")
    output = CSP_PATTERN.sub("", source, count=1)
    if "Content-Security-Policy" in output:
        raise SystemExit("no se pudo eliminar la meta Content-Security-Policy de index.html")

    output, reg_links = WINDOWS_FILE_PATTERN.subn(
        lambda match: 'href="%s/%s"' % (BLOB_URL, match.group(1)), output)
    output, md_links = MARKDOWN_PATTERN.subn(
        lambda match: 'href="%s/%s"' % (BLOB_URL, match.group(1)), output)

    if reg_links < 2:
        raise SystemExit("se esperaban al menos dos enlaces .reg en index.html")
    if md_links < 1:
        raise SystemExit("se esperaban enlaces .md en index.html")

    for leftover in re.findall(r'href="(?:\.reg/|[^":/]+\.md")', output):
        raise SystemExit("queda un enlace relativo sin convertir: %s" % leftover)
    for required in ("styles.css", "script.js", "assets/favicon.svg"):
        if required not in output:
            raise SystemExit("index.html no referencia %s" % required)
    return output


def build() -> dict[Path, bytes]:
    index_source = (ROOT / "index.html").read_text(encoding="utf-8")
    generated: dict[Path, bytes] = {}
    for source_name, target_name in COPY_FILES:
        raw = (ROOT / source_name).read_bytes()
        if source_name == "index.html":
            raw = transform_index(raw.decode("utf-8")).encode("utf-8")
        generated[OUT_DIR / target_name] = raw
    return generated


def main(argv: list[str]) -> int:
    check = "--check" in argv[1:]
    generated = build()

    if check:
        problems = []
        for path, data in generated.items():
            if not path.is_file():
                problems.append("falta %s" % path.relative_to(ROOT))
            elif path.read_bytes() != data:
                problems.append("desactualizado %s" % path.relative_to(ROOT))
        if problems:
            print("FAIL  copia de la guía para Android:", file=sys.stderr)
            for problem in problems:
                print("      " + problem, file=sys.stderr)
            print("      ejecuta: python3 tools/build_android_site.py", file=sys.stderr)
            return 1
        print("PASS  copia de la guía para Android sincronizada con la web")
        return 0

    for path, data in generated.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        print("wrote %-58s %6d bytes  sha256=%s" % (
            path.relative_to(ROOT), len(data), hashlib.sha256(data).hexdigest()[:16]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
