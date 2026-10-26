# Registro de cambios

Los cambios de este proyecto se anotan aquí para facilitar la revisión de los perfiles y de la guía.

## 2026-10-26 · Revisión de interfaz y seguridad

- Se añadió una guía web adaptable en español, con explicación del alcance, pasos de instalación, preguntas frecuentes y advertencias explícitas sobre la condición independiente del proyecto.
- Se añadió un verificador SHA-256 que procesa el archivo seleccionado localmente en el navegador y no lo carga a un servidor.
- Se corrigió el tamaño del valor `Dynamic DST\2027` en ambos perfiles: el dato TZI tenía 47 bytes; ahora tiene 44 bytes, conservando los campos de offset y transición que estaban codificados.
- Se añadieron un validador fail-closed para las rutas y los datos permitidos y una comprobación de CI para mantener sincronizados los hashes publicados.
- Se documentó que la estructura y el hash no certifican que las fechas sean una disposición oficial vigente.
