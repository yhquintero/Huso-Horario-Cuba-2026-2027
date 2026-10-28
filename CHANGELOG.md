# Registro de cambios

Los cambios de este proyecto se anotan aquí para facilitar la revisión de los perfiles y de la guía.

## 2026-10-27 · Verificación de reglas y actualización de la aplicación

- La aplicación PowerShell pasa a la versión 1.9.0 y lee la vista real del Registro para validar tipos y bytes TZI de 2026 y 2027, además de decodificar la transición almacenada.
- La escritura conserva otros años y metadatos; se eliminó el borrado de toda la subclave `Dynamic DST`. La aplicación vuelve a leer los valores después de aplicarlos y aborta si no puede validar un respaldo previo.
- El informe, la comprobación post-reinicio, el estado visual y `-VerifyOnly` muestran una corrección pendiente cuando las reglas no coinciden.
- Se actualizaron guía, README, validador y CI para los archivos activos. CI comprueba estáticamente los datos, valida el JavaScript del verificador y analiza la sintaxis PowerShell 5.1 sin ejecutar ni modificar el Registro.
- Las pruebas locales no pueden sustituir una prueba funcional en Windows; el resultado efectivo debe verificarse en un equipo de destino. No se atribuye carácter oficial al calendario.

## 2026-10-26 · Revisión de interfaz y seguridad

- Se añadió una guía web adaptable en español, con explicación del alcance, pasos de instalación, preguntas frecuentes y advertencias explícitas sobre la condición independiente del proyecto.
- Se añadió un verificador SHA-256 que procesa el archivo seleccionado localmente en el navegador y no lo carga a un servidor.
- Se corrigió el tamaño del valor `Dynamic DST\2027` en ambos perfiles: el dato TZI tenía 47 bytes; ahora tiene 44 bytes, conservando los campos de offset y transición que estaban codificados.
- Se añadieron un validador fail-closed para las rutas y los datos permitidos y una comprobación de CI para mantener sincronizados los hashes publicados.
- Se documentó que la estructura y el hash no certifican que las fechas sean una disposición oficial vigente.
