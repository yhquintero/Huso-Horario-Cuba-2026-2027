#!/usr/bin/env python3
"""Fail-closed structural checks for the published Windows Registry profiles.

This tool uses only Python's standard library. It checks the registry paths and
values that may be written, validates the Windows TZI payload sizes and 2027
fields, and verifies that the SHA-256 values shown on the landing page match the
committed files in the landing page and README. It is a scope/integrity check,
not a certification that the calendar rules are official or legally current.
"""

from __future__ import annotations

import hashlib
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_DIR = ROOT / ".reg"
PAGE_PATH = ROOT / "index.html"
BASE_KEY = r"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time"
DYNAMIC_KEY = BASE_KEY + r"\Dynamic DST"

FILE_ENCODINGS = {
    "Cuba_2027_Original.reg": "utf-8",
    "Cuba_2027_Completo.reg": "utf-16",
}

ALLOWED_VALUE_NAMES = {
    BASE_KEY: {
        "Display",
        "Dlt",
        "MUI_Display",
        "MUI_Dlt",
        "MUI_Std",
        "Std",
        "TZI",
        "MapID",
    },
    DYNAMIC_KEY: {
        "FirstEntry",
        "LastEntry",
        *(str(year) for year in range(2003, 2014)),
        "2027",
    },
}

EXPECTED_BASE_VALUES = {
    "Display": '"(GMT-05:00) Cuba Time (Havana)"',
    "Dlt": '"Cuba Daylight Time"',
    "Std": '"Cuba Standard Time"',
    "MapID": '"-1,84"',
}
EXPECTED_MUI_VALUES = {
    "MUI_Display": '"@tzres.dll,-2430"',
    "MUI_Dlt": '"@tzres.dll,-2431"',
    "MUI_Std": '"@tzres.dll,-2432"',
}
EXPECTED_BASE_STANDARD = (0, 10, 0, 5, 1, 0, 0, 0)
EXPECTED_BASE_DAYLIGHT = (0, 3, 0, 5, 0, 0, 0, 0)
EXPECTED_2027_STANDARD = (0, 11, 0, 1, 1, 0, 0, 0)
EXPECTED_2027_DAYLIGHT = (0, 3, 0, 5, 0, 0, 0, 0)


class ValidationError(Exception):
    """Raised when a profile or its published metadata fails validation."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def decode_profile(filename: str) -> tuple[bytes, str]:
    path = REGISTRY_DIR / filename
    require(path.is_file(), f"Missing registry file: {path.relative_to(ROOT)}")
    raw = path.read_bytes()

    if filename == "Cuba_2027_Completo.reg":
        require(raw.startswith(b"\xff\xfe"), f"{filename} must be UTF-16 LE with a BOM")
    else:
        require(not raw.startswith((b"\xff\xfe", b"\xfe\xff")), f"{filename} must remain UTF-8")

    try:
        text = raw.decode(FILE_ENCODINGS[filename])
    except UnicodeDecodeError as exc:
        raise ValidationError(f"{filename} has an invalid {FILE_ENCODINGS[filename]} encoding") from exc

    text = text.lstrip("\ufeff")
    require(
        text.splitlines()[0] == "Windows Registry Editor Version 5.00",
        f"{filename} has an unexpected Registry Editor header",
    )
    require("\x00" not in text, f"{filename} contains an unexpected NUL character")
    return raw, text


def parse_profile(filename: str, text: str) -> dict[str, dict[str, str]]:
    sections: dict[str, dict[str, str]] = {}
    current_section: str | None = None
    pending = ""

    for line_number, raw_line in enumerate(text.splitlines(), start=1):
        line = raw_line.strip()
        if not line or line == "Windows Registry Editor Version 5.00":
            continue

        if line.startswith("[") and line.endswith("]"):
            require(not pending, f"{filename}:{line_number}: unfinished value before section")
            section = line[1:-1]
            require(not section.startswith("-"), f"{filename}:{line_number}: key deletion is not allowed")
            require(section in ALLOWED_VALUE_NAMES, f"{filename}:{line_number}: unapproved Registry path {section!r}")
            require(section not in sections, f"{filename}:{line_number}: duplicate Registry section {section!r}")
            sections[section] = {}
            current_section = section
            continue

        require(current_section is not None, f"{filename}:{line_number}: value outside an approved section")
        pending += line
        if pending.endswith("\\"):
            pending = pending[:-1]
            continue

        match = re.fullmatch(r'"([^"\\]+)"=(.+)', pending)
        require(match is not None, f"{filename}:{line_number}: unrecognized Registry value")
        name, value = match.groups()
        require(name in ALLOWED_VALUE_NAMES[current_section], f"{filename}:{line_number}: unapproved value {name!r}")
        require(name not in sections[current_section], f"{filename}:{line_number}: duplicate value {name!r}")
        sections[current_section][name] = value
        pending = ""

    require(not pending, f"{filename}: unfinished continued Registry value")
    require(set(sections) == {BASE_KEY, DYNAMIC_KEY}, f"{filename}: expected exactly the Cuba key and its Dynamic DST subkey")
    return sections


def parse_hex_binary(filename: str, key: str, name: str, value: str) -> bytes:
    match = re.fullmatch(r"hex:([0-9a-fA-F,]+)", value)
    require(match is not None, f"{filename}: {key}\\{name} must be a plain hex REG_BINARY value")
    try:
        return bytes(int(part, 16) for part in match.group(1).split(","))
    except ValueError as exc:
        raise ValidationError(f"{filename}: invalid hexadecimal data in {name}") from exc


def check_tzi(filename: str, key: str, name: str, value: str) -> bytes:
    data = parse_hex_binary(filename, key, name, value)
    require(len(data) == 44, f"{filename}: {key}\\{name} is {len(data)} bytes; Windows TZI must be 44 bytes")
    bias, standard_bias, daylight_bias = struct.unpack_from("<iii", data)
    require(-840 <= bias <= 840, f"{filename}: {name} has an invalid UTC bias")
    require(-840 <= standard_bias <= 840, f"{filename}: {name} has an invalid standard bias")
    require(-840 <= daylight_bias <= 840, f"{filename}: {name} has an invalid daylight bias")

    for offset, label in ((12, "standard"), (28, "daylight")):
        fields = struct.unpack_from("<8H", data, offset)
        year, month, day_of_week, week, hour, minute, second, milliseconds = fields
        require(year == 0, f"{filename}: {name} {label} rule must use a recurring year")
        if month == 0:
            require(fields == (0, 0, 0, 0, 0, 0, 0, 0),
                    f"{filename}: {name} {label} rule has a partial no-transition date")
            continue
        require(1 <= month <= 12, f"{filename}: {name} {label} rule has an invalid month")
        require(day_of_week <= 6, f"{filename}: {name} {label} rule has an invalid weekday")
        require(1 <= week <= 5, f"{filename}: {name} {label} rule has an invalid week")
        require(hour <= 23 and minute <= 59 and second <= 59 and milliseconds <= 999,
                f"{filename}: {name} {label} rule has an invalid time")
    return data


def validate_profile(filename: str) -> tuple[bytes, dict[str, dict[str, str]]]:
    raw, text = decode_profile(filename)
    sections = parse_profile(filename, text)
    base = sections[BASE_KEY]
    dynamic = sections[DYNAMIC_KEY]

    require(set(base) == ({"Display", "Dlt", "Std", "TZI", "MapID"} if filename == "Cuba_2027_Original.reg" else set(ALLOWED_VALUE_NAMES[BASE_KEY])),
            f"{filename}: unexpected values in the Cuba timezone key")
    require(set(dynamic) == ({"FirstEntry", "LastEntry", "2027"} if filename == "Cuba_2027_Original.reg" else {"FirstEntry", "LastEntry", *(str(year) for year in range(2003, 2014)), "2027"}),
            f"{filename}: unexpected values in the Dynamic DST key")

    for name, expected in EXPECTED_BASE_VALUES.items():
        require(base.get(name) == expected, f"{filename}: unexpected {name} label")
    if filename == "Cuba_2027_Completo.reg":
        for name, expected in EXPECTED_MUI_VALUES.items():
            require(base.get(name) == expected, f"{filename}: unexpected {name} resource reference")

    require(dynamic.get("FirstEntry") == "dword:000007eb", f"{filename}: FirstEntry must be 2027")
    require(dynamic.get("LastEntry") == "dword:000007eb", f"{filename}: LastEntry must be 2027")

    base_tzi = check_tzi(filename, BASE_KEY, "TZI", base["TZI"])
    require(struct.unpack_from("<iii", base_tzi) == (300, 0, -60), f"{filename}: unexpected base UTC offsets")
    require(struct.unpack_from("<8H", base_tzi, 12) == EXPECTED_BASE_STANDARD,
            f"{filename}: unexpected generic standard-time transition")
    require(struct.unpack_from("<8H", base_tzi, 28) == EXPECTED_BASE_DAYLIGHT,
            f"{filename}: unexpected generic daylight-time transition")

    for name, value in dynamic.items():
        if name in {"FirstEntry", "LastEntry"}:
            continue
        check_tzi(filename, DYNAMIC_KEY, name, value)

    tzi_2027 = parse_hex_binary(filename, DYNAMIC_KEY, "2027", dynamic["2027"])
    require(struct.unpack_from("<iii", tzi_2027) == (300, 0, -60), f"{filename}: unexpected 2027 UTC offsets")
    require(struct.unpack_from("<8H", tzi_2027, 12) == EXPECTED_2027_STANDARD,
            f"{filename}: unexpected encoded standard-time transition for 2027")
    require(struct.unpack_from("<8H", tzi_2027, 28) == EXPECTED_2027_DAYLIGHT,
            f"{filename}: unexpected encoded daylight-time transition for 2027")
    return raw, sections


def validate_published_hashes(file_bytes: dict[str, bytes]) -> None:
    require(PAGE_PATH.is_file(), "Missing index.html")
    page = PAGE_PATH.read_text(encoding="utf-8")
    declared = re.findall(r'data-file="([^"]+)"\s+data-sha256="([0-9A-Fa-f]{64})"', page)
    require(len(declared) == len(FILE_ENCODINGS), "index.html must publish exactly one SHA-256 for each profile")
    page_hashes = {name: value.upper() for name, value in declared}
    require(set(page_hashes) == set(FILE_ENCODINGS), "index.html profile names do not match the repository files")

    for filename, raw in file_bytes.items():
        actual = hashlib.sha256(raw).hexdigest().upper()
        require(page_hashes[filename] == actual, f"index.html SHA-256 is stale for {filename}: expected {actual}")

    require(page.count('href=".reg/Cuba_2027_Original.reg"') == 1, "index.html must link the compact profile once")
    require(page.count('href=".reg/Cuba_2027_Completo.reg"') == 1, "index.html must link the expanded profile once")

    readme_path = ROOT / "README.md"
    require(readme_path.is_file(), "Missing README.md")
    readme = readme_path.read_text(encoding="utf-8")
    rows = re.findall(r"\| `([^`]+\.reg)` \| ([0-9,]+) bytes \| `([0-9A-Fa-f]{64})` \|", readme)
    require(len(rows) == len(FILE_ENCODINGS), "README.md must publish one size and SHA-256 row for each profile")
    readme_hashes = {name: (int(size.replace(",", "")), digest.upper()) for name, size, digest in rows}
    require(set(readme_hashes) == set(FILE_ENCODINGS), "README.md profile names do not match the repository files")

    for filename, raw in file_bytes.items():
        size, digest = readme_hashes[filename]
        require(size == len(raw), f"README.md has a stale file size for {filename}")
        actual = hashlib.sha256(raw).hexdigest().upper()
        require(digest == actual, f"README.md SHA-256 is stale for {filename}: expected {actual}")


def main() -> int:
    try:
        file_bytes: dict[str, bytes] = {}
        parsed: dict[str, dict[str, dict[str, str]]] = {}

        for filename in FILE_ENCODINGS:
            raw, sections = validate_profile(filename)
            file_bytes[filename] = raw
            parsed[filename] = sections
            print(f"PASS  {filename}: encoding, allow-listed scope, values and TZI structures")

        compact_2027 = parse_hex_binary(
            "Cuba_2027_Original.reg", DYNAMIC_KEY, "2027", parsed["Cuba_2027_Original.reg"][DYNAMIC_KEY]["2027"]
        )
        expanded_2027 = parse_hex_binary(
            "Cuba_2027_Completo.reg", DYNAMIC_KEY, "2027", parsed["Cuba_2027_Completo.reg"][DYNAMIC_KEY]["2027"]
        )
        require(compact_2027 == expanded_2027, "The 2027 rule differs between the two profiles")
        compact_base = parse_hex_binary(
            "Cuba_2027_Original.reg", BASE_KEY, "TZI", parsed["Cuba_2027_Original.reg"][BASE_KEY]["TZI"]
        )
        expanded_base = parse_hex_binary(
            "Cuba_2027_Completo.reg", BASE_KEY, "TZI", parsed["Cuba_2027_Completo.reg"][BASE_KEY]["TZI"]
        )
        require(compact_base == expanded_base, "The generic timezone rule differs between the two profiles")
        validate_published_hashes(file_bytes)

        for filename, raw in file_bytes.items():
            digest = hashlib.sha256(raw).hexdigest().upper()
            print(f"PASS  SHA-256 {filename}: {digest}")
        print("PASS  index.html links and SHA-256 values in index.html and README.md match the committed files")
        print("NOTE  Structural checks do not establish official status or legal correctness of the 2027 calendar rules.")
        return 0
    except (OSError, ValidationError, KeyError, ValueError, struct.error) as exc:
        print(f"FAIL  {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
