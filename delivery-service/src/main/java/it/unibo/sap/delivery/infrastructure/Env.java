package it.unibo.sap.delivery.infrastructure;

final class Env {

    static String get(final String name, final String defaultValue) {
        final String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static int getInt(final String name, final int defaultValue) {
        final String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }
}
