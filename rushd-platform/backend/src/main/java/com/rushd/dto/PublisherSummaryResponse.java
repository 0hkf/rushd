package com.rushd.dto;

import com.rushd.entity.Role;

public record PublisherSummaryResponse(Long id, String name, Role role) {
}
