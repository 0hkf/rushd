package com.rushd.market;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Internal persistence; workflow supplies authorization and transactions. Lists never select original_bytes. */
final class MarketStore {
    record Source(UUID id, MarketInput.Source metadata, String creationHash, String sha256, Long size, boolean archived,
                  String stateReason, long revision, long createdBy, long changedBy, Instant createdAt, Instant updatedAt) { }
    record Reference(UUID id, UUID sourceId, String sourceReference, String creationHash, long revision, MarketInput.Facts facts,
                     boolean archived, String stateReason, String changeReason, long changedBy, Instant changedAt) { }
    private final JdbcTemplate jdbc;
    private static final String SOURCE_COLUMNS = "id,title,official_url,period_start,period_end,obtained_date,licence_note,notes,creation_hash,original_sha256,original_size,archived,state_reason,revision,created_by,changed_by,created_at,updated_at";
    private static final String REFERENCE_SELECT = "SELECT r.id,r.source_id,r.source_reference,r.creation_hash,v.* FROM market_references r JOIN market_reference_versions v ON v.reference_id=r.id ";
    private static final RowMapper<Source> SOURCES = (rs, n) -> new Source(rs.getObject("id", UUID.class),
            new MarketInput.Source(rs.getString("title"), rs.getString("official_url"), rs.getString("period_start"), rs.getString("period_end"),
                    rs.getString("obtained_date"), rs.getString("licence_note"), rs.getString("notes")), rs.getString("creation_hash"),
            rs.getString("original_sha256"), rs.getObject("original_size", Long.class), rs.getBoolean("archived"), rs.getString("state_reason"),
            rs.getLong("revision"), rs.getLong("created_by"), rs.getLong("changed_by"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private static final RowMapper<Reference> REFERENCES = (rs, n) -> {
        try {
            return new Reference(rs.getObject("id", UUID.class), rs.getObject("source_id", UUID.class), rs.getString("source_reference"),
                    rs.getString("creation_hash"), rs.getLong("revision"), MarketInput.JSON.readValue(rs.getString("facts_json"), MarketInput.Facts.class),
                    rs.getBoolean("archived"), rs.getString("state_reason"), rs.getString("change_reason"), rs.getLong("changed_by"), rs.getTimestamp("changed_at").toInstant());
        } catch (java.io.IOException ex) { throw new IllegalStateException("Invalid persisted market snapshot", ex); }
    };
    MarketStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    long lockQuota() { return Objects.requireNonNull(jdbc.queryForObject("SELECT used_bytes FROM market_storage_quota WHERE id=1 FOR UPDATE", Long.class)); }
    void useQuota(long bytes) { jdbc.update("UPDATE market_storage_quota SET used_bytes=used_bytes+? WHERE id=1", bytes); }
    Source source(UUID id, boolean lock) {
        return jdbc.query("SELECT " + SOURCE_COLUMNS + " FROM market_sources WHERE id=?" + (lock ? " FOR UPDATE" : ""), SOURCES, id)
                .stream().findFirst().orElseThrow(MarketFailure::missing);
    }
    Optional<Source> existingSource(UUID id) {
        return jdbc.query("SELECT " + SOURCE_COLUMNS + " FROM market_sources WHERE id=?", SOURCES, id).stream().findFirst();
    }
    List<Source> sources(int limit, int offset) {
        return jdbc.query("SELECT " + SOURCE_COLUMNS + " FROM market_sources ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?", SOURCES, limit, offset);
    }
    void insertSource(UUID id, MarketInput.Source s, String hash, long actor, Instant now) {
        jdbc.update("""
            INSERT INTO market_sources(id,title,official_url,period_start,period_end,obtained_date,licence_note,notes,creation_hash,
              archived,revision,created_by,changed_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,false,0,?,?,?,?)
            """, id,s.title(),s.officialUrl(),java.sql.Date.valueOf(s.periodStart()),java.sql.Date.valueOf(s.periodEnd()),java.sql.Date.valueOf(s.obtainedDate()),
                s.licenceNote(),s.notes(),hash,actor,actor,Timestamp.from(now),Timestamp.from(now));
    }
    void original(UUID id, byte[] bytes, String hash, long actor, Instant now) {
        jdbc.update("UPDATE market_sources SET original_bytes=?,original_sha256=?,original_size=?,revision=revision+1,changed_by=?,updated_at=? WHERE id=?",
                bytes,hash,bytes.length,actor,Timestamp.from(now),id);
    }
    byte[] original(UUID id) { return jdbc.queryForObject("SELECT original_bytes FROM market_sources WHERE id=?", byte[].class,id); }
    void sourceState(UUID id, boolean archived, String reason, long actor, Instant now) {
        jdbc.update("UPDATE market_sources SET archived=?,state_reason=?,revision=revision+1,changed_by=?,updated_at=? WHERE id=?", archived,reason,actor,Timestamp.from(now),id);
    }
    Optional<Reference> existingReference(UUID id, boolean lock) {
        if (lock) jdbc.query("SELECT id FROM market_references WHERE id=? FOR UPDATE", (rs,n)->rs.getObject(1, UUID.class),id);
        return jdbc.query(REFERENCE_SELECT + "WHERE r.id=? AND v.revision=r.revision",REFERENCES,id).stream().findFirst();
    }
    List<Reference> references(int limit, int offset) {
        return jdbc.query(REFERENCE_SELECT + "WHERE v.revision=r.revision ORDER BY v.changed_at DESC,r.id DESC LIMIT ? OFFSET ?",REFERENCES,limit,offset);
    }
    List<Reference> versions(UUID id, int limit, int offset) {
        return jdbc.query(REFERENCE_SELECT + "WHERE r.id=? ORDER BY v.revision DESC LIMIT ? OFFSET ?",REFERENCES,id,limit,offset);
    }
    void insertReference(UUID id, UUID source, String sourceRef, String hash) {
        jdbc.update("INSERT INTO market_references(id,source_id,source_reference,creation_hash,revision) VALUES (?,?,?,?,0)",id,source,sourceRef,hash);
    }
    void version(Reference r, MarketInput.Facts facts, boolean archived, String stateReason, String changeReason, long actor, Instant now, boolean initial) {
        long revision = initial ? 0 : Math.addExact(r.revision(),1);
        jdbc.update("""
            INSERT INTO market_reference_versions(reference_id,revision,facts_json,price,area,archived,state_reason,change_reason,changed_by,changed_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,r.id(),revision,MarketInput.json(facts),new BigDecimal(facts.price()),facts.area()==null?null:new BigDecimal(facts.area()),archived,stateReason,changeReason,actor,Timestamp.from(now));
        if (!initial) jdbc.update("UPDATE market_references SET revision=? WHERE id=?",revision,r.id());
    }
}
