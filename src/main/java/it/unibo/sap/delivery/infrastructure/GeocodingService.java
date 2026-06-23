package it.unibo.sap.delivery.infrastructure;

import it.unibo.sap.common.hexagonal.OutputAdapter;
import it.unibo.sap.delivery.application.GeocodingPort;
import it.unibo.sap.delivery.domain.deliveries.Coordinates;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class GeocodingService implements GeocodingPort, OutputAdapter {

    private static final double LAT_MIN = 44.45;
    private static final double LAT_MAX = 44.55;
    private static final double LON_MIN = 11.28;
    private static final double LON_MAX = 11.40;

    @Override
    public Coordinates geocode(final String street, final int number) {
        if (street == null || street.isBlank() || number <= 0) {
            throw new InvalidAddressException("Invalid address");
        }
        final String normalized = (street.trim().toLowerCase() + "#" + number);
        final byte[] hash = sha256(normalized);

        final long latBits = toUnsignedLong(hash, 0);
        final long lonBits = toUnsignedLong(hash, 4);

        final double lat = LAT_MIN + (LAT_MAX - LAT_MIN) * (latBits / (double) 0xFFFFFFFFL);
        final double lon = LON_MIN + (LON_MAX - LON_MIN) * (lonBits / (double) 0xFFFFFFFFL);
        return new Coordinates(lat, lon);
    }

    private static long toUnsignedLong(final byte[] bytes, final int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24)
                | ((long) (bytes[offset + 1] & 0xFF) << 16)
                | ((long) (bytes[offset + 2] & 0xFF) << 8)
                | ((long) (bytes[offset + 3] & 0xFF));
    }

    private static byte[] sha256(final String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
