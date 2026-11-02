#!/usr/bin/env python3
"""Comprobaciones estáticas del proyecto Android.

Verifica, sin compilar, que la app Android sigue siendo coherente con los
perfiles de Registro del repositorio y con las decisiones documentadas:

* la regla Java coincide con los TZI de ``.reg/2_Cuba_2027_Correcto.reg``
  (2026 = UTC-04:00 sin transición; 2027 = cambio el 7 de noviembre a la 01:00
  hora local, es decir 05:00 UTC);
* el manifiesto no pide INTERNET, bloquea el tráfico en claro y declara los
  componentes y permisos previstos;
* los recursos usados desde Java y el manifiesto existen;
* la guía empaquetada en ``assets/site`` coincide con la web publicada.

Es una comprobación estática: no firma ni instala el APK.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import build_android_site  # noqa: E402
import verify_registry  # noqa: E402

ANDROID = ROOT / "android"
APP = ANDROID / "app"
SRC = APP / "src" / "main"
JAVA_DIR = SRC / "java" / "com" / "yhquintero" / "cubahora2027"
RES_DIR = SRC / "res"
MANIFEST = SRC / "AndroidManifest.xml"
BUILD_GRADLE = APP / "build.gradle"
CUBARULES = JAVA_DIR / "CubaRules.java"
MAIN_ACTIVITY = JAVA_DIR / "MainActivity.java"
KEYSTORE_PROPS = ANDROID / "keystore" / "keystore.properties"
EXPECTED_SIGNER = ANDROID / "expected-signer.txt"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

APPLICATION_ID = "com.yhquintero.cubahora2027"
MIN_SDK = 21
TARGET_SDK = 34
COMPILE_SDK = 34

REQUIRED_PERMISSIONS = {
    "android.permission.WRITE_SECURE_SETTINGS",
    "android.permission.SET_TIME_ZONE",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.POST_NOTIFICATIONS",
}
FORBIDDEN_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.USE_EXACT_ALARM",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.READ_PHONE_STATE",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_CONTACTS",
    "android.permission.SEND_SMS",
}
EXPECTED_COMPONENTS = {
    ".MainActivity": "activity",
    ".GuideActivity": "activity",
    ".BootReceiver": "receiver",
    ".TransitionReceiver": "receiver",
}
JAVA_COMPONENT_FILES = {
    ".MainActivity": "MainActivity.java",
    ".GuideActivity": "GuideActivity.java",
    ".BootReceiver": "BootReceiver.java",
    ".TransitionReceiver": "TransitionReceiver.java",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("FAIL  " + message)


def read(path: Path) -> str:
    require(path.is_file(), "falta %s" % path.relative_to(ROOT))
    return path.read_text(encoding="utf-8")


# --------------------------------------------------------------------------
# Gradle
# --------------------------------------------------------------------------

def check_gradle() -> None:
    source = read(BUILD_GRADLE)
    require('namespace "com.yhquintero.cubahora2027"' in source,
            "build.gradle: namespace inesperado")
    require('applicationId "%s"' % APPLICATION_ID in source,
            "build.gradle: applicationId inesperado")
    require(re.search(r"compileSdk\s+%d" % COMPILE_SDK, source) is not None,
            "build.gradle: compileSdk debe ser %d" % COMPILE_SDK)
    require(re.search(r"minSdk\s+%d" % MIN_SDK, source) is not None,
            "build.gradle: minSdk debe ser %d (Android 5.0)" % MIN_SDK)
    require(re.search(r"targetSdk\s+%d" % TARGET_SDK, source) is not None,
            "build.gradle: targetSdk debe ser %d" % TARGET_SDK)

    top = read(ANDROID / "build.gradle")
    plugin_version = re.search(r'id "com\.android\.application" version "([0-9.]+)"', top)
    require(plugin_version is not None,
            "android/build.gradle: falta la versión del Android Gradle Plugin")
    major = int(plugin_version.group(1).split(".")[0])
    require(major >= 8, "android/build.gradle: se esperaba AGP 8 o superior")

    require("dependencies {" in source, "build.gradle: falta el bloque dependencies")
    require("implementation" not in source,
            "build.gradle: la app no debe declarar dependencias externas")


# --------------------------------------------------------------------------
# Manifiesto
# --------------------------------------------------------------------------

def check_manifest() -> None:
    text = read(MANIFEST)
    tree = ET.parse(MANIFEST)
    root = tree.getroot()

    permissions = {
        element.get(ANDROID_NS + "name")
        for element in root.findall("uses-permission")
    }
    missing = REQUIRED_PERMISSIONS - permissions
    require(not missing, "manifiesto: faltan permisos %s" % sorted(missing))
    forbidden = FORBIDDEN_PERMISSIONS & permissions
    require(not forbidden, "manifiesto: permisos no permitidos %s" % sorted(forbidden))

    application = root.find("application")
    require(application is not None, "manifiesto: falta <application>")
    require(application.get(ANDROID_NS + "usesCleartextTraffic") == "false",
            "manifiesto: usesCleartextTraffic debe ser false")
    require(application.get(ANDROID_NS + "allowBackup") == "false",
            "manifiesto: allowBackup debe ser false")

    found = {}
    for tag in ("activity", "receiver", "service"):
        for element in application.findall(tag):
            found[element.get(ANDROID_NS + "name")] = tag
    for name, tag in EXPECTED_COMPONENTS.items():
        require(found.get(name) == tag,
                "manifiesto: %s debe declararse como %s" % (name, tag))
        require((JAVA_DIR / JAVA_COMPONENT_FILES[name]).is_file(),
                "falta la clase %s" % JAVA_COMPONENT_FILES[name])

    for element in application.findall("activity"):
        exported = element.get(ANDROID_NS + "exported")
        name = element.get(ANDROID_NS + "name")
        if name == ".MainActivity":
            filters = element.findall("intent-filter")
            require(exported == "true", "manifiesto: MainActivity debe ser exported")
            require(filters, "manifiesto: MainActivity necesita el intent-filter LAUNCHER")
        else:
            require(exported == "false",
                    "manifiesto: %s no debe ser exported" % name)

    for element in application.findall("receiver"):
        require(element.get(ANDROID_NS + "exported") == "false",
                "manifiesto: %s no debe ser exported" % element.get(ANDROID_NS + "name"))

    boot = [element for element in application.findall("receiver")
            if element.get(ANDROID_NS + "name") == ".BootReceiver"][0]
    actions = {
        action.get(ANDROID_NS + "name")
        for action in boot.iter("action")
        if action.get(ANDROID_NS + "name")
    }
    require("android.intent.action.BOOT_COMPLETED" in actions,
            "manifiesto: BootReceiver debe escuchar BOOT_COMPLETED")
    require("android.permission.INTERNET" not in text,
            "manifiesto: no debe pedirse INTERNET")


def check_resources() -> None:
    manifest_text = read(MANIFEST)

    strings = ET.parse(RES_DIR / "values" / "strings.xml").getroot()
    string_names = {element.get("name") for element in strings.findall("string")}

    for reference in set(re.findall(r"@string/([A-Za-z0-9_]+)", manifest_text)):
        require(reference in string_names,
                "manifiesto: @string/%s no existe" % reference)

    java_sources = "\n".join(
        path.read_text(encoding="utf-8") for path in sorted(JAVA_DIR.glob("*.java")))
    for reference in sorted(set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", java_sources))):
        require(reference in string_names, "Java: R.string.%s no existe" % reference)

    drawables = {path.stem for path in RES_DIR.rglob("*.xml")}
    drawables |= {path.stem for path in RES_DIR.rglob("*.png")}
    for reference in sorted(set(re.findall(r"R\.drawable\.([A-Za-z0-9_]+)", java_sources))):
        require(reference in drawables, "Java: R.drawable.%s no existe" % reference)

    declared = {path.stem for path in RES_DIR.rglob("*.*")}
    for values_file in (RES_DIR / "values").glob("*.xml"):
        for element in ET.parse(values_file).getroot():
            name = element.get("name")
            if name:
                declared.add(name)
    for reference in set(re.findall(r"@(?:mipmap|drawable|xml|style|color)/([A-Za-z0-9_.]+)",
                                    manifest_text)):
        require(reference in declared,
                "manifiesto: el recurso %s no existe" % reference)


# --------------------------------------------------------------------------
# Regla horaria
# --------------------------------------------------------------------------

def check_cuba_rules() -> None:
    raw_active, active_text = verify_registry.read_utf8_reg(
        verify_registry.ACTIVE_PROFILE)
    sections, _ = verify_registry.parse_reg_text(
        verify_registry.ACTIVE_PROFILE, active_text)
    dynamic = sections[verify_registry.DYNAMIC_KEY]
    tzi_2026 = verify_registry.parse_binary(
        verify_registry.ACTIVE_PROFILE, "2026", dynamic["2026"])
    tzi_2027 = verify_registry.parse_binary(
        verify_registry.ACTIVE_PROFILE, "2027", dynamic["2027"])

    bias_2026 = int.from_bytes(tzi_2026[0:4], "little", signed=True)
    bias_2027 = int.from_bytes(tzi_2027[0:4], "little", signed=True)

    transition = verify_registry.recurring_transition(tzi_2027, 2027)
    require(transition == datetime(2027, 11, 7, 1, 0, 0),
            "el StandardDate del perfil no decodifica al 7/11/2027 01:00")

    # En el TZI de Windows, UTC = hora local + Bias (Bias 240 = UTC-04:00).
    change_utc = transition + timedelta(minutes=bias_2026)
    require(change_utc == datetime(2027, 11, 7, 5, 0),
            "el cambio calculado no es 2027-11-07 05:00 UTC")

    # Bias positivo (240) significa desfase UTC negativo (-240 minutos).
    utc_offset_2026 = -bias_2026
    utc_offset_2027 = -bias_2027

    source = read(CUBARULES)
    expected_snippets = {
        "OFFSET_SUMMER = %d" % utc_offset_2026:
            "desfase de verano debe ser %d minutos" % utc_offset_2026,
        "OFFSET_STANDARD = %d" % utc_offset_2027:
            "desfase estándar debe ser %d minutos" % utc_offset_2027,
        "utc(2026, 1, 1, 4, 0)": "inicio de la regla (2026-01-01 00:00 con UTC-04:00)",
        "utc(%d, %d, %d, %d, %d)" % (
            change_utc.year, change_utc.month, change_utc.day,
            change_utc.hour, change_utc.minute): "instante UTC de la transición de 2027",
    }
    for snippet, message in expected_snippets.items():
        require(snippet in source, "CubaRules.java: %s" % message)

    zone_controller = read(JAVA_DIR / "ZoneController.java")
    require("America/Havana" in zone_controller,
            "ZoneController.java: falta la zona de referencia America/Havana")
    require('TIME_ZONE_SETTING = "time_zone"' in zone_controller,
            "ZoneController.java: la clave de zona horaria debe ser el ajuste real time_zone")
    require("Settings.Global.TIME_ZONE" not in zone_controller,
            "ZoneController.java: Settings.Global.TIME_ZONE no es API pública de Android")
    require(raw_active, "el perfil activo no debe estar vacío")

    main_source = read(MAIN_ACTIVITY)
    require("pm grant %s android.permission.WRITE_SECURE_SETTINGS" % APPLICATION_ID
            in main_source,
            "MainActivity.java: el comando ADB debe usar el applicationId real")
    require("adb shell pm grant" in main_source,
            "MainActivity.java: falta la instrucción ADB para conceder el permiso")


def check_no_network_apis() -> None:
    for path in sorted(JAVA_DIR.glob("*.java")):
        source = path.read_text(encoding="utf-8")
        for token in ("java.net.", "HttpURLConnection", "OkHttp", "Socket("):
            require(token not in source,
                    "%s: la app no debe usar red (%s)" % (path.name, token))
        require("http://" not in source,
                "%s: no debe haber URL en claro" % path.name)


def check_guide_assets() -> dict[str, bytes]:
    generated = build_android_site.build()
    for path, data in generated.items():
        require(path.is_file(), "falta %s (ejecuta tools/build_android_site.py)"
                % path.relative_to(ROOT))
        require(path.read_bytes() == data,
                "%s está desactualizado (ejecuta tools/build_android_site.py)"
                % path.relative_to(ROOT))
    index = generated[build_android_site.OUT_DIR / "index.html"].decode("utf-8")
    require("Content-Security-Policy" not in index,
            "la copia embebida no debe conservar la CSP")
    return generated


def check_signing() -> None:
    properties = read(KEYSTORE_PROPS)
    for key in ("storeFile", "storePassword", "keyAlias", "keyPassword"):
        require(re.search(r"^%s=.+" % key, properties, re.MULTILINE) is not None,
                "keystore.properties: falta %s" % key)
    store_file = re.search(r"^storeFile=(.+)$", properties, re.MULTILINE).group(1).strip()
    require((ANDROID / store_file).is_file(),
            "falta el almacén de claves %s" % store_file)

    signer = read(EXPECTED_SIGNER).strip().upper().replace(":", "")
    require(re.fullmatch(r"[0-9A-F]{64}", signer) is not None,
            "expected-signer.txt debe contener una huella SHA-256 de 64 dígitos")

    readme = read(ANDROID / "keystore" / "README.md")
    formatted = ":".join(signer[index:index + 2] for index in range(0, 64, 2))
    require(formatted in readme,
            "android/keystore/README.md debe publicar la huella del certificado")


def main(argv: list[str]) -> int:
    del argv
    try:
        check_gradle()
        check_manifest()
        check_resources()
        check_cuba_rules()
        check_no_network_apis()
        generated = check_guide_assets()
        check_signing()
    except SystemExit as error:
        if error.code in (0, None):
            raise
        if isinstance(error.code, str):
            print(error.code, file=sys.stderr)
        return 1
    print("PASS  Gradle: applicationId, SDK 21/34 y sin dependencias externas")
    print("PASS  Manifiesto: sin INTERNET, sin tráfico en claro, componentes y permisos previstos")
    print("PASS  Recursos usados desde Java y el manifiesto")
    print("PASS  CubaRules.java coincide con los TZI 2026/2027 (cambio 2027-11-07 05:00 UTC)")
    print("PASS  Guía embebida sincronizada (%d archivos en assets/site)" % len(generated))
    print("PASS  Firma: almacén de claves y huella publicada")
    print("NOTE  Comprobación estática: no compila ni instala el APK.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
