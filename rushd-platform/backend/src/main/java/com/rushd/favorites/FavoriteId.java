package com.rushd.favorites;

import java.io.Serializable;
import java.util.Objects;

public class FavoriteId implements Serializable {
    public Long userId;
    public Long propertyId;
    public FavoriteId() {}
    public FavoriteId(Long userId, Long propertyId) { this.userId = userId; this.propertyId = propertyId; }
    @Override public boolean equals(Object other) {
        return other instanceof FavoriteId id && Objects.equals(userId, id.userId) && Objects.equals(propertyId, id.propertyId);
    }
    @Override public int hashCode() { return Objects.hash(userId, propertyId); }
}
