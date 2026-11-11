package com.rushd.favorites;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.*;
import com.rushd.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "rushd.favorites.enabled=true")
@AutoConfigureMockMvc
class FavoritesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired FavoriteService service;
    @Autowired Favorites favorites;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    User admin, buyer, other, seller;
    Long active, sold, draft, inactive;
    final List<Long> ownedProperties = new ArrayList<>();
    @BeforeEach void fixture() {
        admin = newUser(Role.ADMIN); buyer = newUser(Role.BUYER); other = newUser(Role.BUYER); seller = newUser(Role.SELLER);
        active = property(PropertyStatus.ACTIVE); sold = property(PropertyStatus.SOLD); draft = property(PropertyStatus.DRAFT); inactive = property(PropertyStatus.INACTIVE);
    }
    User newUser(Role role) {
        var value = new User(); value.setName("Synthetic P13"); value.setEmail("p13-" + UUID.randomUUID() + "@example.invalid");
        value.setPassword("unused-test-only"); value.setRole(role); return users.saveAndFlush(value);
    }
    Long property(PropertyStatus status) {
        var value = new Property(); value.setPublisher(admin); value.setTitle("P13 " + status); value.setType(PropertyType.LAND);
        value.setCity("مكة"); value.setDistrict("النوارية"); value.setPrice(new BigDecimal("9007199254740993.01"));
        value.setArea(new BigDecimal("500.25")); value.setStatus(status); properties.saveAndFlush(value); ownedProperties.add(value.getId()); return value.getId();
    }
    @AfterEach void cleanup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (User value : List.of(admin,buyer,other,seller)) jdbc.update("delete from property_favorites where user_id=?",value.getId());
            for (Long id : ownedProperties) properties.deleteById(id);
            properties.flush();
            for (User value : List.of(buyer,other,seller,admin)) users.deleteById(value.getId());
            users.flush();
        });
        ownedProperties.clear();
    }
    @Test void repeatedPutIs204AndKeepsOneRowAndOriginalTimestamp() throws Exception {
        mvc.perform(put("/api/me/favorites/{id}",active).with(user(buyer.getEmail())).with(csrf())).andExpect(status().isNoContent());
        var before = service.list(buyer.getEmail(),0,12).getContent().get(0);
        service.add(buyer.getEmail(),active.toString());
        var after = service.list(buyer.getEmail(),0,12);
        assertEquals(1,after.getTotalElements()); assertEquals(before.createdAt(),after.getContent().get(0).createdAt());
    }
    @Test void activeAndSoldReturnPrecisePublicSummaryWithoutAccountData() throws Exception {
        service.add(buyer.getEmail(), active.toString()); service.add(buyer.getEmail(), sold.toString());
        mvc.perform(get("/api/me/favorites").with(user(buyer.getEmail())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].propertyId").value(sold.toString()))
                .andExpect(jsonPath("$.content[0].property.price").value("9007199254740993.01"))
                .andExpect(jsonPath("$.content[0].property.area").value("500.25"))
                .andExpect(jsonPath("$.content[0].property.status").value("SOLD"))
                .andExpect(jsonPath("$.content[0].property.publisherId").doesNotExist())
                .andExpect(jsonPath("$.content[0].property.publisherName").doesNotExist())
                .andExpect(jsonPath("$.content[0].property.email").doesNotExist());
    }
    @Test void hiddenFavoriteHasNoPropertyFactsAndReappearsWithCurrentValues() throws Exception {
        service.add(buyer.getEmail(),active.toString());
        new TransactionTemplate(transactions).executeWithoutResult(status -> { var p=properties.findById(active).orElseThrow();p.setStatus(PropertyStatus.DRAFT);p.setTitle("SECRET-P13"); });
        String body=mvc.perform(get("/api/me/favorites").with(user(buyer.getEmail()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].available").value(false)).andReturn().getResponse().getContentAsString();
        var row=json.readTree(body).get("content").get(0); assertTrue(row.get("property").isNull()); assertEquals(4,row.size());
        assertFalse(body.contains("SECRET-P13"));assertFalse(body.contains("DRAFT"));assertFalse(body.contains("9007199254740993"));
        assertThrows(FavoriteFailure.class,()->service.add(admin.getEmail(),active.toString()));
        new TransactionTemplate(transactions).executeWithoutResult(status -> properties.findById(active).orElseThrow().setStatus(PropertyStatus.ACTIVE));
        assertEquals("SECRET-P13",service.list(buyer.getEmail(),0,12).getContent().get(0).property().title());
    }
    @Test void inactiveFavoriteCanBeRemovedAndRepeatedDeleteIs204() throws Exception {
        service.add(buyer.getEmail(),active.toString());
        new TransactionTemplate(transactions).executeWithoutResult(status -> properties.findById(active).orElseThrow().setStatus(PropertyStatus.INACTIVE));
        for(int i=0;i<2;i++) mvc.perform(delete("/api/me/favorites/{id}",active).with(user(buyer.getEmail())).with(csrf())).andExpect(status().isNoContent());
        assertEquals(0,service.list(buyer.getEmail(),0,12).getTotalElements());
    }
    @Test void cannotReadOrRemoveAnotherAccountsFavoritesBySpoofingUserId() throws Exception {
        service.add(buyer.getEmail(),active.toString());
        mvc.perform(get("/api/me/favorites").param("userId",buyer.getId().toString()).with(user(other.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(delete("/api/me/favorites/{id}",active).param("userId",buyer.getId().toString()).with(user(other.getEmail())).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put("/api/me/favorites/{id}",sold).contentType("application/json").content("{\"userId\":"+buyer.getId()+"}").with(user(other.getEmail())).with(csrf())).andExpect(status().isNoContent());
        assertEquals(List.of(active.toString()),service.status(buyer.getEmail(),List.of(active.toString(),sold.toString())));
        assertEquals(List.of(sold.toString()),service.status(other.getEmail(),List.of(active.toString(),sold.toString())));
    }
    @Test void legacySellerAndAdminHaveOnlyTheirOwnFavorites() {
        for(User value:List.of(admin,seller)) { service.add(value.getEmail(),active.toString());assertEquals(1,service.list(value.getEmail(),0,12).getTotalElements()); }
        assertEquals(0,service.list(buyer.getEmail(),0,12).getTotalElements());
    }
    @Test void privateAndMissingPropertiesAllReturn404EvenForAdmin() throws Exception {
        for(Long id:List.of(draft,inactive,Long.MAX_VALUE)) for(User value:List.of(admin,buyer))
            mvc.perform(put("/api/me/favorites/{id}",id).with(user(value.getEmail())).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void statusBatchIsBoundedDeduplicatedAndScoped() throws Exception {
        service.add(buyer.getEmail(),active.toString());service.add(other.getEmail(),sold.toString());
        mvc.perform(get("/api/me/favorites/status").param("propertyIds",active+","+sold+","+active).with(user(buyer.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.favoritePropertyIds.length()").value(1))
                .andExpect(jsonPath("$.favoritePropertyIds[0]").value(active.toString()));
        assertThrows(FavoriteFailure.class,()->service.status(buyer.getEmail(),Collections.nCopies(101,active.toString())));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","01","1.5","1e3","9223372036854775808","bad"})
    void invalidIdentifiersReturn400(String id) throws Exception {
        mvc.perform(put("/api/me/favorites/{id}",id).with(user(buyer.getEmail())).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/me/favorites/{id}",id).with(user(buyer.getEmail())).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/favorites/status").param("propertyIds",id).with(user(buyer.getEmail()))).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"page=-1","size=0","size=101","page=bad","size=1.2"})
    void invalidPagesReturn400(String query) throws Exception {
        mvc.perform(get("/api/me/favorites?"+query).with(user(buyer.getEmail()))).andExpect(status().isBadRequest());
    }
    @Test void authAndCsrfRemainRequired() throws Exception {
        mvc.perform(get("/api/me/favorites")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/favorites/status").param("propertyIds",active.toString())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/favorites/{id}",active).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/favorites/{id}",active).with(user(buyer.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/me/favorites/{id}",active).with(user(buyer.getEmail())).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
    }
    @Test void paginationUsesCreatedAtThenPropertyIdAndDoesNotChangeOnDuplicateSave() {
        service.add(buyer.getEmail(),active.toString());service.add(buyer.getEmail(),sold.toString());
        jdbc.update("update property_favorites set created_at=? where user_id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00Z")),buyer.getId());
        service.add(buyer.getEmail(),active.toString());
        assertEquals(sold.toString(),service.list(buyer.getEmail(),0,1).getContent().get(0).propertyId());
        assertEquals(active.toString(),service.list(buyer.getEmail(),1,1).getContent().get(0).propertyId());
        assertTrue(service.list(buyer.getEmail(),9,1).getContent().isEmpty());
    }
    @Test void concurrentDuplicatePutsNeverCreateDuplicateOr500() throws Exception {
        var pool=Executors.newFixedThreadPool(4); var start=new CountDownLatch(1);
        try {
            var tasks=new ArrayList<Future<?>>(); for(int i=0;i<4;i++)tasks.add(pool.submit(()->{try{start.await();service.add(buyer.getEmail(),active.toString());}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}));
            start.countDown();for(var task:tasks)task.get(15,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertEquals(1,service.list(buyer.getEmail(),0,12).getTotalElements());
    }
    @Test void longIdentifierIsReturnedAsExactText() throws Exception {
        long id=9007199254740993L;
        jdbc.update("insert into properties(id,seller_id,title,type,city,district,area,price,status,listing_type) values(?,?,?,?,?,?,?,?,?,?)",id,admin.getId(),"P13 large id","LAND","مكة","النوارية",new BigDecimal("500.25"),new BigDecimal("9007199254740993.01"),"ACTIVE","SALE");
        ownedProperties.add(id);service.add(buyer.getEmail(),Long.toString(id));
        mvc.perform(get("/api/me/favorites").with(user(buyer.getEmail()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].propertyId").value("9007199254740993"))
                .andExpect(jsonPath("$.content[0].property.id").value("9007199254740993"));
    }
    @Test void deletingUnrelatedAccountOrPropertyDoesNotDeleteOtherFavorites() {
        service.add(buyer.getEmail(),active.toString());service.add(other.getEmail(),sold.toString());
        jdbc.update("delete from properties where id=?",sold);ownedProperties.remove(sold);
        assertEquals(0,service.list(other.getEmail(),0,12).getTotalElements());assertEquals(1,service.list(buyer.getEmail(),0,12).getTotalElements());
        jdbc.update("delete from users where id=?",buyer.getId());
        assertFalse(favorites.existsById(new FavoriteId(buyer.getId(),active)));
    }
}
