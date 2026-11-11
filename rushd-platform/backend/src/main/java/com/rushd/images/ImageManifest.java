package com.rushd.images;
import java.util.List;
import java.util.UUID;

public record ImageManifest(String revision, UUID primaryImageId, List<ImageDescriptor> images) {
    public ImageManifest { images = List.copyOf(images); }
}
