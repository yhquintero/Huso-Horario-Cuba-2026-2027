package com.yhquintero.cubahora2027;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Regla horaria de Cuba usada por la aplicación.
 *
 * <p>Es la lectura del perfil publicado en el repositorio
 * ({@code .reg/2_Cuba_2027_Correcto.reg}) combinada con la decisión documentada
 * del proyecto: la entrada 2026 no declara transición (UTC-04:00 durante todo
 * 2026) y el paso a UTC-05:00 se aplica el 7 de noviembre de 2027 a la 01:00,
 * fecha que corresponde al StandardDate codificado en la entrada 2027.</p>
 *
 * <p>El proyecto no certifica que este calendario sea oficial.</p>
 */
public final class CubaRules {

    /** Horario de verano: UTC-04:00. */
    public static final int OFFSET_SUMMER = -240;

    /** Horario estándar: UTC-05:00. */
    public static final int OFFSET_STANDARD = -300;

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** 1 de enero de 2026, 00:00 local con UTC-04:00 = 04:00 UTC. */
    public static final long START_2026 = utc(2026, 1, 1, 4, 0);

    /** 7 de noviembre de 2027, 01:00 local con UTC-04:00 = 05:00 UTC. */
    public static final long CHANGE_2027 = utc(2027, 11, 7, 5, 0);

    private CubaRules() {
    }

    /** Construye un instante UTC sin depender de la zona del dispositivo. */
    public static long utc(int year, int month, int day, int hour, int minute) {
        GregorianCalendar cal = new GregorianCalendar(UTC, Locale.US);
        cal.clear();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Desfase respecto a UTC, en minutos, para un instante dado. */
    public static int offsetMinutes(long epochMillis) {
        return epochMillis < CHANGE_2027 ? OFFSET_SUMMER : OFFSET_STANDARD;
    }

    /**
     * Indica si el instante cae en el tramo que la regla define explícitamente
     * (de 2026-01-01 a la transición de 2027). Después de esa fecha la regla
     * mantiene UTC-05:00 sin más cambios declarados.
     */
    public static boolean insideDefinedRange(long epochMillis) {
        return epochMillis >= START_2026 && epochMillis < CHANGE_2027;
    }

    /**
     * Siguiente transición estrictamente posterior a {@code fromMillis}.
     * Devuelve {@code -1} si la regla no define más cambios.
     */
    public static long nextTransition(long fromMillis) {
        if (fromMillis < START_2026) {
            return START_2026;
        }
        if (fromMillis < CHANGE_2027) {
            return CHANGE_2027;
        }
        return -1L;
    }

    /**
     * Identificador de zona IANA con desfase fijo equivalente al del perfil.
     * La convención de {@code Etc/GMT} invierte el signo: UTC-04:00 = Etc/GMT+4.
     */
    public static String zoneIdForOffset(int minutes) {
        if (minutes == 0) {
            return "Etc/GMT";
        }
        int hours = Math.abs(minutes) / 60;
        return "Etc/GMT" + (minutes < 0 ? "+" : "-") + hours;
    }

    /** Etiqueta neutra del tipo {@code UTC-04:00}. */
    public static String labelForMinutes(int minutes) {
        int abs = Math.abs(minutes);
        return "UTC" + (minutes < 0 ? "-" : "+") + twoDigits(abs / 60) + ":" + twoDigits(abs % 60);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
