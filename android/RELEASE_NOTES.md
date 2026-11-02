# Cuba Hora 2027 · v1.2.0

Versión de mantenimiento centrada en dos cosas: corregir el hueco visual de la
tarjeta *Regla del proyecto* y añadir un manual con las formas reales de aplicar
el cambio.

## Qué cambia

- **Tarjeta REGLA DEL PROYECTO corregida.** El separador ya no aparece pegado al
  título dejando un hueco; ahora el título lleva a la derecha el tramo vigente
  (UTC−04:00 o UTC−05:00), cada valor ocupa su propia línea y el texto de la
  regla va en una caja con fondo. Los valores largos («7 de noviembre de 2027,
  01:00 → UTC−05:00») ya no se aprietan contra la etiqueta.
- **Manual de aplicación nuevo.** Pantalla con las tres vías: Ajustes del sistema,
  comando por ADB sin root y zona automática, más verificación y reversión. Cada
  comando se copia al portapapeles con un toque.
- **Comando que sí cambia la zona.** `adb shell cmd alarm set-timezone ...`
  funciona sin root porque `com.android.shell` declara `SET_TIME_ZONE`. La app lo
  documenta y copia el comando del tramo vigente.
- **Capturas de pantalla** del inicio, de la tarjeta corregida y del manual en
  `docs/capturas/` (render con las medidas reales del código).

## Instalación

1. Descarga `CubaHora2027-v1.2.0.apk` en el teléfono.
2. Permite instalar apps desconocidas para el explorador o el navegador que
   uses y acepta el aviso de Play Protect.
3. Android 5.0 o superior. Compatible con Samsung Galaxy S22 y otros móviles.

Se actualiza encima de la 1.1.0 sin desinstalar: usa la misma identidad de firma.

## Aplicar el cambio

| Vía | Pasos |
|---|---|
| Sin PC | Ajustes → Administración general → Fecha y hora → desactiva «Zona horaria automática» → elige **Santo Domingo** (UTC−04:00) o **Bogotá** (UTC−05:00). |
| Con PC, sin root | `adb shell cmd alarm set-timezone America/Santo_Domingo` y `adb shell settings put global auto_time_zone 0` |
| Revertir | `adb shell cmd alarm set-timezone America/Havana` o activa de nuevo la zona automática. |

## Verificación

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs CubaHora2027-v1.2.0.apk
```

- SHA-256 del APK: ver `SHA256SUMS` adjunto y `dist/README.md`.
- SHA-256 del certificado de firma:
  `95:82:38:D5:49:D6:98:04:D0:E6:94:63:3E:45:C5:7C:60:1D:12:26:C1:F9:DF:E9:23:08:88:F2:77:A0:30:48`

## Aviso

Proyecto independiente y no oficial. No está afiliado a Microsoft, a Samsung ni
a una institución del Gobierno de Cuba. Las fechas aplicadas son las del perfil
publicado en el repositorio y no constituyen una certificación oficial;
comprueba la normativa vigente antes de modificar un equipo.
