package com.rushd.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

@Component
public class SecurityErrorWriter {
    private final ObjectMapper mapper;
    public SecurityErrorWriter(ObjectMapper mapper) { this.mapper = mapper; }
    public void write(HttpServletRequest request, HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), Map.of("timestamp", java.time.Instant.now().toString(),
                "status", status, "error", org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase(),
                "message", message, "path", request.getRequestURI()));
    }
}
