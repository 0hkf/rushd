package com.rushd.drafts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DraftStoreTest {
    private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
    private static final String BODY = "{\"values\":{\"price\":\"١٫٢\"},\"specifications\":{}}";
    private JdbcTemplate jdbc;
    private DraftStore store;
    private TransactionTemplate transaction;

    @BeforeEach void setup() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.execute("""
                CREATE TABLE admin_property_drafts (
                  id uuid PRIMARY KEY, owner_id bigint NOT NULL, kind varchar(6) NOT NULL,
                  property_id bigint, base_fingerprint varchar(64), revision bigint NOT NULL,
                  payload text NOT NULL, created_at timestamp with time zone NOT NULL,
                  updated_at timestamp with time zone NOT NULL, UNIQUE(owner_id, property_id))
                """);
        jdbc.execute("""
                CREATE TABLE admin_property_draft_receipts (
                  owner_id bigint NOT NULL, operation_id uuid NOT NULL, draft_id uuid NOT NULL,
                  draft_revision bigint NOT NULL, outcome varchar(6) NOT NULL, request_fingerprint varchar(64) NOT NULL,
                  result_property_id bigint, committed_at timestamp with time zone NOT NULL,
                  PRIMARY KEY(owner_id, operation_id), UNIQUE(owner_id, draft_id))
                """);
        store = new DraftStore(jdbc);
    }

    @Test void readsAndUpdatesOnlyTheOwnerAndExpectedRevision() {
        UUID id = UUID.randomUUID();
        store.insert(new DraftStore.Draft(id, 1, "CREATE", null, null, 0, BODY, NOW, NOW));
        assertTrue(store.find(2, id, false).isEmpty());
        assertFalse(store.update(2, id, 0, BODY, NOW));
        assertTrue(store.update(1, id, 0, BODY, NOW.plusSeconds(1)));
        assertFalse(store.update(1, id, 0, BODY, NOW.plusSeconds(2)));
        assertEquals(1, store.find(1, id, false).orElseThrow().revision());
        assertEquals(BODY, store.find(1, id, false).orElseThrow().payload());
        assertFalse(store.delete(1, id, 0));
        assertTrue(store.delete(1, id, 1));
    }

    @Test void rejectsBadPayloadBeforeWritingAndSeparatesOwnersInList() {
        UUID id = UUID.randomUUID();
        store.insert(new DraftStore.Draft(id, 1, "CREATE", null, null, 0, BODY, NOW, NOW));
        store.insert(new DraftStore.Draft(UUID.randomUUID(), 2, "CREATE", null, null, 0, BODY, NOW, NOW));
        assertEquals(1, store.list(1, 20, 0).size());
        assertThrows(IllegalArgumentException.class, () -> store.update(1, id, 0,
                "{\"values\":{\"password\":\"secret\"},\"specifications\":{}}", NOW));
        assertEquals(0, store.find(1, id, false).orElseThrow().revision());
        assertThrows(IllegalArgumentException.class, () -> store.list(1, 101, 0));
    }

    @Test void transactionRollsBackReceiptAndDraftDeletionTogether() {
        UUID id = UUID.randomUUID(), operation = UUID.randomUUID();
        store.insert(new DraftStore.Draft(id, 1, "CREATE", null, null, 0, BODY, NOW, NOW));
        transaction.executeWithoutResult(status -> {
            assertTrue(store.delete(1, id, 0));
            store.record(new DraftStore.Receipt(1, operation, id, 0, "COMMIT", "a".repeat(64), 10L, NOW));
            status.setRollbackOnly();
        });
        assertTrue(store.find(1, id, false).isPresent());
        assertTrue(store.receipt(1, operation).isEmpty());
    }
}
