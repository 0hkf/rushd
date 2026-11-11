package com.rushd.service;

import com.rushd.dto.PropertyCriterionMatch;
import com.rushd.dto.PropertyMatchResponse;
import com.rushd.dto.PropertyMatchesResponse;
import com.rushd.dto.PropertyNeedsRequest;
import com.rushd.dto.PropertyNeedsRequirement;
import com.rushd.dto.PropertyNeedsResponse;
import com.rushd.dto.PropertySpecificationMatch;
import com.rushd.validation.PropertyMatchingFields;
import com.rushd.entity.Property;
import com.rushd.exception.PropertyMatchingTooBroadException;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.specification.PropertyMatchingSpecification;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PropertyMatchingService {
    public static final int CORE_CANDIDATE_LIMIT = 1000;
    private static final Comparator<PropertyMatchResponse> ORDER = Comparator
            .comparingInt(PropertyMatchResponse::preferredMatchedCount).reversed()
            .thenComparing(PropertyMatchResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(PropertyMatchResponse::id, Comparator.nullsLast(Comparator.reverseOrder()));

    private final PropertyRepository properties;
    private final com.rushd.images.PrimaryImageLookup propertyImages;

    public PropertyMatchingService(PropertyRepository properties, com.rushd.images.PrimaryImageLookup propertyImages) {
        this.properties = properties;
        this.propertyImages = propertyImages;
    }

    @Transactional(readOnly = true)
    public PropertyMatchesResponse matches(PropertyNeedsRequest request, int page, int size) {
        PropertyNeedsResponse needs = PropertyNeedsResponse.from(request);
        // One bounded page only: never rank an arbitrary sample or load an unbounded catalogue.
        var candidates = properties.findAll(PropertyMatchingSpecification.coreCandidates(request),
                PageRequest.of(0, CORE_CANDIDATE_LIMIT + 1));
        if (candidates.getTotalElements() > CORE_CANDIDATE_LIMIT
                || candidates.getContent().size() > CORE_CANDIDATE_LIMIT) {
            throw new PropertyMatchingTooBroadException();
        }

        List<PropertyMatchResponse> matches = new ArrayList<>();
        for (Property candidate : candidates.getContent()) {
            if (!PropertyLocationComparison.isMakkah(candidate.getCity())) {
                continue;
            }
            if (needs.commercialScope() != null && (candidate.getSpecifications() == null
                    || candidate.getSpecifications().getCommercialScope() != needs.commercialScope())) continue;
            PropertyCriterionMatch district = districtMatch(candidate, needs);
            PropertyCriterionMatch area = areaMatch(candidate, request);
            if ((needs.districtRequirement() == PropertyNeedsRequirement.REQUIRED && district != PropertyCriterionMatch.MATCHED)
                    || (needs.areaRequirement() == PropertyNeedsRequirement.REQUIRED && area != PropertyCriterionMatch.MATCHED)) {
                continue;
            }

            int preferredCount = 0;
            int preferredMatchedCount = 0;
            List<PropertySpecificationMatch> evidence = new ArrayList<>();
            boolean rejected = false;
            for (var entry : request.getSpecificationCriteria().values().entrySet()) {
                var criterion = entry.getValue();
                var outcome = PropertyMatchingFields.match(candidate, entry.getKey(), criterion);
                if (criterion.requirement() == PropertyNeedsRequirement.REQUIRED && outcome != PropertyCriterionMatch.MATCHED) rejected = true;
                if (criterion.requirement() == PropertyNeedsRequirement.PREFERRED) {
                    preferredCount++;
                    if (outcome == PropertyCriterionMatch.MATCHED) preferredMatchedCount++;
                }
                evidence.add(new PropertySpecificationMatch(entry.getKey(), criterion,
                        PropertyMatchingFields.actual(candidate, entry.getKey()), outcome));
            }
            if (rejected) continue;
            if (needs.districtRequirement() == PropertyNeedsRequirement.PREFERRED) {
                preferredCount++;
                preferredMatchedCount += district == PropertyCriterionMatch.MATCHED ? 1 : 0;
            }
            if (needs.areaRequirement() == PropertyNeedsRequirement.PREFERRED) {
                preferredCount++;
                preferredMatchedCount += area == PropertyCriterionMatch.MATCHED ? 1 : 0;
            }
            matches.add(new PropertyMatchResponse(
                    candidate.getId(), candidate.getTitle(), candidate.getType(), candidate.getListingType(),
                    candidate.getRentalPeriod(), candidate.getCity(), candidate.getDistrict(),
                    decimal(candidate.getPrice()), positiveDecimal(candidate.getArea()), candidate.getCreatedAt(),
                    preferredMatchedCount, preferredCount, district, area, reasons(district, area, needs),
                    needs.areaBasis(), positiveDecimal(request.getAreaBasis().read(candidate)), List.copyOf(evidence), null));
        }
        matches.sort(ORDER);

        long offset = (long) page * size;
        List<PropertyMatchResponse> content = offset >= matches.size() ? List.of()
                : List.copyOf(matches.subList((int) offset, Math.min(matches.size(), (int) offset + size)));
        var primary = propertyImages.primaries(content.stream().map(PropertyMatchResponse::id).toList());
        content = content.stream().map(item -> item.withPrimaryImage(primary.get(item.id()))).toList();
        return new PropertyMatchesResponse(needs, content, page, size, matches.size(),
                (matches.size() + size - 1) / size, request.usesAdvancedMatching() ? "matching-v2" : "matching-v1", "PREFERENCES_THEN_NEWEST");
    }

    static PropertyCriterionMatch districtMatch(Property property, PropertyNeedsResponse needs) {
        if (needs.district() == null) {
            return PropertyCriterionMatch.NOT_REQUESTED;
        }
        String desired = PropertyLocationComparison.districtKey(needs.district());
        String actual = PropertyLocationComparison.districtKey(property.getDistrict());
        if (desired == null || actual == null) {
            return PropertyCriterionMatch.UNKNOWN;
        }
        return desired.equals(actual) ? PropertyCriterionMatch.MATCHED : PropertyCriterionMatch.MISMATCHED;
    }

    static PropertyCriterionMatch areaMatch(Property property, PropertyNeedsRequest needs) {
        if (needs.getMinArea() == null && needs.getMaxArea() == null) {
            return PropertyCriterionMatch.NOT_REQUESTED;
        }
        BigDecimal actual = needs.getAreaBasis().read(property);
        if (actual == null || actual.signum() <= 0) {
            return PropertyCriterionMatch.UNKNOWN;
        }
        if ((needs.getMinArea() != null && actual.compareTo(needs.getMinArea()) < 0)
                || (needs.getMaxArea() != null && actual.compareTo(needs.getMaxArea()) > 0)) {
            return PropertyCriterionMatch.MISMATCHED;
        }
        return PropertyCriterionMatch.MATCHED;
    }

    private List<String> reasons(PropertyCriterionMatch district, PropertyCriterionMatch area, PropertyNeedsResponse needs) {
        List<String> reasons = new ArrayList<>();
        reasons.add("نوع العقار والعرض وفترة الإيجار عند انطباقها تطابق طلبك.");
        reasons.add("المدينة المسجلة تقع ضمن أسماء مكة المعتمدة للمقارنة.");
        reasons.add("السعر المعلن ضمن الحد الأعلى للميزانية، وليس تقديراً لإجمالي التكاليف أو قيمة السوق.");
        addCriterionReason(reasons, "الحي", district, needs.districtRequirement());
        String areaLabel = switch (needs.areaBasis()) {
            case ADVERTISED -> "معيار المساحة المسجلة في الإعلان";
            case LAND -> "مساحة قطعة الأرض";
            case UNIT_NET -> "صافي مساحة الوحدة";
            case UNIT_GROSS -> "إجمالي مساحة الوحدة";
            case TOTAL_BUILT -> "إجمالي مساحة البناء";
        };
        addCriterionReason(reasons, areaLabel, area, needs.areaRequirement());
        if (!needs.specificationCriteria().values().isEmpty()) reasons.add("نتيجة كل مواصفة طلبتها موضحة بدليلها؛ المجهول لا يحتسب تفضيلًا متحققًا.");
        if (needs.searchGoal() != null) {
            reasons.add("هدف البحث للمعلومة فقط؛ لم يُستخدم لاستنتاج الاستخدام المسموح أو جدوى الاستثمار.");
        }
        return List.copyOf(reasons);
    }

    private void addCriterionReason(List<String> reasons, String label, PropertyCriterionMatch outcome,
                                    PropertyNeedsRequirement requirement) {
        switch (outcome) {
            case MATCHED -> reasons.add(label + (requirement == PropertyNeedsRequirement.REQUIRED
                    ? " يحقق شرطك الإلزامي." : " يحقق تفضيلك."));
            case MISMATCHED -> reasons.add(label + " لا يحقق تفضيلك؛ بقي العرض لأن هذا التفضيل ليس إلزامياً.");
            case UNKNOWN -> reasons.add(label + " غير معروف أو غير واضح؛ لم يُحسب تفضيلاً متحققاً.");
            case NOT_REQUESTED -> { }
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String positiveDecimal(BigDecimal value) {
        return value == null || value.signum() <= 0 ? null : value.toPlainString();
    }
}
