# Seguridad · perfiles de zona horaria de Windows

La importación de un `.reg` puede escribir en el Registro del equipo. Este proyecto trata los perfiles como cambios sensibles y limita lo que sus comprobaciones aceptan. Aun así, la verificación de estructura **no garantiza** que una regla sea oficial, que todas las ediciones de Windows la interpreten igual ni que una máquina concreta esté libre de otros riesgos.

## Límites y alcance

Los dos perfiles están restringidos a estas rutas:

```text
HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time
HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time\Dynamic DST
```

No se permiten otras claves, secciones que eliminen claves ni nombres de valores fuera de la lista validada por [`tools/verify_registry.py`](tools/verify_registry.py). La prueba valida además la codificación de cada archivo, los valores esperados, el tamaño de 44 bytes de los datos TZI y la coincidencia de los tamaños y hashes publicados en `index.html` y `README.md` con los archivos versionados.

El alcance anterior es una garantía del **validador del repositorio ante cambios revisados**; no es una firma digital ni protege frente a que un publicador malicioso cambie simultáneamente el archivo, la página y la prueba. La regla TZI de 2027 expresa datos del proyecto, no una validación jurídica. Confirma las fechas con una fuente oficial antes de aplicar el perfil.

## Antes de importar

1. Descarga el archivo directamente desde el repositorio que has decidido confiar.
2. Compara su SHA-256 completo con el publicado en [`README.md`](README.md) o en la página. Si difiere, no lo importes.
3. Abre el archivo en un editor de texto. Comprueba el encabezado y que solo aparecen la clave de `Cuba Standard Time` y la subclave `Dynamic DST`.
4. En el Editor del Registro, exporta la clave existente de `Cuba Standard Time` si la hay. Si necesitas una recuperación más amplia, crea también un punto de restauración o sigue el procedimiento de respaldo de tu organización.
5. Importa solo en un equipo que tengas autorización para administrar. Lee el aviso de Windows antes de aceptarlo. Si el equipo está administrado por una organización, consulta a su soporte técnico.

Una importación `.reg` combina los valores del archivo con los que ya existen: una exportación previa puede restaurar valores guardados, pero no elimina automáticamente claves o valores nuevos que no aparecían en la copia. No es una reversión completa. Para revertir todo el cambio, utiliza el punto de restauración o asistencia técnica. No borres claves manualmente si no conoces sus efectos.

## SHA-256 y verificador de la página

La guía calcula el SHA-256 con `crypto.subtle` en el navegador. El archivo seleccionado se procesa localmente; el JavaScript no contiene código para enviarlo a un servidor. Para que el API criptográfico esté disponible, abre la página por HTTPS o en un contexto seguro compatible.

Una coincidencia confirma que el archivo tiene los mismos bytes que el hash que figura en la página. **No prueba la identidad de quien publicó la página**, la autenticidad del repositorio por sí sola ni la corrección legal de la regla. Los perfiles no están firmados digitalmente.

## Buenas prácticas de desarrollo

- No añadir dependencias remotas, telemetría ni una carga del archivo de usuario.
- Mantener permitidas solo las dos rutas y los nombres de valores necesarios.
- Ejecutar `python3 tools/verify_registry.py` antes de proponer cambios.
- Revisar cambios en los `.reg`, sus hashes y el validador en conjunto.
- Mantener el flujo de CI con permisos de solo lectura y la acción de checkout fijada a un commit.

## Reportar un problema

Si encuentras una vulnerabilidad, evita publicar detalles de explotación, archivos modificados u otra información sensible en un issue público. Si el repositorio ofrece **GitHub Private Vulnerability Reporting**, envía el informe desde **Security → Report a vulnerability**. Si esa opción no está disponible, abre un issue solicitando un canal privado, sin incluir detalles técnicos, contraseñas, datos personales ni información confidencial.

Los errores de calendario o compatibilidad también deben reportarse, indicando el nombre del perfil, la versión de Windows y el resultado observado. No envíes exportaciones del Registro que contengan datos del equipo.
