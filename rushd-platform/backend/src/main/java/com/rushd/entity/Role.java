package com.rushd.entity;

public enum Role {
    BUYER,
    // Legacy database value only. No publishing or public signup privileges.
    @Deprecated
    SELLER,
    ADMIN
}
