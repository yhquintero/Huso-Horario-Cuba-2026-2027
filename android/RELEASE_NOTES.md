# Cuba Hora 2027 · v1.5.0

Perfecciona el uso horario **Cuba 2026-2027** y el cambio automático: más
fiable, más claro y con diagnóstico.

## Por qué este cambio

Muchos teléfonos no cambian la hora con un solo comando: el gestor de root
usa `su` de otra forma, la red devuelve la zona, o el comando se acepta pero
la verificación tarda. Esta versión lo explica sin rodeos, prueba varias
órdenes y deja un informe que se puede copiar.

## Qué cambia

- **El perfil Cuba 2026-2027, explicado como es.** Android no deja crear un
  nombre nuevo en la lista de zonas sin modificar la partición del sistema;
  solo una app del sistema puede. El perfil de esta app hace lo equivalente:
  pone la zona fija correcta (Santo Domingo para UTC−04:00, Bogotá para
  UTC−05:00), del **01/11/2026 al 07/11/2027**, y la cambia sola el 07/11/2027
  a la 01:00. El diálogo «sin canal» ya lo dice así.
- **Cambio automático más resistente.** Por cada intento se prueban, en orden,
  `cmd alarm set-timezone`, `setprop persist.sys.timezone` y
  `settings put global time_zone`; después de cada una se verifica con
  `getprop persist.sys.timezone` hasta 10 veces. Se registra qué orden funcionó.
- **Root compatible con más teléfonos.** `RootChannel` prueba `su -c`,
  `su 0 -c` y `su root -c`, cierra la entrada para no colgarse y espera
  12 s. Guarda la variante que funcionó y la reutiliza.
- **Diagnóstico con un botón.** «Diagnosticar por qué no cambia» recopila el
  desfase, las zonas, Shizuku/root, el resultado de la prueba y qué hacer;
  se puede copiar al portapapeles.
- **Mensajes que no se inventan el resultado.** «Aplicado y verificado» solo
  cuando el sistema lo confirma; «sin verificar» o «falló» con el detalle real
  y el acceso directo al informe.

## Verificación

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs CubaHora2027-v1.5.0.apk
```

Certificado de firma (idéntico al de 1.1.0–1.4.0):
`95:82:38:D5:49:D6:98:04:D0:E6:94:63:3E:45:C5:7C:60:1D:12:26:C1:F9:DF:E9:23:08:88:F2:77:A0:30:48`

## Límites conocidos

- Sin Shizuku, sin root y sin PC, el cambio de 2027 sigue siendo manual: Android
  no permite a una app instalada cambiarlo. La app avisa y guía.
- Con Shizuku, conviene activar «Iniciar al arrancar» para que el canal siga
  vivo después de reiniciar. Con root depende del gestor.
- La fecha aplicada es la del perfil de este repositorio, no una certificación
  oficial.

## Aviso

Proyecto independiente y no oficial. No está afiliado a Microsoft, a Samsung ni
a una institución del Gobierno de Cuba. Comprueba la normativa vigente antes de
modificar un equipo.
