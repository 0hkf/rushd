package com.rushd.favorites;

import com.rushd.entity.Property;
import com.rushd.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;

@Entity
@Table(name = "property_favorites", indexes = @Index(name = "ix_favorites_page", columnList = "user_id,created_at DESC,property_id DESC"))
@IdClass(FavoriteId.class)
public class Favorite {
    @Id @Column(name = "user_id", nullable = false) Long userId;
    @Id @Column(name = "property_id", nullable = false) Long propertyId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_favorites_user"))
    @OnDelete(action = OnDeleteAction.CASCADE) User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_favorites_property"))
    @OnDelete(action = OnDeleteAction.CASCADE) Property property;
    protected Favorite() {}
    public Favorite(Long userId, Long propertyId, Instant createdAt) {
        this.userId = userId; this.propertyId = propertyId; this.createdAt = createdAt;
    }
}
