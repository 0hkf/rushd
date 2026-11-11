package com.rushd.favorites;

import com.rushd.dto.PageResponse;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/me/favorites")
public class FavoriteController {
    private final FavoriteService service;
    public FavoriteController(FavoriteService service) { this.service = service; }
    public record Status(List<String> favoritePropertyIds) {}
    @GetMapping
    public ResponseEntity<PageResponse<FavoriteResponse>> list(Authentication auth, @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(auth.getName(), page, size));
    }
    @GetMapping("/status")
    public ResponseEntity<Status> status(Authentication auth, @RequestParam List<String> propertyIds) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status(service.status(auth.getName(), propertyIds)));
    }
    @PutMapping("/{propertyId}")
    public ResponseEntity<Void> add(Authentication auth, @PathVariable String propertyId) {
        service.add(auth.getName(), propertyId); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @DeleteMapping("/{propertyId}")
    public ResponseEntity<Void> remove(Authentication auth, @PathVariable String propertyId) {
        service.remove(auth.getName(), propertyId); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
