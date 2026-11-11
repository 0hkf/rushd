package com.rushd.drafts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/** Explicit source selection, with coupled values kept in the same group. No numeric coercion. */
final class DraftMerge {
    record Group(String id, List<String> paths, boolean choiceRequired) { }
    private final ObjectMapper mapper;
    DraftMerge(ObjectMapper mapper) { this.mapper = mapper; }

    List<Group> groups(Map<String, JsonNode> sources) {
        Map<String, List<String>> paths = new TreeMap<>();
        boolean differentType = sources.values().stream().map(n -> n.path("values").path("type")).distinct().count() > 1
                || sources.values().stream().map(n -> n.path("specifications").path("commercialScope")).distinct().count() > 1;
        for (String section : List.of("values", "specifications")) {
            Set<String> fields = new TreeSet<>();
            sources.values().forEach(n -> n.path(section).fieldNames().forEachRemaining(fields::add));
            String errorSection = section.equals("values") ? "sourceErrors" : "specificationSourceErrors";
            sources.values().forEach(n -> n.path(errorSection).fieldNames().forEachRemaining(fields::add));
            for (String field : fields) {
                String path = section + "." + field;
                String group = path;
                if (Set.of("values.price", "values.listingType", "values.rentalPeriod", "values.status").contains(path)) group = "offer";
                if (Set.of("values.city", "values.district", "values.formattedAddress", "values.neighborhood", "values.googlePlaceId", "values.latitude", "values.longitude").contains(path)) group = "location";
                if (Set.of("values.area", "specifications.primaryAreaSource", "specifications.landArea", "specifications.unitNetArea", "specifications.unitGrossArea", "specifications.totalBuiltArea").contains(path)) group = "areas";
                if (Set.of("specifications.estimatedAgeYears", "specifications.ageAsOfYear").contains(path)) group = "age";
                if (differentType && (section.equals("specifications") || path.equals("values.area") || path.equals("values.type"))) group = "type-context";
                List<String> members = paths.computeIfAbsent(group, ignored -> new ArrayList<>());
                members.add(path);
                members.add((section.equals("values") ? "sourceErrors." : "specificationSourceErrors.") + field);
            }
        }
        paths.computeIfAbsent(differentType ? "type-context" : "cached", ignored -> new ArrayList<>()).addAll(List.of("typeDrafts", "scopes"));
        return paths.entrySet().stream().map(e -> new Group(e.getKey(), List.copyOf(e.getValue()),
                e.getValue().stream().anyMatch(path -> sources.values().stream().map(n -> at(n, path)).distinct().count() > 1))).toList();
    }

    ObjectNode merge(Map<String, JsonNode> sources, JsonNode choices) {
        if (choices == null || !choices.isObject()) throw invalid();
        List<Group> groups = groups(sources);
        Set<String> required = new HashSet<>();
        groups.stream().filter(Group::choiceRequired).forEach(g -> required.add(g.id()));
        Set<String> supplied = new HashSet<>(); choices.fieldNames().forEachRemaining(supplied::add);
        if (!required.equals(supplied)) throw invalid();
        ObjectNode result = mapper.createObjectNode();
        result.putObject("values"); result.putObject("specifications");
        for (Group group : groups) {
            if (group.choiceRequired() && !choices.path(group.id()).isTextual()) throw invalid();
            String source = group.choiceRequired() && choices.path(group.id()).isTextual() ? choices.path(group.id()).textValue() : "SERVER";
            if (group.choiceRequired() && !sources.containsKey(source)) throw invalid();
            JsonNode selected = sources.get(source);
            for (String path : group.paths()) {
                JsonNode value = at(selected, path);
                if (value.isMissingNode()) continue;
                String[] parts = path.split("\\.", 2);
                if (parts.length == 1) result.set(parts[0], value.deepCopy());
                else result.with(parts[0]).set(parts[1], value.deepCopy());
            }
        }
        new DraftPayloadValidator().validate(result.toString());
        return result;
    }

    private static JsonNode at(JsonNode node, String path) {
        for (String part : path.split("\\.")) node = node.path(part);
        return node;
    }
    private static DraftFailure invalid() { return new DraftFailure(400, "Select an explicit source for every differing group"); }
}
