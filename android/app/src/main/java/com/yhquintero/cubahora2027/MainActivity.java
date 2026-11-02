package com.yhquintero.cubahora2027;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pantalla principal: muestra el estado real del teléfono, la regla del
 * proyecto y los pasos para ajustar la zona desde los ajustes del sistema.
 */
public class MainActivity extends Activity {

    private TextView statusPill;
    private TextView zoneValue;
    private TextView offsetValue;
    private TextView deviceTimeValue;
    private TextView cubaTimeValue;
    private TextView requiredValue;
    private TextView autoValue;
    private TextView nextValue;
    private TextView coverageValue;
    private TextView hintValue;
    private TextView remindersHint;
    private Button primaryButton;
    private Button exactAlarmButton;
    private CheckBox remindersBox;
    private boolean bindingReminders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifications.ensureChannel(this);
        setContentView(buildUi());
        refresh();
        ReminderScheduler.scheduleNext(this);
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ------------------------------------------------------------------
    // Interfaz
    // ------------------------------------------------------------------

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.CANVAS);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        int pad = dp(20);
        scroll.setPadding(pad, pad, pad, dp(32));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Encabezado
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = Ui.text(this, "UTC", 13f, Ui.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.rounded(this, Ui.NAVY, 14f, null));
        mark.setWidth(dp(52));
        mark.setHeight(dp(52));
        header.addView(mark);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        TextView title = Ui.text(this, "Cuba Hora 2027", 22f, Ui.INK, true);
        TextView subtitle = Ui.text(this, "Huso horario · 2026–2027", 13.5f, Ui.MUTED, false);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        column.addView(header);

        column.addView(Ui.spacer(this, 18f));

        // Tarjeta de estado
        LinearLayout status = Ui.card(this);
        LinearLayout statusTop = new LinearLayout(this);
        statusTop.setOrientation(LinearLayout.HORIZONTAL);
        statusTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView statusTitle = Ui.text(this, "ESTADO DEL TELÉFONO", 11.5f, Ui.MUTED, true);
        statusTop.addView(statusTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusPill = Ui.pill(this, "…", Ui.BLUE_PALE, Ui.BLUE_DARK);
        statusTop.addView(statusPill);
        status.addView(statusTop);

        status.addView(Ui.spacer(this, 8f));
        zoneValue = addRow(status, "Zona horaria", "…", true);
        offsetValue = addRow(status, "Desfase del teléfono", "…", true);
        requiredValue = addRow(status, "Desfase que exige la regla", "…", true);
        deviceTimeValue = addRow(status, "Hora del teléfono", "…", false);
        cubaTimeValue = addRow(status, "Hora de Cuba según la regla", "…", false);
        autoValue = addRow(status, "Zona horaria automática", "…", false);

        hintValue = Ui.text(this, "", 12.5f, Ui.MUTED, false);
        hintValue.setPadding(0, dp(10), 0, 0);
        status.addView(hintValue);
        column.addView(status);

        column.addView(Ui.spacer(this, 14f));

        // Acciones
        LinearLayout actions = Ui.card(this);
        actions.addView(Ui.text(this, "CÓMO AJUSTARLO", 11.5f, Ui.MUTED, true));
        actions.addView(Ui.spacer(this, 8f));
        TextView why = Ui.text(this,
                "Android solo deja cambiar la zona horaria a aplicaciones del sistema: "
                        + "una app instalada como esta no puede hacerlo. La app compara tu "
                        + "teléfono con la regla y te indica la zona exacta que debes elegir "
                        + "en los ajustes.", 13f, Ui.MUTED, false);
        actions.addView(why);
        actions.addView(Ui.spacer(this, 12f));

        primaryButton = Ui.button(this, "Abrir ajustes de fecha y hora", true);
        primaryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ZoneController.openDateTimeSettings(MainActivity.this);
            }
        });
        actions.addView(primaryButton, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button steps = Ui.button(this, "Ver los pasos y la zona a elegir", false);
        steps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSteps();
            }
        });
        actions.addView(steps, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button guide = Ui.button(this, "Abrir guía del proyecto", false);
        guide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, GuideActivity.class));
            }
        });
        actions.addView(guide, fullWidth());
        column.addView(actions);

        column.addView(Ui.spacer(this, 14f));

        // Regla del proyecto
        LinearLayout rule = Ui.card(this);
        rule.addView(Ui.text(this, "REGLA DEL PROYECTO", 11.5f, Ui.MUTED, true));
        rule.addView(Ui.spacer(this, 8f));
        coverageValue = addRow(rule, "Cobertura definida", "…", true);
        nextValue = addRow(rule, "Próxima transición", "…", true);
        rule.addView(Ui.spacer(this, 6f));
        TextView ruleText = Ui.text(this,
                "2026 completo en UTC−04:00 (sin cambio el 1 de noviembre). "
                        + "El 7 de noviembre de 2027 a la 01:00 la regla pasa a UTC−05:00. "
                        + "Después de esa fecha no hay más cambios definidos.", 13f, Ui.MUTED, false);
        rule.addView(ruleText);
        column.addView(rule);

        column.addView(Ui.spacer(this, 14f));

        // Recordatorios
        LinearLayout reminders = Ui.card(this);
        reminders.addView(Ui.text(this, "RECORDATORIOS", 11.5f, Ui.MUTED, true));
        reminders.addView(Ui.spacer(this, 8f));
        remindersBox = new CheckBox(this);
        remindersBox.setText("Avisarme antes de cada transición");
        remindersBox.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
        remindersBox.setTextColor(Ui.INK);
        remindersBox.setPadding(0, dp(4), 0, dp(4));
        remindersBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingReminders) {
                return;
            }
            ReminderScheduler.setRemindersEnabled(this, isChecked);
            if (isChecked) {
                requestNotificationPermissionIfNeeded();
                ReminderScheduler.scheduleNext(this);
                refresh();
            } else {
                ReminderScheduler.cancel(this);
            }
        });
        reminders.addView(remindersBox);

        remindersHint = Ui.text(this, "", 12.5f, Ui.MUTED, false);
        remindersHint.setPadding(0, dp(6), 0, 0);
        reminders.addView(remindersHint);

        exactAlarmButton = Ui.button(this, "Permitir alarmas exactas", false);
        exactAlarmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openExactAlarmSettings();
            }
        });
        reminders.addView(exactAlarmButton, fullWidth());
        column.addView(reminders);

        column.addView(Ui.spacer(this, 18f));
        TextView note = Ui.text(this,
                "Proyecto independiente y no oficial: no está afiliado a Microsoft, a Samsung "
                        + "ni a una institución del Gobierno de Cuba. La app no se conecta a "
                        + "Internet y no envía datos. Cambiar la zona horaria afecta alarmas, "
                        + "calendarios y registros: verifica la normativa vigente antes de "
                        + "aplicarla.", 11.5f, Ui.MUTED, false);
        note.setGravity(Gravity.START);
        column.addView(note);

        column.addView(Ui.spacer(this, 8f));
        column.addView(Ui.text(this, "Versión " + versionLabel(), 11.5f, Ui.MUTED, false));

        return scroll;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView addRow(LinearLayout parent, String label, String value, boolean emphasize) {
        parent.addView(Ui.divider(this));
        LinearLayout row = Ui.row(this);
        TextView valueView = Ui.text(this, value, emphasize ? 15f : 13.5f,
                emphasize ? Ui.INK : Ui.MUTED, emphasize);
        row.addView(Ui.pair(this, Ui.text(this, label, 13.5f, Ui.MUTED, false), valueView));
        parent.addView(row);
        return valueView;
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    private void refresh() {
        long now = System.currentTimeMillis();
        int required = CubaRules.offsetMinutes(now);
        String zoneId = ZoneController.currentZoneId();
        int actual = ZoneController.currentOffsetMinutes(now);
        boolean auto = ZoneController.autoTimeZoneEnabled(this);
        boolean known = now >= CubaRules.START_2026;
        boolean matches = actual == required;

        zoneValue.setText(zoneId);
        offsetValue.setText(CubaRules.labelForMinutes(actual));
        requiredValue.setText(CubaRules.labelForMinutes(required));
        deviceTimeValue.setText(formatLocal(now, actual));
        cubaTimeValue.setText(formatLocal(now, required));
        autoValue.setText(auto ? "Activada" : "Desactivada");

        if (!known) {
            setStatus("Antes de la regla", Ui.BLUE_PALE, Ui.BLUE_DARK);
        } else if (matches) {
            setStatus("Coincide con la regla", Ui.MINT_PALE, Ui.MINT_DEEP);
        } else {
            setStatus("Ajuste pendiente", Ui.AMBER_PALE, Ui.AMBER_DEEP);
        }

        primaryButton.setText(matches
                ? "Abrir ajustes (ya coincide)"
                : "Abrir ajustes · elegir " + CubaRules.labelForMinutes(required));

        hintValue.setText(OffsetGuide.statusHint(auto, matches, zoneId));

        long next = CubaRules.nextTransition(now);
        if (next > 0) {
            int offsetAfter = CubaRules.offsetMinutes(next);
            nextValue.setText(formatTransition(next) + " → "
                    + CubaRules.labelForMinutes(offsetAfter));
        } else {
            nextValue.setText("Ninguna definida después de 2027");
        }

        if (now < CubaRules.START_2026) {
            coverageValue.setText("Antes de 2026 (sin regla)");
        } else if (CubaRules.insideDefinedRange(now)) {
            coverageValue.setText("2026–2027, hasta el 7/11/2027");
        } else {
            coverageValue.setText("UTC−05:00 desde 7/11/2027 (sin más cambios)");
        }

        bindingReminders = true;
        remindersBox.setChecked(ReminderScheduler.remindersEnabled(this));
        bindingReminders = false;

        StringBuilder hint = new StringBuilder();
        if (!Notifications.canPost(this)) {
            hint.append("Las notificaciones están bloqueadas en este teléfono. ");
        }
        hint.append(next > 0
                ? "Aviso 7 días antes, 24 h antes y en el momento del cambio. "
                + "El aviso no cambia la zona: te recuerda hacerlo."
                : "No quedan transiciones por recordar con la regla actual.");
        remindersHint.setText(hint.toString());

        exactAlarmButton.setVisibility(needsExactAlarmPermission() ? View.VISIBLE : View.GONE);
    }

    private boolean needsExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < 31) {
            return false;
        }
        try {
            AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return alarms != null && !alarms.canScheduleExactAlarms();
        } catch (Throwable error) {
            return false;
        }
    }

    private void setStatus(String text, int background, int color) {
        statusPill.setText(text);
        statusPill.setBackground(Ui.rounded(this, background, 999f, null));
        statusPill.setTextColor(color);
    }

    private String formatLocal(long epochMillis, int offsetMinutes) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss · dd/MM/yyyy",
                new Locale("es", "ES"));
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date(epochMillis + offsetMinutes * 60000L));
    }

    private String formatTransition(long epochMillis) {
        int offset = CubaRules.offsetMinutes(epochMillis - 1000L);
        SimpleDateFormat format = new SimpleDateFormat("d 'de' MMMM 'de' yyyy, HH:mm",
                new Locale("es", "ES"));
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date(epochMillis + offset * 60000L));
    }

    @SuppressWarnings("deprecation")
    private String versionLabel() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable error) {
            return "1.1.0";
        }
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    private void showSteps() {
        long now = System.currentTimeMillis();
        int required = CubaRules.offsetMinutes(now);
        final String stepsText = OffsetGuide.stepsFor(required);

        final TextView message = Ui.text(this, stepsText, 13.5f, Ui.INK, false);
        message.setTextIsSelectable(true);
        message.setPadding(dp(20), dp(8), dp(20), dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ajustar la zona en este teléfono")
                .setView(message)
                .setPositiveButton("Abrir Ajustes", (d, which) ->
                        ZoneController.openDateTimeSettings(MainActivity.this))
                .setNeutralButton("Copiar pasos", (d, which) -> copyToClipboard(stepsText))
                .setNegativeButton("Cerrar", null)
                .create();
        dialog.show();
    }

    private void copyToClipboard(String value) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Pasos", value));
                toast("Pasos copiados al portapapeles.");
            }
        } catch (Throwable error) {
            toast("No se pudo copiar el texto.");
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable error) {
            toast("Abre Ajustes → Aplicaciones → Cuba Hora 2027 → Alarmas y recordatorios.");
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (!ReminderScheduler.remindersEnabled(this)) {
            return;
        }
        try {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
            }
        } catch (Throwable ignored) {
            // Sin permiso la app sigue funcionando; solo no muestra avisos.
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001) {
            refresh();
        }
    }

    private void toast(String message) {
        if (TextUtils.isEmpty(message)) {
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }
}
