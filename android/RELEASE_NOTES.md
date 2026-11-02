# Cuba Hora 2027 · v1.0.0

Primera versión del APK para Android del proyecto **Huso horario de Cuba
2026–2027**.

## Qué incluye

- Estado real del teléfono: zona horaria, desfase efectivo, hora local, hora de
  Cuba según la regla y ajuste automático.
- Comparación con la regla del repositorio: 2026 completo en UTC−04:00 y cambio
  a UTC−05:00 el **7 de noviembre de 2027 a la 01:00**.
- Ajuste de la zona con un toque cuando el permiso opcional
  `WRITE_SECURE_SETTINGS` está concedido; si no, la app explica cómo usar los
  ajustes del sistema.
- Botones para usar `America/Havana` y para restaurar la zona automática.
- Recordatorios locales 24 h antes y en el momento de la transición.
- Guía completa del proyecto disponible sin conexión dentro de la app.

## Instalación

1. Descarga `CubaHora2027-v1.0.0.apk` en el teléfono.
2. Permite instalar apps desconocidas para el explorador o el navegador que
   uses y acepta el aviso de Play Protect.
3. Android 5.0 o superior. Compatible con Samsung Galaxy S22 y otros móviles.

## Verificación

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs CubaHora2027-v1.0.0.apk
```

- SHA-256 del APK: ver `SHA256SUMS` adjunto y `dist/README.md`.
- SHA-256 del certificado de firma:
  `95:82:38:D5:49:D6:98:04:D0:E6:94:63:3E:45:C5:7C:60:1D:12:26:C1:F9:DF:E9:23:08:88:F2:77:A0:30:48`

## Permiso opcional

Para que la app aplique el cambio de zona sin pasar por los ajustes:

```bash
adb shell pm grant com.yhquintero.cubahora2027 android.permission.WRITE_SECURE_SETTINGS
```

## Aviso

Proyecto independiente y no oficial. No está afiliado a Microsoft, a Samsung ni
a una institución del Gobierno de Cuba. Las fechas aplicadas son las del perfil
publicado en el repositorio y no constituyen una certificación oficial;
comprueba la normativa vigente antes de modificar un equipo.
