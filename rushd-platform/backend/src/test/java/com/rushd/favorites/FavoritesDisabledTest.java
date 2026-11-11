package com.rushd.favorites;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="rushd.favorites.enabled=false") @AutoConfigureMockMvc
class FavoritesDisabledTest {
    @Autowired MockMvc mvc;
    @Test void disabledFeatureReturns503WithoutRequiringAccountOrFavoriteRows() throws Exception {
        mvc.perform(get("/api/me/favorites").with(user("unused@example.invalid"))).andExpect(status().isServiceUnavailable());
        mvc.perform(put("/api/me/favorites/1").with(user("unused@example.invalid")).with(csrf())).andExpect(status().isServiceUnavailable());
        mvc.perform(delete("/api/me/favorites/1").with(user("unused@example.invalid")).with(csrf())).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/me/favorites")).andExpect(status().isUnauthorized());
    }
}
