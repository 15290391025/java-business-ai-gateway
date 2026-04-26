package io.github.caoxin.aigateway.autoconfigure;

import io.github.caoxin.aigateway.core.validation.AiCommandValidator;
import io.github.caoxin.aigateway.core.validation.ValidationResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Set;

public class BeanValidationAiCommandValidator implements AiCommandValidator {

    private final Validator validator;

    public BeanValidationAiCommandValidator(Validator validator) {
        this.validator = validator;
    }

    @Override
    public ValidationResult validate(Object command) {
        Set<ConstraintViolation<Object>> violations = validator.validate(command);
        if (violations.isEmpty()) {
            return ValidationResult.valid();
        }

        List<String> fields = violations.stream()
            .map(violation -> violation.getPropertyPath().toString())
            .distinct()
            .toList();

        String message = violations.stream()
            .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
            .findFirst()
            .orElse("参数校验失败");

        return ValidationResult.invalid(fields, message);
    }
}
