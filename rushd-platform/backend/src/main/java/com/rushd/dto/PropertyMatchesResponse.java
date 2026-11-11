package com.rushd.dto;

import java.util.List;

public record PropertyMatchesResponse(
        PropertyNeedsResponse needs,
        List<PropertyMatchResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String rulesVersion,
        String sort) {
}
