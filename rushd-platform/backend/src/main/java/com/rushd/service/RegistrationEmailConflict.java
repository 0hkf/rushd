package com.rushd.service;

import org.hibernate.exception.ConstraintViolationException;

/** Recognize only the existing users.email unique constraint, never arbitrary integrity failures. */
final class RegistrationEmailConflict {
    private RegistrationEmailConflict() {}

    static boolean isEmailConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && "23505".equals(constraint.getSQLState())) {
                String name = constraint.getConstraintName();
                if (name == null) return false;
                // PostgreSQL reports the declared constraint name. H2 reports
                // its generated backing index, optionally followed by SQL detail.
                String identifier = name.replace("\"", "").split(" ", 2)[0];
                return identifier.equals("uq_users_email")
                        || identifier.matches("PUBLIC\\.UQ_USERS_EMAIL_INDEX_[0-9A-F]+");
            }
        }
        return false;
    }
}
