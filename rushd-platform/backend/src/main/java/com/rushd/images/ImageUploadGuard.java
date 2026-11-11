package com.rushd.images;

import com.rushd.security.SecurityErrorWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Runs after authorization but before DispatcherServlet/multipart parsing. */
@Component
public class ImageUploadGuard extends OncePerRequestFilter {
    private final PrivateImageStorage storage;
    private final SecurityErrorWriter errors;
    public ImageUploadGuard(PrivateImageStorage storage, SecurityErrorWriter errors) { this.storage = storage; this.errors = errors; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !java.util.Set.of("POST","PUT","DELETE").contains(request.getMethod())
                || !request.getRequestURI().substring(request.getContextPath().length())
                .matches("/api/admin/properties/[^/]+/images(?:/[^/]+)?/?");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            errors.write(request, response, 403, "Insufficient permission"); return;
        }
        try { storage.requireEnabled(); }
        catch (ImageFailure e) { errors.write(request,response,e.status(),e.getMessage()); return; }
        if (!request.getMethod().equals("POST")) { chain.doFilter(request,response); return; }
        if (request.getContentLengthLong() > 9 * PrivateImageStorage.MIB) {
            errors.write(request, response, 413, "Multipart request exceeds 9 MiB"); return;
        }
        AutoCloseable reservation;
        try { reservation = storage.reserveUpload(); }
        catch (ImageFailure e) {
            if (e.status() == 429) response.setHeader("Retry-After", "2");
            errors.write(request, response, e.status(), e.getMessage()); return;
        }
        try { chain.doFilter(request, response); }
        finally {
            try { reservation.close(); }
            catch (Exception e) { throw new ServletException("Image reservation release failed"); }
        }
    }
}
