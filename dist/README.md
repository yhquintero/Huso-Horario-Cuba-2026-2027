# APK publicado

Copia instalable de **Cuba Hora 2027** para Android, generada por
`.github/workflows/build-apk.yml` a partir del código de este repositorio.

> [!IMPORTANT]
> Proyecto independiente y no oficial. No está afiliado a Microsoft, a Samsung
> ni a una institución del Gobierno de Cuba. El calendario que aplica es el del
> perfil publicado en este repositorio (2026 en UTC−04:00 y cambio a UTC−05:00
> el 7 de noviembre de 2027 a la 01:00) y no constituye una certificación
> oficial. Comprueba la normativa vigente antes de usarlo.

## Archivo

| Archivo | Versión | Tamaño | SHA-256 |
|---|---|---:|---|
| `CubaHora2027-v1.1.0.apk` | 1.1.0 (versionCode 2) | 81,088 bytes | `A8BFD5EB156CF4017DB54B58237FBD194B99830051A7E03ADD2EDBF4F5BA7209` |

- **Android mínimo:** 5.0 (API 21). Probado con el flujo de compilación para
  API 34; funciona en Samsung Galaxy S22 y en otros teléfonos con Android 5.0
  o superior.
- **Firma:** SHA-256 del certificado
  `95:82:38:D5:49:D6:98:04:D0:E6:94:63:3E:45:C5:7C:60:1D:12:26:C1:F9:DF:E9:23:08:88:F2:77:A0:30:48` (identidad pública del proyecto, ver
  `android/keystore/README.md`).
- **Permisos:** ninguno de sistema ni peligroso. No pide INTERNET y no puede
  leer datos personales. Pide solo notificaciones locales y, opcionalmente,
  alarmas exactas para los recordatorios. La app **no** cambia la zona
  horaria: Android reserva ese cambio a aplicaciones del sistema, así que
  muestra el desfase correcto y los pasos para ajustarlo en Ajustes.

## Instalación

1. Descarga `CubaHora2027-v1.1.0.apk` en el teléfono.
2. Abre el archivo; si Android lo bloquea, permite «Instalar aplicaciones
   desconocidas» para la app con la que lo abriste (Archivos o Chrome).
3. Acepta el aviso de Play Protect y termina la instalación.

También se publica como adjunto en la release de GitHub del repositorio, con
este mismo `SHA256SUMS`.

## Comprobación

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs CubaHora2027-v1.1.0.apk
```
