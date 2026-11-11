package com.rushd.images;

import jakarta.servlet.http.*;
import org.springframework.security.web.csrf.*;
import java.util.function.Supplier;

/** Avoid CsrfFilter.getParameter triggering multipart parsing before upload resource reservation. */
public final class ImageCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final XorCsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();
    @Override public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
        delegate.handle(request,response,token);
    }
    @Override public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
        if (request.getMethod().equals("POST") && request.getRequestURI().substring(request.getContextPath().length())
                .matches("/api/admin/properties/[^/]+/images(?:/[^/]+)?/?")) {
            // Image uploads require the existing X-XSRF-TOKEN header. Other routes retain parameter support.
            return delegate.resolveCsrfTokenValue(new HttpServletRequestWrapper(request) {
                @Override public String getParameter(String name) {
                    return name.equals(token.getParameterName()) ? null : super.getParameter(name);
                }
            }, token);
        }
        return delegate.resolveCsrfTokenValue(request,token);
    }
}
