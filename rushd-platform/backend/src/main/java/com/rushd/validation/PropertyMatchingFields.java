package com.rushd.validation;

import com.rushd.dto.*;
import com.rushd.entity.Property;
import com.rushd.entity.PropertyType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/** Reuses the storage contract; excludes age, free text, areas and structural scope. */
public final class PropertyMatchingFields {
    private PropertyMatchingFields() { }
    private static final Set<String> EXCLUDED = Set.of("primaryAreaSource", "landArea", "unitNetArea",
            "unitGrossArea", "totalBuiltArea", "estimatedAgeYears", "ageAsOfYear", "commercialScope", "otherTypeDescription");
    public static final Map<String, PropertySpecificationFields.Field<?>> ALL;
    static {
        Map<String, PropertySpecificationFields.Field<?>> fields = new LinkedHashMap<>();
        PropertySpecificationFields.ALL.stream().filter(field -> !EXCLUDED.contains(field.name()))
                .forEach(field -> fields.put(field.name(), field));
        ALL = Collections.unmodifiableMap(fields);
    }

    public static Map<String, String> errors(PropertyNeedsRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        var allowed = PropertySpecificationsRules.allowedFields(request.getType(), request.getCommercialScope());
        if (request.getCommercialScope() != null && request.getType() != PropertyType.COMMERCIAL)
            errors.put("commercialScope", "نطاق التجاري للتجاري فقط");
        if (request.getAreaBasis() != PropertyNeedsAreaBasis.ADVERTISED) {
            if (!allowed.contains(request.getAreaBasis().field())) errors.put("areaBasis", "معنى المساحة لا ينطبق على النوع أو نطاق التجاري؛ حدد النطاق عند الحاجة");
            if (request.getMinArea() == null && request.getMaxArea() == null) errors.put("areaBasis", "حدد مدى مساحة قبل اختيار معنى مسمى");
        }
        request.getSpecificationCriteria().values().forEach((name, criterion) -> {
            String path = "specificationCriteria." + name;
            var field = ALL.get(name);
            if (field == null || !allowed.contains(name)) { errors.put(path, "هذا المعيار لا ينطبق على النوع أو النطاق"); return; }
            if (criterion == null || criterion.requirement() == null) { errors.put(path, "حدد أهمية المعيار صراحة"); return; }
            if (field.type() == Integer.class) {
                if (criterion.value() != null || (criterion.min() == null && criterion.max() == null)) errors.put(path, "حدد حدًا صحيحًا واحدًا على الأقل للعداد");
                if ((criterion.min() != null && (criterion.min() < field.min() || criterion.min() > field.max()))
                        || (criterion.max() != null && (criterion.max() < field.min() || criterion.max() > field.max()))) errors.put(path, "حدود العداد خارج المجال المعتمد");
                if (criterion.min() != null && criterion.max() != null && criterion.min() > criterion.max()) errors.put(path, "الحد الأدنى لا يتجاوز الأعلى");
            } else if (criterion.min() != null || criterion.max() != null || !field.type().isInstance(criterion.value())) errors.put(path, "حدد قيمة صريحة من النوع الصحيح");
        });
        return errors;
    }

    public static PropertyCriterionMatch match(Property property, String name, PropertySpecificationCriterion criterion) {
        Object actual = actual(property, name);
        if (actual == null) return PropertyCriterionMatch.UNKNOWN;
        boolean matched = actual instanceof Integer number
                ? (criterion.min() == null || number >= criterion.min()) && (criterion.max() == null || number <= criterion.max())
                : actual.equals(criterion.value());
        return matched ? PropertyCriterionMatch.MATCHED : PropertyCriterionMatch.MISMATCHED;
    }

    public static Object actual(Property property, String name) {
        if (property.getSpecifications() == null) return null;
        // A non-applicable fact must not become evidence even in a malformed legacy row.
        if (!PropertySpecificationsRules.allowedFields(property.getType(), property.getSpecifications().getCommercialScope()).contains(name)) return null;
        return ALL.get(name).read().apply(property.getSpecifications());
    }
}
