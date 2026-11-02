package com.yhquintero.cubahora2027;

/**
 * Texto de ayuda para fijar en el teléfono el desfase que exige la regla.
 *
 * <p>Como una aplicación instalada no puede cambiar la zona horaria, la app
 * indica qué zona fija elegir en los ajustes del sistema. Se proponen ciudades
 * que hoy mantienen el mismo desfase todo el año, para que el teléfono no
 * vuelva a moverse por su cuenta.</p>
 */
public final class OffsetGuide {

    private OffsetGuide() {
    }

    /** Ciudad principal con desfase fijo igual al indicado, o {@code null}. */
    public static String cityFor(int minutes) {
        if (minutes == CubaRules.OFFSET_SUMMER) {
            return "Santo Domingo (República Dominicana)";
        }
        if (minutes == CubaRules.OFFSET_STANDARD) {
            return "Bogotá (Colombia)";
        }
        return null;
    }

    /** Otras ciudades con el mismo desfase fijo, para la lista del teléfono. */
    public static String alternativesFor(int minutes) {
        if (minutes == CubaRules.OFFSET_SUMMER) {
            return "San Juan, La Paz, Manaos, Caracas";
        }
        if (minutes == CubaRules.OFFSET_STANDARD) {
            return "Lima, Panamá, Kingston, Quito";
        }
        return "";
    }

    /** Nombre que suele mostrar el selector de Android para ese desfase. */
    public static String pickerLabelFor(int minutes) {
        int absolute = Math.abs(minutes);
        String sign = minutes < 0 ? "-" : "+";
        String hours = absolute / 60 < 10 ? "0" + (absolute / 60) : String.valueOf(absolute / 60);
        String mins = absolute % 60 < 10 ? "0" + (absolute % 60) : String.valueOf(absolute % 60);
        return "GMT" + sign + hours + ":" + mins;
    }

    /** Pasos numerados para dejar el teléfono en el desfase indicado. */
    public static String stepsFor(int minutes) {
        String label = CubaRules.labelForMinutes(minutes);
        String picker = pickerLabelFor(minutes);
        String city = cityFor(minutes);
        String alternatives = alternativesFor(minutes);
        StringBuilder text = new StringBuilder();
        text.append("Lo que la regla pide ahora: ").append(label).append(".\n\n");
        text.append("1. Abre Ajustes → Administración general → Fecha y hora ")
                .append("(en Samsung; en otros Android suele ser Ajustes → Sistema → Fecha y hora).\n\n");
        text.append("2. Desactiva «Zona horaria automática». Si sigue activada, la red ")
                .append("puede volver a imponer America/Havana.\n\n");
        text.append("3. Entra en «Zona horaria» y elige una zona con desfase fijo ")
                .append(label).append(": ");
        if (city != null) {
            text.append(city).append(".\n\n");
            text.append("   Alternativas con el mismo desfase: ").append(alternatives)
                    .append(". Si tu teléfono lista desfases en vez de ciudades, ")
                    .append("elige ").append(picker).append(".\n\n");
        } else {
            text.append(picker).append(".\n\n");
        }
        text.append("4. Vuelve a esta app: la tarjeta de estado debe indicar que el ")
                .append("desfase coincide con la regla.\n\n");
        text.append("Por qué no lo hace la app: Android solo permite cambiar la zona ")
                .append("a aplicaciones del sistema o con permiso de firma. Una app ")
                .append("instalada por el usuario únicamente puede leer el estado.");
        return text.toString();
    }

    /** Aviso corto que explica la situación actual del teléfono. */
    public static String statusHint(boolean auto, boolean matches, String zoneId) {
        if (auto) {
            return "Zona automática activada (" + zoneId + "). Mientras esté activa, "
                    + "la red puede devolver la zona de Cuba y deshacer el ajuste manual.";
        }
        if (matches) {
            return "Zona fija " + zoneId + ". El teléfono ya no se moverá solo: vuelve "
                    + "a revisar esta pantalla en la próxima transición.";
        }
        return "Zona fija " + zoneId + ", con un desfase distinto al de la regla. "
                + "Sigue los pasos de «Cómo ajustarlo».";
    }
}
