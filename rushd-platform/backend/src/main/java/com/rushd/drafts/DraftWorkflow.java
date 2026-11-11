package com.rushd.drafts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.dto.CreatePropertyRequest;
import com.rushd.dto.PropertyResponse;
import com.rushd.dto.UpdatePropertyRequest;
import com.rushd.entity.Property;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.mapper.PropertyMapper;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.PropertyService;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Draft workflow. The HTTP surface is disabled by default until an isolated activation is approved. */
@Service
public class DraftWorkflow {
    private static final int MAX_DRAFTS_PER_OWNER = 25;
    private final DraftStore store;
    private final UserRepository users;
    private final PropertyRepository properties;
    private final PropertyDraftFingerprint fingerprints;
    private final PropertyService propertyService;
    private final ObjectMapper mapper;
    private final Validator validation;
    private final Clock clock;
    private final boolean enabled;

    public record EditContext(PropertyResponse property, String baseFingerprint) { }
    public record DraftView(String id, String kind, String propertyId, String baseFingerprint,
                            String revision, String payload, Instant createdAt, Instant updatedAt) { }
    public record DraftSummary(String id, String kind, String propertyId, String revision, Instant createdAt, Instant updatedAt) { }
    public record CommitResult(String propertyId, boolean replayed) { }
    public record OperationResult(String outcome, String revision, String propertyId) { }
    public record ConflictView(String id, String revision, Map<String, JsonNode> sources, List<DraftMerge.Group> groups,
                               String resolvedRevision, Instant createdAt) { }

    public DraftWorkflow(JdbcTemplate jdbc, UserRepository users, PropertyRepository properties,
                         ObjectMapper mapper, Clock clock, PropertyService propertyService, Validator validation,
                         @Value("${rushd.drafts.enabled:false}") boolean enabled) {
        this.store = new DraftStore(jdbc);
        this.users = users;
        this.properties = properties;
        this.fingerprints = new PropertyDraftFingerprint(mapper);
        this.propertyService = propertyService;
        this.mapper = mapper;
        this.validation = validation;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Transactional(readOnly = true)
    public EditContext context(String email, long propertyId) {
        requireEnabled(); owner(email, false);
        Property property = properties.findById(propertyId).orElseThrow(DraftWorkflow::missing);
        return new EditContext(PropertyMapper.toResponse(property), fingerprints.of(property));
    }

    @Transactional(readOnly = true)
    public DraftView get(String email, UUID id) {
        requireEnabled();
        return view(store.find(owner(email, false).getId(), requireId(id), false).orElseThrow(DraftWorkflow::missing));
    }

    @Transactional(readOnly = true)
    public OperationResult result(String email, UUID id) {
        requireEnabled();
        DraftStore.Receipt receipt = store.receiptForDraft(owner(email, false).getId(), requireId(id)).orElseThrow(DraftWorkflow::missing);
        return new OperationResult(receipt.outcome(), Long.toString(receipt.revision()),
                receipt.propertyId() == null ? null : Long.toString(receipt.propertyId()));
    }

    @Transactional(readOnly = true)
    public List<DraftSummary> list(String email, int limit, int offset) {
        requireEnabled();
        if (limit < 1 || limit > 100 || offset < 0) throw new DraftFailure(400, "Invalid page");
        return store.list(owner(email, false).getId(), limit, offset).stream()
                .map(draft -> new DraftSummary(draft.id().toString(), draft.kind(),
                        draft.propertyId() == null ? null : draft.propertyId().toString(),
                        Long.toString(draft.revision()), draft.createdAt(), draft.updatedAt())).toList();
    }

    @Transactional
    public DraftView begin(String email, UUID id, String kind, Long propertyId, String base, String payload) {
        requireEnabled(); requireId(id);
        long ownerId = owner(email, true).getId();
        if (!"CREATE".equals(kind) && !"EDIT".equals(kind)) throw new DraftFailure(400, "Invalid draft type");
        if ("CREATE".equals(kind) && (propertyId != null || base != null)) throw new DraftFailure(400, "Invalid draft type");
        if ("EDIT".equals(kind) && (propertyId == null || propertyId <= 0 || base == null || !base.matches("[0-9a-f]{64}")))
            throw new DraftFailure(400, "Invalid edit context");
        checkPayload(payload);
        if (store.receiptForDraft(ownerId, id).isPresent()) throw conflict();
        var existing = store.find(ownerId, id, true);
        if (existing.isPresent()) {
            DraftStore.Draft draft = existing.get();
            if (draft.kind().equals(kind) && Objects.equals(draft.propertyId(), propertyId)
                    && Objects.equals(draft.baseFingerprint(), base) && draft.payload().equals(payload)) return view(draft);
            throw conflict();
        }
        if (store.count(ownerId) >= MAX_DRAFTS_PER_OWNER) throw new DraftFailure(409, "Draft limit reached");
        if ("EDIT".equals(kind)) {
            if (store.hasEdit(ownerId, propertyId)) throw conflict();
            Property property = properties.findByIdForUpdate(propertyId).orElseThrow(DraftWorkflow::missing);
            if (!fingerprints.of(property).equals(base)) throw conflict();
        }
        Instant now = clock.instant();
        var draft = new DraftStore.Draft(id, ownerId, kind, propertyId, base, 0, payload, now, now);
        try { store.insert(draft); } catch (DuplicateKeyException duplicate) { throw conflict(); }
        return view(draft);
    }

    @Transactional
    public DraftView update(String email, UUID id, long revision, String payload) {
        requireEnabled(); requireId(id); checkPayload(payload);
        long ownerId = owner(email, true).getId();
        DraftStore.Draft current = store.find(ownerId, id, true).orElseThrow(DraftWorkflow::missing);
        if (revision < 0 || revision == Long.MAX_VALUE) throw conflict();
        // An acknowledgement may be lost. An identical immediate retry is safe and does not write again.
        if (current.revision() == revision + 1 && current.payload().equals(payload)) return view(current);
        if (current.revision() != revision || !store.update(ownerId, id, revision, payload, clock.instant())) throw conflict();
        return view(store.find(ownerId, id, false).orElseThrow(DraftWorkflow::missing));
    }

    @Transactional(readOnly = true)
    public List<String> conflicts(String email, UUID id) {
        requireEnabled();
        store.find(owner(email, false).getId(), requireId(id), false).orElseThrow(DraftWorkflow::missing);
        return store.conflicts(id).stream().map(UUID::toString).toList();
    }

    @Transactional(readOnly = true)
    public ConflictView conflict(String email, UUID id, UUID conflictId) {
        requireEnabled();
        store.find(owner(email, false).getId(), requireId(id), false).orElseThrow(DraftWorkflow::missing);
        return conflictView(store.conflict(id, requireId(conflictId)).orElseThrow(DraftWorkflow::missing));
    }

    /** Preserve both copies before making a merge decision; no property is published here. */
    @Transactional
    public ConflictView preserve(String email, UUID id, UUID conflictId, String localPayload) {
        requireEnabled(); requireId(id); requireId(conflictId); checkPayload(localPayload);
        long ownerId = owner(email, true).getId();
        DraftStore.Draft draft = store.find(ownerId, id, true).orElseThrow(DraftWorkflow::missing);
        var previous = store.conflict(id, conflictId);
        if (previous.isPresent()) {
            if (!json(previous.get().localPayload()).equals(json(localPayload))) throw conflict();
            return conflictView(previous.get());
        }
        if (store.conflicts(id).size() >= 20) throw new DraftFailure(409, "Conflict archive limit reached; no copies were removed");
        String propertyPayload = null, fingerprint = null;
        if (draft.propertyId() != null) {
            Property property = properties.findByIdForUpdate(draft.propertyId()).orElseThrow(DraftWorkflow::missing);
            fingerprint = fingerprints.of(property);
            propertyPayload = propertySnapshot(property).toString();
            checkPayload(propertyPayload);
        }
        var saved = new DraftStore.Conflict(conflictId, id, draft.revision(), localPayload, draft.payload(),
                propertyPayload, fingerprint, null, null, clock.instant());
        try { store.insertConflict(saved); } catch (DuplicateKeyException duplicate) { throw conflict(); }
        return conflictView(saved);
    }

    /** CAS of both draft and property, plus archives, in one transaction. */
    @Transactional
    public DraftView resolve(String email, UUID id, UUID conflictId, JsonNode choices) {
        requireEnabled(); requireId(id); requireId(conflictId);
        long ownerId = owner(email, true).getId();
        var draft = store.find(ownerId, id, true).orElseThrow(DraftWorkflow::missing);
        var review = store.conflict(id, conflictId).orElseThrow(DraftWorkflow::missing);
        Map<String, JsonNode> sources = sources(review);
        ObjectNode merged = new DraftMerge(mapper).merge(sources, choices);
        String choicesHash = hash(merged.toString());
        if (review.resolvedRevision() != null) {
            if (choicesHash.equals(review.choicesHash()) && draft.revision() == review.resolvedRevision()) return view(draft);
            throw conflict();
        }
        if (draft.revision() != review.serverRevision() || draft.revision() == Long.MAX_VALUE) throw conflict();
        if (draft.propertyId() != null) {
            Property current = properties.findByIdForUpdate(draft.propertyId()).orElseThrow(DraftWorkflow::missing);
            if (!fingerprints.of(current).equals(review.propertyFingerprint())) throw conflict();
        }
        store.resolved(review, choicesHash, draft.revision() + 1, merged.toString(), clock.instant());
        return view(store.find(ownerId, id, false).orElseThrow(DraftWorkflow::missing));
    }

    private ConflictView conflictView(DraftStore.Conflict c) {
        Map<String, JsonNode> values = sources(c);
        return new ConflictView(c.id().toString(), Long.toString(c.serverRevision()), values, new DraftMerge(mapper).groups(values),
                c.resolvedRevision() == null ? null : c.resolvedRevision().toString(), c.createdAt());
    }

    private Map<String, JsonNode> sources(DraftStore.Conflict c) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        values.put("SERVER", json(c.serverPayload())); values.put("LOCAL", json(c.localPayload()));
        if (c.propertyPayload() != null) values.put("PROPERTY", json(c.propertyPayload()));
        return values;
    }

    private ObjectNode propertySnapshot(Property property) {
        JsonNode response = mapper.valueToTree(PropertyMapper.toResponse(property));
        ObjectNode root = mapper.createObjectNode(), values = root.putObject("values"), specs = root.putObject("specifications");
        // Response facts only; omit identity, publisher, timestamps and images.
        for (String key : java.util.List.of("title", "type", "city", "district", "area", "price", "listingType", "rentalPeriod",
                "streetWidth", "facade", "purpose", "description", "status", "formattedAddress", "neighborhood", "latitude", "longitude", "googlePlaceId")) {
            JsonNode value = response.path(key);
            values.put(key, value.isMissingNode() || value.isNull() ? "" : value.asText());
        }
        response.path("specifications").fields().forEachRemaining(e -> specs.put(e.getKey(), e.getValue().isNull() ? "" : e.getValue().asText()));
        root.putObject("sourceErrors"); root.putObject("specificationSourceErrors");
        // Property responses contain no cached type edits. This absence is shown explicitly in the review.
        root.putObject("typeDrafts"); root.putObject("scopes");
        return root;
    }

    private JsonNode json(String raw) {
        try { return mapper.readTree(raw); } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new DraftFailure(400, "Invalid draft payload"); }
    }

    @Transactional
    public void delete(String email, UUID id, long revision, UUID operationId) {
        requireEnabled(); requireId(id); requireId(operationId);
        long ownerId = owner(email, true).getId();
        if (revision < 0) throw conflict();
        String requestHash = hash("DELETE\n" + id + "\n" + revision);
        var prior = store.receipt(ownerId, operationId);
        if (prior.isPresent()) {
            var receipt = prior.get();
            if (receipt.outcome().equals("DELETE") && receipt.draftId().equals(id)
                    && receipt.revision() == revision && receipt.requestFingerprint().equals(requestHash)) return;
            throw conflict();
        }
        DraftStore.Draft current = store.find(ownerId, id, true).orElseThrow(DraftWorkflow::missing);
        if (current.revision() != revision) throw conflict();
        if (!store.delete(ownerId, id, revision)) throw conflict();
        store.record(new DraftStore.Receipt(ownerId, operationId, id, revision, "DELETE", requestHash, null, clock.instant()));
    }

    /** Validated property save, receipt and draft removal must share the caller's database transaction. */
    @Transactional
    public CommitResult commit(String email, UUID id, long revision, UUID operationId, JsonNode propertyPayload) {
        requireEnabled(); requireId(id); requireId(operationId);
        if (revision < 0 || propertyPayload == null || !propertyPayload.isObject()) throw new DraftFailure(400, "Invalid property payload");
        final String json;
        try {
            json = mapper.writeValueAsString(propertyPayload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new DraftFailure(400, "Invalid property payload");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > DraftPayloadValidator.MAX_PAYLOAD_BYTES)
            throw new DraftFailure(400, "Invalid property payload");
        long ownerId = owner(email, true).getId();
        String requestHash = hash("COMMIT\n" + id + "\n" + revision + "\n" + json);
        var prior = store.receipt(ownerId, operationId);
        if (prior.isPresent()) {
            var receipt = prior.get();
            if (receipt.outcome().equals("COMMIT") && receipt.draftId().equals(id)
                    && receipt.revision() == revision && receipt.requestFingerprint().equals(requestHash))
                return new CommitResult(Long.toString(receipt.propertyId()), true);
            throw conflict();
        }
        DraftStore.Draft draft = store.find(ownerId, id, true).orElseThrow(DraftWorkflow::missing);
        if (draft.revision() != revision) throw conflict();
        PropertyResponse saved;
        try {
            if (draft.kind().equals("CREATE")) {
                CreatePropertyRequest request = mapper.readerFor(CreatePropertyRequest.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
                if (!validation.validate(request).isEmpty()) throw new DraftFailure(400, "Invalid property payload");
                saved = propertyService.createProperty(request, email);
            } else {
                Property property = properties.findByIdForUpdate(draft.propertyId()).orElseThrow(DraftWorkflow::missing);
                if (!fingerprints.of(property).equals(draft.baseFingerprint())) throw conflict();
                UpdatePropertyRequest request = mapper.readerFor(UpdatePropertyRequest.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
                if (!validation.validate(request).isEmpty()) throw new DraftFailure(400, "Invalid property payload");
                saved = propertyService.updateProperty(draft.propertyId(), request, email);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new DraftFailure(400, "Invalid property payload");
        }
        if (!store.delete(ownerId, id, revision)) throw conflict();
        store.record(new DraftStore.Receipt(ownerId, operationId, id, revision, "COMMIT", requestHash, saved.getId(), clock.instant()));
        return new CommitResult(Long.toString(saved.getId()), false);
    }

    private User owner(String email, boolean lock) {
        if (email == null || email.isBlank()) throw new UsernameNotFoundException("Authentication is required");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        User user = (lock ? users.findByEmailForUpdate(normalized) : users.findByEmail(normalized))
                .orElseThrow(() -> new UsernameNotFoundException("Authentication is required"));
        if (user.getRole() != Role.ADMIN) throw new AccessDeniedException("Admin access is required");
        return user;
    }

    private void requireEnabled() { if (!enabled) throw new DraftFailure(503, "Drafts are not enabled"); }
    private static UUID requireId(UUID id) { if (id == null) throw new DraftFailure(400, "Invalid identifier"); return id; }
    private static void checkPayload(String payload) {
        try { new DraftPayloadValidator().validate(payload); }
        catch (IllegalArgumentException ex) { throw new DraftFailure(400, "Invalid draft payload"); }
    }
    private static DraftView view(DraftStore.Draft draft) {
        return new DraftView(draft.id().toString(), draft.kind(), draft.propertyId() == null ? null : draft.propertyId().toString(),
                draft.baseFingerprint(), Long.toString(draft.revision()), draft.payload(), draft.createdAt(), draft.updatedAt());
    }
    private static DraftFailure missing() { return new DraftFailure(404, "Draft or property is unavailable"); }
    private static DraftFailure conflict() { return new DraftFailure(409, "Draft or property changed; review before retrying"); }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
