package com.rushd.market;

import com.rushd.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/admin/market")
@PreAuthorize("hasRole('ADMIN')")
public class MarketController {
    private static final Set<String> STATE=Set.of("expectedRevision","archived","reason");
    private final MarketWorkflow workflow;
    public MarketController(MarketWorkflow workflow) { this.workflow=workflow; }
    @GetMapping("/sources")
    public ResponseEntity<?> sources(Authentication a,@RequestParam(defaultValue="20") int limit,@RequestParam(defaultValue="0") int offset) { return ok(workflow.sources(a.getName(),limit,offset)); }
    @GetMapping("/sources/{id}")
    public ResponseEntity<?> source(Authentication a,@PathVariable String id) { return ok(workflow.source(a.getName(),MarketInput.uuid(id))); }
    @PostMapping(value="/sources",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSource(Authentication a,HttpServletRequest request) { workflow.authorize(a.getName()); return ok(workflow.createSource(a.getName(),MarketInput.body(request,MarketInput.SOURCE))); }
    @PutMapping(value="/sources/{id}/original",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> upload(Authentication a,@PathVariable String id,HttpServletRequest request) throws IOException {
        workflow.authorize(a.getName());
        return ok(workflow.upload(a.getName(),MarketInput.uuid(id),request.getInputStream()));
    }
    @GetMapping("/sources/{id}/original")
    public ResponseEntity<byte[]> download(Authentication a,@PathVariable String id) {
        var uuid=MarketInput.uuid(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"market-"+uuid+".xlsx\"")
                .body(workflow.download(a.getName(),uuid));
    }
    @PutMapping(value="/sources/{id}/state",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sourceState(Authentication a,@PathVariable String id,HttpServletRequest r) { workflow.authorize(a.getName()); return ok(workflow.sourceState(a.getName(),MarketInput.uuid(id),MarketInput.body(r,STATE))); }
    @GetMapping("/references")
    public ResponseEntity<?> references(Authentication a,@RequestParam(defaultValue="20") int limit,@RequestParam(defaultValue="0") int offset) { return ok(workflow.references(a.getName(),limit,offset)); }
    @GetMapping("/references/{id}")
    public ResponseEntity<?> reference(Authentication a,@PathVariable String id) { return ok(workflow.reference(a.getName(),MarketInput.uuid(id))); }
    @PostMapping(value="/references",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createReference(Authentication a,HttpServletRequest r) { workflow.authorize(a.getName()); return ok(workflow.createReference(a.getName(),MarketInput.body(r,MarketInput.fields(MarketInput.FACTS,"id","sourceId","sourceReference")))); }
    @PutMapping(value="/references/{id}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateReference(Authentication a,@PathVariable String id,HttpServletRequest r) { workflow.authorize(a.getName()); return ok(workflow.updateReference(a.getName(),MarketInput.uuid(id),MarketInput.body(r,MarketInput.fields(MarketInput.FACTS,"expectedRevision","changeReason")))); }
    @PutMapping(value="/references/{id}/state",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> referenceState(Authentication a,@PathVariable String id,HttpServletRequest r) { workflow.authorize(a.getName()); return ok(workflow.referenceState(a.getName(),MarketInput.uuid(id),MarketInput.body(r,STATE))); }
    @GetMapping("/references/{id}/versions")
    public ResponseEntity<?> versions(Authentication a,@PathVariable String id,@RequestParam(defaultValue="20") int limit,@RequestParam(defaultValue="0") int offset) { return ok(workflow.versions(a.getName(),MarketInput.uuid(id),limit,offset)); }
    @ExceptionHandler(MarketFailure.class)
    public ResponseEntity<ErrorResponse> failure(MarketFailure ex,HttpServletRequest r) { return ResponseEntity.status(ex.status()).cacheControl(CacheControl.noStore()).body(new ErrorResponse(ex.status(),HttpStatus.valueOf(ex.status()).getReasonPhrase(),ex.getMessage(),r.getRequestURI())); }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> duplicate(HttpServletRequest r) { return failure(MarketFailure.conflict(),r); }
    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
