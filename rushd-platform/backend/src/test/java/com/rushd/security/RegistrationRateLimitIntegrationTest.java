package com.rushd.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "security.auth.registration-rate-limit.requests=5",
        "security.auth.registration-rate-limit.window=1m",
        "security.auth.registration-rate-limit.max-clients=1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RegistrationRateLimitIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void invalidBodiesCountButCsrfRejectionsDoNotAndOtherQuotasStayIndependent() throws Exception {
        for (int i = 0; i < 7; i++) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/register").with(csrf()).header("X-Forwarded-For", "fake-" + i)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/register").with(csrf()).header("X-Forwarded-For", "different")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        // A new remote address is rejected when the registration-only map is full.
        mvc.perform(post("/api/auth/register").with(csrf()).with(request -> {
                    request.setRemoteAddr("192.0.2.20");
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/register").with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}
