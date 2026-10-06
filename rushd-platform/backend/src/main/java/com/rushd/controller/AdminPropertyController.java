package com.rushd.controller;

import com.rushd.dto.PageResponse;
import com.rushd.dto.PropertyResponse;
import com.rushd.service.PropertyService;
import com.rushd.exception.InvalidPropertyQueryException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/admin/properties")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPropertyController {
    private final PropertyService propertyService;

    public AdminPropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PropertyResponse>> listManagedProperties(
            @RequestParam(defaultValue = "0") int page) {
        if (page < 0) {
            throw new InvalidPropertyQueryException("page cannot be negative");
        }
        return ResponseEntity.ok(PageResponse.from(propertyService.listManagedProperties(
                PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")))));
    }
}
