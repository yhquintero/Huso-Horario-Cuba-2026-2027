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
| `CubaHora2027-v1.3.0.apk` | 1.3.0 (versionCode 4) | 114,575 bytes | `CE82C1E3405D44D3E414E7D1ECDB71B15991C578EFF0632F01669711026D9D4F` |

- **Android mínimo:** 6.0 (API 23, el mínimo de Shizuku). Probado con el flujo
  de compilación para API 34; funciona en Samsung Galaxy S22 y en otros
  teléfonos con Android 6.0 o superior.
- **Firma:** SHA-256 del certificado
  `95:82:38:D5:49:D6:98:04:D0:E6:94:63:3E:45:C5:7C:60:1D:12:26:C1:F9:DF:E9:23:08:88:F2:77:A0:30:48` (identidad pública del proyecto, ver
  `android/keystore/README.md`).
- **Permisos:** ninguno de sistema ni peligroso. No pide INTERNET y no puede
  leer datos personales. Pide solo notificaciones locales y, opcionalmente,
  alarmas exactas para los recordatorios.
- **Cambio automático:** la app no puede cambiar la zona por sí misma (Android
  reserva `SET_TIME_ZONE` a las aplicaciones del sistema). Con **Shizuku**
  instalado y autorizado una vez por el usuario, un solo botón activa el
  cambio: el 7 de noviembre de 2027 a la 01:00 aplica UTC−05:00 y verifica el
  resultado. Sin Shizuku, muestra el desfase correcto y los pasos para
  ajustarlo en Ajustes.

## Instalación

1. Descarga `CubaHora2027-v1.3.0.apk` en el teléfono.
2. Abre el archivo; si Android lo bloquea, permite «Instalar aplicaciones
   desconocidas» para la app con la que lo abriste (Archivos o Chrome).
3. Acepta el aviso de Play Protect y termina la instalación.

También se publica como adjunto en la release de GitHub del repositorio, con
este mismo `SHA256SUMS`.

## Comprobación

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs CubaHora2027-v1.3.0.apk
```
