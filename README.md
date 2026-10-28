# Huso horario de Cuba · Windows 2026–2027

Guía independiente para revisar una definición de zona horaria de Cuba en Windows, entender su alcance y verificar los datos antes de aplicarlos.

> [!IMPORTANT]
> **Proyecto independiente y no oficial.** No está afiliado a Microsoft ni a una institución del Gobierno de Cuba. Los valores publicados son datos propuestos para 2026–2027; no certifican una disposición oficial ni cómo cada versión de Windows los aplicará. Confirma la normativa vigente en una fuente oficial y prueba en un entorno adecuado antes de modificar un equipo.

## Archivos vigentes

| Archivo | Uso | Contenido |
|---|---|---|
| [`.ps1/CubaHora2027.ps1`](.ps1/CubaHora2027.ps1) | Aplicación PowerShell 1.9.0 | Escribe los valores gestionados, selecciona la zona, vuelve a leer la regla del Registro y deja pendiente el estado si los valores no coinciden. |
| [`.reg/2_Cuba_2027_Correcto.reg`](.reg/2_Cuba_2027_Correcto.reg) | Perfil de Registro activo | Valores base y entradas `Dynamic DST` para 2026 y 2027. UTF-8. |
| [`.reg/1_Limpiar.reg`](.reg/1_Limpiar.reg) | Limpieza avanzada, no instalación | Elimina por completo la subclave `Dynamic DST`; puede borrar reglas históricas. No es necesario para instalar ni revierte el perfil. |
| [`index.html`](index.html) | Guía web | Explica el alcance y ofrece verificación SHA-256 local en el navegador. |
| [`tools/verify_registry.py`](tools/verify_registry.py) | Validador estático | Comprueba el alcance de los `.reg`, los TZI y su coherencia con los bytes y verificaciones estáticas de la aplicación. |
| [`SECURITY.md`](SECURITY.md) | Seguridad | Precauciones, recuperación y límites de verificación. |

Los archivos de `.reg/Salvas/` son respaldos o referencias históricas; no son los perfiles activos recomendados aquí.

## Qué codifican los datos TZI

El perfil activo declara `FirstEntry=2026` y `LastEntry=2027`. Cada valor TZI mide 44 bytes, el tamaño de `REG_TZI_FORMAT` de Windows:

- **2026:** `Bias=240` (desfase base UTC−04:00); ambos campos `SYSTEMTIME` de transición están vacíos. El dato no codifica un cambio el 1 de noviembre de 2026.
- **2027:** `Bias=300` (desfase base UTC−05:00); `StandardDate` está configurado para el primer domingo de noviembre a la 01:00, que al decodificarlo para 2027 resulta en el 7 de noviembre. `DaylightDate` está vacío.

Esta es una lectura de los bytes almacenados, no una prueba de la fecha efectiva que mostrará Windows en todos los equipos ni una afirmación de que la regla sea oficial. La aplicación y el validador informan si el Registro coincide exactamente con estos datos, no si el calendario tiene aprobación legal.

## Uso manual del perfil `.reg`

1. Confirma por una fuente oficial que quieres usar estos datos y que tienes autorización para administrar el equipo.
2. Descarga únicamente [`.reg/2_Cuba_2027_Correcto.reg`](.reg/2_Cuba_2027_Correcto.reg) desde el repositorio. **No uses `1_Limpiar.reg` para instalar:** ese archivo elimina toda la subclave `Dynamic DST`.
3. Comprueba el SHA-256 publicado abajo. Si no coincide, no importes el archivo.
4. Inspecciona el `.reg` en un editor de texto. Sus únicas secciones deben ser la clave `Cuba Standard Time` y su subclave `Dynamic DST`.
5. Exporta la clave existente y crea un punto de restauración antes de cambiarla. Reimportar una exportación `.reg` no elimina automáticamente claves o valores que se hayan añadido; una reversión completa requiere un método apropiado.
6. Importa el perfil solo después de revisar el aviso de Windows. El `.reg` define los datos, pero no selecciona por sí solo la zona activa. Si corresponde, selecciónala en **Configuración → Hora e idioma → Fecha y hora**.
7. Consulta la zona activa con `tzutil /g`. No cambies manualmente la fecha o la hora para probar transiciones.

## Uso de la aplicación PowerShell

La aplicación se encuentra en [`.ps1/CubaHora2027.ps1`](.ps1/CubaHora2027.ps1) y requiere Windows PowerShell 5.1. La interfaz gráfica solicita permisos de administrador mediante UAC; ejecuta `-Silent` desde una consola ya elevada (si no, sale con error sin aplicar cambios). `-VerifyOnly` es de solo lectura y no requiere elevación. Antes de aplicar cambios, inspecciona el código y confirma que la regla propuesta es la que necesitas; el archivo no tiene firma digital.

La aplicación crea un respaldo de la definición existente y cancela si no puede validar ese respaldo. Solo actualiza los valores que administra; conserva otros años y metadatos en `Dynamic DST`. Después de escribir, vuelve a leer las etiquetas base, el `TZI` principal, `FirstEntry`, `LastEntry` y las entradas `2026` y `2027`; valida tipos y bytes, y calcula la transición a partir del `StandardDate` leído. La interfaz y el informe indican **corrección pendiente** cuando la configuración no coincide; la mera existencia de las claves no basta. Al aplicar, también selecciona `Cuba Standard Time`, puede reiniciar W32Time y solicita una resincronización; no configura servidores NTP. Guarda logs en `%TEMP%` y preferencias, historial y respaldos en `%APPDATA%\CubaHora2027`; el acceso directo de escritorio es opcional. Puede ser necesario reiniciar Windows para confirmar el comportamiento efectivo.

Modos de línea de comandos:

```powershell
# Informe de verificación; sale con 0 si las reglas coinciden y 1 si quedan pendientes.
powershell.exe -NoProfile -File .\.ps1\CubaHora2027.ps1 -VerifyOnly

# Desde una consola elevada: aplica sin interfaz y devuelve error si falla.
powershell.exe -NoProfile -File .\.ps1\CubaHora2027.ps1 -Silent
```

La aplicación no usa `-ExecutionPolicy Bypass`; respeta la política configurada en el equipo. No la ejecutes en dispositivos administrados sin autorización.

## Integridad publicada

SHA-256 de los bytes exactos versionados:

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `2_Cuba_2027_Correcto.reg` | 829 bytes | `7E9F4CD428AFC6D968C9824EFC11D99AE0272E0CC005E7F4E4724E1654B77B48` |
| `1_Limpiar.reg` | 146 bytes | `AA42596AA6EAE80298F267992DEE11A7819C6473B88E6BD879C7E1716DFC34E0` |

El verificador web procesa el archivo seleccionado localmente y no lo envía a un servidor. Una coincidencia SHA-256 confirma igualdad de bytes con el valor publicado; no demuestra la identidad del publicador, no es una firma digital y no valida la regla legalmente.

## Validación de cambios

Ejecuta desde la raíz del repositorio:

```bash
python3 tools/verify_registry.py
```

El validador comprueba las dos rutas permitidas del perfil activo, los tipos y datos de cada año, que la fecha de 2027 se decodifique desde `StandardDate`, que las matrices de bytes de la aplicación coincidan con el `.reg`, y que el archivo de limpieza solo elimine `Dynamic DST`. También compara enlaces, tamaños y SHA-256 en `index.html` y este README. GitHub Actions ejecuta estas comprobaciones, valida la sintaxis del JavaScript del verificador SHA-256 y analiza la sintaxis de PowerShell con Windows PowerShell 5.1; no aplica cambios en un Registro real.

Las pruebas estáticas no sustituyen la ejecución controlada en Windows. Comprueba la interfaz, la lectura posterior a la escritura y el resultado efectivo de la transición en las versiones de Windows que correspondan. Ninguna prueba de este repositorio certifica fechas oficiales.

## Proyecto y reportes

- [Política de seguridad](SECURITY.md)
- [Registro de cambios](CHANGELOG.md)
- [Código fuente en GitHub](https://github.com/yhquintero/Huso-Horario-Cuba-2026-2027)

Si sospechas de una vulnerabilidad, sigue el procedimiento privado descrito en [`SECURITY.md`](SECURITY.md). No publiques contraseñas, datos personales ni exportaciones del Registro que contengan información del equipo.
