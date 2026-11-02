# Registro de cambios

Los cambios de este proyecto se anotan aquí para facilitar la revisión de los perfiles y de la guía.

## 2026-11-01 · Android 1.1.0: ajuste guiado y sin permisos de sistema

- Se corrigió un supuesto falso de la versión 1.0.0: en AOSP, `AlarmManager.setTimeZone()` exige `SET_TIME_ZONE` (`signature|privileged|role`) y escribir `time_zone` en `Settings.Global` no aplica el cambio. Una app instalada por el usuario no puede cambiar la zona horaria.
- La app deja de declarar `WRITE_SECURE_SETTINGS` y `SET_TIME_ZONE` y elimina el flujo que podía informar de un éxito inexistente. Ahora lee el estado real con `TimeZone.getDefault()`, separa el desfase del teléfono del que exige la regla y avisa si la zona automática puede deshacer el ajuste.
- Se añade `OffsetGuide.java` con los pasos para One UI y la zona fija recomendada por tramo: Santo Domingo (UTC−04:00) y Bogotá (UTC−05:00), con alternativas y el valor GMT equivalente; los pasos se pueden copiar.
- Los recordatorios pasan a 7 días antes, 24 h antes y en el momento de la transición, y la app solicita permiso de notificaciones al abrirse en Android 13 o superior.
- `tools/verify_android.py` ahora falla si vuelve a declararse un permiso de sistema, si aparece una escritura de zona o si la interfaz promete un cambio automático.
- Se actualizan guía web, README, SECURITY, notas de release y la ficha de `dist/`. El APK se recompila y se publica como v1.1.0 con la misma identidad de firma, de modo que actualiza sobre la versión anterior.

## 2026-11-01 · Aplicación Android y APK firmado

- Se añadió el proyecto Android en `android/`: app nativa sin dependencias externas que compara la zona del teléfono con la regla del repositorio y aplica el cambio cuando el permiso opcional `WRITE_SECURE_SETTINGS` está concedido.
- La regla Java se deriva del perfil activo: 2026 completo en UTC−04:00 y transición a UTC−05:00 el 7 de noviembre de 2027 a la 01:00 (05:00 UTC), decidida en este cambio; los bytes del `.reg` no fijaban antes esa interpretación.
- La app no pide INTERNET, bloquea el tráfico en claro, no incluye rastreadores y envía recordatorios locales 24 h antes y en el momento de la transición.
- Se añadió `tools/build_android_site.py`, que genera la copia de la guía empaquetada en el APK sin la política CSP y con los enlaces de descarga apuntando al repositorio.
- Se añadieron `tools/verify_android.py` y `tools/verify_release_hashes.py`, que comprueban manifiesto, recursos, regla, guía embebida y los checksums publicados de `dist/`.
- El APK se compila y firma en `.github/workflows/build-apk.yml`; se publica en `dist/` y como release de GitHub. La clave de firma es pública y está documentada en `android/keystore/README.md`.
- La guía web incorpora una sección de Android con los pasos de instalación, el permiso opcional por ADB y la verificación del archivo.
- Las comprobaciones son estáticas y de CI: la instalación y el comportamiento real deben verificarse en el teléfono de destino. No se atribuye carácter oficial al calendario.

## 2026-10-27 · Verificación de reglas y actualización de la aplicación

- La aplicación PowerShell pasa a la versión 1.9.0 y lee la vista real del Registro para validar tipos y bytes TZI de 2026 y 2027, además de decodificar la transición almacenada.
- La escritura conserva otros años y metadatos; se eliminó el borrado de toda la subclave `Dynamic DST`. La aplicación vuelve a leer los valores después de aplicarlos y aborta si no puede validar un respaldo previo.
- El informe, la comprobación post-reinicio, el estado visual y `-VerifyOnly` muestran una corrección pendiente cuando las reglas no coinciden.
- Se actualizaron guía, README, validador y CI para los archivos activos. CI comprueba estáticamente los datos, valida el JavaScript del verificador y analiza la sintaxis PowerShell 5.1 sin ejecutar ni modificar el Registro.
- Las pruebas locales no pueden sustituir una prueba funcional en Windows; el resultado efectivo debe verificarse en un equipo de destino. No se atribuye carácter oficial al calendario.

## 2026-10-26 · Revisión de interfaz y seguridad

- Se añadió una guía web adaptable en español, con explicación del alcance, pasos de instalación, preguntas frecuentes y advertencias explícitas sobre la condición independiente del proyecto.
- Se añadió un verificador SHA-256 que procesa el archivo seleccionado localmente en el navegador y no lo carga a un servidor.
- Se corrigió el tamaño del valor `Dynamic DST\2027` en ambos perfiles: el dato TZI tenía 47 bytes; ahora tiene 44 bytes, conservando los campos de offset y transición que estaban codificados.
- Se añadieron un validador fail-closed para las rutas y los datos permitidos y una comprobación de CI para mantener sincronizados los hashes publicados.
- Se documentó que la estructura y el hash no certifican que las fechas sean una disposición oficial vigente.
