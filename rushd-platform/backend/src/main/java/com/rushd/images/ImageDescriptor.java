package com.rushd.images;

import java.util.UUID;

public record ImageDescriptor(UUID id, int order, String altText, Variant thumbnail, Variant display) {
    public record Variant(String url, int width, int height) {}
    static ImageDescriptor from(PropertyImage image) {
        String base = "/api/properties/" + image.propertyId + "/images/" + image.id + "/";
        return new ImageDescriptor(image.id, image.sortOrder, image.altText,
                new Variant(base + "thumbnail", image.thumbnailWidth, image.thumbnailHeight),
                new Variant(base + "display", image.displayWidth, image.displayHeight));
    }
}
