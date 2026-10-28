# Seguridad · perfiles de zona horaria de Windows

La importación de un `.reg` y la aplicación PowerShell pueden modificar datos del Registro bajo `HKEY_LOCAL_MACHINE`. Este proyecto limita y comprueba el alcance declarado, pero las pruebas estáticas no garantizan que una regla sea oficial, que cada versión de Windows la interprete igual ni que un equipo concreto esté libre de otros riesgos.

## Archivos y alcance

El perfil activo [`2_Cuba_2027_Correcto.reg`](.reg/2_Cuba_2027_Correcto.reg) solo escribe en estas claves:

```text
HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time
HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time\Dynamic DST
```

El archivo [`1_Limpiar.reg`](.reg/1_Limpiar.reg) es distinto: elimina completamente `Dynamic DST`, incluidas entradas históricas. No es necesario para instalar ni para revertir el perfil. No lo importes salvo que quieras expresamente eliminar esa subclave y dispongas de un respaldo apropiado.

La aplicación [`.ps1/CubaHora2027.ps1`](.ps1/CubaHora2027.ps1) utiliza la vista de Registro de Windows de 64 bits cuando está disponible. Antes de escribir, exporta la definición existente; si hay una clave previa y no puede validar el archivo de respaldo, cancela la aplicación. Actualiza solo los valores que administra, conserva los demás años y vuelve a leer los valores TZI de 2026 y 2027 para comparar tipos, bytes y transición codificada. No elimina la subclave `Dynamic DST`. Al aplicar, puede reiniciar W32Time y pedir una resincronización, pero no configura servidores NTP. Escribe logs en `%TEMP%` y preferencias, historial y respaldos en `%APPDATA%\CubaHora2027`; la creación de un acceso directo es opcional.

El validador [`tools/verify_registry.py`](tools/verify_registry.py) comprueba el alcance y estructura de los `.reg`, los bytes TZI, su coincidencia con los valores embebidos en la aplicación y los tamaños y SHA-256 publicados. CI comprueba la sintaxis del JavaScript y analiza el script con Windows PowerShell 5.1, sin ejecutar la aplicación ni escribir en el Registro. Estos controles ayudan a mantener la consistencia de los cambios revisados; no son una firma digital y no certifican el resultado efectivo ni la validez legal del calendario.

## Antes de aplicar

1. Confirma en una fuente oficial que los datos horarios propuestos son los que necesitas. El proyecto no está afiliado a Microsoft ni a una institución del Gobierno de Cuba.
2. Descarga el perfil directamente desde el repositorio que has decidido confiar y compara el SHA-256 completo con [`README.md`](README.md) o con la página. Si no coincide, no lo importes.
3. Inspecciona el contenido en un editor de texto. El perfil activo no debe incluir rutas ajenas a `Cuba Standard Time` y `Dynamic DST`; no confundas el perfil con el archivo de limpieza.
4. Exporta la clave existente de `Cuba Standard Time` y crea un punto de restauración o sigue el procedimiento de respaldo de tu organización.
5. Aplica solo en un equipo que tengas autorización para administrar. Lee la solicitud UAC y los avisos de Windows antes de aceptarlos. Consulta con soporte si el dispositivo está administrado.
6. Después de aplicar, ejecuta la verificación y reinicia si Windows lo requiere. Confirma el identificador de zona con `tzutil /g` y valida el comportamiento real en ese equipo.

Una importación `.reg` combina valores con el Registro existente: reimportar una exportación previa puede restaurar valores guardados, pero no elimina automáticamente claves o valores añadidos después. No es una reversión completa. Para recuperar todo el estado, usa el punto de restauración o asistencia técnica. No borres claves manualmente si no conoces sus efectos.

## SHA-256 y privacidad

El verificador de la página calcula SHA-256 con `crypto.subtle` en el navegador. El archivo seleccionado se procesa localmente; el JavaScript de esta página no lo carga a un servidor. Para que el API criptográfico esté disponible, abre la página por HTTPS o en un contexto seguro compatible.

Una coincidencia confirma que los bytes del archivo son idénticos al valor publicado en la página. No demuestra quién publicó el archivo, no sustituye una firma digital y no valida la regla legalmente. Los `.reg` y el script PowerShell no están firmados digitalmente.

## Buenas prácticas de desarrollo

- Mantener las escrituras de la aplicación limitadas a las claves y valores documentados.
- No borrar años ni subclaves dinámicas durante la aplicación normal.
- No añadir dependencias remotas, telemetría ni carga de archivos del usuario.
- Ejecutar `python3 tools/verify_registry.py` antes de proponer cambios.
- Revisar conjuntamente `.reg/`, `.ps1/`, los hashes publicados y el validador.
- Mantener CI con permisos de solo lectura y acciones fijadas a un commit.

## Reportar un problema

Si encuentras una vulnerabilidad, evita publicar detalles de explotación, archivos modificados u otra información sensible en un issue público. Si el repositorio ofrece **GitHub Private Vulnerability Reporting**, envía el informe desde **Security → Report a vulnerability**. Si esa opción no está disponible, abre un issue solicitando un canal privado, sin incluir detalles técnicos, contraseñas, datos personales ni información confidencial.

Los errores de calendario o compatibilidad también deben reportarse indicando el perfil, la versión de Windows y el resultado observado. No envíes exportaciones del Registro que contengan datos del equipo.
