package it.unibo.sap.account.infrastructure;
final class Env {

    private Env() {
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
