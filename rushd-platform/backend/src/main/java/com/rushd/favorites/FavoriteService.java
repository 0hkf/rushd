package com.rushd.favorites;

import com.rushd.dto.PageResponse;
import com.rushd.entity.*;
import com.rushd.images.PrimaryImageLookup;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

@Service
public class FavoriteService {
    private final Favorites favorites;
    private final UserRepository users;
    private final PropertyRepository properties;
    private final PrimaryImageLookup images;
    private final boolean enabled;
    public FavoriteService(Favorites favorites, UserRepository users, PropertyRepository properties,
                           PrimaryImageLookup images, @Value("${rushd.favorites.enabled:false}") boolean enabled) {
        this.favorites = favorites; this.users = users; this.properties = properties; this.images = images; this.enabled = enabled;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<FavoriteResponse> list(String email, int page, int size) {
        requireEnabled();
        if (page < 0 || size < 1 || size > 100) throw new FavoriteFailure(400, "page must be non-negative and size between 1 and 100");
        Long userId = user(email, false).getId();
        var result = favorites.pageFor(userId, PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("propertyId"))));
        var visible = result.getContent().stream().filter(row -> visible(row.property)).map(row -> row.propertyId).toList();
        var primary = visible.isEmpty() ? Map.<Long,com.rushd.images.ImageDescriptor>of() : images.primaries(visible);
        return PageResponse.from(result.map(row -> {
            boolean available = visible(row.property);
            return new FavoriteResponse(row.propertyId.toString(), row.createdAt, available,
                    available ? FavoriteResponse.Summary.from(row.property, primary.get(row.propertyId)) : null);
        }));
    }
    @Transactional(readOnly = true)
    public List<String> status(String email, List<String> rawIds) {
        requireEnabled();
        if (rawIds == null || rawIds.isEmpty() || rawIds.size() > 100) throw new FavoriteFailure(400, "Provide between 1 and 100 propertyIds");
        Set<Long> ids = new LinkedHashSet<>();
        rawIds.forEach(value -> ids.add(id(value)));
        return favorites.savedIds(user(email, false).getId(), ids).stream().map(Object::toString).toList();
    }
    @Transactional
    public void add(String email, String rawId) {
        requireEnabled(); Long propertyId = id(rawId);
        // Same-user writes serialize before the existence check: duplicate PUTs remain idempotent.
        Long userId = user(email, true).getId();
        Property property = properties.findByIdForUpdate(propertyId).orElseThrow(() -> new FavoriteFailure(404, "Property is not available"));
        if (!visible(property)) throw new FavoriteFailure(404, "Property is not available");
        if (!favorites.existsById(new FavoriteId(userId, propertyId))) favorites.saveAndFlush(new Favorite(userId, propertyId, Instant.now()));
    }
    @Transactional
    public void remove(String email, String rawId) {
        requireEnabled(); Long propertyId = id(rawId);
        favorites.removeFor(user(email, true).getId(), propertyId);
    }
    private User user(String email, boolean lock) {
        if (email == null) throw new UsernameNotFoundException("Authentication is required");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return (lock ? users.findByEmailForUpdate(normalized) : users.findByEmail(normalized))
                .orElseThrow(() -> new UsernameNotFoundException("Authentication is required"));
    }
    private void requireEnabled() { if (!enabled) throw new FavoriteFailure(503, "Favorites are not enabled"); }
    static boolean visible(Property property) { return property.getStatus() == PropertyStatus.ACTIVE || property.getStatus() == PropertyStatus.SOLD; }
    static Long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new FavoriteFailure(400, "Invalid property identifier");
        try { return Long.parseLong(value); } catch (NumberFormatException failure) { throw new FavoriteFailure(400, "Invalid property identifier"); }
    }
}
