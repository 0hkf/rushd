package com.rushd.drafts;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Internal persistence primitives. The caller must authenticate/authorize and establish a transaction. */
final class DraftStore {
    record Draft(UUID id, long ownerId, String kind, Long propertyId, String baseFingerprint,
                 long revision, String payload, Instant createdAt, Instant updatedAt) { }
    record Receipt(long ownerId, UUID operationId, UUID draftId, long revision, String outcome,
                   String requestFingerprint, Long propertyId, Instant committedAt) { }
    record Conflict(UUID id, UUID draftId, long serverRevision, String localPayload, String serverPayload,
                    String propertyPayload, String propertyFingerprint, String choicesHash, Long resolvedRevision, Instant createdAt) { }

    private final JdbcTemplate jdbc;
    private final DraftPayloadValidator validator = new DraftPayloadValidator();

    DraftStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void insert(Draft draft) {
        String payload = validator.validate(draft.payload());
        jdbc.update("""
                INSERT INTO admin_property_drafts
                (id, owner_id, kind, property_id, base_fingerprint, revision, payload, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, draft.id(), draft.ownerId(), draft.kind(), draft.propertyId(), draft.baseFingerprint(),
                draft.revision(), payload, Timestamp.from(draft.createdAt()), Timestamp.from(draft.updatedAt()));
    }

    Optional<Draft> find(long ownerId, UUID draftId, boolean lock) {
        String sql = "SELECT * FROM admin_property_drafts WHERE owner_id = ? AND id = ?" + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, row) -> new Draft(rs.getObject("id", UUID.class), rs.getLong("owner_id"),
                rs.getString("kind"), rs.getObject("property_id", Long.class), rs.getString("base_fingerprint"),
                rs.getLong("revision"), rs.getString("payload"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), ownerId, draftId).stream().findFirst();
    }

    boolean hasEdit(long ownerId, long propertyId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_property_drafts WHERE owner_id = ? AND property_id = ?",
                Long.class, ownerId, propertyId);
        return count != null && count > 0;
    }

    long count(long ownerId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_property_drafts WHERE owner_id = ?", Long.class, ownerId);
        return count == null ? 0 : count;
    }

    List<Draft> list(long ownerId, int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw new IllegalArgumentException("Invalid page");
        return jdbc.query("""
                SELECT * FROM admin_property_drafts WHERE owner_id = ?
                ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new Draft(rs.getObject("id", UUID.class), rs.getLong("owner_id"),
                rs.getString("kind"), rs.getObject("property_id", Long.class), rs.getString("base_fingerprint"),
                rs.getLong("revision"), rs.getString("payload"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), ownerId, limit, offset);
    }

    boolean update(long ownerId, UUID id, long expectedRevision, String payload, Instant now) {
        if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE) return false;
        String checked = validator.validate(payload);
        return jdbc.update("""
                UPDATE admin_property_drafts SET payload = ?, revision = revision + 1, updated_at = ?
                WHERE owner_id = ? AND id = ? AND revision = ?
                """, checked, Timestamp.from(now), ownerId, id, expectedRevision) == 1;
    }

    boolean delete(long ownerId, UUID id, long expectedRevision) {
        if (expectedRevision < 0) return false;
        return jdbc.update("DELETE FROM admin_property_drafts WHERE owner_id = ? AND id = ? AND revision = ?",
                ownerId, id, expectedRevision) == 1;
    }

    void record(Receipt receipt) {
        jdbc.update("""
                INSERT INTO admin_property_draft_receipts
                (owner_id, operation_id, draft_id, draft_revision, outcome, request_fingerprint, result_property_id, committed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, receipt.ownerId(), receipt.operationId(), receipt.draftId(), receipt.revision(),
                receipt.outcome(), receipt.requestFingerprint(), receipt.propertyId(), Timestamp.from(receipt.committedAt()));
    }

    Optional<Receipt> receipt(long ownerId, UUID operationId) {
        return jdbc.query("SELECT * FROM admin_property_draft_receipts WHERE owner_id = ? AND operation_id = ?",
                (rs, row) -> new Receipt(rs.getLong("owner_id"), rs.getObject("operation_id", UUID.class),
                        rs.getObject("draft_id", UUID.class), rs.getLong("draft_revision"), rs.getString("outcome"),
                        rs.getString("request_fingerprint"), rs.getObject("result_property_id", Long.class),
                        rs.getTimestamp("committed_at").toInstant()), ownerId, operationId).stream().findFirst();
    }

    Optional<Receipt> receiptForDraft(long ownerId, UUID draftId) {
        return jdbc.query("SELECT * FROM admin_property_draft_receipts WHERE owner_id = ? AND draft_id = ?",
                (rs, row) -> new Receipt(rs.getLong("owner_id"), rs.getObject("operation_id", UUID.class),
                        rs.getObject("draft_id", UUID.class), rs.getLong("draft_revision"), rs.getString("outcome"),
                        rs.getString("request_fingerprint"), rs.getObject("result_property_id", Long.class),
                        rs.getTimestamp("committed_at").toInstant()), ownerId, draftId).stream().findFirst();
    }

    Optional<Conflict> conflict(UUID draftId, UUID id) {
        return jdbc.query("SELECT * FROM admin_property_draft_conflicts WHERE draft_id = ? AND id = ?",
                (rs, row) -> new Conflict(rs.getObject("id", UUID.class), rs.getObject("draft_id", UUID.class),
                        rs.getLong("server_revision"), rs.getString("local_payload"), rs.getString("server_payload"),
                        rs.getString("property_payload"), rs.getString("property_fingerprint"), rs.getString("choices_hash"),
                        rs.getObject("resolved_revision", Long.class), rs.getTimestamp("created_at").toInstant()), draftId, id).stream().findFirst();
    }

    List<UUID> conflicts(UUID draftId) {
        return jdbc.query("SELECT id FROM admin_property_draft_conflicts WHERE draft_id = ? ORDER BY created_at, id",
                (rs, row) -> rs.getObject("id", UUID.class), draftId);
    }

    void insertConflict(Conflict c) {
        jdbc.update("""
                INSERT INTO admin_property_draft_conflicts
                (id, draft_id, server_revision, local_payload, server_payload, property_payload, property_fingerprint, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, c.id(), c.draftId(), c.serverRevision(), c.localPayload(), c.serverPayload(), c.propertyPayload(),
                c.propertyFingerprint(), Timestamp.from(c.createdAt()));
    }

    void resolved(Conflict c, String hash, long revision, String payload, Instant now) {
        jdbc.update("UPDATE admin_property_draft_conflicts SET choices_hash = ?, resolved_revision = ? WHERE id = ? AND draft_id = ?",
                hash, revision, c.id(), c.draftId());
        jdbc.update("UPDATE admin_property_drafts SET payload = ?, base_fingerprint = ?, revision = ?, updated_at = ? WHERE id = ?",
                validator.validate(payload), c.propertyFingerprint(), revision, Timestamp.from(now), c.draftId());
    }
}
