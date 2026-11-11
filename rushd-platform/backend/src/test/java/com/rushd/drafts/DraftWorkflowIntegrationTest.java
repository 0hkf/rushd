package com.rushd.drafts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.rushd.entity.*;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.PropertyService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "rushd.drafts.enabled=true")
@AutoConfigureMockMvc
@Transactional
class DraftWorkflowIntegrationTest {
    @Autowired DraftWorkflow workflow;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired ObjectMapper mapper;
    @Autowired Clock clock;
    @Autowired PropertyService propertyService;
    @Autowired Validator validation;
    @Autowired MockMvc mvc;
    private String email, otherEmail, buyerEmail;
    private long propertyId;
    private static final String BODY = "{\"values\":{\"price\":\"١٫٢\"},\"specifications\":{}}";
    private static final String CHANGED = "{\"values\":{\"price\":\"١٫٣\"},\"specifications\":{}}";

    @BeforeEach void setup() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS admin_property_drafts (
                  id uuid PRIMARY KEY, owner_id bigint NOT NULL, kind varchar(6) NOT NULL,
                  property_id bigint, base_fingerprint varchar(64), revision bigint NOT NULL,
                  payload text NOT NULL, created_at timestamp with time zone NOT NULL,
                  updated_at timestamp with time zone NOT NULL, UNIQUE(owner_id, property_id))
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS admin_property_draft_receipts (
                  owner_id bigint NOT NULL, operation_id uuid NOT NULL, draft_id uuid NOT NULL,
                  draft_revision bigint NOT NULL, outcome varchar(6) NOT NULL, request_fingerprint varchar(64) NOT NULL,
                  result_property_id bigint, committed_at timestamp with time zone NOT NULL,
                  PRIMARY KEY(owner_id, operation_id), UNIQUE(owner_id, draft_id))
                """);
        String suffix = UUID.randomUUID().toString();
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS admin_property_draft_conflicts (
                  id uuid PRIMARY KEY, draft_id uuid NOT NULL REFERENCES admin_property_drafts(id) ON DELETE CASCADE,
                  server_revision bigint NOT NULL, local_payload text NOT NULL, server_payload text NOT NULL,
                  property_payload text, property_fingerprint varchar(64), choices_hash varchar(64), resolved_revision bigint,
                  created_at timestamp with time zone NOT NULL)
                """);
        User admin = createUser("draft-" + suffix + "@example.invalid", Role.ADMIN);
        email = admin.getEmail();
        otherEmail = createUser("other-" + suffix + "@example.invalid", Role.ADMIN).getEmail();
        buyerEmail = createUser("buyer-" + suffix + "@example.invalid", Role.BUYER).getEmail();
        Property property = new Property(); property.setPublisher(admin); property.setTitle("Synthetic draft");
        property.setType(PropertyType.LAND); property.setCity("مكة"); property.setDistrict("النوارية");
        property.setArea(new BigDecimal("100")); property.setPrice(new BigDecimal("100"));
        property.setPurpose(PropertyPurpose.RESIDENTIAL); propertyId = properties.saveAndFlush(property).getId();
    }

    private User createUser(String email, Role role) {
        User user = new User(); user.setName("Synthetic"); user.setEmail(email);
        user.setPassword("test-hash"); user.setRole(role); return users.saveAndFlush(user);
    }

    @Test void sameOwnerCanSaveAndRetryButAnotherOwnerCannotRead() {
        UUID id = UUID.randomUUID();
        var draft = workflow.begin(email, id, "CREATE", null, null, BODY);
        assertEquals("0", draft.revision());
        assertEquals(draft, workflow.begin(email, id, "CREATE", null, null, BODY));
        assertEquals(404, assertThrows(DraftFailure.class, () -> workflow.get(otherEmail, id)).status());
        assertThrows(AccessDeniedException.class, () -> workflow.get(buyerEmail, id));
        assertEquals("1", workflow.update(email, id, 0, CHANGED).revision());
        assertEquals("1", workflow.update(email, id, 0, CHANGED).revision());
        assertEquals(409, assertThrows(DraftFailure.class, () -> workflow.update(email, id, 0, BODY)).status());
        assertEquals(CHANGED, workflow.get(email, id).payload());
    }

    @Test void editUsesTheOriginalFingerprintAndNeverSilentlyOverwritesAChangedProperty() {
        var context = workflow.context(email, propertyId);
        Property property = properties.findById(propertyId).orElseThrow();
        property.setPrice(new BigDecimal("101")); properties.saveAndFlush(property);
        assertEquals(409, assertThrows(DraftFailure.class, () -> workflow.begin(email, UUID.randomUUID(),
                "EDIT", propertyId, context.baseFingerprint(), BODY)).status());
        assertTrue(workflow.list(email, 20, 0).isEmpty());
    }

    @Test void multipleCreateDraftsButOnlyOneEditDraftPerPropertyAndOwner() {
        workflow.begin(email, UUID.randomUUID(), "CREATE", null, null, BODY);
        workflow.begin(email, UUID.randomUUID(), "CREATE", null, null, CHANGED);
        var base = workflow.context(email, propertyId).baseFingerprint();
        UUID original = UUID.randomUUID();
        workflow.begin(email, original, "EDIT", propertyId, base, BODY);
        assertEquals(409, assertThrows(DraftFailure.class, () ->
                workflow.begin(email, UUID.randomUUID(), "EDIT", propertyId, base, CHANGED)).status());
        assertEquals(BODY, workflow.get(email, original).payload());
        workflow.begin(otherEmail, UUID.randomUUID(), "EDIT", propertyId, base, BODY);
        assertEquals(3, workflow.list(email, 20, 0).size());
    }

    @Test void manualDeleteIsIdempotentAndPreventsDraftResurrection() {
        UUID id = UUID.randomUUID(), operation = UUID.randomUUID();
        workflow.begin(email, id, "CREATE", null, null, BODY);
        workflow.delete(email, id, 0, operation);
        workflow.delete(email, id, 0, operation);
        assertEquals(404, assertThrows(DraftFailure.class, () -> workflow.get(email, id)).status());
        assertEquals(409, assertThrows(DraftFailure.class, () -> workflow.begin(email, id, "CREATE", null, null, BODY)).status());
    }

    private JsonNode choose(DraftWorkflow.ConflictView review, String source) {
        var choices = mapper.createObjectNode();
        review.groups().stream().filter(DraftMerge.Group::choiceRequired).forEach(group -> choices.put(group.id(), source));
        return choices;
    }

    @Test void explicitMergePreservesBothCopiesAndRetriesWithoutWritingAgain() {
        UUID id = UUID.randomUUID(), reviewId = UUID.randomUUID();
        workflow.begin(email, id, "CREATE", null, null, BODY);
        var review = workflow.preserve(email, id, reviewId, CHANGED);
        assertEquals(BODY, review.sources().get("SERVER").toString());
        assertEquals(CHANGED, review.sources().get("LOCAL").toString());
        assertEquals(review.id(), workflow.preserve(email, id, reviewId, CHANGED).id());
        assertEquals(404, assertThrows(DraftFailure.class, () -> workflow.conflict(otherEmail, id, reviewId)).status());
        var selected = choose(review, "LOCAL");
        var merged = workflow.resolve(email, id, reviewId, selected);
        assertEquals("1", merged.revision());
        assertEquals("١٫٣", json(merged.payload()).path("values").path("price").asText());
        assertEquals("1", workflow.resolve(email, id, reviewId, selected).revision());
        assertEquals(BODY, workflow.conflict(email, id, reviewId).sources().get("SERVER").toString());
        assertEquals(409, assertThrows(DraftFailure.class, () -> workflow.resolve(email, id, reviewId, choose(review, "SERVER"))).status());
        workflow.delete(email, id, 1, UUID.randomUUID());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admin_property_draft_conflicts WHERE draft_id = ?", Integer.class, id));
    }

    private JsonNode json(String value) {
        try { return mapper.readTree(value); } catch (Exception ex) { throw new AssertionError(ex); }
    }

    @Test void changedDraftOrPropertyStopsResolutionWithoutRemovingArchives() {
        UUID id = UUID.randomUUID(), reviewId = UUID.randomUUID();
        var base = workflow.context(email, propertyId).baseFingerprint();
        workflow.begin(email, id, "EDIT", propertyId, base, BODY);
        var review = workflow.preserve(email, id, reviewId, CHANGED);
        Property property = properties.findById(propertyId).orElseThrow();
        property.setPrice(new BigDecimal("101")); properties.saveAndFlush(property);
        assertEquals(409, assertThrows(DraftFailure.class, () -> workflow.resolve(email, id, reviewId, choose(review, "LOCAL"))).status());
        assertEquals(BODY, workflow.get(email, id).payload());
        assertEquals(1, workflow.conflicts(email, id).size());
        var fresh = workflow.preserve(email, id, UUID.randomUUID(), CHANGED);
        workflow.update(email, id, 0, CHANGED);
        assertEquals(409, assertThrows(DraftFailure.class, () -> workflow.resolve(email, id, UUID.fromString(fresh.id()), choose(fresh, "LOCAL"))).status());
    }

    @Test void conflictHttpWritesRequireCsrfAndNeverAcceptOwnerFromClient() throws Exception {
        UUID id = UUID.randomUUID(), conflictId = UUID.randomUUID();
        workflow.begin(email, id, "CREATE", null, null, BODY);
        String body = "{\"conflictId\":\"" + conflictId + "\",\"localPayload\":" + CHANGED + "}";
        mvc.perform(post("/api/admin/property-drafts/" + id + "/conflicts").with(user(email).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/property-drafts/" + id + "/conflicts").with(user(email).roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/admin/property-drafts/" + id + "/conflicts/" + conflictId).with(user(otherEmail).roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/property-drafts/" + id + "/conflicts").with(user(buyerEmail).roles("BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test void successfulCommitRemovesDraftAndRetryNeverCreatesAnotherProperty() throws Exception {
        UUID id = UUID.randomUUID(), operation = UUID.randomUUID();
        workflow.begin(email, id, "CREATE", null, null, BODY);
        JsonNode payload = mapper.readTree("""
                {"title":"Synthetic","type":"LAND","listingType":"SALE","city":"مكة",
                 "district":"النوارية","area":"100.00","price":"9007199254740993.01","purpose":"RESIDENTIAL"}
                """);
        var first = workflow.commit(email, id, 0, operation, payload);
        assertFalse(first.replayed());
        assertEquals(404, assertThrows(DraftFailure.class, () -> workflow.get(email, id)).status());
        var second = workflow.commit(email, id, 0, operation, payload);
        assertTrue(second.replayed());
        assertEquals(first.propertyId(), second.propertyId());
        assertEquals("COMMIT", workflow.result(email, id).outcome());
        assertEquals(first.propertyId(), workflow.result(email, id).propertyId());
        assertEquals(404, assertThrows(DraftFailure.class, () -> workflow.result(otherEmail, id)).status());
        assertTrue(properties.findById(Long.valueOf(first.propertyId())).isPresent());
        assertEquals(409, assertThrows(DraftFailure.class, () -> workflow.begin(email, id, "CREATE", null, null, BODY)).status());
    }

    @Test void invalidCommitKeepsDraft() throws Exception {
        UUID id = UUID.randomUUID();
        workflow.begin(email, id, "CREATE", null, null, BODY);
        JsonNode invalid = mapper.readTree("{\"title\":\"Missing fields\"}");
        assertEquals(400, assertThrows(DraftFailure.class, () -> workflow.commit(email, id, 0, UUID.randomUUID(), invalid)).status());
        assertEquals(BODY, workflow.get(email, id).payload());
    }

    @Test void changedPropertyPreventsEditCommitAndKeepsDraft() throws Exception {
        UUID id = UUID.randomUUID();
        var context = workflow.context(email, propertyId);
        workflow.begin(email, id, "EDIT", propertyId, context.baseFingerprint(), BODY);
        Property property = properties.findById(propertyId).orElseThrow();
        property.setPrice(new BigDecimal("101")); properties.saveAndFlush(property);
        JsonNode payload = mapper.readTree("""
                {"title":"Synthetic","type":"LAND","listingType":"SALE","city":"مكة",
                 "district":"النوارية","area":"100.00","price":"102.00","purpose":"RESIDENTIAL","status":"ACTIVE"}
                """);
        assertEquals(409, assertThrows(DraftFailure.class, () -> workflow.commit(email, id, 0, UUID.randomUUID(), payload)).status());
        assertEquals(BODY, workflow.get(email, id).payload());
        assertEquals(0, new BigDecimal("101").compareTo(properties.findById(propertyId).orElseThrow().getPrice()));
    }

    @Test void successfulEditCommitPreservesIdentityAndRemovesDraft() throws Exception {
        UUID id = UUID.randomUUID(), operation = UUID.randomUUID();
        var context = workflow.context(email, propertyId);
        workflow.begin(email, id, "EDIT", propertyId, context.baseFingerprint(), BODY);
        workflow.preserve(email, id, UUID.randomUUID(), CHANGED);
        JsonNode payload = mapper.readTree("""
                {"title":"Edited synthetic","type":"LAND","listingType":"SALE","city":"مكة",
                 "district":"النوارية","area":"100.25","price":"9007199254740993.01","purpose":"RESIDENTIAL","status":"ACTIVE"}
                """);
        var result = workflow.commit(email, id, 0, operation, payload);
        assertEquals(Long.toString(propertyId), result.propertyId());
        assertEquals(404, assertThrows(DraftFailure.class, () -> workflow.get(email, id)).status());
        Property saved = properties.findById(propertyId).orElseThrow();
        assertEquals("Edited synthetic", saved.getTitle());
        assertEquals(0, saved.getArea().compareTo(new BigDecimal("100.25")));
        assertEquals(0, saved.getPrice().compareTo(new BigDecimal("9007199254740993.01")));
        assertTrue(workflow.commit(email, id, 0, operation, payload).replayed());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admin_property_draft_conflicts WHERE draft_id = ?", Integer.class, id));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failureWritingReceiptRollsBackPropertyAndDraftDeletion() throws Exception {
        UUID id = UUID.randomUUID();
        long ownerId = users.findByEmail(email).orElseThrow().getId();
        workflow.begin(email, id, "CREATE", null, null, BODY);
        workflow.preserve(email, id, UUID.randomUUID(), CHANGED);
        Long before = jdbc.queryForObject("SELECT COUNT(*) FROM properties WHERE seller_id = ?", Long.class, ownerId);
        JsonNode payload = mapper.readTree("""
                {"title":"Must roll back","type":"LAND","listingType":"SALE","city":"مكة",
                 "district":"النوارية","area":"100.00","price":"100.00","purpose":"RESIDENTIAL"}
                """);
        jdbc.execute("ALTER TABLE admin_property_draft_receipts ADD CONSTRAINT draft_force_rollback CHECK (request_fingerprint = 'force_failure')");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> workflow.commit(email, id, 0, UUID.randomUUID(), payload));
            assertEquals(BODY, workflow.get(email, id).payload());
            assertEquals(1, workflow.conflicts(email, id).size());
            assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM properties WHERE seller_id = ?", Long.class, ownerId));
        } finally {
            jdbc.execute("ALTER TABLE admin_property_draft_receipts DROP CONSTRAINT draft_force_rollback");
            jdbc.update("DELETE FROM admin_property_drafts WHERE owner_id = ?", ownerId);
            jdbc.update("DELETE FROM properties WHERE seller_id = ?", ownerId);
            jdbc.update("DELETE FROM users WHERE email IN (?, ?, ?)", email, otherEmail, buyerEmail);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoDevicesCannotSilentlyOverwriteTheSameRevision() throws Exception {
        UUID id = UUID.randomUUID();
        long ownerId = users.findByEmail(email).orElseThrow().getId();
        workflow.begin(email, id, "CREATE", null, null, BODY);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> { ready.countDown(); start.await(); return tryUpdate(id, CHANGED); });
            var second = executor.submit(() -> { ready.countDown(); start.await(); return tryUpdate(id,
                    "{\"values\":{\"price\":\"١٫٤\"},\"specifications\":{}}"); });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var outcomes = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertTrue(outcomes.contains(200));
            assertTrue(outcomes.contains(409));
            assertEquals("1", workflow.get(email, id).revision());
        } finally {
            start.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM admin_property_drafts WHERE owner_id = ?", ownerId);
            jdbc.update("DELETE FROM properties WHERE seller_id = ?", ownerId);
            jdbc.update("DELETE FROM users WHERE email IN (?, ?, ?)", email, otherEmail, buyerEmail);
        }
    }

    private int tryUpdate(UUID id, String payload) {
        try { workflow.update(email, id, 0, payload); return 200; }
        catch (DraftFailure failure) { return failure.status(); }
    }

    @Test void disabledServiceDoesNotRequireDraftTables() {
        var disabled = new DraftWorkflow(jdbc, users, properties, mapper, clock, propertyService, validation, false);
        assertEquals(503, assertThrows(DraftFailure.class, () -> disabled.get(email, UUID.randomUUID())).status());
    }

    @Test void httpRequiresAdminAndCsrfAndNeverShowsAnotherOwnersDraft() throws Exception {
        UUID id = UUID.randomUUID();
        String path = "/api/admin/property-drafts";
        String request = "{\"id\":\"" + id + "\",\"kind\":\"CREATE\",\"payload\":" + BODY + "}";
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(user(buyerEmail).roles("BUYER")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(email).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(email).roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.revision").value("0"));
        mvc.perform(get(path + "/" + id).with(user(otherEmail).roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get(path).with(user(email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].payload").doesNotExist());
    }

    @Test void httpRejectsDuplicateFieldsAndOversizedBodies() throws Exception {
        String path = "/api/admin/property-drafts";
        String duplicate = "{\"id\":\"" + UUID.randomUUID() + "\",\"kind\":\"CREATE\",\"kind\":\"EDIT\",\"payload\":" + BODY + "}";
        mvc.perform(post(path).with(user(email).roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).with(user(email).roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(" ".repeat(128 * 1024 + 1)))
                .andExpect(status().isPayloadTooLarge());
    }
}
