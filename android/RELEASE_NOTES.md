# Cuba Hora 2027 · v1.4.0

Perfil **Cuba 2026-2027** instalable, textos corregidos y más formas de activar
el cambio automático.

## Qué cambia

- **El estado del cambio automático ya no se parte.** La tarjeta muestra el
  valor corto en su propia línea («Desactivado») y debajo el detalle del canal:
  «Sin canal: ni Shizuku ni root», «Shizuku detenido», «Activo con root»… Antes
  el texto se partía en la fila a dos columnas y se cortaba.
- **Perfil Cuba 2026-2027 en su propia tarjeta:** del **01/11/2026 al
  07/11/2027**, UTC−04:00 en todo el tramo y UTC−05:00 desde el 07/11/2027 a la
  01:00. Un botón lo instala: elige canal, arma las alarmas y aplica el desfase
  vigente.
- **Canales nuevos, uno por botón:**
  - **Shizuku** (sin root), como antes.
  - **Root (`su`)**: la app comprueba root al abrirse y, al pulsar el botón, pide
    permiso al gestor y ejecuta los mismos comandos. Sin root no se ejecuta nada.
  - **Comandos para ADB**: se copian al portapapeles listos para pegar.
  - **Ajuste manual** en Ajustes, con la zona fija recomendada.
- La app sigue usando el primer canal disponible y **verificando** el resultado
  con `getprop persist.sys.timezone`: nunca informa de un cambio sin comprobarlo.

## Verificación

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs CubaHora2027-v1.4.0.apk
```

Certificado de firma (idéntico al de 1.1.0–1.3.0):
`95:82:38:D5:49:D6:98:04:D0:E6:94:63:3E:45:C5:7C:60:1D:12:26:C1:F9:DF:E9:23:08:88:F2:77:A0:30:48`

## Límites conocidos

- Sin Shizuku, sin root y sin PC, el cambio de 2027 sigue siendo manual: Android
  no permite a una app instalada cambiarlo. La app avisa y guía.
- Con Shizuku, conviene activar «Iniciar al arrancar» para que el canal siga
  vivo después de reiniciar. Con root depende del gestor de root.
- La fecha aplicada es la del perfil de este repositorio, no una certificación
  oficial.

## Aviso

Proyecto independiente y no oficial. No está afiliado a Microsoft, a Samsung ni
a una institución del Gobierno de Cuba. Comprueba la normativa vigente antes de
modificar un equipo.
