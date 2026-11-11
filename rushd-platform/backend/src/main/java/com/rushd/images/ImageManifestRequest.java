package com.rushd.images;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record ImageManifestRequest(
        @JsonProperty(required = true) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = RevisionStringDeserializer.class) String expectedRevision,
        @JsonProperty(required = true) UUID primaryImageId,
        @JsonProperty(required = true) List<Item> images) {
    // Missing altText is rejected, while an explicitly supplied null clears it.
    public record Item(@JsonProperty(required = true) UUID id, @JsonProperty(required = true) String altText) {}
}
