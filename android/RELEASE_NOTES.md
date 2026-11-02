# Cuba Hora 2027 · v1.1.0

Versión del APK que corrige el ajuste de la zona horaria: ahora la app dice la
verdad sobre lo que Android permite y guía el cambio paso a paso.

## Qué cambia

- **Se eliminó el cambio automático de zona.** Android reserva `SET_TIME_ZONE`
  a aplicaciones del sistema (`signature|privileged|role`) y escribir
  `time_zone` en los ajustes globales no aplica nada. La versión 1.0.0 podía
  informar de un cambio que el teléfono no había hecho.
- **Sin permisos de sistema.** Ya no declara `WRITE_SECURE_SETTINGS` ni
  `SET_TIME_ZONE`; solo notificaciones locales, aviso al reiniciar y alarmas
  exactas opcionales.
- **Pasos exactos para One UI.** La app abre los ajustes de fecha y hora y
  muestra la zona fija que hay que elegir —Santo Domingo para UTC−04:00,
  Bogotá para UTC−05:00—, sus alternativas y el valor GMT equivalente. Los
  pasos se pueden copiar al portapapeles.
- **Estado más claro.** Separa el desfase del teléfono del que exige la regla y
  avisa cuando la zona automática puede deshacer el ajuste manual.
- **Recordatorios ampliados.** Aviso 7 días antes, 24 horas antes y en el
  momento de la transición; en Android 13+ la app pide permiso de
  notificaciones al abrirse.

## Instalación

1. Descarga `CubaHora2027-v1.1.0.apk` en el teléfono.
2. Permite instalar apps desconocidas para el explorador o el navegador que
   uses y acepta el aviso de Play Protect.
3. Android 5.0 o superior. Compatible con Samsung Galaxy S22 y otros móviles.

La instalación sobre la versión 1.0.0 se actualiza sin desinstalar: usa la
misma identidad de firma del proyecto.

## Verificación

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs CubaHora2027-v1.1.0.apk
```

- SHA-256 del APK: ver `SHA256SUMS` adjunto y `dist/README.md`.
- SHA-256 del certificado de firma:
  `95:82:38:D5:49:D6:98:04:D0:E6:94:63:3E:45:C5:7C:60:1D:12:26:C1:F9:DF:E9:23:08:88:F2:77:A0:30:48`

## Aviso

Proyecto independiente y no oficial. No está afiliado a Microsoft, a Samsung ni
a una institución del Gobierno de Cuba. Las fechas aplicadas son las del perfil
publicado en el repositorio y no constituyen una certificación oficial;
comprueba la normativa vigente antes de modificar un equipo.
