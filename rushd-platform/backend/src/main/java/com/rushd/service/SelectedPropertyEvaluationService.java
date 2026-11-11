package com.rushd.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.dto.*;
import com.rushd.dto.SelectedPropertyEvaluation.*;
import com.rushd.entity.*;
import com.rushd.exception.PropertyNotFoundException;
import com.rushd.repository.PropertyRepository;
import com.rushd.validation.PropertyMatchingFields;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class SelectedPropertyEvaluationService {
    private static final PropertyNeedsRequirement REQUIRED = PropertyNeedsRequirement.REQUIRED;
    private final PropertyRepository properties;
    private final ObjectMapper mapper;

    public SelectedPropertyEvaluationService(PropertyRepository properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public SelectedPropertyEvaluation evaluate(Long id, PropertyNeedsRequest request) {
        Property property = properties.findById(id).filter(p -> p.getStatus() == PropertyStatus.ACTIVE)
                .orElseThrow(() -> new PropertyNotFoundException(id));
        PropertyNeedsResponse needs = PropertyNeedsResponse.from(request);
        List<Evidence> evidence = new ArrayList<>();

        Outcome type = equality(request.getType(), property.getType());
        Outcome listing = equality(request.getListingType(), property.getListingType());
        add(evidence, "type", REQUIRED, request.getType(), property.getType(), null, type);
        add(evidence, "listingType", REQUIRED, request.getListingType(), property.getListingType(), null, listing);
        Outcome period = Outcome.MATCHED;
        if (request.getListingType() == ListingType.RENT) {
            period = listing == Outcome.MATCHED ? equality(request.getRentalPeriod(), property.getRentalPeriod()) : Outcome.NOT_COMPARABLE;
            add(evidence, "rentalPeriod", REQUIRED, request.getRentalPeriod(), property.getRentalPeriod(), null, period);
        }
        String city = property.getCity();
        add(evidence, "city", REQUIRED, needs.city(), city, null,
                city == null || city.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
                        ? Outcome.UNKNOWN : PropertyLocationComparison.isMakkah(city) ? Outcome.MATCHED : Outcome.MISMATCHED);
        BigDecimal price = property.getPrice();
        Outcome budget = listing != Outcome.MATCHED || period != Outcome.MATCHED ? Outcome.NOT_COMPARABLE
                : price == null || price.signum() <= 0 ? Outcome.UNKNOWN
                : price.compareTo(request.getMaxBudget()) <= 0 ? Outcome.MATCHED : Outcome.MISMATCHED;
        add(evidence, "maxBudget", REQUIRED, needs.maxBudget(), decimal(price), "SAR", budget);

        Outcome scope = Outcome.MATCHED;
        if (needs.commercialScope() != null) {
            CommercialScope actualScope = property.getSpecifications() == null ? null : property.getSpecifications().getCommercialScope();
            scope = type == Outcome.MATCHED ? equality(needs.commercialScope(), actualScope) : Outcome.NOT_COMPARABLE;
            add(evidence, "commercialScope", REQUIRED, needs.commercialScope(), actualScope, null, scope);
        }
        if (needs.districtRequirement() != null) {
            add(evidence, "district", needs.districtRequirement(), needs.district(), property.getDistrict(), null,
                    convert(PropertyMatchingService.districtMatch(property, needs)));
        }
        boolean comparableStructure = type == Outcome.MATCHED && scope == Outcome.MATCHED;
        if (needs.areaRequirement() != null) {
            BigDecimal area = request.getAreaBasis().read(property);
            Outcome result = comparableStructure ? convert(PropertyMatchingService.areaMatch(property, request)) : Outcome.NOT_COMPARABLE;
            add(evidence, "area", needs.areaRequirement(), new AreaRequest(needs.minArea(), needs.maxArea(), needs.areaBasis()),
                    comparableStructure ? decimal(area) : null, "SQUARE_METRE", result);
        }
        request.getSpecificationCriteria().values().forEach((name, criterion) -> {
            Object actual = comparableStructure ? PropertyMatchingFields.actual(property, name) : null;
            Outcome result = comparableStructure ? convert(PropertyMatchingFields.match(property, name, criterion)) : Outcome.NOT_COMPARABLE;
            add(evidence, "specification." + name, criterion.requirement(), criterion, actual, null, result);
        });

        long matched = count(evidence, Outcome.MATCHED), mismatched = count(evidence, Outcome.MISMATCHED);
        long unknown = count(evidence, Outcome.UNKNOWN), notComparable = count(evidence, Outcome.NOT_COMPARABLE);
        int preferred = (int) evidence.stream().filter(e -> e.requirement() == PropertyNeedsRequirement.PREFERRED).count();
        int preferredMatched = (int) evidence.stream().filter(e -> e.requirement() == PropertyNeedsRequirement.PREFERRED && e.outcome() == Outcome.MATCHED).count();
        boolean requiredMismatch = evidence.stream().anyMatch(e -> e.requirement() == REQUIRED && e.outcome() == Outcome.MISMATCHED);
        Classification classification = requiredMismatch ? Classification.NOT_SUITABLE
                : unknown + notComparable > 0 ? Classification.INSUFFICIENT_DATA
                : preferred == 0 ? Classification.BASIC_MATCH
                : preferredMatched == preferred ? Classification.VERY_SUITABLE
                : preferredMatched * 2 >= preferred ? Classification.SUITABLE : Classification.PARTIALLY_SUITABLE;
        List<Evidence> snapshot = List.copyOf(evidence);
        return new SelectedPropertyEvaluation(id.toString(), Instant.now(), "selected-property-evaluation-v1", needs,
                fingerprint(id, snapshot), classification, new Coverage(matched, mismatched, unknown, notComparable),
                preferred, preferredMatched, snapshot);
    }

    private static void add(List<Evidence> list, String id, PropertyNeedsRequirement requirement, Object requested,
                            Object actual, String unit, Outcome outcome) {
        list.add(new Evidence(id, requirement, requested, actual, unit, outcome, outcome.name()));
    }
    private static Outcome equality(Object expected, Object actual) {
        return actual == null ? Outcome.UNKNOWN : expected.equals(actual) ? Outcome.MATCHED : Outcome.MISMATCHED;
    }
    private static Outcome convert(PropertyCriterionMatch result) { return Outcome.valueOf(result.name()); }
    private static long count(List<Evidence> evidence, Outcome outcome) { return evidence.stream().filter(e -> e.outcome() == outcome).count(); }
    private static String decimal(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private String fingerprint(Long id, List<Evidence> snapshot) {
        try {
            // Hash only the exposed fact identities and values, not user needs or account data.
            var facts = snapshot.stream().map(e -> new Fact(e.id(), e.actual())).toList();
            byte[] bytes = mapper.writeValueAsString(new FactSnapshot(id, facts)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to fingerprint public evaluation facts", e);
        }
    }
    private record AreaRequest(String min, String max, PropertyNeedsAreaBasis basis) { }
    private record Fact(String id, Object actual) { }
    private record FactSnapshot(Long propertyId, List<Fact> facts) { }
}
