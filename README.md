# Huso horario de Cuba · perfiles Windows 2027

**Guía en español para revisar e instalar, con cautela, una definición de zona horaria de Cuba en Windows.** Este repositorio contiene dos archivos `.reg`, una página estática de explicación y herramientas para comprobar el alcance e integridad de los archivos.

> [!IMPORTANT]
> **Proyecto independiente, no oficial.** No está afiliado a Microsoft ni a una institución del Gobierno de Cuba. Los perfiles codifican reglas propuestas para 2027; este repositorio no certifica que coincidan con una disposición oficial vigente. Confirma la normativa en una fuente oficial antes de importarlos.

## Qué contiene

| Archivo | Perfil | Contenido |
|---|---|---|
| [`.reg/Cuba_2027_Original.reg`](.reg/Cuba_2027_Original.reg) | Compacto | Etiquetas y datos base de `Cuba Standard Time`, con una entrada dinámica para 2027. Codificación UTF-8. |
| [`.reg/Cuba_2027_Completo.reg`](.reg/Cuba_2027_Completo.reg) | Ampliado | Incluye además metadatos de visualización de Windows y entradas históricas de 2003–2013. Codificación UTF-16 LE con BOM. |
| [`index.html`](index.html) | Guía | Vista adaptable para escritorio y móvil, con pasos, alcance, preguntas frecuentes y verificador local SHA-256. |
| [`tools/verify_registry.py`](tools/verify_registry.py) | Validador | Comprueba rutas y valores permitidos, estructura de datos TZI, reglas codificadas para 2027 y hashes publicados. Usa solo Python estándar. |
| [`SECURITY.md`](SECURITY.md) | Seguridad | Límites del proyecto, verificación, recuperación y canal de reporte. |

Ambos perfiles incluyen la misma entrada dinámica 2027 y declaran `FirstEntry` y `LastEntry` como 2027. **Escoge un solo archivo; no es necesario importar los dos.**

## Qué escriben los perfiles

Las únicas secciones permitidas son:

```text
HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time
HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time\Dynamic DST
```

El perfil define un desfase base UTC−05:00 y un ajuste estacional de 60 minutos (UTC−04:00) en la regla dinámica. La regla de transición de 2027 que figura en el archivo está almacenada como `REG_TZI_FORMAT`: primer domingo de noviembre a la 01:00 para volver a hora estándar y último domingo de marzo a las 00:00 para iniciar la regla estacional. **Estas son una lectura de los bytes publicados, no una confirmación de fechas oficiales.** La entrada genérica `TZI` del perfil tiene su propia regla recurrente.

La importación **no elige por sí sola la zona activa del equipo ni cambia manualmente el reloj**. Después de incorporar la definición, Windows puede requerir que la selecciones desde **Configuración → Hora e idioma → Fecha y hora**. El perfil requiere autorización para modificar una clave bajo `HKEY_LOCAL_MACHINE`.

## Uso recomendado

1. Lee [`SECURITY.md`](SECURITY.md) y confirma por una fuente oficial que la regla es la que necesitas.
2. Descarga un solo perfil desde [`index.html`](index.html) o desde este repositorio.
3. Comprueba su SHA-256 —se publica abajo y en la página— antes de importarlo. Si no coincide, **no continúes**.
4. Exporta la clave de zona horaria si existe y crea un punto de restauración para disponer de una reversión completa. Reimportar una exportación `.reg` no elimina automáticamente valores nuevos. Revisa el archivo en un editor de texto y confirma que solo contiene las dos rutas indicadas.
5. Importa únicamente si tienes autorización y entiendes el cambio. Revisa los avisos de Windows.
6. Selecciona la zona horaria en Configuración y consulta la zona activa con `tzutil /g` si lo necesitas. No ajustes manualmente la fecha y hora para probar cambios estacionales.

La guía web ofrece instrucciones más detalladas. Para verla localmente, ejecuta desde la raíz del repositorio:

```bash
python3 -m http.server 8000
```

Abre `http://localhost:8000`. El sitio no requiere compilación, dependencias de terceros, fuentes remotas ni servicios analíticos.

## Integridad publicada

Los hashes son SHA-256 de los **bytes exactos** actualmente versionados:

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `Cuba_2027_Original.reg` | 707 bytes | `EED8F9E6295F82DB3BED9C211593583CAA42B7F79DAB412E4C40CB3C8453D465` |
| `Cuba_2027_Completo.reg` | 4,920 bytes | `1E06110839D78CC5580C08F8E66F4D88104CD244461A82279E1E963BB9C48830` |

La página permite comparar el archivo seleccionado directamente en el navegador, sin subirlo. **La huella no es una firma digital:** permite detectar una diferencia frente al valor publicado, pero no prueba la identidad del publicador ni la validez oficial de las reglas.

## Validación para cambios

Requiere Python 3.10 o posterior. Ejecuta:

```bash
python3 tools/verify_registry.py
```

El validador falla si aparecen rutas o valores no autorizados, si se elimina una clave, si la estructura de una regla no es válida, si el registro TZI de 2027 no mide 44 bytes o si los tamaños o SHA-256 publicados en `index.html` o `README.md` no coinciden con los perfiles. El flujo de GitHub Actions repite esta comprobación en cambios que afectan a los perfiles o a la página.

Los controles son pruebas de estructura y alcance; **no** prueban el comportamiento en todas las versiones de Windows ni certifican la decisión legal sobre el cambio horario.

## Cambios relevantes de esta revisión

Durante la comprobación estructural se detectó que el valor dinámico de 2027 de ambos archivos tenía 47 bytes, en vez de los 44 bytes de la estructura TZI de Windows. Se retiraron los tres bytes cero sobrantes antes del campo de transición estacional, conservando los offsets y campos de transición que expresaba el archivo. Se añadió una prueba para evitar que vuelva a pasar. La fecha oficial aplicable a Cuba debe confirmarse por separado.

## Proyecto y reportes

- [Política de seguridad](SECURITY.md)
- [Registro de cambios](CHANGELOG.md)
- [Código fuente en GitHub](https://github.com/yhquintero/Huso-Horario-Cuba-2026-2027)

Si sospechas de una vulnerabilidad, sigue el procedimiento privado descrito en [`SECURITY.md`](SECURITY.md). No publiques contraseñas, datos personales ni información sensible.
