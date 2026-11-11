package com.rushd.drafts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Private experimental API; the backing workflow is disabled by default. */
@RestController
@RequestMapping("/api/admin/property-drafts")
@PreAuthorize("hasRole('ADMIN')")
public class DraftController {
    private static final int MAX_REQUEST_BYTES = 128 * 1024;
    private final DraftWorkflow workflow;
    private final JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final ObjectMapper parser = new ObjectMapper(factory);

    public record Summary(String id, String kind, String propertyId, String revision, java.time.Instant updatedAt) { }
    public DraftController(DraftWorkflow workflow) { this.workflow = workflow; }

    @GetMapping("/context/{propertyId}")
    public ResponseEntity<DraftWorkflow.EditContext> context(Authentication auth, @PathVariable String propertyId) {
        return privateResponse(workflow.context(auth.getName(), positiveLong(propertyId)));
    }

    @GetMapping
    public ResponseEntity<List<Summary>> list(Authentication auth, @RequestParam(defaultValue = "20") int limit,
                                               @RequestParam(defaultValue = "0") int offset) {
        return privateResponse(workflow.list(auth.getName(), limit, offset).stream()
                .map(d -> new Summary(d.id(), d.kind(), d.propertyId(), d.revision(), d.updatedAt())).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DraftWorkflow.DraftView> get(Authentication auth, @PathVariable String id) {
        return privateResponse(workflow.get(auth.getName(), uuid(id)));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<DraftWorkflow.OperationResult> result(Authentication auth, @PathVariable String id) {
        return privateResponse(workflow.result(auth.getName(), uuid(id)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftWorkflow.DraftView> begin(Authentication auth, HttpServletRequest request) {
        JsonNode body = body(request, Set.of("id", "kind", "propertyId", "baseFingerprint", "payload"));
        String propertyId = optionalText(body, "propertyId"), base = optionalText(body, "baseFingerprint");
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(workflow.begin(auth.getName(),
                uuid(text(body, "id")), text(body, "kind"), propertyId == null ? null : positiveLong(propertyId),
                base, object(body, "payload").toString()));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftWorkflow.DraftView> update(Authentication auth, @PathVariable String id, HttpServletRequest request) {
        JsonNode body = body(request, Set.of("revision", "payload"));
        return privateResponse(workflow.update(auth.getName(), uuid(id), nonNegativeLong(text(body, "revision")), object(body, "payload").toString()));
    }

    @DeleteMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable String id, HttpServletRequest request) {
        JsonNode body = body(request, Set.of("revision", "operationId"));
        workflow.delete(auth.getName(), uuid(id), nonNegativeLong(text(body, "revision")), uuid(text(body, "operationId")));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/{id}/conflicts")
    public ResponseEntity<List<String>> conflicts(Authentication auth, @PathVariable String id) {
        return privateResponse(workflow.conflicts(auth.getName(), uuid(id)));
    }

    @GetMapping("/{id}/conflicts/{conflictId}")
    public ResponseEntity<DraftWorkflow.ConflictView> conflict(Authentication auth, @PathVariable String id, @PathVariable String conflictId) {
        return privateResponse(workflow.conflict(auth.getName(), uuid(id), uuid(conflictId)));
    }

    @PostMapping(value = "/{id}/conflicts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftWorkflow.ConflictView> preserve(Authentication auth, @PathVariable String id, HttpServletRequest request) {
        JsonNode body = body(request, Set.of("conflictId", "localPayload"));
        return privateResponse(workflow.preserve(auth.getName(), uuid(id), uuid(text(body, "conflictId")), object(body, "localPayload").toString()));
    }

    @PostMapping(value = "/{id}/conflicts/{conflictId}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftWorkflow.DraftView> resolve(Authentication auth, @PathVariable String id, @PathVariable String conflictId, HttpServletRequest request) {
        JsonNode body = body(request, Set.of("choices"));
        return privateResponse(workflow.resolve(auth.getName(), uuid(id), uuid(conflictId), object(body, "choices")));
    }

    @PostMapping(value = "/{id}/commit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftWorkflow.CommitResult> commit(Authentication auth, @PathVariable String id, HttpServletRequest request) {
        JsonNode body = body(request, Set.of("revision", "operationId", "property"));
        return privateResponse(workflow.commit(auth.getName(), uuid(id), nonNegativeLong(text(body, "revision")),
                uuid(text(body, "operationId")), object(body, "property")));
    }

    @ExceptionHandler(DraftFailure.class)
    public ResponseEntity<ErrorResponse> error(DraftFailure ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).cacheControl(CacheControl.noStore()).body(
                new ErrorResponse(ex.status(), HttpStatus.valueOf(ex.status()).getReasonPhrase(), ex.getMessage(), request.getRequestURI()));
    }

    private JsonNode body(HttpServletRequest request, Set<String> allowed) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
            if (bytes.length > MAX_REQUEST_BYTES) throw new DraftFailure(413, "Draft request is too large");
            try (JsonParser reader = factory.createParser(bytes)) {
                JsonNode node = parser.readTree(reader);
                if (node == null || !node.isObject() || reader.nextToken() != null) throw invalid();
                var names = node.fieldNames();
                while (names.hasNext()) if (!allowed.contains(names.next())) throw invalid();
                return node;
            }
        } catch (IOException ex) { throw invalid(); }
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) throw invalid();
        return value.textValue();
    }
    private static String optionalText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }
    private static JsonNode object(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isObject()) throw invalid();
        return value;
    }
    private static UUID uuid(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw invalid();
        return UUID.fromString(value);
    }
    private static long positiveLong(String value) {
        long number = nonNegativeLong(value);
        if (number == 0) throw invalid();
        return number;
    }
    private static long nonNegativeLong(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(value); } catch (NumberFormatException ex) { throw invalid(); }
    }
    private static DraftFailure invalid() { return new DraftFailure(400, "Invalid draft request"); }
    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
