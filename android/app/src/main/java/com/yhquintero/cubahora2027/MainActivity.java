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

import rikka.shizuku.Shizuku;

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
    private TextView automationValue;
    private TextView automationDetail;
    private TextView profilePill;
    private TextView nextValue;
    private TextView rulePill;
    private TextView coverageValue;
    private TextView hintValue;
    private TextView remindersHint;
    private Button primaryButton;
    private Button testButton;
    private Button exactAlarmButton;
    private CheckBox remindersBox;
    private boolean bindingReminders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifications.ensureChannel(this);
        setContentView(buildUi());
        refresh();
        if (AutoSwitch.isEnabled(this)) {
            AutoSwitch.arm(this);
        } else {
            ReminderScheduler.scheduleNext(this);
        }
        requestNotificationPermissionIfNeeded();
        probeRootInBackground();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener);
            Shizuku.addBinderReceivedListenerSticky(binderListener);
        } catch (Throwable ignored) {
            // Shizuku no instalado: la interfaz sigue en modo manual.
        }
        refresh();
    }

    @Override
    protected void onPause() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
            Shizuku.removeBinderReceivedListener(binderListener);
        } catch (Throwable ignored) {
            // Nada que retirar.
        }
        super.onPause();
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

        // Etiqueta arriba y valor debajo: el estado del cambio automático es
        // largo («Desactivado · Shizuku detenido · ábrelo o usa root») y en una
        // fila a dos columnas se partía y se cortaba.
        status.addView(Ui.divider(this));
        status.addView(Ui.spacer(this, 8f));
        status.addView(Ui.text(this, "Cambio automático", 12f, Ui.MUTED, false));
        automationValue = Ui.text(this, "…", 15.5f, Ui.INK, true);
        automationValue.setPadding(0, dp(2), 0, 0);
        status.addView(automationValue);
        automationDetail = Ui.text(this, "", 12.5f, Ui.MUTED, false);
        automationDetail.setPadding(0, dp(4), 0, 0);
        status.addView(automationDetail);

        hintValue = Ui.text(this, "", 12.5f, Ui.MUTED, false);
        hintValue.setPadding(0, dp(10), 0, 0);
        status.addView(hintValue);
        column.addView(status);

        column.addView(Ui.spacer(this, 14f));

        // Perfil Cuba 2026-2027: lo que se instala en el teléfono
        LinearLayout profile = Ui.card(this);
        LinearLayout profileTop = new LinearLayout(this);
        profileTop.setOrientation(LinearLayout.HORIZONTAL);
        profileTop.setGravity(Gravity.CENTER_VERTICAL);
        profileTop.addView(Ui.text(this, "PERFIL "
                        + CubaRules.PROFILE_NAME.toUpperCase(Locale.US), 11.5f, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        profilePill = Ui.pill(this, "…", Ui.BLUE_PALE, Ui.BLUE_DARK);
        profileTop.addView(profilePill);
        profile.addView(profileTop);

        profile.addView(Ui.spacer(this, 10f));
        profile.addView(Ui.text(this, "Desde el 01/11/2026 hasta el 07/11/2027",
                15f, Ui.INK, true));
        profile.addView(Ui.spacer(this, 6f));
        TextView profileText = Ui.text(this,
                "UTC−04:00 en todo ese tramo y UTC−05:00 a partir del 07/11/2027 a la "
                        + "01:00. Al instalarlo, el teléfono queda en la zona fija "
                        + "equivalente y la app aplica el cambio sola, comprobando el "
                        + "resultado con el sistema.", 13f, Ui.INK_SOFT, false);
        profileText.setBackground(Ui.rounded(this, Ui.CANVAS, 12f, null));
        int profilePad = dp(12);
        profileText.setPadding(profilePad, dp(10), profilePad, dp(10));
        profile.addView(profileText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        profile.addView(Ui.spacer(this, 12f));
        primaryButton = Ui.button(this, "Instalar el perfil y activarlo", true);
        primaryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onPrimaryAction();
            }
        });
        profile.addView(primaryButton, fullWidth());

        profile.addView(Ui.spacer(this, 10f));
        testButton = Ui.button(this, "Probar ahora sin esperar a 2027", false);
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runAutomation(true);
            }
        });
        profile.addView(testButton, fullWidth());
        column.addView(profile);

        column.addView(Ui.spacer(this, 14f));

        // Más opciones para activar el cambio automático
        LinearLayout actions = Ui.card(this);
        actions.addView(Ui.text(this, "MÁS OPCIONES PARA ACTIVARLO", 11.5f, Ui.MUTED, true));
        actions.addView(Ui.spacer(this, 8f));
        TextView why = Ui.text(this,
                "Android solo deja cambiar la zona horaria a aplicaciones del sistema: una "
                        + "app instalada como esta no puede hacerlo por su cuenta. Cualquiera "
                        + "de estos canales sirve; la app usa el primero que esté disponible.",
                13f, Ui.MUTED, false);
        actions.addView(why);

        actions.addView(Ui.spacer(this, 12f));
        Button useShizuku = Ui.button(this, "Activar con Shizuku (sin root)", false);
        useShizuku.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onShizukuOption();
            }
        });
        actions.addView(useShizuku, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button useRoot = Ui.button(this, "Activar con root (su)", false);
        useRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activateWithRoot();
            }
        });
        actions.addView(useRoot, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button adb = Ui.button(this, "Copiar comandos para ADB", false);
        adb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyAdbCommands();
            }
        });
        actions.addView(adb, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button openSettings = Ui.button(this, "Ajustar a mano en Ajustes", false);
        openSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ZoneController.openDateTimeSettings(MainActivity.this);
            }
        });
        actions.addView(openSettings, fullWidth());

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
        Button manual = Ui.button(this, "Manual completo: opciones y comandos", false);
        manual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, ManualActivity.class));
            }
        });
        actions.addView(manual, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button guide = Ui.button(this, "Abrir guía del proyecto", false);
        guide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, GuideActivity.class));
            }
        });
        actions.addView(guide, fullWidth());

        actions.addView(Ui.spacer(this, 10f));
        Button diagnostics = Ui.button(this, "Diagnosticar por qué no cambia", false);
        diagnostics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runDiagnostics();
            }
        });
        actions.addView(diagnostics, fullWidth());
        column.addView(actions);

        column.addView(Ui.spacer(this, 14f));

        // Regla del proyecto
        LinearLayout rule = Ui.card(this);
        LinearLayout ruleTop = new LinearLayout(this);
        ruleTop.setOrientation(LinearLayout.HORIZONTAL);
        ruleTop.setGravity(Gravity.CENTER_VERTICAL);
        ruleTop.addView(Ui.text(this, "REGLA DEL PROYECTO", 11.5f, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        rulePill = Ui.pill(this, "…", Ui.BLUE_PALE, Ui.BLUE_DARK);
        ruleTop.addView(rulePill);
        rule.addView(ruleTop);

        rule.addView(Ui.spacer(this, 12f));
        coverageValue = addBlock(rule, "Cobertura definida");
        nextValue = addBlock(rule, "Próxima transición");

        TextView ruleText = Ui.text(this,
                "2026 completo en UTC−04:00 (sin cambio el 1 de noviembre). "
                        + "El 7 de noviembre de 2027 a la 01:00 la regla pasa a UTC−05:00. "
                        + "Después de esa fecha no hay más cambios definidos.", 13f, Ui.INK_SOFT, false);
        ruleText.setBackground(Ui.rounded(this, Ui.CANVAS, 12f, null));
        int boxPad = dp(12);
        ruleText.setPadding(boxPad, dp(10), boxPad, dp(10));
        rule.addView(ruleText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return valueView;
    }

    /** Etiqueta y valor en líneas separadas: los valores largos no se aprietan. */
    private TextView addBlock(LinearLayout parent, String label) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(6), 0, dp(6));
        TextView value = Ui.text(this, "…", 15f, Ui.INK, true);
        block.addView(Ui.text(this, label, 12f, Ui.MUTED, false));
        block.addView(value);
        parent.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return value;
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

        rulePill.setText(CubaRules.labelForMinutes(required));
        rulePill.setTextColor(matches ? Ui.MINT_DEEP : Ui.AMBER_DEEP);
        rulePill.setBackground(Ui.rounded(this, matches ? Ui.MINT_PALE : Ui.AMBER_PALE,
                999f, null));

        if (!known) {
            setStatus("Antes de la regla", Ui.BLUE_PALE, Ui.BLUE_DARK);
        } else if (matches) {
            setStatus("Coincide con la regla", Ui.MINT_PALE, Ui.MINT_DEEP);
        } else {
            setStatus("Ajuste pendiente", Ui.AMBER_PALE, Ui.AMBER_DEEP);
        }

        boolean autoReady = AutoSwitch.canApplyNow(this);
        boolean autoOn = AutoSwitch.isEnabled(this);

        // Valor corto en su propia línea y detalle debajo: el estado completo
        // («Desactivado · Shizuku detenido · ábrelo o usa root») no cabe en una
        // fila a dos columnas y antes se partía y se cortaba.
        if (autoOn && autoReady) {
            automationValue.setText("Activo con " + AutoSwitch.channelName());
        } else if (autoOn) {
            automationValue.setText("Activo · canal no disponible");
        } else {
            automationValue.setText("Desactivado");
        }
        automationValue.setTextColor(autoOn && autoReady ? Ui.MINT_DEEP
                : (autoReady ? Ui.BLUE_DARK : Ui.AMBER_DEEP));

        long nextAuto = CubaRules.nextTransition(now);
        String detail;
        if (autoOn && autoReady) {
            detail = nextAuto > 0
                    ? "Cambia solo el " + formatTransition(nextAuto)
                    + " y comprueba el resultado."
                    : "No quedan transiciones definidas en la regla.";
        } else if (autoOn) {
            detail = AutoSwitch.channelStatus(this)
                    + " · el cambio de 2027 no se aplicará: ábrelo o ajusta a mano.";
        } else if (autoReady) {
            detail = "Canal listo (" + AutoSwitch.channelName()
                    + "). Pulsa «Instalar el perfil y activarlo».";
        } else {
            detail = AutoSwitch.channelStatus(this)
                    + " · pulsa «Instalar el perfil y activarlo» para elegir canal.";
        }
        automationDetail.setText(detail);

        if (profilePill != null) {
            profilePill.setText(CubaRules.labelForMinutes(required));
            profilePill.setTextColor(matches ? Ui.MINT_DEEP : Ui.AMBER_DEEP);
            profilePill.setBackground(Ui.rounded(this,
                    matches ? Ui.MINT_PALE : Ui.AMBER_PALE, 999f, null));
        }

        if (autoReady && autoOn) {
            primaryButton.setText(matches
                    ? "Reaplicar · el teléfono ya coincide"
                    : "Aplicar ahora " + CubaRules.labelForMinutes(required));
        } else {
            primaryButton.setText("Instalar el perfil y activarlo");
        }
        testButton.setVisibility(autoReady ? View.VISIBLE : View.GONE);

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

    // ------------------------------------------------------------------
    // Cambio automático
    // ------------------------------------------------------------------

    /** Un solo toque: elige canal, activa el cambio y aplica el desfase vigente. */
    private void onPrimaryAction() {
        if (AutoSwitch.canApplyNow(this)) {
            AutoSwitch.setEnabled(this, true);
            AutoSwitch.arm(this);
            runAutomation(false);
            return;
        }
        if (ShizukuChannel.installed(this) && ShizukuChannel.running()
                && !ShizukuChannel.authorized()) {
            ShizukuChannel.requestPermission();
            return;
        }
        showChannelOptions();
    }

    /** Opción 1: Shizuku (sin root). */
    private void onShizukuOption() {
        if (AutoSwitch.shizukuReady()) {
            AutoSwitch.setEnabled(this, true);
            AutoSwitch.arm(this);
            runAutomation(false);
            return;
        }
        if (ShizukuChannel.installed(this) && ShizukuChannel.running()) {
            ShizukuChannel.requestPermission();
            return;
        }
        openShizuku();
    }

    /** Abre Shizuku si está instalado; si no, explica cómo instalarlo. */
    private void openShizuku() {
        try {
            Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(ShizukuChannel.SHIZUKU_PACKAGE);
            if (intent != null) {
                startActivity(intent);
                return;
            }
        } catch (Throwable ignored) {
            // Se ofrece la explicación con el enlace de descarga.
        }
        showShizukuHelp();
    }

    /** Comprueba root en segundo plano al abrir la app, sin bloquear la interfaz. */
    private void probeRootInBackground() {
        if (RootChannel.checked()) {
            return;
        }
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                RootChannel.probe();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                });
            }
        }, "cuba-root-probe");
        worker.setDaemon(true);
        worker.start();
    }

    /** Opción 2: root. Comprueba su y, si responde, aplica el cambio. */
    private void activateWithRoot() {
        toast("Comprobando root…");
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean root = RootChannel.probe();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                        if (root) {
                            AutoSwitch.setEnabled(MainActivity.this, true);
                            AutoSwitch.arm(MainActivity.this);
                            runAutomation(false);
                        } else {
                            showRootHelp();
                        }
                    }
                });
            }
        }, "cuba-root");
        worker.setDaemon(true);
        worker.start();
    }

    /** Opción 3: copiar los comandos para el shell de ADB desde un PC. */
    private void copyAdbCommands() {
        long now = System.currentTimeMillis();
        String zoneId = OffsetGuide.zoneIdFor(CubaRules.offsetMinutes(now));
        String commands = "adb shell settings put global auto_time_zone 0\n"
                + "adb shell cmd alarm set-timezone " + zoneId + "\n"
                + "adb shell getprop persist.sys.timezone\n"
                + "# volver a la zona de Cuba: adb shell cmd alarm set-timezone America/Havana";
        copyToClipboard(commands);
        new AlertDialog.Builder(this)
                .setTitle("Comandos para ADB")
                .setMessage("Copiados al portapapeles. Con el cable, la depuración USB "
                        + "activada y «adb shell» no necesitas root ni Shizuku.\n\n"
                        + commands
                        + "\n\nEl perfil Cuba 2026-2027 hace esto mismo en 2027, "
                        + "pero ADB solo cambia la zona de hoy.")
                .setPositiveButton("Entendido", null)
                .setNeutralButton("Ver el manual", (dialog, which) ->
                        startActivity(new Intent(MainActivity.this, ManualActivity.class)))
                .show();
    }

    /** Diálogo con los tres canales disponibles. */
    private void showChannelOptions() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(8), pad, dp(8));
        box.addView(Ui.text(this, AutoSwitch.channelStatus(this)
                + ". Elige un canal para el cambio de 2027:", 13.5f, Ui.INK, false));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Activarlo con un canal")
                .setView(box)
                .setNegativeButton("Cerrar", null)
                .create();

        box.addView(Ui.spacer(this, 12f));
        Button shizuku = Ui.button(this, "Usar Shizuku (sin root)", true);
        shizuku.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                onShizukuOption();
            }
        });
        box.addView(shizuku, fullWidth());

        box.addView(Ui.spacer(this, 10f));
        Button root = Ui.button(this, "Usar root (su)", false);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                activateWithRoot();
            }
        });
        box.addView(root, fullWidth());

        box.addView(Ui.spacer(this, 10f));
        Button adb = Ui.button(this, "Copiar comandos para ADB", false);
        adb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                copyAdbCommands();
            }
        });
        box.addView(adb, fullWidth());

        dialog.show();
    }

    /** Explica qué hacer si no hay root operativo. */
    private void showRootHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Root no disponible")
                .setMessage("«su» no respondió: este teléfono no tiene root operativo o el "
                        + "gestor no dio permiso a la app.\n\nPuedes usar Shizuku (sin root) o "
                        + "los comandos de ADB desde un PC; el cambio se aplica igual.")
                .setPositiveButton("Usar Shizuku", (dialog, which) -> onShizukuOption())
                .setNeutralButton("Copiar ADB", (dialog, which) -> copyAdbCommands())
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void onShizukuPermission(int requestCode, int grantResult) {
        if (requestCode != ShizukuChannel.REQUEST_CODE) {
            return;
        }
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            AutoSwitch.setEnabled(this, true);
            AutoSwitch.arm(this);
            runAutomation(false);
        } else {
            toast("Sin autorización en Shizuku: se mantiene el aviso manual.");
            refresh();
        }
    }

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    onShizukuPermission(requestCode, grantResult);
                }
            };

    private final Shizuku.OnBinderReceivedListener binderListener =
            new Shizuku.OnBinderReceivedListener() {
                @Override
                public void onBinderReceived() {
                    refresh();
                }
            };

    /** Ejecuta la aplicación en segundo plano y muestra el resultado. */
    private void runAutomation(boolean manualTest) {
        final long now = System.currentTimeMillis();
        final int required = CubaRules.offsetMinutes(now);
        final String label = CubaRules.labelForMinutes(required);
        requestNotificationPermissionIfNeeded();
        if (needsExactAlarmPermission()) {
            toast("Concede «Alarmas y recordatorios» para que el cambio sea puntual.");
        }
        toast("Aplicando " + label + "…");

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final AutoSwitch.Outcome outcome = AutoSwitch.applyNowDetailed(
                        getApplicationContext(), now);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showResultDialog(outcome, label, manualTest);
                        refresh();
                    }
                });
            }
        }, "cuba-apply");
        worker.setDaemon(true);
        worker.start();
    }

    private void showDiagnostics() {
        final Diagnostics.Report[] holder = new Diagnostics.Report[1];
        String title = "Informe del sistema";
        String pending = "Recopilando estado del teléfono, canales y prueba de cambio…";
        TextView message = Ui.text(this, pending, 13f, Ui.INK, false);
        message.setPadding(dp(20), dp(10), dp(20), dp(10));
        message.setTextIsSelectable(true);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(message)
                .setPositiveButton("Copiar informe", null)
                .setNeutralButton("Cerrar", null)
                .create();
        dialog.show();
        // Reemplazar el listener por defecto para no cerrar al copiar.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Diagnostics.Report report = holder[0];
                if (report != null) {
                    copyToClipboard(report.text);
                } else {
                    toast("Aún sin informe: espera un instante.");
                }
            }
        });

        // Ejecutar el diagnóstico fuera del hilo principal.
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final Diagnostics.Report report = Diagnostics.run(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        holder[0] = report;
                        message.setText(report.text);
                        refresh();
                    }
                });
            }
        }, "cuba-diag");
        worker.setDaemon(true);
        worker.start();
    }

    private void runDiagnostics() {
        toast("Generando informe…");
        showDiagnostics();
    }

    private void showResultDialog(AutoSwitch.Outcome outcome, String label, boolean manualTest) {
        AutoSwitch.Result result = outcome.result;
        String title;
        String message;
        switch (result) {
            case APPLIED:
                title = "Cambio aplicado";
                message = "La zona del teléfono es ahora " + label + " y está verificada"
                        + (outcome.strategy.length() > 0 ? " con " + outcome.strategy : "")
                        + ".\n\nLa transición del 7 de noviembre de 2027 se aplicará sola a la "
                        + "01:00, siempre que el canal siga activo (" + AutoSwitch.channelName()
                        + "). Si se detiene, recibirás un aviso para hacerlo en un toque.";
                break;
            case UNVERIFIED:
                title = "Cambio sin verificar";
                message = "Se envió la orden (" + outcome.strategy + "), pero el sistema no confirma "
                        + label + " (leído: " + outcome.detail + ").\n\nDesactiva «Zona horaria "
                        + "automática» en Ajustes y vuelve a intentarlo. Si sigue igual, pulsa "
                        + "«Diagnosticar por qué no cambia» para ver el informe completo.";
                break;
            case NO_CHANNEL:
                title = "Falta el canal privilegiado";
                message = "Android no permite a una app instalada cambiar la zona por sí "
                        + "sola (" + outcome.detail + ").\n\nEl perfil Cuba 2026-2027 existe dentro de la app, "
                        + "no como nombre nuevo en la lista del sistema: Android guarda sus zonas en "
                        + "la partición del sistema y solo una app de sistema puede añadir nombres. "
                        + "Lo que hace esta app es poner la zona fija equivalente (" + label
                        + ") y cambiarla sola cuando hay canal.\n\nPara activarlo elige Shizuku, "
                        + "root o ADB. Mientras tanto puedes aplicar " + label + " desde Ajustes.";
                break;
            default:
                title = "No se pudo aplicar";
                message = "El canal respondió con un error al ejecutar el cambio a " + label
                        + " (" + outcome.detail + ").\n\nPrueba de nuevo, revisa el permiso en el "
                        + "gestor de root/Shizuku o pulsa «Diagnosticar por qué no cambia»."
                        + (manualTest ? "" : "");
                break;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Entendido", null);
        if (result == AutoSwitch.Result.APPLIED) {
            builder.setNeutralButton("Ver estado", (dialog, which) -> refresh());
        } else if (result == AutoSwitch.Result.NO_CHANNEL) {
            builder.setNeutralButton("Elegir canal", (dialog, which) -> showChannelOptions());
        } else {
            builder.setNeutralButton("Diagnosticar", (dialog, which) -> showDiagnostics());
        }
        // Siempre ofrecer el diagnóstico como tercera opción.
        if (result != AutoSwitch.Result.APPLIED) {
            builder.setNegativeButton("Ver informe", (dialog, which) -> showDiagnostics());
        }
        builder.show();
    }

    /** Explica cómo instalar y arrancar Shizuku. */
    private void showShizukuHelp() {
        String status = AutoSwitch.channelStatus(this);
        String message = "Estado: " + status + "\n\n"
                + "El cambio automático necesita que esta app pueda ejecutar el comando "
                + "de zona como el shell de Android. Shizuku presta ese permiso sin root:\n\n"
                + "1. Instala Shizuku (Play Store o github.com/RikkaApps/Shizuku).\n"
                + "2. Activa Opciones de desarrollador y Depuración inalámbrica.\n"
                + "3. En Shizuku, «Emparejar» + «Iniciar» (y «Iniciar al arrancar» si "
                + "quieres que siga tras reiniciar).\n"
                + "4. Vuelve aquí y pulsa «Instalar el perfil y activarlo».\n\n"
                + "Si el teléfono tiene root, puedes usar «Activar con root (su)» sin "
                + "instalar nada. Y sin ningún canal, la app seguirá avisándote y podrás "
                + "aplicar el cambio desde Ajustes en un minuto.";
        new AlertDialog.Builder(this)
                .setTitle("Shizuku")
                .setMessage(message)
                .setPositiveButton("Abrir Shizuku", (dialog, which) -> {
                    try {
                        Intent intent = getPackageManager()
                                .getLaunchIntentForPackage(ShizukuChannel.SHIZUKU_PACKAGE);
                        if (intent != null) {
                            startActivity(intent);
                        } else {
                            toast("Shizuku no está instalado en este teléfono.");
                        }
                    } catch (Throwable error) {
                        toast("No se pudo abrir Shizuku.");
                    }
                })
                .setNeutralButton("Instalar Shizuku", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/RikkaApps/Shizuku/releases")));
                    } catch (Throwable error) {
                        toast("Abre github.com/RikkaApps/Shizuku en el navegador.");
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

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
