package io.github.caoxin.aigateway.core.validation;

public class NoopAiCommandValidator implements AiCommandValidator {

    @Override
    public ValidationResult validate(Object command) {
        return ValidationResult.valid();
    }
}

