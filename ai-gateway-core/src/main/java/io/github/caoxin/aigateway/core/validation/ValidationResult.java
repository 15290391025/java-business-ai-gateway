package io.github.caoxin.aigateway.core.validation;

import java.util.List;

public record ValidationResult(
    boolean valid,
    List<String> missingFields,
    String message
) {

    public static ValidationResult valid() {
        return new ValidationResult(true, List.of(), "");
    }

    public static ValidationResult invalid(List<String> missingFields, String message) {
        return new ValidationResult(false, missingFields, message);
    }
}

