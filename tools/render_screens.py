#!/usr/bin/env python3
"""Genera las capturas de pantalla de la app a partir de su diseño real.

No es una captura de un teléfono: es un render que reproduce las mismas
medidas (dp), tamaños de letra (sp), colores y textos que
``MainActivity``/``ManualActivity`` y ``Ui.java``. Sirve para documentar la
interfaz y revisar el espaciado sin instalar el APK.

Uso:
    python3 tools/render_screens.py           # escribe docs/capturas/*.png
    python3 tools/render_screens.py --check   # solo verifica que existan
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "docs" / "capturas"

# Perfil Galaxy S22: 1080x2340 px, densidad 3 (xxhdpi).
DENSITY = 3
WIDTH_DP = 360
HEIGHT_DP = 780

FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"

# Paleta de Ui.java y styles.css.
NAVY = (16, 33, 59)
INK = (20, 35, 59)
INK_SOFT = (52, 68, 93)
MUTED = (103, 117, 138)
LINE = (226, 231, 239)
CANVAS = (245, 247, 251)
WHITE = (255, 255, 255)
BLUE = (79, 111, 226)
BLUE_DARK = (49, 81, 189)
BLUE_PALE = (237, 241, 255)
MINT_PALE = (234, 248, 243)
MINT_DEEP = (32, 124, 100)
AMBER_PALE = (253, 244, 222)
AMBER_DEEP = (148, 102, 19)


def dp(value: float) -> int:
    return round(value * DENSITY)


def font(size_sp: float, bold: bool = False, mono: bool = False):
    path = FONT_MONO if mono else (FONT_BOLD if bold else FONT_REGULAR)
    return ImageFont.truetype(path, round(size_sp * DENSITY))


class Screen:
    """Mini motor de composición vertical equivalente a LinearLayout.

    Las tarjetas necesitan dos pasadas: primero se mide su alto (las
    operaciones se apuntan) y después se pinta el fondo y el contenido. Si se
    pintara el fondo al final, taparía el texto.
    """

    def __init__(self, height_dp: float = HEIGHT_DP):
        self.image = Image.new("RGB", (dp(WIDTH_DP), dp(height_dp)), CANVAS)
        self.draw = ImageDraw.Draw(self.image)
        self.y = 0
        self.record: list | None = None

    # -- primitivas (se apuntan si hay una tarjeta en medición) --------
    def _op(self, operation) -> None:
        if self.record is not None:
            self.record.append(operation)
        else:
            operation()

    def text_width(self, text: str, size: float, bold: bool = False, mono: bool = False) -> int:
        return self.draw.textlength(text, font=font(size, bold, mono))

    def wrap(self, text: str, size: float, max_width: int, bold: bool = False) -> list[str]:
        lines, current = [], ""
        for word in text.split():
            candidate = (current + " " + word).strip()
            if self.text_width(candidate, size, bold) <= max_width or not current:
                current = candidate
            else:
                lines.append(current)
                current = word
        if current:
            lines.append(current)
        return lines

    def _draw_text_now(self, x: int, y: int, text: str, size: float, color,
                       bold: bool, mono: bool, max_width: int | None) -> int:
        lines = self.wrap(text, size, max_width, bold) if max_width else [text]
        line_height = round(size * DENSITY * 1.35)
        for line in lines:
            self.draw.text((x, y), line, font=font(size, bold, mono), fill=color)
            y += line_height
        return y

    def draw_text(self, x: int, y: int, text: str, size: float, color, bold: bool = False,
                  mono: bool = False, max_width: int | None = None) -> int:
        self._op(lambda: self._draw_text_now(x, y, text, size, color, bold, mono, max_width))
        lines = self.wrap(text, size, max_width, bold) if max_width else [text]
        return y + round(size * DENSITY * 1.35) * len(lines)

    def rounded(self, box, radius: float, fill=None, outline=None, width: int = 1):
        self._op(lambda: self.draw.rounded_rectangle(
            box, radius=dp(radius), fill=fill, outline=outline, width=width))

    def line(self, y: int, color=LINE) -> None:
        self._op(lambda: self.draw.line(
            (dp(20), y, dp(WIDTH_DP) - dp(20), y), fill=color, width=1))

    def pill(self, x: int, y: int, text: str, size: float, background, color) -> tuple[int, int]:
        pad_x, pad_y = dp(12), dp(5)
        width = int(self.text_width(text, size, True)) + pad_x * 2
        height = round(size * DENSITY * 1.2) + pad_y * 2
        self.rounded((x, y, x + width, y + height), 999, fill=background)
        self._op(lambda: self.draw.text((x + pad_x, y + height / 2), text,
                                        font=font(size, True), fill=color, anchor="lm"))
        return width, height

    def button(self, text: str, primary: bool = True, y: int | None = None,
               height_dp: float = 50, size: float = 15) -> int:
        # Dentro de una tarjeta el botón respeta su relleno interno; suelto, el margen
        # de pantalla. En la app va siempre dentro del LinearLayout con padding 18dp.
        x0, x1 = ((self.inner_x0, self.inner_x1) if self.record is not None
                  else (dp(20), dp(WIDTH_DP) - dp(20)))
        top = self.cursor if y is None and self.record is not None else (
            self.y if y is None else y)
        self.rounded((x0, top, x1, top + dp(height_dp)), 14,
                     fill=BLUE if primary else WHITE, outline=None if primary else LINE)
        width = self.text_width(text, size)
        self._op(lambda: self.draw.text(((dp(WIDTH_DP) - width) / 2, top + dp(15)), text,
                                        font=font(size), fill=WHITE if primary else INK))
        if self.record is not None:
            self.cursor = top + dp(height_dp)
        else:
            self.y = top + dp(height_dp)
        return top + dp(height_dp)

    # -- contenedores -------------------------------------------------
    def card_start(self, top_margin: float = 0) -> tuple[int, int, int, int]:
        self.y += dp(top_margin)
        self.card_top = self.y
        self.card_x0, self.card_x1 = dp(20), dp(WIDTH_DP) - dp(20)
        self.inner_x0 = self.card_x0 + dp(18)
        self.inner_x1 = self.card_x1 - dp(18)
        self.cursor = self.card_top + dp(18)
        self.record = []
        return self.card_x0, self.card_top, self.card_x1, self.cursor

    def card_end(self) -> None:
        operations = self.record or []
        self.record = None
        bottom = self.cursor + dp(18)
        self.draw.rounded_rectangle((self.card_x0, self.card_top, self.card_x1, bottom),
                                    radius=dp(18), fill=WHITE, outline=LINE, width=1)
        for operation in operations:
            operation()
        self.y = bottom

    def block(self, label: str, value: str, gap: float = 6) -> None:
        self.cursor += dp(gap)
        self.cursor = self.draw_text(self.inner_x0, self.cursor, label, 12, MUTED,
                                     max_width=self.inner_x1 - self.inner_x0)
        self.cursor = self.draw_text(self.inner_x0, self.cursor, value, 15, INK, bold=True,
                                     max_width=self.inner_x1 - self.inner_x0)

    def body(self, text: str, size: float = 13.5, color=MUTED, gap: float = 6) -> None:
        self.cursor += dp(gap)
        self.cursor = self.draw_text(self.inner_x0, self.cursor, text, size, color,
                                     max_width=self.inner_x1 - self.inner_x0)

    def divider(self, gap: float = 10) -> None:
        self.cursor += dp(gap)
        self.line(self.cursor)
        self.cursor += dp(1)

    def row(self, label: str, value: str) -> None:
        self.divider()
        self.cursor += dp(10)
        value_width = int(self.text_width(value, 15, True))
        label_width = self.inner_x1 - self.inner_x0 - value_width - dp(12)
        self.draw_text(self.inner_x0, self.cursor, label, 13.5, MUTED, max_width=label_width)
        value_x = self.inner_x1 - value_width
        value_y = self.cursor + dp(1)
        self._op(lambda: self.draw.text((value_x, value_y), value,
                                        font=font(15, True), fill=INK))
        self.cursor += dp(34)

    def box(self, lines: list[tuple[str, float, tuple, bool]], gap: float = 10) -> None:
        self.cursor += dp(gap)
        pad = dp(12)
        top = self.cursor
        max_width = self.inner_x1 - self.inner_x0 - pad * 2

        # Medir primero: alto total y posición de cada línea.
        cursor = top + dp(10)
        layout = []
        for text, size, color, bold in lines:
            wrapped = self.wrap(text, size, max_width, bold)
            layout.append((cursor, wrapped, size, color, bold))
            cursor += round(size * DENSITY * 1.35) * len(wrapped)
        bottom = cursor + dp(10)

        self.rounded((self.inner_x0, top, self.inner_x1, bottom), 12, fill=CANVAS)
        line_height = lambda size: round(size * DENSITY * 1.35)
        for start_y, wrapped, size, color, bold in layout:
            y = start_y
            for line in wrapped:
                self._op(lambda line=line, y=y, size=size, color=color, bold=bold:
                         self.draw.text((self.inner_x0 + pad, y), line,
                                        font=font(size, bold), fill=color))
                y += line_height(size)
        self.cursor = bottom

    def crop(self) -> Image.Image:
        """Recorta la captura al contenido real (para detalles, no pantallas)."""
        height = min(self.image.height, max(self.y + dp(24), dp(120)))
        return self.image.crop((0, 0, self.image.width, height))

    def status_bar(self, clock: str = "9:41") -> None:
        self.draw.rectangle((0, 0, dp(WIDTH_DP), dp(28)), fill=CANVAS)
        self.draw.text((dp(16), dp(7)), clock, font=font(12, True), fill=INK)
        for index, label in enumerate(["▲", "◔", "▮"]):
            self.draw.text((dp(WIDTH_DP) - dp(56) + index * dp(18), dp(7)), label,
                           font=font(11), fill=INK)
        self.y = dp(34)



def header_row(screen: Screen, label: str, pill_text: str, pill_bg, pill_fg) -> None:
    """Etiqueta a la izquierda y pill a la derecha sin solaparse."""
    label_width = int(screen.text_width(label, 11.5, True))
    pill_width = int(screen.text_width(pill_text, 12.5, True)) + dp(24)
    if label_width + pill_width + dp(8) > screen.inner_x1 - screen.inner_x0:
        screen.draw_text(screen.inner_x0, screen.cursor, label, 11.5, MUTED, bold=True)
        screen.cursor += dp(22)
        screen.pill(screen.inner_x0, screen.cursor, pill_text, 12.5, pill_bg, pill_fg)
        screen.cursor += dp(26)
        return
    screen.draw_text(screen.inner_x0, screen.cursor, label, 11.5, MUTED, bold=True)
    screen.pill(screen.inner_x1 - pill_width, screen.cursor - dp(2), pill_text, 12.5,
                pill_bg, pill_fg)
    screen.cursor += dp(24)


def header(screen: Screen) -> None:
    x0 = dp(20)
    screen.rounded((x0, screen.y, x0 + dp(52), screen.y + dp(52)), 14, fill=NAVY)
    label = "UTC"
    width = screen.text_width(label, 13, True)
    screen.draw.text((x0 + (dp(52) - width) / 2, screen.y + dp(17)), label,
                     font=font(13, True), fill=WHITE)
    screen.draw.text((x0 + dp(66), screen.y + dp(6)), "Cuba Hora 2027",
                     font=font(22, True), fill=INK)
    screen.draw.text((x0 + dp(66), screen.y + dp(34)), "Huso horario · 2026–2027",
                     font=font(13.5), fill=MUTED)
    screen.y += dp(52)


def main_screen() -> Screen:
    """Pantalla principal de 1.5.0: estado, perfil Cuba 2026-2027, opciones y diagnóstico."""
    s = Screen(height_dp=1090)
    s.status_bar()
    header(s)

    # Tarjeta de estado
    s.card_start(top_margin=18)
    header_row(s, "ESTADO DEL TELÉFONO", "Coincide con la regla", MINT_PALE, MINT_DEEP)
    s.row("Zona horaria", "America/Havana")
    s.row("Desfase del teléfono", "UTC-04:00")
    s.row("Desfase que exige la regla", "UTC-04:00")
    s.row("Zona horaria automática", "Activada")
    # Bloque del cambio automático: etiqueta arriba y valor debajo.
    s.divider()
    s.cursor += dp(8)
    s.cursor = s.draw_text(s.inner_x0, s.cursor, "Cambio automático", 12, MUTED,
                           max_width=s.inner_x1 - s.inner_x0)
    s.cursor = s.draw_text(s.inner_x0, s.cursor, "Desactivado", 15.5, AMBER_DEEP, bold=True,
                           max_width=s.inner_x1 - s.inner_x0)
    s.cursor = s.draw_text(s.inner_x0, s.cursor,
                           "Sin canal: ni Shizuku ni root",
                           12.5, MUTED, max_width=s.inner_x1 - s.inner_x0)
    s.card_end()

    # Perfil Cuba 2026-2027
    s.card_start(top_margin=14)
    header_row(s, "PERFIL CUBA 2026-2027", "UTC-04:00", BLUE_PALE, BLUE_DARK)
    s.cursor += dp(6)
    s.cursor = s.draw_text(s.inner_x0, s.cursor, "Desde el 01/11/2026 hasta el 07/11/2027",
                           15, INK, bold=True, max_width=s.inner_x1 - s.inner_x0)
    s.box([("UTC−04:00 en todo ese tramo y UTC−05:00 a partir del 07/11/2027 a la 01:00. "
            "Al instalarlo, el teléfono queda en la zona fija equivalente y la app aplica "
            "el cambio sola, comprobando el resultado con el sistema.", 13, INK_SOFT, False)],
          gap=8)
    s.cursor += dp(12)
    s.button("Instalar el perfil y activarlo")
    s.button("Probar ahora sin esperar a 2027", primary=False, y=s.cursor + dp(10))
    s.card_end()

    s.card_start(top_margin=14)
    s.draw_text(s.inner_x0, s.cursor, "MÁS OPCIONES PARA ACTIVARLO", 11.5, MUTED, bold=True)
    s.cursor += dp(24)
    s.body("Si el cambio no se aplica, pulsa «Diagnosticar»: genera un informe con el estado del teléfono, los canales y la prueba real.", gap=0)
    s.cursor += dp(12)
    s.button("Diagnosticar por qué no cambia", primary=False, y=s.cursor)
    s.card_end()
    return s


def options_screen() -> Screen:
    """Detalle de la tarjeta MÁS OPCIONES PARA ACTIVARLO."""
    s = Screen(height_dp=760)
    s.status_bar()
    s.draw_text(dp(20), s.y, "Detalle · más opciones", 13, MUTED, bold=True)
    s.y += dp(26)

    s.card_start()
    s.draw_text(s.inner_x0, s.cursor, "MÁS OPCIONES PARA ACTIVARLO", 11.5, MUTED, bold=True)
    s.cursor += dp(24)
    s.body("Android solo deja cambiar la zona horaria a aplicaciones del sistema: una app "
           "instalada como esta no puede hacerlo por su cuenta. Cualquiera de estos canales "
           "sirve; la app usa el primero que esté disponible.", gap=0)
    s.cursor += dp(12)
    for index, label in enumerate(("Activar con Shizuku (sin root)",
                                   "Activar con root (su)",
                                   "Copiar comandos para ADB",
                                   "Ajustar a mano en Ajustes",
                                   "Diagnosticar por qué no cambia")):
        s.button(label, primary=False, y=s.cursor)
        s.cursor += dp(10)
    s.card_end()
    return s


def rule_screen() -> Screen:
    """Detalle de la tarjeta REGLA DEL PROYECTO ya corregida."""
    s = Screen(height_dp=760)
    s.status_bar()
    s.draw_text(dp(20), s.y, "Detalle · tarjeta corregida", 13, MUTED, bold=True)
    s.y += dp(26)

    s.card_start()
    header_row(s, "REGLA DEL PROYECTO", "UTC-04:00", MINT_PALE, MINT_DEEP)
    s.cursor += dp(10)
    s.block("Cobertura definida", "2026–2027, hasta el 7/11/2027", gap=4)
    s.block("Próxima transición", "7 de noviembre de 2027, 01:00 → UTC-05:00")
    s.box([("2026 completo en UTC−04:00 (sin cambio el 1 de noviembre). El 7 de "
            "noviembre de 2027 a la 01:00 la regla pasa a UTC−05:00. Después de esa "
            "fecha no hay más cambios definidos.", 13, INK_SOFT, False)], gap=10)
    s.card_end()
    return s


def manual_screen() -> Screen:
    """Primera tarjeta del manual: perfil Cuba 2026-2027 y canales."""
    s = Screen()
    s.status_bar()
    s.draw_text(dp(20), dp(38), "Manual de aplicación", 22, INK, bold=True)
    s.draw_text(dp(20), dp(38) + dp(34),
                "Cuatro formas de dejar el teléfono en el desfase que pide la regla.", 13,
                MUTED, max_width=dp(WIDTH_DP) - dp(40))

    s.y = dp(96)
    s.card_start(top_margin=10)
    s.pill(s.inner_x0, s.cursor, "AUTOMÁTICO · LISTO", 11, MINT_PALE, MINT_DEEP)
    s.cursor += dp(28)
    s.cursor = s.draw_text(s.inner_x0, s.cursor, "Cambio sin tocar el teléfono", 18, INK,
                           bold=True, max_width=s.inner_x1 - s.inner_x0)
    s.body("El perfil Cuba 2026-2027 cubre del 01/11/2026 al 07/11/2027: UTC−04:00 y, "
           "desde el 07/11/2027 a la 01:00, UTC−05:00. Al instalarlo, la app cambia la "
           "zona sola y verifica el resultado con el sistema. Android reserva ese cambio "
           "a las apps del sistema, así que hace falta un canal: Shizuku, root o ADB.")
    s.cursor += dp(12)
    for number, text in (
            ("1", "Instala Shizuku desde Play Store o su repositorio de GitHub."),
            ("2", "Activa Opciones de desarrollador y «Depuración inalámbrica»."),
            ("3", "En Shizuku, elige «Emparejar» y pulsa «Iniciar»; para que siga "
                  "activo tras reiniciar, activa «Iniciar al arrancar»."),
            ("4", "Vuelve a la app y pulsa «Instalar el perfil y activarlo».")):
        s.draw_text(s.inner_x0, s.cursor, number, 12, BLUE_DARK, bold=True)
        s.cursor = s.draw_text(s.inner_x0 + dp(20), s.cursor, text, 13.5, INK,
                               max_width=s.inner_x1 - s.inner_x0 - dp(20))
        s.cursor += dp(8)
    s.body("¿Tienes root? Pulsa «Activar con root (su)»: la app pide permiso al gestor "
           "de root y ejecuta los mismos comandos.", gap=4)
    s.card_end()
    return s


def build() -> dict[Path, Image.Image]:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    return {
        OUT_DIR / "01-inicio.png": main_screen().crop(),
        OUT_DIR / "02-regla-del-proyecto.png": rule_screen().crop(),
        OUT_DIR / "03-manual.png": manual_screen().crop(),
        OUT_DIR / "04-opciones.png": options_screen().crop(),
    }


def main(argv: list[str]) -> int:
    screens = build()
    if "--check" in argv[1:]:
        missing = [path for path in screens if not path.is_file()]
        if missing:
            print("FAIL  faltan capturas: %s (ejecuta tools/render_screens.py)"
                  % ", ".join(str(p.relative_to(ROOT)) for p in missing), file=sys.stderr)
            return 1
        print("PASS  Capturas presentes (%d archivos en docs/capturas)" % len(screens))
        return 0

    for path, image in screens.items():
        image.save(path, optimize=True)
        print("wrote %-42s %dx%d" % (path.relative_to(ROOT), image.width, image.height))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
