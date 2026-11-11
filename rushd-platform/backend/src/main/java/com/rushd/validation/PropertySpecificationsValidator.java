package com.rushd.validation;

import com.rushd.dto.PropertySpecificationsRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PropertySpecificationsValidator implements ConstraintValidator<ValidPropertySpecifications, PropertySpecificationsRequest> {
    @Override
    public boolean isValid(PropertySpecificationsRequest request, ConstraintValidatorContext context) {
        if (request == null) return true; // Absent group, not an explicit JSON null.
        var errors = PropertySpecificationsRules.valueErrors(request.values(), request.presentFields());
        if (errors.isEmpty()) return true;
        context.disableDefaultConstraintViolation();
        errors.forEach((field, message) -> context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field).addConstraintViolation());
        return false;
    }
}
