package com.rushd.images;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import java.security.Principal;
import java.util.*;

@RestController
public class PropertyImageController {
    private final PropertyImageService service;
    private final ImageProcessor processor;
    public PropertyImageController(PropertyImageService service, ImageProcessor processor) { this.service = service; this.processor = processor; }
    @GetMapping("/api/properties/{propertyId}/images")
    public ResponseEntity<ImageManifest> manifest(@PathVariable Long propertyId, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.manifest(propertyId, email(principal)));
    }
    @GetMapping("/api/properties/{propertyId}/images/{imageId}/{variant}")
    public ResponseEntity<byte[]> content(@PathVariable Long propertyId, @PathVariable UUID imageId,
                                         @PathVariable String variant, Principal principal) {
        byte[] bytes = service.content(propertyId, imageId, variant, email(principal)).bytes();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=\"" + imageId + "-" + variant + ".jpg\"")
                .contentLength(bytes.length).body(bytes);
    }
    @PostMapping(value = "/api/admin/properties/{propertyId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImageManifest> upload(@PathVariable Long propertyId, MultipartHttpServletRequest request,
            @RequestParam UUID uploadToken, @RequestParam boolean rightsConfirmed,
            @RequestParam PropertyImage.RightsBasis rightsBasis,
            @RequestParam(required = false) String sourceReference, @RequestParam(required = false) String altText, Principal principal) {
        if (request.getMultiFileMap().size() != 1 || !request.getMultiFileMap().containsKey("file")
                || request.getFiles("file").size() != 1) throw ImageFailure.invalid("Exactly one file part named file is required");
        var allowed = Set.of("uploadToken", "rightsConfirmed", "rightsBasis", "sourceReference", "altText", "_csrf");
        if (request.getParameterMap().entrySet().stream().anyMatch(entry -> !allowed.contains(entry.getKey()) || entry.getValue().length != 1))
            throw ImageFailure.invalid("Unknown or duplicate multipart field");
        var file = request.getFile("file");
        var result = service.upload(propertyId, uploadToken, processor.source(file), file.getOriginalFilename(), file.getContentType(),
                rightsConfirmed, rightsBasis, sourceReference, altText, email(principal));
        return ResponseEntity.status(result.created() ? 201 : 200).cacheControl(CacheControl.noStore()).body(result.manifest());
    }
    @PutMapping("/api/admin/properties/{propertyId}/images") @PreAuthorize("hasRole('ADMIN')")
    public ImageManifest update(@PathVariable Long propertyId, @RequestBody ImageManifestRequest request, Principal principal) {
        return service.update(propertyId, request, email(principal));
    }
    @DeleteMapping("/api/admin/properties/{propertyId}/images/{imageId}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long propertyId, @PathVariable UUID imageId,
            @RequestParam String expectedRevision, @RequestParam(required = false) UUID replacementPrimaryImageId, Principal principal) {
        return ResponseEntity.status(service.delete(propertyId, imageId, expectedRevision, replacementPrimaryImageId, email(principal)) ? 204 : 202).build();
    }
    private static String email(Principal principal) { return principal == null ? null : principal.getName(); }
}
