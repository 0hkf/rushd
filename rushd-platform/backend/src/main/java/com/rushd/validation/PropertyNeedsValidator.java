package com.rushd.validation;

import com.rushd.dto.PropertyNeedsRequest;
import com.rushd.entity.ListingType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PropertyNeedsValidator implements ConstraintValidator<ConsistentPropertyNeeds, PropertyNeedsRequest> {
    @Override
    public boolean isValid(PropertyNeedsRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (request.getListingType() == ListingType.RENT && request.getRentalPeriod() == null) {
            violation(context, "rentalPeriod", "حدد فترة الإيجار الشهرية أو السنوية");
            valid = false;
        } else if (request.getListingType() == ListingType.SALE && request.getRentalPeriod() != null) {
            violation(context, "rentalPeriod", "فترة الإيجار لا تستخدم مع البيع");
            valid = false;
        }

        if (request.getDistrictRequirement() != null
                && (request.getDistrict() == null || request.getDistrict().isBlank())) {
            violation(context, "district", "حدد الحي قبل تحديد أهمية هذا الشرط");
            valid = false;
        }
        if (request.getAreaRequirement() != null
                && request.getMinArea() == null && request.getMaxArea() == null) {
            violation(context, "areaRequirement", "حدد حداً للمساحة قبل تحديد أهمية هذا الشرط");
            valid = false;
        }

        if (request.getMinArea() != null && request.getMaxArea() != null
                && request.getMinArea().compareTo(request.getMaxArea()) > 0) {
            violation(context, "maxArea", "الحد الأعلى للمساحة يجب ألا يقل عن الحد الأدنى");
            valid = false;
        }
        return valid;
    }

    private void violation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field).addConstraintViolation();
    }
}
