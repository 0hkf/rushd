package com.rushd.controller;

import com.rushd.dto.PropertyNeedsRequest;
import com.rushd.dto.PropertyNeedsResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Existing security requires authentication and CSRF; this endpoint never queries or saves data. */
@RestController
@RequestMapping("/api/property-needs")
public class PropertyNeedsController {
    @PostMapping("/validate")
    public ResponseEntity<PropertyNeedsResponse> validate(@Valid @RequestBody PropertyNeedsRequest request) {
        return ResponseEntity.ok(PropertyNeedsResponse.from(request));
    }
}
