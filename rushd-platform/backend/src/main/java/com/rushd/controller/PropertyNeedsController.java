package com.rushd.controller;

import com.rushd.dto.PropertyNeedsRequest;
import com.rushd.dto.PropertyNeedsResponse;
import com.rushd.dto.PropertyMatchesResponse;
import com.rushd.exception.InvalidPropertyQueryException;
import com.rushd.service.PropertyMatchingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Existing security requires authentication and CSRF. Validation remains independent of matching. */
@RestController
@RequestMapping("/api/property-needs")
public class PropertyNeedsController {
    private final PropertyMatchingService matching;

    public PropertyNeedsController(PropertyMatchingService matching) {
        this.matching = matching;
    }

    @PostMapping("/validate")
    public ResponseEntity<PropertyNeedsResponse> validate(@Valid @RequestBody PropertyNeedsRequest request) {
        return ResponseEntity.ok(PropertyNeedsResponse.from(request));
    }

    @PostMapping("/matches")
    public ResponseEntity<PropertyMatchesResponse> matches(
            @Valid @RequestBody PropertyNeedsRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam MultiValueMap<String, String> parameters) {
        if (page < 0 || size < 1 || size > 20) {
            throw new InvalidPropertyQueryException("page must be nonnegative and size must be between 1 and 20");
        }
        if (parameters.entrySet().stream().anyMatch(entry ->
                (!entry.getKey().equals("page") && !entry.getKey().equals("size")) || entry.getValue().size() != 1)) {
            throw new InvalidPropertyQueryException("Only one page and size parameter are supported");
        }
        return ResponseEntity.ok(matching.matches(request, page, size));
    }
}
