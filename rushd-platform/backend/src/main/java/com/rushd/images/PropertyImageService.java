package com.rushd.images;

import com.rushd.entity.*;
import com.rushd.exception.PropertyNotFoundException;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class PropertyImageService implements PrimaryImageLookup {
    private final PropertyRepository properties;
    private final UserRepository users;
    private final PropertyImages images;
    private final ImageCollections collections;
    private final PrivateImageStorage storage;
    private final ImageProcessor processor;
    private final TransactionTemplate writes;
    public PropertyImageService(PropertyRepository properties, UserRepository users, PropertyImages images,
                                ImageCollections collections, PrivateImageStorage storage, ImageProcessor processor,
                                PlatformTransactionManager transactions) {
        this.properties = properties; this.users = users; this.images = images; this.collections = collections;
        this.storage = storage; this.processor = processor;
        writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record UploadResult(boolean created, ImageManifest manifest) {}
    public record Content(byte[] bytes) {}

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ImageManifest manifest(Long propertyId, String email) {
        visible(propertyId, email); storage.requireAvailable();
        return manifestInternal(propertyId);
    }
    @Transactional(readOnly = true)
    public Content content(Long propertyId, UUID imageId, String variant, String email) {
        visible(propertyId, email); storage.requireAvailable();
        if (!Set.of("display", "thumbnail").contains(variant)) throw ImageFailure.missing();
        PropertyImage image = images.findByIdAndPropertyId(imageId, propertyId).orElseThrow(ImageFailure::missing);
        if (image.state != PropertyImage.State.READY) throw ImageFailure.missing();
        return new Content(storage.read(image.storageKey, variant,
                variant.equals("display") ? image.displayBytes : image.thumbnailBytes));
    }
    /** Bulk query is performed only for the final page, and is entirely skipped while disabled. */
    @Transactional(readOnly = true)
    public Map<Long, ImageDescriptor> primaries(Collection<Long> ids) {
        if (!storage.enabled() || ids.isEmpty()) return Map.of();
        try { storage.requireAvailable(); }
        catch (ImageFailure unavailable) { return Map.of(); }
        Map<Long, ImageDescriptor> result = new HashMap<>();
        for (var image : images.findPrimaries(ids, PropertyImage.State.READY)) result.put(image.propertyId, ImageDescriptor.from(image));
        return result;
    }
    public UploadResult upload(Long propertyId, UUID token, byte[] source, String filename, String mime,
                               boolean confirmed, PropertyImage.RightsBasis basis, String reference, String altText, String email) {
        storage.requireAvailable();
        storage.requireUploadReservation();
        if (token == null || !confirmed || basis == null) throw ImageFailure.invalid("Upload token and rights attestation are required");
        text(altText, 160, "altText"); text(reference, 500, "sourceReference");
        if (basis == PropertyImage.RightsBasis.LICENSED && (reference == null || reference.isBlank()))
            throw ImageFailure.invalid("Licensed images require a source reference");
        String fingerprint = fingerprint(source, basis.name(), reference, altText);
        var processed = processor.process(source, filename, mime);
        UUID key = UUID.randomUUID();
        boolean[] filesWritten = {false};
        try {
            return writes.execute(status -> {
                var admin = admin(email);
                lock(propertyId);
                var existing = images.findByPropertyIdAndUploadToken(propertyId, token);
                if (existing.isPresent()) {
                    PropertyImage image = existing.get();
                    if (image.state != PropertyImage.State.READY || !image.fingerprint.equals(fingerprint))
                        throw new ImageFailure(409, "Upload token was already used with another payload or deleted image");
                    return new UploadResult(false, manifestInternal(propertyId));
                }
                // DELETING still occupies quota until both files are gone.
                if (images.countByPropertyIdAndStateNot(propertyId, PropertyImage.State.DELETED) >= 12)
                    throw new ImageFailure(409, "Property already has 12 images or pending deletions");
                var collection = collections.findById(propertyId).orElseGet(() -> collections.saveAndFlush(new PropertyImageCollection(propertyId)));
                storage.write(key, processed); filesWritten[0] = true;
                var image = new PropertyImage();
                image.id = UUID.randomUUID(); image.propertyId = propertyId; image.storageKey = key; image.uploadToken = token;
                image.fingerprint = fingerprint; image.state = PropertyImage.State.READY;
                image.sortOrder = ready(propertyId).size(); image.altText = altText;
                image.displayWidth = processed.displayWidth(); image.displayHeight = processed.displayHeight();
                image.thumbnailWidth = processed.thumbnailWidth(); image.thumbnailHeight = processed.thumbnailHeight();
                image.displayBytes = processed.display().length; image.thumbnailBytes = processed.thumbnail().length;
                image.attestedBy = admin.getId(); image.rightsBasis = basis; image.sourceReference = reference; image.createdAt = Instant.now();
                images.saveAndFlush(image);
                if (collection.primaryImageId == null) collection.primaryImageId = image.id;
                increment(collection); collections.saveAndFlush(collection);
                return new UploadResult(true, manifestInternal(propertyId));
            });
        } catch (RuntimeException e) {
            // Includes a failure during transaction COMMIT, not merely during repository.save.
            if (filesWritten[0]) {
                // An ambiguous commit outcome must not unlink a possibly committed image.
                try { if (images.findByStorageKey(key).isEmpty()) storage.remove(key); }
                catch (RuntimeException ignored) { /* retain as private orphan for a later, DB-aware dry-run */ }
            }
            throw e;
        }
    }
    public ImageManifest update(Long propertyId, ImageManifestRequest request, String email) {
        storage.requireAvailable();
        return writes.execute(status -> {
            admin(email); lock(propertyId);
            if (request == null || request.images() == null || request.images().size() > 12)
                throw ImageFailure.invalid("A complete image manifest is required");
            var collection = collections.findById(propertyId).orElseGet(() -> new PropertyImageCollection(propertyId));
            expected(request.expectedRevision(), collection.revision);
            List<PropertyImage> current = ready(propertyId);
            Map<UUID, PropertyImage> byId = new HashMap<>(); current.forEach(image -> byId.put(image.id, image));
            Set<UUID> supplied = new HashSet<>();
            List<PropertyImage> ordered = new ArrayList<>();
            for (var item : request.images()) {
                if (item == null || item.id() == null || !supplied.add(item.id()) || !byId.containsKey(item.id()))
                    throw ImageFailure.invalid("Manifest contains a duplicate, unknown or foreign image");
                text(item.altText(), 160, "altText");
                PropertyImage image = byId.get(item.id()); image.altText = item.altText(); ordered.add(image);
            }
            if (ordered.size() != current.size()) throw ImageFailure.invalid("Manifest must include every current image");
            if (current.isEmpty() ? request.primaryImageId() != null : !supplied.contains(request.primaryImageId()))
                throw ImageFailure.invalid("Select a primary image from this property");
            reorder(ordered); collection.primaryImageId = request.primaryImageId(); increment(collection);
            collections.saveAndFlush(collection); return manifestInternal(propertyId);
        });
    }
    /** Returns true when cleanup is complete. Revocation transaction ALWAYS commits before unlink. */
    public boolean delete(Long propertyId, UUID imageId, String revision, UUID replacement, String email) {
        storage.requireAvailable();
        PropertyImage image = writes.execute(status -> {
            admin(email); lock(propertyId);
            PropertyImage target = images.findByIdAndPropertyId(imageId, propertyId).orElseThrow(ImageFailure::missing);
            if (target.state != PropertyImage.State.READY) return target;
            var collection = collections.findById(propertyId).orElseThrow(ImageFailure::missing);
            expected(revision, collection.revision);
            List<PropertyImage> remaining = new ArrayList<>(ready(propertyId)); remaining.removeIf(i -> i.id.equals(imageId));
            boolean primary = imageId.equals(collection.primaryImageId);
            if (primary && !remaining.isEmpty()) {
                if (replacement == null || remaining.stream().noneMatch(i -> i.id.equals(replacement)))
                    throw ImageFailure.invalid("Deleting the primary image requires an explicit replacement from this property");
                collection.primaryImageId = replacement;
            } else if (replacement != null) throw ImageFailure.invalid("Replacement is only allowed when deleting a primary with remaining images");
            else if (primary) collection.primaryImageId = null;
            // Change primary before revocation; the DB FK is association-only, visibility uses READY.
            target.state = PropertyImage.State.DELETING; target.sortOrder = null;
            images.flush(); reorder(remaining); increment(collection); collections.saveAndFlush(collection);
            return target;
        });
        if (image.state == PropertyImage.State.DELETED) return true;
        if (!storage.remove(image.storageKey)) return false;
        writes.executeWithoutResult(status -> {
            lock(propertyId);
            var target = images.findByIdAndPropertyId(imageId, propertyId).orElseThrow(ImageFailure::missing);
            if (target.state == PropertyImage.State.DELETING) {
                target.state = PropertyImage.State.DELETED; target.deletedAt = Instant.now(); images.saveAndFlush(target);
            }
        });
        return true;
    }
    public record MaintenanceReport(List<PrivateImageStorage.MaintenanceEntry> files, List<UUID> expiredTokens) {}
    public MaintenanceReport maintenance(boolean apply) {
        storage.requireEnabled();
        return storage.exclusive(() -> maintenanceExclusive(apply));
    }
    private MaintenanceReport maintenanceExclusive(boolean apply) {
        var files = storage.maintenance(apply, () -> new HashSet<>(images.liveStorageKeys(PropertyImage.State.DELETED)), Instant.now());
        List<UUID> expired = new ArrayList<>();
        for (var row : images.findByStateAndDeletedAtBefore(PropertyImage.State.DELETED, Instant.now().minusSeconds(30L*86400))) {
            if (!storage.absent(row.storageKey)) continue;
            if (apply) writes.executeWithoutResult(status -> {
                lock(row.propertyId);
                var fresh = images.findById(row.id).orElse(null);
                if (fresh != null && fresh.state == PropertyImage.State.DELETED && fresh.deletedAt != null
                        && fresh.deletedAt.isBefore(Instant.now().minusSeconds(30L*86400)) && storage.absent(fresh.storageKey)) images.delete(fresh);
            });
            expired.add(row.id);
        }
        return new MaintenanceReport(files,List.copyOf(expired));
    }
    private void reorder(List<PropertyImage> ordered) {
        // Two flushes avoid transient collisions under the non-deferrable unique constraint (PG and H2).
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).sortOrder = 1000 + i;
        images.flush();
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).sortOrder = i;
        images.flush();
    }
    private List<PropertyImage> ready(Long id) { return images.findByPropertyIdAndStateOrderBySortOrder(id, PropertyImage.State.READY); }
    private ImageManifest manifestInternal(Long id) {
        var collection = collections.findById(id);
        return new ImageManifest(collection.map(c -> Long.toString(c.revision)).orElse("0"),
                collection.map(c -> c.primaryImageId).orElse(null), ready(id).stream().map(ImageDescriptor::from).toList());
    }
    private void visible(Long id, String email) {
        Property property = properties.findById(id).orElseThrow(() -> new PropertyNotFoundException(id));
        if (property.getStatus() != PropertyStatus.ACTIVE && property.getStatus() != PropertyStatus.SOLD
                && (email == null || users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(u -> u.getRole() != Role.ADMIN).orElse(true)))
            throw new PropertyNotFoundException(id);
    }
    private User admin(String email) {
        if (email == null) throw new org.springframework.security.access.AccessDeniedException("Only admins can manage images");
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).filter(u -> u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Only admins can manage images"));
    }
    private void lock(Long id) { properties.findByIdForUpdate(id).orElseThrow(() -> new PropertyNotFoundException(id)); }
    private static void expected(String value, long revision) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw ImageFailure.invalid("expectedRevision must be a decimal string");
        try { if (Long.parseLong(value) != revision) throw new ImageFailure(409, "Image collection changed; reload its manifest"); }
        catch (NumberFormatException e) { throw ImageFailure.invalid("Invalid expectedRevision"); }
    }
    private static void increment(PropertyImageCollection collection) {
        if (collection.revision == Long.MAX_VALUE) throw new ImageFailure(409, "Image revision limit reached");
        collection.revision++;
    }
    static void text(String value, int max, String field) {
        if (value != null && (value.length() > max || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>')))
            throw ImageFailure.invalid(field + " must be plain text up to " + max + " characters");
    }
    private static String fingerprint(byte[] source, String... fields) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(source.length).array()); digest.update(source);
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            for (String value : fields) { out.writeBoolean(value != null); if (value != null) out.writeUTF(value); }
            digest.update(bytes.toByteArray()); return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) { throw new IllegalStateException("Image fingerprint unavailable"); }
    }
}
