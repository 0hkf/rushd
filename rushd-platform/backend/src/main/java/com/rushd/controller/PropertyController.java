package com.rushd.controller;

import com.rushd.dto.CreatePropertyRequest;
import com.rushd.dto.PageResponse;
import com.rushd.dto.PropertyResponse;
import com.rushd.entity.PropertyType;
import com.rushd.exception.InvalidPropertyQueryException;
import com.rushd.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Set;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "title", "type", "city", "district", "area", "price",
            "streetWidth", "facade", "purpose", "status", "createdAt", "updatedAt"
    );

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PropertyResponse>> listProperties(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) PropertyType type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        Page<PropertyResponse> properties = propertyService.listProperties(
                city, district, type, minPrice, maxPrice, pageable);
        return ResponseEntity.ok(PageResponse.from(properties));
    }

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<PropertyResponse> createProperty(
            @Valid @RequestBody CreatePropertyRequest request,
            Authentication authentication) {
        PropertyResponse response = propertyService.createProperty(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Pageable createPageable(int page, int size, String sort) {
        if (page < 0) {
            throw new InvalidPropertyQueryException("page cannot be negative");
        }
        if (size < 1 || size > 100) {
            throw new InvalidPropertyQueryException("size must be between 1 and 100");
        }

        String[] sortParts = sort.split(",", -1);
        if (sortParts.length < 1 || sortParts.length > 2) {
            throw new InvalidPropertyQueryException("sort must use the format field,direction");
        }

        String sortField = sortParts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new InvalidPropertyQueryException("Unsupported sort field: " + sortField);
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (sortParts.length == 2) {
            direction = Sort.Direction.fromOptionalString(sortParts[1].trim())
                    .orElseThrow(() -> new InvalidPropertyQueryException(
                            "Sort direction must be asc or desc"));
        }

        return PageRequest.of(page, size, Sort.by(direction, sortField));
    }
}
