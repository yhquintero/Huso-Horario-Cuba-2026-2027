# Firma del APK

Esta carpeta contiene la identidad de firma con la que se publican los APK de
**Cuba Hora 2027**.

> [!IMPORTANT]
> La clave y su contraseña son **públicas y están versionadas a propósito**.
> No protegen ningún secreto del proyecto: su única función es que Android
> acepte las actualizaciones del APK encima de la versión ya instalada (misma
> identidad de firma). Cualquier persona puede recompilar el proyecto y firmar
> un APK con esta misma clave; la confianza debe basarse en el código fuente y
> en el SHA-256 publicado, no en la firma.

## Datos

| Dato | Valor |
|---|---|
| Archivo | `cuba-release.p12` (PKCS#12) |
| Alias | `cubahora2027` |
| Contraseña de almacén y de clave | `cuba-hora-2027-firma-publica` |
| Algoritmo | RSA 3072 bits, SHA256withRSA |
| Validez | 12 000 días (hasta 2059) |
| SHA-256 del certificado | `95:82:38:D5:49:D6:98:04:D0:E6:94:63:3E:45:C5:7C:60:1D:12:26:C1:F9:DF:E9:23:08:88:F2:77:A0:30:48` |

Puedes comprobar la huella de un APK publicado con:

```bash
apksigner verify --print-certs CubaHora2027-v1.0.0.apk
```

El campo `Signer #1 certificate SHA-256 digest` debe coincidir con la huella de
la tabla.

## Uso en la compilación

`android/app/build.gradle` lee `keystore.properties` automáticamente. Si
prefieres no usar esta identidad, define las variables de entorno
`CUBA_KEYSTORE_PATH`, `CUBA_KEYSTORE_PASSWORD`, `CUBA_KEY_ALIAS` y
`CUBA_KEY_PASSWORD` con tu propia clave, o compila sin ninguna de las dos cosas:
el APK saldrá firmado con la clave de depuración (también instalable).

Generar una clave nueva:

```bash
keytool -genkeypair \
  -keystore cuba-release.p12 -storetype PKCS12 \
  -alias cubahora2027 -keyalg RSA -keysize 3072 \
  -validity 12000 \
  -dname "CN=Cuba Hora 2027, O=Huso-Horario-Cuba-2026-2027, C=CU"
```
