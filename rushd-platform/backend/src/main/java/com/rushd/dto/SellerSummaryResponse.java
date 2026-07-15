package com.rushd.dto;

import com.rushd.entity.Role;

public class SellerSummaryResponse {

    private final Long id;
    private final String name;
    private final Role role;

    public SellerSummaryResponse(Long id, String name, Role role) {
        this.id = id;
        this.name = name;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }
}
