package com.rushd.images;
import java.util.Collection;
import java.util.Map;
/** Page-scoped, optional enrichment; independent of upload/mutation operations. */
public interface PrimaryImageLookup {
    Map<Long, ImageDescriptor> primaries(Collection<Long> ids);
}
