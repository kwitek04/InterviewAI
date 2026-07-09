package com.interviewai.cv.domain;

import java.util.Objects;

/**
 * Fail-fast validation helpers shared by the domain's value objects.
 */
final class Preconditions {

    private Preconditions() {
    }

    static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, () -> fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
