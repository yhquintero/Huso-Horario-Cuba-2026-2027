# Registro de cambios

Los cambios de este proyecto se anotan aquí para facilitar la revisión de los perfiles y de la guía.

## 2026-11-02 · Android 1.5.0: el perfil Cuba 2026-2027 explicado y diagnóstico

- El perfil Cuba 2026-2027 ya explica en la app por qué no aparece como nombre nuevo en la lista del sistema (solo una app de sistema puede añadir zonas) y qué hace en su lugar: la zona fija equivalente con cambio automático verificado.
- `AutoSwitch` prueba tres órdenes del sistema por intento y verifica con `getprop persist.sys.timezone` en hasta 10 lecturas; registra la orden que funcionó.
- `RootChannel` compatible con Magisk/KernelSU y otros gestores: prueba `su -c`, `su 0 -c` y `su root -c`, cierra la entrada, espera 12 s y reutiliza la variante que funcionó.
- Nuevo `Diagnostics` y botón «Diagnosticar por qué no cambia»: informe con estado, canales, prueba real y qué hacer, copiable.
- `MainActivity` usa el resultado detallado: «aplicado» solo si se verifica, y acceso directo al diagnóstico en cada error.
- Capturas actualizadas (inicio y más opciones con el botón de diagnóstico) y versión 1.5.0 (versionCode 6).

## 2026-11-01 · Android 1.4.0: perfil Cuba 2026-2027 y más canales

- La tarjeta de estado deja de partir el texto del cambio automático: el valor corto va en su propia línea y el detalle del canal debajo.
- Nueva tarjeta **Perfil Cuba 2026-2027** (del 01/11/2026 al 07/11/2027), con el tramo explícito y el botón «Instalar el perfil y activarlo».
- Canal nuevo por **root** (`su`): `RootChannel` comprueba root al abrir la app y ejecuta los mismos comandos que Shizuku. `AutoSwitch` elige el primer canal disponible (Shizuku → root) y verifica siempre con `getprop persist.sys.timezone`.
- Tarjeta **Más opciones para activarlo** con Shizuku, root, comandos para ADB (copiados al portapapeles) y ajuste manual en Ajustes; el botón principal abre un selector de canales cuando no hay ninguno disponible.
- `ChannelOutput` unifica la salida de los dos canales; `ShizukuChannel` ya no guarda la verificación, que vive en `AutoSwitch`.
- `CubaRules` centraliza el nombre y el tramo del perfil (`PROFILE_NAME`, `PROFILE_START`, `profileRangeLabel`).
- Capturas nuevas de inicio, manual y más opciones; `verify_android.py` valida el canal de root y el perfil.

## 2026-11-01 · Android 1.3.0: cambio automático en la transición

- Se añade el cambio automático del huso horario: el 7 de noviembre de 2027 a la 01:00 la app aplica UTC−05:00 sin intervención y verifica el resultado con `getprop persist.sys.timezone`.
- Nuevo canal privilegiado con Shizuku (`dev.rikka.shizuku:api` y `:provider` 12.1.0): la app ejecuta como shell `settings put global auto_time_zone 0` y `cmd alarm set-timezone`. Es la única vía sin root, porque Android reserva `SET_TIME_ZONE` a las aplicaciones del sistema.
- La pantalla principal estrena botón único «Activar el cambio automático», que configura el canal, pide autorización, arma las alarmas y aplica el desfase vigente; se añade «Probar ahora» para validarlo sin esperar a 2027.
- `TransitionReceiver` aplica la transición en segundo plano (`goAsync`) y distingue APPLIED / UNVERIFIED / NO_CHANNEL / FAILED; nunca informa de un éxito no verificado. `BootReceiver` rearma el cambio al reiniciar.
- `minSdk` pasa a 23 (mínimo de Shizuku) y `gradle.properties` activa AndroidX por la dependencia transitiva `androidx.annotation`. La app sigue sin pedir INTERNET.
- `verify_android.py` valida la lista exacta de dependencias, el proveedor de Shizuku, los comandos del canal y la verificación posterior; `verify-apk.sh` comprueba en el APK el permiso de cliente de Shizuku y la ausencia de permisos de sistema.
- CI: el reporte de fallos muestra el bloque «What went wrong» de Gradle, que faltaba para diagnosticar fallos del plugin.

## 2026-11-01 · Android 1.2.0: tarjeta de la regla y manual de aplicación

- Se corrige el hueco de la tarjeta **REGLA DEL PROYECTO**: el separador se insertaba bajo el título y los valores largos se apretaban contra la etiqueta. Ahora el título lleva el tramo vigente a la derecha, cada valor usa su propia línea (`addBlock`) y el texto de la regla va en una caja.
- Se añade `ManualActivity` con las tres vías para aplicar el cambio (Ajustes, comando ADB sin root y zona automática), verificación con `getprop` y reversión a `America/Havana`, con copia de comandos al portapapeles.
- Se documenta que `adb shell cmd alarm set-timezone` funciona sin root porque `com.android.shell` declara `SET_TIME_ZONE`; verificado en el código de AOSP (`AlarmManagerService.ShellCmd` y el manifiesto del paquete Shell).
- `tools/render_screens.py` genera las capturas de `docs/capturas/` con las medidas, colores y textos del código (perfil Galaxy S22).
- `tools/verify_android.py` comprueba el componente nuevo y los pasos del manual; la versión sube a 1.2.0 (versionCode 3) y se publica con la misma firma.

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
