package it.unibo.sap.account.domain;

import it.unibo.sap.common.ddd.ValueObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class Password implements ValueObject {

    private final String hash;

    private Password(final String hash) {
        this.hash = hash;
    }

    public static Password fromRaw(final String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        return new Password(sha256(rawPassword));
    }

    public static Password fromHash(final String hash) {
        Objects.requireNonNull(hash, "Password hash must not be null");
        return new Password(hash);
    }

    public boolean matches(final String rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        return this.hash.equals(sha256(rawPassword));
    }

    public String hash() {
        return hash;
    }

    private static String sha256(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Password that)) return false;
        return hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public String toString() {
        return "Password{****}";
    }
}