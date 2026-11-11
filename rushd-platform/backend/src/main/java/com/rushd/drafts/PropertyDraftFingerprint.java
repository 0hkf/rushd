package com.rushd.drafts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rushd.entity.Property;
import com.rushd.mapper.PropertyMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;

/** Fingerprint of editable property facts; compare under the same row lock as a future commit. */
final class PropertyDraftFingerprint {
    private final ObjectMapper mapper;
    PropertyDraftFingerprint(ObjectMapper mapper) { this.mapper = mapper; }

    String of(Property property) {
        ObjectNode values = mapper.valueToTree(PropertyMapper.toResponse(property));
        values.remove(java.util.List.of("id", "publisher", "publisherId", "publisherName",
                "createdAt", "updatedAt", "primaryImage"));
        try {
            byte[] content = mapper.writeValueAsBytes(sort(values));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("rushd-property-edit-v1\n".getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to fingerprint property", ex);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            var names = new ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            names.forEach(name -> sorted.set(name, sort(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = mapper.createArrayNode();
            node.forEach(item -> sorted.add(sort(item)));
            return sorted;
        }
        return node;
    }
}
