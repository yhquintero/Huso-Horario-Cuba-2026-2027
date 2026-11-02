package com.yhquintero.cubahora2027;

import android.Manifest;
import android.app.Activity;
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
 * proyecto y las acciones disponibles.
 */
public class MainActivity extends Activity {

    private static final String ADB_COMMAND =
            "adb shell pm grant com.yhquintero.cubahora2027 android.permission.WRITE_SECURE_SETTINGS";

    private TextView statusPill;
    private TextView zoneValue;
    private TextView offsetValue;
    private TextView deviceTimeValue;
    private TextView cubaTimeValue;
    private TextView autoValue;
    private TextView permissionValue;
    private TextView requiredValue;
    private TextView nextValue;
    private TextView coverageValue;
    private TextView remindersHint;
    private Button applyButton;
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
        TextView subtitle = Ui.text(this, "Ajuste del huso horario · 2026–2027", 13.5f, Ui.MUTED, false);
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
        offsetValue = addRow(status, "Desfase real del sistema", "…", true);
        deviceTimeValue = addRow(status, "Hora del teléfono", "…", false);
        cubaTimeValue = addRow(status, "Hora de Cuba según la regla", "…", true);
        requiredValue = addRow(status, "Desfase que exige la regla", "…", false);
        autoValue = addRow(status, "Zona horaria automática", "…", false);
        permissionValue = addRow(status, "Permiso para cambiar la zona", "…", false);
        column.addView(status);

        column.addView(Ui.spacer(this, 14f));

        // Acciones
        LinearLayout actions = Ui.card(this);
        actions.addView(Ui.text(this, "ACCIONES", 11.5f, Ui.MUTED, true));
        actions.addView(Ui.spacer(this, 10f));

        applyButton = Ui.button(this, "Ajustar el teléfono a la regla", true);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyRule();
            }
        });
        actions.addView(applyButton, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button manual = Ui.button(this, "Abrir ajustes de fecha y hora", false);
        manual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ZoneController.openDateTimeSettings(MainActivity.this);
            }
        });
        actions.addView(manual, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button havana = Ui.button(this, "Usar zona de Cuba (America/Havana)", false);
        havana.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyZone(ZoneController.HAVANA_ZONE_ID);
            }
        });
        actions.addView(havana, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button restore = Ui.button(this, "Restaurar zona automática", false);
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                restoreAutomatic();
            }
        });
        actions.addView(restore, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button permissions = Ui.button(this, "Cómo habilitar el cambio de zona", false);
        permissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPermissionHelp();
            }
        });
        actions.addView(permissions, fullWidth());
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
        rule.addView(Ui.spacer(this, 10f));
        Button guide = Ui.button(this, "Abrir guía del proyecto", false);
        guide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, GuideActivity.class));
            }
        });
        rule.addView(guide, fullWidth());
        column.addView(rule);

        column.addView(Ui.spacer(this, 14f));

        // Recordatorios
        LinearLayout reminders = Ui.card(this);
        reminders.addView(Ui.text(this, "RECORDATORIOS", 11.5f, Ui.MUTED, true));
        reminders.addView(Ui.spacer(this, 8f));
        remindersBox = new CheckBox(this);
        remindersBox.setText("Avisarme antes y durante las transiciones");
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
                        + "calendarios y registros: verifica la normativa vigente antes de aplicarla.",
                11.5f, Ui.MUTED, false);
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
        String zoneId = ZoneController.currentZoneId(this);
        int actual = ZoneController.currentOffsetMinutes(this, now);
        boolean auto = ZoneController.autoTimeZoneEnabled(this);
        boolean canWrite = ZoneController.canWriteSecureSettings(this);
        boolean havanaManaged = auto && ZoneController.HAVANA_ZONE_ID.equals(zoneId);
        boolean matches = actual == required;

        zoneValue.setText(zoneId);
        offsetValue.setText(CubaRules.labelForMinutes(actual));
        deviceTimeValue.setText(formatLocal(now, actual));
        cubaTimeValue.setText(formatLocal(now, required));
        requiredValue.setText(CubaRules.labelForMinutes(required));
        autoValue.setText(auto ? "Activada" : "Desactivada");
        permissionValue.setText(canWrite ? "Concedido" : "No concedido");

        if (havanaManaged) {
            setStatus("Zona de Cuba gestionada por el sistema", Ui.MINT_PALE, Ui.MINT_DEEP);
        } else if (!matches && canWrite) {
            setStatus("Ajuste pendiente", Ui.AMBER_PALE, Ui.AMBER_DEEP);
        } else if (!matches) {
            setStatus("Ajuste pendiente · falta permiso", Ui.AMBER_PALE, Ui.AMBER_DEEP);
        } else {
            setStatus("Coincide con la regla", Ui.MINT_PALE, Ui.MINT_DEEP);
        }

        if (havanaManaged) {
            applyButton.setText("El sistema ya aplica America/Havana");
        } else if (!canWrite && !matches) {
            applyButton.setText("Cómo ajustar la zona del teléfono");
        } else {
            applyButton.setText("Ajustar el teléfono a " + CubaRules.labelForMinutes(required));
        }

        long next = CubaRules.nextTransition(now);
        if (next > 0) {
            int offset = CubaRules.offsetMinutes(next);
            nextValue.setText(formatTransition(next) + " → " + CubaRules.labelForMinutes(offset));
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
                ? "Aviso 24 h antes y en el momento del cambio."
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
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss · dd/MM/yyyy", new Locale("es", "ES"));
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
            return "1.0.0";
        }
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    private void applyRule() {
        long now = System.currentTimeMillis();
        int required = CubaRules.offsetMinutes(now);
        String zoneId = CubaRules.zoneIdForOffset(required);
        if (!ZoneController.canWriteSecureSettings(this)) {
            showPermissionHelp();
            return;
        }
        applyZone(zoneId);
    }

    private void applyZone(String zoneId) {
        int result = ZoneController.applyZone(this, zoneId);
        if (result == ZoneController.RESULT_OK) {
            toast("Zona cambiada a " + zoneId);
        } else if (result == ZoneController.RESULT_NO_PERMISSION) {
            showPermissionHelp();
        } else {
            toast("El teléfono rechazó el cambio. Usa los ajustes del sistema.");
        }
        refresh();
    }

    private void restoreAutomatic() {
        int result = ZoneController.restoreAutomatic(this);
        if (result == ZoneController.RESULT_OK) {
            toast("Zona automática restaurada (America/Havana).");
        } else if (result == ZoneController.RESULT_NO_PERMISSION) {
            showPermissionHelp();
        } else {
            toast("No se pudo restaurar. Revisa los ajustes del sistema.");
        }
        refresh();
    }

    private void showPermissionHelp() {
        final TextView message = Ui.text(this,
                "Android no permite que una app normal cambie la zona horaria. "
                        + "Hay dos caminos:\n\n"
                        + "1) Cambio manual (sin PC)\n"
                        + "Ajustes → Administración general → Fecha y hora → desactiva "
                        + "«Zona horaria automática» y elige la zona manualmente.\n\n"
                        + "2) Cambio directo desde esta app (permiso por ADB)\n"
                        + "Solo la primera vez, desde un PC con ADB y depuración USB:\n\n"
                        + ADB_COMMAND + "\n\n"
                        + "Después de concederlo, esta pantalla aplicará la zona con un toque. "
                        + "El permiso no requiere root y se puede revocar con pm revoke.",
                13.5f, Ui.INK, false);
        message.setTextIsSelectable(true);
        message.setPadding(dp(20), dp(8), dp(20), dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Habilitar el cambio de zona")
                .setView(message)
                .setPositiveButton("Copiar comando", (d, which) -> copyToClipboard(ADB_COMMAND))
                .setNeutralButton("Ajustes del sistema", (d, which) ->
                        ZoneController.openDateTimeSettings(MainActivity.this))
                .setNegativeButton("Cerrar", null)
                .create();
        dialog.show();
    }

    private void copyToClipboard(String value) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Comando ADB", value));
                toast("Comando copiado al portapapeles.");
            }
        } catch (Throwable error) {
            toast("No se pudo copiar el comando.");
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
