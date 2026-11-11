package com.rushd.service;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationEmailConflictTest {
    @Test void onlyNamedEmailUniqueViolationsAreRecognized() {
        assertTrue(conflict("23505", "uq_users_email"));
        assertTrue(conflict("23505", "PUBLIC.UQ_USERS_EMAIL_INDEX_4"));
        assertFalse(conflict("23505", "users_pkey"));
        assertFalse(conflict("23505", "uq_other_email"));
        assertFalse(conflict("23505", null));
        assertFalse(conflict("23503", "uq_users_email"));
        assertFalse(conflict("22001", "uq_users_email"));
        assertFalse(RegistrationEmailConflict.isEmailConflict(new DataIntegrityViolationException("unrelated")));
    }
    private boolean conflict(String state, String constraint) {
        return RegistrationEmailConflict.isEmailConflict(new DataIntegrityViolationException("safe test",
                new ConstraintViolationException("safe test", new SQLException("not inspected", state), constraint)));
    }
}
