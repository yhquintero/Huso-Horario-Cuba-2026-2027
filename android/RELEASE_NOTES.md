# Cuba Hora 2027 · v1.3.0

Cambio automático del huso horario en la transición de 2027, con un solo botón.

## Qué cambia

- **Un botón lo hace todo.** «Activar el cambio automático» comprueba el canal
  privilegiado, pide autorización si hace falta, arma las alarmas y aplica el
  desfase vigente. A partir de ahí, el **7 de noviembre de 2027 a la 01:00** el
  teléfono pasa a UTC−05:00 solo.
- **Canal con Shizuku.** La app ejecuta como shell `settings put global
  auto_time_zone 0`, `cmd alarm set-timezone` y `getprop persist.sys.timezone`.
  Shizuku se instala aparte, no necesita root y la autorización se concede una
  vez. Sin Shizuku la app sigue funcionando en modo aviso.
- **Nunca informa de un éxito falso.** Cada aplicación se verifica leyendo la
  zona del sistema; el resultado real se avisa por notificación (aplicado,
  sin verificar o pendiente) y se guarda en la app.
- **«Probar ahora»** aplica el desfase vigente sin esperar a 2027, para
  comprobar en segundos que el canal funciona.
- **Android 6.0 o superior** (mínimo de Shizuku) y dos dependencias fijadas:
  `dev.rikka.shizuku:api` y `:provider` 12.1.0. Sigue sin pedir INTERNET.

## Cómo activarlo

1. Instala `CubaHora2027-v1.3.0.apk` (actualiza sobre 1.2.0 sin desinstalar).
2. Instala [Shizuku](https://github.com/RikkaApps/Shizuku) (Play Store o GitHub).
3. Ajustes → Opciones de desarrollador → activa «Depuración inalámbrica».
4. En Shizuku: *Emparejar* con la depuración inalámbrica y *Iniciar*.
5. Abre Cuba Hora 2027 → **Activar el cambio automático** → autoriza.

Si no quieres instalar Shizuku, la tarjeta de estado y el manual siguen
indicando la zona exacta para aplicarla desde Ajustes en un minuto.

## Verificación

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs CubaHora2027-v1.3.0.apk
```

- SHA-256 del APK: ver `SHA256SUMS` adjunto y `dist/README.md`.
- Certificado de firma (idéntico al de 1.1.0 y 1.2.0):
  `95:82:38:D5:49:D6:98:04:D0:E6:94:63:3E:45:C5:7C:60:1D:12:26:C1:F9:DF:E9:23:08:88:F2:77:A0:30:48`

## Límites conocidos

- La automatización depende de que Shizuku esté en ejecución en ese momento. Si
  se detuvo (por ejemplo tras reiniciar sin reanudarlo), recibirás un aviso con
  el resultado real y los pasos manuales; no se fingirá un cambio.
- Shizuku, Sui o root son la única vía técnica: Android reserva `SET_TIME_ZONE`
  a aplicaciones del sistema.
- La fecha aplicada es la del perfil de este repositorio, no una certificación
  oficial.

## Aviso

Proyecto independiente y no oficial. No está afiliado a Microsoft, a Samsung ni
a una institución del Gobierno de Cuba. Comprueba la normativa vigente antes de
modificar un equipo.
