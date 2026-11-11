package com.rushd.validation;

import com.rushd.dto.PropertySpecificationsRequest;
import com.rushd.entity.*;
import com.rushd.exception.InvalidPropertySpecificationsException;
import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure candidate validation: no repository access and no mutation of a managed entity. */
public final class PropertySpecificationsRules {
    private PropertySpecificationsRules() { }
    private static final Set<String> AREAS = Set.of("landArea", "unitNetArea", "unitGrossArea", "totalBuiltArea");
    private static final Set<String> AGE = Set.of("estimatedAgeYears", "ageAsOfYear");
    private static final Set<String> ROOMS = Set.of("bedrooms", "bathrooms", "livingRooms");
    private static final Set<String> UNITS = Set.of("totalUnits", "residentialUnits", "commercialUnits");
    private static final Set<String> SERVICES = Set.of("electricityConnected", "waterConnected", "sewerConnected");
    private static final Set<String> EQUIPMENT = Set.of("hasElevator", "parkingSpaces", "hasAirConditioning",
            "hasFittedKitchen", "hasMaidRoom", "hasDriverRoom", "hasPool", "hasYard", "hasBalcony", "hasPrivateRoof");

    public static Map<String, String> valueErrors(PropertySpecifications values, Set<String> present) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (var field : PropertySpecificationFields.ALL) {
            Object value = field.read().apply(values);
            if (value == null) continue;
            if (value instanceof Integer number && (number < field.min() || number > field.max())) {
                errors.put(field.name(), "القيمة خارج المجال المسموح: " + field.min() + " إلى " + field.max());
            }
            if (value instanceof BigDecimal decimal && (decimal.signum() <= 0
                    || decimal.compareTo(new BigDecimal("9999999999.99")) > 0
                    || !SafeDecimalPrecisionValidator.withinLimits(decimal, 10, 2))) {
                errors.put(field.name(), "المساحة من 0.01 إلى 9999999999.99 وبمنزلتين عشريتين كحد أقصى");
            }
            if (value instanceof String text && text.length() > 100) {
                errors.put(field.name(), "وصف النوع يجب ألا يتجاوز 100 حرف");
            }
        }
        if (values.getAgeAsOfYear() != null && values.getAgeAsOfYear() > Year.now(ZoneId.of("Asia/Riyadh")).getValue()) {
            errors.put("ageAsOfYear", "سنة التقدير لا تقبل المستقبل");
        }
        if (present.contains("estimatedAgeYears") != present.contains("ageAsOfYear")) {
            errors.put("estimatedAgeYears", "أرسل العمر وسنة تقديره معًا للتحديث أو المسح");
        }
        if ((values.getEstimatedAgeYears() == null) != (values.getAgeAsOfYear() == null)) {
            errors.put("ageAsOfYear", "العمر وسنة تقديره إما معلومان معًا أو كلاهما غير محدد");
        }
        return errors;
    }

    public static PropertySpecifications mergeAndValidate(PropertyType type, BigDecimal area,
            PropertySpecifications stored, PropertySpecificationsRequest request) {
        PropertySpecifications candidate = stored == null ? new PropertySpecifications() : stored.copy();
        if (request != null) {
            PropertySpecifications values = request.values();
            request.presentFields().forEach(name -> PropertySpecificationFields.BY_NAME.get(name).apply(candidate, values));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        valueErrors(candidate, request == null ? Set.of() : request.presentFields())
                .forEach((field, message) -> errors.put("specifications." + field, message));
        Set<String> allowed = allowedFields(type, candidate.getCommercialScope());
        for (var field : PropertySpecificationFields.ALL) {
            if (field.read().apply(candidate) != null && !allowed.contains(field.name())) {
                errors.put("specifications." + field.name(), "هذا الحقل لا ينطبق على نوع العقار أو نطاقه؛ امسحه صراحة عند تغيير النوع");
            }
        }
        if (candidate.getUnitNetArea() != null && candidate.getUnitGrossArea() != null
                && candidate.getUnitNetArea().compareTo(candidate.getUnitGrossArea()) > 0) {
            errors.put("specifications.unitNetArea", "صافي الوحدة لا يتجاوز إجماليها");
        }
        if (candidate.getPrimaryAreaSource() != null) {
            BigDecimal source = switch (candidate.getPrimaryAreaSource()) {
                case LAND -> candidate.getLandArea();
                case UNIT_NET -> candidate.getUnitNetArea();
                case UNIT_GROSS -> candidate.getUnitGrossArea();
                case TOTAL_BUILT -> candidate.getTotalBuiltArea();
            };
            if (source == null || area == null || area.compareTo(source) != 0) {
                errors.put("specifications.primaryAreaSource", "المساحة الرئيسية يجب أن تطابق مصدرًا محددًا موجودًا؛ صحح القيم أو فك الربط صراحة");
            }
        }
        Integer total = candidate.getTotalUnits();
        Integer residential = candidate.getResidentialUnits();
        Integer commercial = candidate.getCommercialUnits();
        if (total != null && ((residential != null && residential > total)
                || (commercial != null && commercial > total)
                || (residential != null && commercial != null && (long) residential + commercial > total))) {
            errors.put("specifications.totalUnits", "أعداد الوحدات الجزئية ومجموعها لا تتجاوز الإجمالي المعلوم");
        }
        if (!errors.isEmpty()) throw new InvalidPropertySpecificationsException(errors);
        return candidate;
    }

    public static Set<String> allowedFields(PropertyType type, CommercialScope scope) {
        Set<String> allowed = new HashSet<>(Set.of("primaryAreaSource"));
        if (type == null) return allowed;
        switch (type) {
            case LAND -> { allowed.addAll(Set.of("landArea", "parkingSpaces")); allowed.addAll(SERVICES); }
            case APARTMENT, VILLA -> {
                allowed.addAll(AGE); allowed.addAll(ROOMS); allowed.addAll(EQUIPMENT);
                allowed.addAll(Set.of("condition", "finishing", "furnishing"));
                allowed.addAll(type == PropertyType.APARTMENT ? Set.of("unitNetArea", "unitGrossArea", "floorNumber")
                        : Set.of("landArea", "totalBuiltArea", "floorCount"));
            }
            case BUILDING -> {
                allowed.addAll(AGE); allowed.addAll(UNITS); allowed.addAll(SERVICES);
                allowed.addAll(Set.of("landArea", "totalBuiltArea", "floorCount", "parkingSpaces", "condition", "finishing",
                        "hasElevator", "hasAirConditioning", "hasPool", "hasYard", "hasPrivateRoof"));
            }
            case COMMERCIAL -> {
                allowed.addAll(AGE); allowed.addAll(SERVICES);
                allowed.addAll(Set.of("commercialScope", "commercialSubtype", "bathrooms", "parkingSpaces", "condition", "finishing",
                        "hasElevator", "hasAirConditioning", "hasFittedKitchen", "hasPool", "hasYard", "hasBalcony", "hasPrivateRoof"));
                if (scope == CommercialScope.UNIT) allowed.addAll(Set.of("unitNetArea", "unitGrossArea", "floorNumber"));
                if (scope == CommercialScope.WHOLE_BUILDING) {
                    allowed.addAll(Set.of("landArea", "totalBuiltArea", "floorCount")); allowed.addAll(UNITS);
                }
            }
            case FARM -> allowed.addAll(Set.of("landArea", "totalBuiltArea", "parkingSpaces", "hasPool", "hasYard",
                    "electricityConnected", "hasIrrigation", "wellCount", "waterSource"));
            case OTHER -> { allowed.addAll(AREAS); allowed.addAll(EQUIPMENT); allowed.add("otherTypeDescription"); }
        }
        return Set.copyOf(allowed);
    }
}
