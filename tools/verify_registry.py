#!/usr/bin/env python3
"""Fail-closed checks for the active Cuba timezone profile and PowerShell app.

This standard-library-only validator checks the exact Registry scope, the
2026/2027 Windows TZI payloads, consistency with the PowerShell application's
embedded bytes and read-back checks, and published SHA-256 metadata. It does
not execute Windows code or certify that any proposed calendar rule is official.
"""

from __future__ import annotations

import calendar
import hashlib
import re
import struct
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_DIR = ROOT / ".reg"
PAGE_PATH = ROOT / "index.html"
README_PATH = ROOT / "README.md"
APP_PATH = ROOT / ".ps1" / "CubaHora2027.ps1"
BASE_KEY = r"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time"
DYNAMIC_KEY = BASE_KEY + r"\Dynamic DST"
ACTIVE_PROFILE = "2_Cuba_2027_Correcto.reg"
CLEANUP_PROFILE = "1_Limpiar.reg"
PROFILE_NAMES = (ACTIVE_PROFILE, CLEANUP_PROFILE)
REG_HEADER = "Windows Registry Editor Version 5.00"

EXPECTED_BASE_VALUES = {
    "Display": '"(UTC-05:00) La Habana"',
    "Dlt": '"Cuba Daylight Time"',
    "Std": '"Cuba Standard Time"',
    "MapID": '"-1,84"',
}
EXPECTED_DYNAMIC_NAMES = {"FirstEntry", "LastEntry", "2026", "2027"}
EXPECTED_2026_BIAS = (240, 0, -60)
EXPECTED_2027_BIAS = (300, 0, -60)
NO_TRANSITION = (0, 0, 0, 0, 0, 0, 0, 0)
EXPECTED_2027_STANDARD = (0, 11, 0, 1, 1, 0, 0, 0)


class ValidationError(Exception):
    """Raised when a checked file or its published metadata is inconsistent."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def read_utf8_reg(filename: str) -> tuple[bytes, str]:
    path = REGISTRY_DIR / filename
    require(path.is_file(), f"Missing Registry file: {path.relative_to(ROOT)}")
    raw = path.read_bytes()
    require(not raw.startswith((b"\xef\xbb\xbf", b"\xff\xfe", b"\xfe\xff")),
            f"{filename} must remain UTF-8 without a byte-order mark")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValidationError(f"{filename} is not valid UTF-8") from exc
    require("\x00" not in text, f"{filename} contains an unexpected NUL character")
    require(text.splitlines() and text.splitlines()[0] == REG_HEADER,
            f"{filename} has an unexpected Registry Editor header")
    return raw, text


def parse_reg_text(filename: str, text: str) -> tuple[dict[str, dict[str, str]], list[str]]:
    """Parse the small, single-line REG subset used by the active files."""
    sections: dict[str, dict[str, str]] = {}
    deleted_sections: list[str] = []
    current_section: str | None = None
    deleting = False

    for line_number, raw_line in enumerate(text.splitlines(), start=1):
        line = raw_line.strip()
        if not line or line == REG_HEADER:
            continue

        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            deleting = section.startswith("-")
            if deleting:
                section = section[1:]
                require(section not in deleted_sections,
                        f"{filename}:{line_number}: duplicate key deletion {section!r}")
                deleted_sections.append(section)
                current_section = section
                continue
            require(section not in sections,
                    f"{filename}:{line_number}: duplicate Registry section {section!r}")
            sections[section] = {}
            current_section = section
            continue

        require(current_section is not None,
                f"{filename}:{line_number}: value outside a Registry section")
        require(not deleting,
                f"{filename}:{line_number}: a deleted key must not contain values")
        match = re.fullmatch(r'"([^"\\]+)"=(.+)', line)
        require(match is not None,
                f"{filename}:{line_number}: unsupported or continued Registry value")
        name, value = match.groups()
        require(name not in sections[current_section],
                f"{filename}:{line_number}: duplicate value {name!r}")
        sections[current_section][name] = value

    return sections, deleted_sections


def parse_dword(filename: str, name: str, value: str) -> int:
    match = re.fullmatch(r"dword:([0-9a-fA-F]{8})", value)
    require(match is not None, f"{filename}: {name} must be an eight-digit REG_DWORD")
    return int(match.group(1), 16)


def parse_binary(filename: str, name: str, value: str) -> bytes:
    match = re.fullmatch(r"hex:([0-9a-fA-F]{2}(?:,[0-9a-fA-F]{2})*)", value)
    require(match is not None, f"{filename}: {name} must be a plain REG_BINARY hex value")
    data = bytes(int(part, 16) for part in match.group(1).split(","))
    require(len(data) == 44,
            f"{filename}: {name} is {len(data)} bytes; Windows REG_TZI_FORMAT must be 44 bytes")
    return data


def check_tzi_offsets(filename: str, name: str, data: bytes, expected: tuple[int, int, int]) -> None:
    offsets = struct.unpack_from("<iii", data)
    require(offsets == expected,
            f"{filename}: {name} has UTC biases {offsets}, expected {expected}")


def recurring_transition(data: bytes, year: int) -> datetime | None:
    """Decode the recurring SYSTEMTIME transition stored at byte offset 12."""
    rule_year, month, weekday, week, hour, minute, second, milliseconds = struct.unpack_from(
        "<8H", data, 12
    )
    if month == 0:
        return None
    require(rule_year == 0, "2027 StandardDate must be a recurring SYSTEMTIME rule")
    require(1 <= month <= 12 and weekday <= 6 and 1 <= week <= 5,
            "2027 StandardDate contains an invalid recurring date")
    require(hour <= 23 and minute <= 59 and second <= 59 and milliseconds <= 999,
            "2027 StandardDate contains an invalid time")
    first_weekday, days_in_month = calendar.monthrange(year, month)
    # Python Monday=0; Windows SYSTEMTIME Sunday=0.
    windows_first_weekday = (first_weekday + 1) % 7
    day = 1 + ((weekday - windows_first_weekday + 7) % 7) + (7 * (week - 1))
    if day > days_in_month:
        day -= 7
    return datetime(year, month, day, hour, minute, second, milliseconds * 1000)


def parse_app_byte_array(source: str, name: str) -> bytes:
    pattern = rf"\$Script:{re.escape(name)}\s*=\s*\[byte\[\]\]\s*@\(([^)]*)\)"
    match = re.search(pattern, source, flags=re.IGNORECASE | re.DOTALL)
    require(match is not None, f"PowerShell app is missing its {name} byte array")
    parts = [part.strip() for part in match.group(1).split(",")]
    require(parts and all(re.fullmatch(r"0[xX][0-9a-fA-F]{1,2}", part) for part in parts),
            f"PowerShell app has malformed hexadecimal bytes in {name}")
    return bytes(int(part, 16) for part in parts)


def validate_active_profile(raw: bytes, text: str) -> dict[str, bytes]:
    sections, deletions = parse_reg_text(ACTIVE_PROFILE, text)
    require(not deletions, f"{ACTIVE_PROFILE}: key deletion is not allowed")
    require(set(sections) == {BASE_KEY, DYNAMIC_KEY},
            f"{ACTIVE_PROFILE}: only the Cuba timezone key and its Dynamic DST subkey are allowed")

    base = sections[BASE_KEY]
    dynamic = sections[DYNAMIC_KEY]
    require(set(base) == set(EXPECTED_BASE_VALUES) | {"TZI"},
            f"{ACTIVE_PROFILE}: unexpected or missing values in the base timezone key")
    require(set(dynamic) == EXPECTED_DYNAMIC_NAMES,
            f"{ACTIVE_PROFILE}: Dynamic DST must contain FirstEntry, LastEntry, 2026 and 2027 only")
    for name, expected in EXPECTED_BASE_VALUES.items():
        require(base[name] == expected, f"{ACTIVE_PROFILE}: unexpected {name} string")

    require(parse_dword(ACTIVE_PROFILE, "FirstEntry", dynamic["FirstEntry"]) == 2026,
            f"{ACTIVE_PROFILE}: FirstEntry must be 2026")
    require(parse_dword(ACTIVE_PROFILE, "LastEntry", dynamic["LastEntry"]) == 2027,
            f"{ACTIVE_PROFILE}: LastEntry must be 2027")

    base_tzi = parse_binary(ACTIVE_PROFILE, "TZI", base["TZI"])
    tzi_2026 = parse_binary(ACTIVE_PROFILE, "Dynamic DST\\2026", dynamic["2026"])
    tzi_2027 = parse_binary(ACTIVE_PROFILE, "Dynamic DST\\2027", dynamic["2027"])
    check_tzi_offsets(ACTIVE_PROFILE, "TZI", base_tzi, EXPECTED_2027_BIAS)
    check_tzi_offsets(ACTIVE_PROFILE, "Dynamic DST\\2026", tzi_2026, EXPECTED_2026_BIAS)
    check_tzi_offsets(ACTIVE_PROFILE, "Dynamic DST\\2027", tzi_2027, EXPECTED_2027_BIAS)

    require(base_tzi == tzi_2027,
            f"{ACTIVE_PROFILE}: base TZI and the 2027 Dynamic DST entry must match")
    require(struct.unpack_from("<8H", tzi_2026, 12) == NO_TRANSITION and
            struct.unpack_from("<8H", tzi_2026, 28) == NO_TRANSITION,
            f"{ACTIVE_PROFILE}: 2026 must have no encoded transition (fixed UTC-04:00 rule)")
    require(struct.unpack_from("<8H", tzi_2027, 12) == EXPECTED_2027_STANDARD,
            f"{ACTIVE_PROFILE}: unexpected 2027 StandardDate fields")
    require(struct.unpack_from("<8H", tzi_2027, 28) == NO_TRANSITION,
            f"{ACTIVE_PROFILE}: unexpected 2027 DaylightDate fields")

    transition = recurring_transition(tzi_2027, 2027)
    require(transition == datetime(2027, 11, 7, 1, 0, 0),
            f"{ACTIVE_PROFILE}: decoded 2027 StandardDate is {transition}, not the encoded first Sunday in November")
    return {"TZI": base_tzi, "2026": tzi_2026, "2027": tzi_2027}


def validate_cleanup_profile(text: str) -> None:
    sections, deletions = parse_reg_text(CLEANUP_PROFILE, text)
    require(not sections, f"{CLEANUP_PROFILE}: cleanup file must not write Registry values")
    require(deletions == [DYNAMIC_KEY],
            f"{CLEANUP_PROFILE}: it may delete only the Cuba Dynamic DST subkey")


def validate_app(app_tzi: dict[str, bytes]) -> None:
    require(APP_PATH.is_file(), f"Missing PowerShell application: {APP_PATH.relative_to(ROOT)}")
    source = APP_PATH.read_text(encoding="utf-8-sig")
    require('"1.9.0"' in source, "PowerShell app version must be updated to 1.9.0")

    for name, expected in (("TZI_2026", app_tzi["2026"]), ("TZI_2027", app_tzi["2027"])):
        embedded = parse_app_byte_array(source, name)
        require(embedded == expected,
                f"PowerShell app {name} bytes do not match {ACTIVE_PROFILE}")
    for value in EXPECTED_BASE_VALUES.values():
        require(value[1:-1] in source,
                "PowerShell app base label values do not match the active profile")

    required_checks = (
        '$Script:RegistrySubKey = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Time Zones\\Cuba Standard Time"',
        '$Script:RegistryPath = "HKLM:\\$($Script:RegistrySubKey)"',
        '$Script:DynamicDSTSubKey = "$($Script:RegistrySubKey)\\Dynamic DST"',
        '$expectedSection = "[HKEY_LOCAL_MACHINE\\$($Script:RegistrySubKey)]"',
        '$backupContent.StartsWith("Windows Registry Editor Version 5.00", [System.StringComparison]::Ordinal)',
        '$backupContent -notmatch [regex]::Escape($expectedSection)',
        '$Script:TZI_Main = $Script:TZI_2027',
        '$baseKey.OpenSubKey($Script:RegistrySubKey, $false)',
        '$baseKey.OpenSubKey($Script:DynamicDSTSubKey, $false)',
        '$baseKey.CreateSubKey($Script:DynamicDSTSubKey)',
        'Test-ByteArrayEqual $baseTzi $Script:TZI_Main',
        'Test-ByteArrayEqual $tzi2026 $Script:TZI_2026',
        'Test-ByteArrayEqual $tzi2027 $Script:TZI_2027',
        '$state.BaseLabelsMatch -and',
        '$dynamicKey.GetValueKind("FirstEntry") -eq [Microsoft.Win32.RegistryValueKind]::DWord',
        '$tzKey.GetValueKind($labelName) -eq [Microsoft.Win32.RegistryValueKind]::String',
        '$tzKey.GetValueKind("TZI") -eq [Microsoft.Win32.RegistryValueKind]::Binary',
        '$dynamicKey.GetValueKind("2026") -eq [Microsoft.Win32.RegistryValueKind]::Binary',
        '$dynamicKey.GetValueKind("2027") -eq [Microsoft.Win32.RegistryValueKind]::Binary',
        '($state.Transition2027 -eq [datetime]::new(2027, 11, 7, 1, 0, 0))',
        '$state.DaylightTransition2027 = Get-TziTransitionDate -Tzi $tzi2027 -Year 2027 -SystemTimeOffset 28',
        '($null -eq $state.DaylightTransition2027)',
        '2027 DaylightDate leido del TZI:',
        '$dynamicKey.SetValue("2026", [byte[]]$Script:TZI_2026',
        '$dynamicKey.SetValue("2027", [byte[]]$Script:TZI_2027',
        '$audit = Get-CubaRuleAudit',
        '$finalAudit = Get-CubaRuleAudit',
        'Resultado: CORRECCION PENDIENTE',
        'T "RulesPending"',
        'T "AllPending"',
        'if ($isCuba -and $audit.IsValid)',
        '$report = Get-VerificationReport -Audit $audit',
        'if ($audit.IsValid) { exit 0 }',
    )
    for snippet in required_checks:
        require(snippet in source,
                f"PowerShell app is missing a required live-rule validation: {snippet}")

    apply_start = source.find("function Apply-FullFix")
    apply_end = source.find("function Get-VerificationReport", apply_start)
    apply_body = source[apply_start:apply_end] if apply_start >= 0 and apply_end >= 0 else ""
    backup_guard = apply_body.find("if (-not (Backup-CubaRegistry))")
    registry_write = apply_body.find("$ok = Set-CubaRegistry")
    require(backup_guard >= 0 and registry_write >= 0 and backup_guard < registry_write,
            "PowerShell app must validate the backup before writing Registry values")

    require("Clear-CubaDynamicDST" not in source,
            "PowerShell app must not remove the Dynamic DST key before applying values")
    require(not re.search(r"(?:Remove-Item|DeleteSubKey(?:Tree)?)[^\r\n]*Dynamic DST", source, re.IGNORECASE),
            "PowerShell app must preserve unmodified Dynamic DST years")
    require("-ExecutionPolicy Bypass" not in source,
            "PowerShell app must not bypass the machine's execution policy")
    require("Esperado: \"El horario de verano finaliza" not in source and
            "Mantener horario de verano hasta" not in source and
            "Mantener DST Cuba hasta" not in source,
            "PowerShell UI must not present a fixed end date in place of registry data")


def validate_published_hashes(file_bytes: dict[str, bytes]) -> None:
    require(PAGE_PATH.is_file(), "Missing index.html")
    page = PAGE_PATH.read_text(encoding="utf-8")
    declared = re.findall(
        r'data-file="([^"]+)"\s+data-sha256="([0-9A-Fa-f]{64})"', page
    )
    require(len(declared) == len(file_bytes),
            "index.html must publish exactly one SHA-256 for each active .reg file")
    page_hashes = {name: digest.upper() for name, digest in declared}
    require(set(page_hashes) == set(file_bytes),
            "index.html profile names do not match the active Registry files")
    for filename, raw in file_bytes.items():
        actual = hashlib.sha256(raw).hexdigest().upper()
        require(page_hashes[filename] == actual,
                f"index.html SHA-256 is stale for {filename}: expected {actual}")
        require(page.count(f'href=".reg/{filename}"') == 1,
                f"index.html must link {filename} exactly once")

    for old_name in ("Cuba_2027_Original.reg", "Cuba_2027_Completo.reg"):
        require(old_name not in page,
                f"index.html still refers to an obsolete profile name: {old_name}")

    require(README_PATH.is_file(), "Missing README.md")
    readme = README_PATH.read_text(encoding="utf-8")
    rows = re.findall(
        r"\| `([^`]+\.reg)` \| ([0-9,]+) bytes \| `([0-9A-Fa-f]{64})` \|",
        readme,
    )
    require(len(rows) == len(file_bytes),
            "README.md must publish one size and SHA-256 row for each active .reg file")
    readme_hashes = {
        name: (int(size.replace(",", "")), digest.upper())
        for name, size, digest in rows
    }
    require(set(readme_hashes) == set(file_bytes),
            "README.md Registry file names do not match the active files")
    for filename, raw in file_bytes.items():
        size, digest = readme_hashes[filename]
        require(size == len(raw), f"README.md has a stale size for {filename}")
        actual = hashlib.sha256(raw).hexdigest().upper()
        require(digest == actual,
                f"README.md SHA-256 is stale for {filename}: expected {actual}")

    for old_name in ("Cuba_2027_Original.reg", "Cuba_2027_Completo.reg"):
        require(old_name not in readme,
                f"README.md still refers to an obsolete profile name: {old_name}")
    require(".ps1/CubaHora2027.ps1" in readme,
            "README.md must document the current PowerShell application path")


def main() -> int:
    try:
        raw_active, active_text = read_utf8_reg(ACTIVE_PROFILE)
        raw_cleanup, cleanup_text = read_utf8_reg(CLEANUP_PROFILE)
        app_tzi = validate_active_profile(raw_active, active_text)
        validate_cleanup_profile(cleanup_text)
        validate_app(app_tzi)
        validate_published_hashes({ACTIVE_PROFILE: raw_active, CLEANUP_PROFILE: raw_cleanup})

        print(f"PASS  {ACTIVE_PROFILE}: Registry scope, values and 2026/2027 TZI structures")
        print(f"PASS  2026 transition fields are empty; 2027 StandardDate decodes to 2027-11-07 01:00")
        print(f"PASS  {CLEANUP_PROFILE}: deletion is limited to the Dynamic DST subkey")
        print("PASS  PowerShell app TZI bytes, registry read-back checks and pending-state reporting")
        print("PASS  README.md and index.html links, sizes and SHA-256 values")
        print("NOTE  Static checks do not execute the Windows app or establish legal/official calendar rules.")
        return 0
    except (OSError, UnicodeError, ValidationError, KeyError, ValueError, struct.error) as exc:
        print(f"FAIL  {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
