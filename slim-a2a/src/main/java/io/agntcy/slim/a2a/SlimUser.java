// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a;

import java.util.Objects;

import org.a2aproject.sdk.server.auth.User;

/**
 * The verified SLIM identity of an A2A caller, as reported by the SlimRPC
 * {@link io.agntcy.slim.bindings.slimrpc.Context}. The subject and issuer come
 * from the peer's MLS session token, never from request metadata.
 *
 * <p>{@link #getUsername()} is the token subject ({@code sub}). The subject and
 * issuer are also available as the {@code "sub"} and {@code "iss"} attributes.
 */
public final class SlimUser implements User {

    /** Attribute name for the verified token subject. */
    public static final String SUBJECT_ATTRIBUTE = "sub";
    /** Attribute name for the verified token issuer. */
    public static final String ISSUER_ATTRIBUTE = "iss";

    private final String subject;
    private final String issuer;

    /**
     * @param subject the verified token subject; must not be blank
     * @param issuer  the verified token issuer, or {@code null} if the token has none
     */
    public SlimUser(String subject, String issuer) {
        Objects.requireNonNull(subject, "subject");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        this.subject = subject;
        this.issuer = issuer == null || issuer.isBlank() ? null : issuer;
    }

    /** The verified token subject ({@code sub}). */
    public String getSubject() {
        return subject;
    }

    /** The verified token issuer ({@code iss}), or {@code null} if the token has none. */
    public String getIssuer() {
        return issuer;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public String getUsername() {
        return subject;
    }

    @Override
    public Object getAttribute(String name) {
        return switch (name) {
            case SUBJECT_ATTRIBUTE -> subject;
            case ISSUER_ATTRIBUTE -> issuer;
            case null, default -> null;
        };
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SlimUser other
                && subject.equals(other.subject)
                && Objects.equals(issuer, other.issuer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, issuer);
    }

    @Override
    public String toString() {
        return "SlimUser[subject=" + subject + ", issuer=" + issuer + "]";
    }
}
