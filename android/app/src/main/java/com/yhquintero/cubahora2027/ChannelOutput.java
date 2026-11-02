package com.yhquintero.cubahora2027;

/**
 * Resultado de un comando ejecutado con privilegios (Shizuku o root).
 *
 * <p>Es inmutable y solo describe lo que devolvió el proceso: código de salida,
 * salida estándar y error. Ningún canal puede afirmar que el cambio se aplicó
 * sin leer después la zona del sistema.</p>
 */
public final class ChannelOutput {

    public final int code;
    public final String out;
    public final String error;

    public ChannelOutput(int code, String out, String error) {
        this.code = code;
        this.out = out == null ? "" : out;
        this.error = error == null ? "" : error;
    }

    /** Código de salida 0: el comando terminó sin error. */
    public boolean ok() {
        return code == 0;
    }

    /** Texto útil para mostrar al usuario: la salida o, si está vacía, el error. */
    public String describe() {
        if (out.length() > 0) {
            return out;
        }
        return error;
    }
}
