package com.yhquintero.cubahora2027;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Manual de aplicación del cambio de huso horario.
 *
 * <p>Reúne las dos vías reales para dejar el teléfono en el desfase que pide
 * la regla: los ajustes del sistema (sin PC) y el comando de shell por ADB
 * (con PC y cable, sin root). Incluye verificación y cómo revertir.</p>
 */
public class ManualActivity extends Activity {

    /** Santo Domingo mantiene UTC-04:00 todo el año: no se mueve sola. */
    static final String ZONE_SUMMER = "America/Santo_Domingo";

    /** Bogotá mantiene UTC-05:00 todo el año. */
    static final String ZONE_STANDARD = "America/Bogota";

    static final String CMD_AUTO_OFF = "adb shell settings put global auto_time_zone 0";
    static final String CMD_VERIFY = "adb shell getprop persist.sys.timezone";
    static final String CMD_RESTORE = "adb shell cmd alarm set-timezone America/Havana";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    // ------------------------------------------------------------------
    // Interfaz
    // ------------------------------------------------------------------

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.CANVAS);
        scroll.setClipToPadding(false);
        int pad = dp(20);
        scroll.setPadding(pad, pad, pad, dp(32));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(Ui.text(this, "Manual de aplicación", 22f, Ui.INK, true));
        column.addView(Ui.spacer(this, 4f));
        column.addView(Ui.text(this,
                "Dos formas de dejar el teléfono en el desfase que pide la regla. "
                        + "Elige una; la app no puede cambiarlo sola porque Android reserva "
                        + "ese ajuste al sistema.", 13.5f, Ui.MUTED, false));

        column.addView(Ui.spacer(this, 16f));
        column.addView(optionOne());

        column.addView(Ui.spacer(this, 14f));
        column.addView(optionTwo());

        column.addView(Ui.spacer(this, 14f));
        column.addView(optionThree());

        column.addView(Ui.spacer(this, 14f));
        column.addView(verifyCard());

        column.addView(Ui.spacer(this, 16f));
        TextView note = Ui.text(this,
                "Cambiar la zona horaria afecta alarmas, calendarios y registros. "
                        + "Proyecto independiente y no oficial: las fechas son las del perfil "
                        + "de este repositorio, no una certificación oficial. Comprueba la "
                        + "normativa vigente antes de aplicarlo.", 11.5f, Ui.MUTED, false);
        column.addView(note);

        column.addView(Ui.spacer(this, 10f));
        column.addView(Ui.text(this, "Versión " + versionLabel(), 11.5f, Ui.MUTED, false));

        return scroll;
    }

    private LinearLayout optionOne() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.pill(this, "OPCIÓN 1 · SIN PC", Ui.MINT_PALE, Ui.MINT_DEEP));
        card.addView(Ui.spacer(this, 10f));
        card.addView(Ui.text(this, "Ajustes del teléfono", 18f, Ui.INK, true));
        card.addView(Ui.spacer(this, 6f));
        card.addView(body("Funciona en cualquier Android, sin cables ni permisos. "
                + "Es la vía recomendada para un uso normal."));

        card.addView(Ui.spacer(this, 12f));
        card.addView(step("1", "Abre Ajustes → Administración general → Fecha y hora "
                + "(en Samsung con One UI). En otros Android suele estar en "
                + "Ajustes → Sistema → Fecha y hora."));
        card.addView(step("2", "Desactiva «Zona horaria automática». Si sigue activada, "
                + "la red puede volver a poner la zona de Cuba y deshacer el ajuste."));
        card.addView(step("3", "Entra en «Zona horaria» y elige una ciudad con desfase fijo: "
                + "Santo Domingo para UTC−04:00 o Bogotá para UTC−05:00. Si tu teléfono "
                + "lista desfases, elige GMT−04:00 o GMT−05:00."));
        card.addView(step("4", "Vuelve a Cuba Hora 2027: la tarjeta de estado debe decir "
                + "«Coincide con la regla»."));

        card.addView(Ui.spacer(this, 12f));
        Button open = Ui.button(this, "Abrir ajustes de fecha y hora", true);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ZoneController.openDateTimeSettings(ManualActivity.this);
            }
        });
        card.addView(open, fullWidth());
        return card;
    }

    private LinearLayout optionTwo() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.pill(this, "OPCIÓN 2 · CON PC", Ui.BLUE_PALE, Ui.BLUE_DARK));
        card.addView(Ui.spacer(this, 10f));
        card.addView(Ui.text(this, "Un comando por ADB, sin root", 18f, Ui.INK, true));
        card.addView(Ui.spacer(this, 6f));
        card.addView(body("El usuario «shell» de ADB tiene permiso de zona horaria en Android, "
                + "así que este comando cambia la zona de verdad. Necesitas un PC, el cable "
                + "y la depuración USB activada. No hace falta root."));
        card.addView(Ui.spacer(this, 10f));
        card.addView(body("En el teléfono: Ajustes → Información del teléfono → Información "
                + "del software → toca 7 veces «Número de compilación» → vuelve y activa "
                + "«Depuración USB» en Opciones de desarrollador."));
        card.addView(Ui.spacer(this, 10f));
        card.addView(body("En el PC, con platform-tools instalado y el teléfono conectado "
                + "(acepta el aviso «¿Permitir depuración USB?»):"));

        int required = CubaRules.offsetMinutes(System.currentTimeMillis());
        String zone = required == CubaRules.OFFSET_STANDARD ? ZONE_STANDARD : ZONE_SUMMER;
        String label = CubaRules.labelForMinutes(required);

        card.addView(Ui.spacer(this, 10f));
        card.addView(command("adb devices", "Comprueba que el teléfono aparece como «device»."));
        card.addView(command(CMD_AUTO_OFF, "Desactiva la zona automática para que no se "
                + "deshaga el cambio."));
        card.addView(command("adb shell cmd alarm set-timezone " + zone,
                "Fija " + label + " ahora mismo. Para el otro tramo usa "
                        + (zone.equals(ZONE_SUMMER) ? ZONE_STANDARD : ZONE_SUMMER) + "."));

        card.addView(Ui.spacer(this, 10f));
        card.addView(body("El cambio es inmediato y sobrevive al reinicio. Repítelo solo "
                + "cuando la regla cambie de tramo (7 de noviembre de 2027)."));
        return card;
    }

    private LinearLayout optionThree() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.pill(this, "OPCIÓN 3 · AUTOMÁTICA", Ui.AMBER_PALE, Ui.AMBER_DEEP));
        card.addView(Ui.spacer(this, 10f));
        card.addView(Ui.text(this, "Dejar que la red decida", 18f, Ui.INK, true));
        card.addView(Ui.spacer(this, 6f));
        card.addView(body("Con «Zona horaria automática» activada, el teléfono usa la zona "
                + "que le envía el operador o la que trae su base de datos de zonas. Es lo "
                + "más cómodo, pero no puedes garantizar que aplique el calendario de este "
                + "proyecto: si la regla oficial cambia, el teléfono puede quedarse en "
                + "America/Havana con otra fecha de cambio."));
        card.addView(Ui.spacer(this, 10f));
        card.addView(body("Úsala si prefieres no tocar nada y revisar el estado cada vez "
                + "que la app te avise."));
        return card;
    }

    private LinearLayout verifyCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "VERIFICAR Y REVERTIR", 11.5f, Ui.MUTED, true));
        card.addView(Ui.spacer(this, 10f));
        card.addView(command(CMD_VERIFY, "Devuelve la zona activa, por ejemplo "
                + "America/Santo_Domingo."));
        card.addView(Ui.spacer(this, 10f));
        card.addView(body("Para volver atrás: elige America/Havana en los ajustes y activa "
                + "de nuevo la zona automática, o usa el comando de abajo."));
        card.addView(Ui.spacer(this, 10f));
        card.addView(command(CMD_RESTORE, "Vuelve a la zona de Cuba del sistema."));
        card.addView(Ui.spacer(this, 10f));
        card.addView(body("Si el comando responde «Permission denial», revisa que la "
                + "depuración USB esté autorizada y que uses un cable de datos. Si el "
                + "teléfono vuelve solo a America/Havana, la zona automática sigue activa."));
        return card;
    }

    // ------------------------------------------------------------------
    // Utilidades de composición
    // ------------------------------------------------------------------

    private TextView body(String text) {
        TextView view = Ui.text(this, text, 13.5f, Ui.MUTED, false);
        view.setLineSpacing(dp(2), 1.1f);
        return view;
    }

    private LinearLayout step(String number, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView badge = Ui.text(this, number, 12f, Ui.BLUE_DARK, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rounded(this, Ui.BLUE_PALE, 999f, null));
        badge.setWidth(dp(24));
        badge.setHeight(dp(24));
        row.addView(badge);

        TextView content = Ui.text(this, text, 13.5f, Ui.INK, false);
        content.setPadding(dp(10), 0, 0, 0);
        row.addView(content, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private LinearLayout command(String command, String caption) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rounded(this, Ui.CANVAS, 12f, null));
        int pad = dp(12);
        box.setPadding(pad, dp(10), pad, dp(10));

        TextView code = Ui.text(this, command, 12.5f, Ui.INK, false);
        code.setTypeface(android.graphics.Typeface.MONOSPACE);
        code.setTextIsSelectable(true);
        box.addView(code);

        TextView captionView = Ui.text(this, caption, 12f, Ui.MUTED, false);
        captionView.setPadding(0, dp(6), 0, 0);
        box.addView(captionView);

        Button copy = Ui.button(this, "Copiar comando", false);
        copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        copy.setMinHeight(dp(40));
        copy.setMinimumHeight(dp(40));
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyToClipboard(command);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        box.addView(copy, params);
        return box;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void copyToClipboard(String value) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Comando", value));
                toast("Comando copiado al portapapeles.");
            }
        } catch (Throwable error) {
            toast("No se pudo copiar el comando.");
        }
    }

    @SuppressWarnings("deprecation")
    private String versionLabel() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable error) {
            return "desconocida";
        }
    }

    private void toast(String message) {
        if (TextUtils.isEmpty(message)) {
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }
}
