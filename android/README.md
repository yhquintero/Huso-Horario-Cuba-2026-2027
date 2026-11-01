# Cuba Hora 2027 · aplicación Android

App nativa de Android, sin dependencias externas, que muestra el desfase real
del teléfono frente a la regla horaria de Cuba de este repositorio y, si el
dispositivo lo permite, aplica el cambio de zona con un toque.

- **APK publicado:** [`dist/`](../dist/) y la release de GitHub del proyecto.
- **Paquete:** `com.yhquintero.cubahora2027`
- **Android mínimo:** 5.0 (API 21). Compilado contra API 34.
- **Tamaño:** menos de 1 MB. No pide INTERNET ni contiene rastreadores.

## Qué hace

| Función | Detalle |
|---|---|
| Estado real | Lee `Settings.Global.TIME_ZONE`, el desfase efectivo del sistema y el ajuste automático. |
| Regla del proyecto | 2026 completo en UTC−04:00; el **7 de noviembre de 2027 a la 01:00** pasa a UTC−05:00. Sin más cambios definidos después. |
| Comparación | Indica si el teléfono coincide con la regla o si el ajuste queda pendiente. |
| Ajuste | Con el permiso de desarrollo `WRITE_SECURE_SETTINGS` escribe la zona `Etc/GMT+4` o `Etc/GMT+5` y desactiva la zona automática. Sin permiso, guía a los ajustes del sistema. |
| Zona de Cuba | Botón para usar `America/Havana` (la zona IANA que ya existe en Android). |
| Restaurar | Devuelve `America/Havana` y reactiva la zona automática. |
| Recordatorios | Notificación local 24 h antes y en el instante de la transición; se reprograma al reiniciar. |
| Guía offline | La guía web del proyecto, empaquetada en `assets/site`. |

La app **no** cambia la hora del teléfono, no modifica `tzdata` y no intenta
evadir restricciones de Android: si el permiso no está concedido, solo informa
y enlaza a los ajustes correspondientes.

## Instalación en un Samsung Galaxy S22 (y otros móviles)

1. Descarga `dist/CubaHora2027-v1.0.0.apk` en el teléfono (o desde la release).
2. Abre el archivo y, si Android lo pide, permite «Instalar aplicaciones
   desconocidas» para Archivos o Chrome. Play Protect puede advertir que la app
   no viene de Play Store: es un APK firmado por el proyecto y verificado en CI.
3. Abre **Cuba Hora 2027** y revisa la tarjeta *Estado del teléfono*.
4. Para cambiar la zona sin PC: **Ajustes → Administración general → Fecha y
   hora**, desactiva *Zona horaria automática* y elige la zona. En el S22 la
   ruta puede variar según One UI.
5. Para que la app aplique el cambio con un toque, concede el permiso opcional
   una sola vez desde un PC (ver abajo).

### Permiso opcional por ADB (una sola vez)

```bash
adb shell pm grant com.yhquintero.cubahora2027 android.permission.WRITE_SECURE_SETTINGS
```

Requisitos: *Opciones de desarrollador* activas y *Depuración USB* habilitada.
El permiso no requiere root y se revoca con `pm revoke`. Algunos fabricantes
bloquean este permiso incluso por ADB; en ese caso la app funciona en modo
informativo y el cambio se hace desde los ajustes del sistema.

## Compilar

En GitHub Actions (`.github/workflows/build-apk.yml`) el APK se genera en cada
cambio y se adjunta como artefacto; al ejecutar el flujo a mano con
`publish_release: true` se publica como release.

Localmente, con JDK 17 y Android SDK (compileSdk 34):

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
bash android/scripts/build-apk.sh     # genera app-release.apk
bash android/scripts/verify-apk.sh    # valida el APK y actualiza dist/
```

La firma se toma de `android/keystore/keystore.properties` (identidad pública
del proyecto, ver [`keystore/README.md`](keystore/README.md)) o de las variables
`CUBA_KEYSTORE_PATH`, `CUBA_KEYSTORE_PASSWORD`, `CUBA_KEY_ALIAS` y
`CUBA_KEY_PASSWORD`.

## Estructura

| Ruta | Contenido |
|---|---|
| `app/src/main/java/.../MainActivity.java` | Pantalla principal y acciones. |
| `app/src/main/java/.../CubaRules.java` | Regla horaria y cálculo de transiciones. |
| `app/src/main/java/.../ZoneController.java` | Lectura/escritura de la zona del sistema. |
| `app/src/main/java/.../ReminderScheduler.java` | Alarmas de los recordatorios. |
| `app/src/main/java/.../Notifications.java` | Canal y notificaciones locales. |
| `app/src/main/java/.../BootReceiver.java`, `TransitionReceiver.java` | Reprogramación y avisos. |
| `app/src/main/java/.../GuideActivity.java` | Guía offline en WebView sin red. |
| `app/src/main/assets/site/` | Copia generada de la guía web. |
| `app/src/main/res/` | Iconos, tema e textos. |
| `scripts/` | Compilación, verificación y publicación del APK. |
| `keystore/` | Identidad de firma pública del proyecto. |

## Verificaciones

```bash
python3 tools/verify_registry.py        # perfiles .reg (Windows)
python3 tools/verify_android.py         # regla Java vs .reg, manifiesto, recursos, guía
python3 tools/build_android_site.py --check
python3 tools/verify_release_hashes.py  # dist/ publicado
```

`verify_android.py` falla si la app pide INTERNET, si el manifiesto declara
componentes exportados de más, si la regla Java deja de coincidir con los bytes
de `.reg/2_Cuba_2027_Correcto.reg` o si la guía embebida queda desactualizada.

## Límites conocidos

- Los APK no se distribuyen por Play Store: la instalación es por *sideload*.
- Sin el permiso por ADB, la app no puede cambiar la zona por sí sola.
- Los recordatorios dependen de las notificaciones y, en Android 12+, de la
  autorización de alarmas exactas; sin ella el aviso puede llegar con retraso.
- La fecha de 2027 es una lectura de los datos del repositorio, no una
  certificación oficial. Ver el aviso del [README principal](../README.md).
