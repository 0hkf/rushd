package com.rushd.images;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.*;
import com.rushd.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "rushd.images.enabled=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PropertyImagesIntegrationTest {
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.CsrfRequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf().asHeader();
    }
    static final Path ROOT = ImageFixtures.privateRoot();
    @DynamicPropertySource static void config(DynamicPropertyRegistry registry) { registry.add("rushd.images.root",ROOT::toString); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PropertyRepository properties;
    @Autowired UserRepository users;
    @Autowired PropertyImages images;
    @Autowired ImageCollections collections;
    @Autowired PrivateImageStorage storage;
    static PrivateImageStorage ownedStorage;
    @Autowired PlatformTransactionManager manager;
    TransactionTemplate tx;
    Long propertyId, userId;
    final List<Long> owned = new ArrayList<>();
    String email;
    byte[] jpeg;
    @BeforeEach void fixture() throws Exception {
        ownedStorage = storage;
        tx = new TransactionTemplate(manager); jpeg = ImageFixtures.image("jpeg",640,480,false);
        email = "p10-"+UUID.randomUUID()+"@example.invalid";
        tx.executeWithoutResult(status -> {
            var admin = new User(); admin.setName("Synthetic P10"); admin.setEmail(email); admin.setPassword("unused-test-only"); admin.setRole(Role.ADMIN);
            users.saveAndFlush(admin); userId = admin.getId(); propertyId = property(admin, PropertyStatus.ACTIVE);
        });
    }
    Long property(User admin, PropertyStatus status) {
        var property = new Property(); property.setPublisher(admin); property.setTitle("Synthetic image fixture");
        property.setType(PropertyType.LAND); property.setCity("مكة"); property.setDistrict("النوارية"); property.setArea(new BigDecimal("500.25"));
        property.setPrice(new BigDecimal("9007199254740993.01")); property.setStatus(status); property.setPurpose(PropertyPurpose.RESIDENTIAL);
        properties.saveAndFlush(property); owned.add(property.getId()); return property.getId();
    }
    @AfterEach void cleanup() {
        tx.executeWithoutResult(status -> {
            for (Long id : owned) {
                var collection = collections.findById(id);
                collection.ifPresent(c -> { c.primaryImageId=null; collections.saveAndFlush(c); });
                var rows=images.findByPropertyId(id); rows.forEach(image -> storage.remove(image.storageKey));
                images.deleteAll(rows); images.flush(); collections.deleteById(id); collections.flush();
                properties.deleteById(id); properties.flush();
            }
            users.deleteById(userId); users.flush();
        });
    }
    @AfterAll static void removeRoot() throws Exception {
        if (ownedStorage != null) ownedStorage.close();
        // Test-owned directory only; no application/user path is supplied to this cleanup.
        try (var entries=Files.list(ROOT)) { for (Path file:entries.toList()) Files.delete(file); }
        Files.delete(ROOT);
    }
    ResultActions upload(UUID token) throws Exception {
        return mvc.perform(multipart("/api/admin/properties/{id}/images",propertyId)
                .file(new MockMultipartFile("file","synthetic.jpg","image/jpeg",jpeg))
                .param("uploadToken",token.toString()).param("rightsConfirmed","true").param("rightsBasis","OWNED")
                .with(user(email).roles("ADMIN")).with(csrf()));
    }
    ImageManifest uploaded() throws Exception {
        return mapper.readValue(upload(UUID.randomUUID()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray(), ImageManifest.class);
    }
    ResultActions update(ImageManifestRequest request) throws Exception {
        return mvc.perform(put("/api/admin/properties/{id}/images",propertyId).contentType("application/json")
                .content(mapper.writeValueAsBytes(request)).with(user(email).roles("ADMIN")).with(csrf()));
    }
    @Test void emptyLegacyManifestDoesNotCreateMetadataAndPropertyStillExact() throws Exception {
        mvc.perform(get("/api/properties/{id}/images",propertyId)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value("0"))
                .andExpect(jsonPath("$.primaryImageId").value((Object)null)).andExpect(jsonPath("$.images").isEmpty());
        assertTrue(collections.findById(propertyId).isEmpty());
        mvc.perform(get("/api/properties/{id}",propertyId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value("9007199254740993.01")).andExpect(jsonPath("$.primaryImage").value((Object)null));
    }
    @Test void uploadReadPrimaryAndPrivateAttestation() throws Exception {
        var manifest=uploaded(); UUID id=manifest.primaryImageId(); assertEquals("1",manifest.revision());
        assertEquals(1,manifest.images().size()); assertEquals(id,manifest.images().get(0).id());
        for(String variant:List.of("display","thumbnail")) {
            byte[] bytes=mvc.perform(get("/api/properties/{p}/images/{i}/{v}",propertyId,id,variant))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                    .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes)));
        }
        mvc.perform(get("/api/properties/{id}",propertyId)).andExpect(jsonPath("$.primaryImage.id").value(id.toString()));
        mvc.perform(get("/api/properties")).andExpect(jsonPath("$.content[0].primaryImage.id").value(id.toString()));
        var row=images.findById(id).orElseThrow(); assertEquals(userId,row.attestedBy); assertEquals(PropertyImage.RightsBasis.OWNED,row.rightsBasis);
        try(var files=Files.list(ROOT)) { assertEquals(2,files.filter(p -> p.toString().endsWith(".jpg")).count()); }
        mvc.perform(get("/api/properties/{p}/images",propertyId)).andExpect(jsonPath("$.images[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.images[0].rightsBasis").doesNotExist()).andExpect(jsonPath("$.images[0].attestedBy").doesNotExist());
    }
    @ParameterizedTest @EnumSource(PropertyStatus.class)
    void visibilityMatchesPropertyStatusForManifestAndBothVariants(PropertyStatus state) throws Exception {
        UUID id=uploaded().primaryImageId(); tx.executeWithoutResult(s -> {var p=properties.findById(propertyId).orElseThrow();p.setStatus(state);properties.saveAndFlush(p);});
        boolean visible=state==PropertyStatus.ACTIVE||state==PropertyStatus.SOLD;
        for(String path:List.of("/images","/images/"+id+"/display","/images/"+id+"/thumbnail")) {
            mvc.perform(get("/api/properties/"+propertyId+path)).andExpect(status().is(visible?200:404));
            mvc.perform(get("/api/properties/"+propertyId+path).with(user(email).roles("ADMIN"))).andExpect(status().isOk());
            mvc.perform(get("/api/properties/"+propertyId+path).with(user("buyer@example.invalid").roles("BUYER"))).andExpect(status().is(visible?200:404));
        }
    }
    @ParameterizedTest @ValueSource(strings={"BUYER","SELLER"})
    void nonAdminsCannotUploadUpdateOrDelete(String role) throws Exception {
        mvc.perform(multipart("/api/admin/properties/{id}/images",propertyId).with(user(email).roles(role)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/properties/{id}/images",propertyId).contentType("application/json").content("{}").with(user(email).roles(role)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/properties/{p}/images/{i}",propertyId,UUID.randomUUID()).param("expectedRevision","0").with(user(email).roles(role)).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void anonymousAndMissingOrBadCsrfRejectedBeforeUpload() throws Exception {
        mvc.perform(multipart("/api/admin/properties/{id}/images",propertyId).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/properties/{id}/images",propertyId).with(user(email).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/properties/{id}/images",propertyId).with(user(email).roles("ADMIN")).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        assertTrue(images.findByPropertyId(propertyId).isEmpty());
    }
    @Test void idempotencyConflictAndDeletedTokenNeverResurrects() throws Exception {
        UUID token=UUID.randomUUID(); var first=mapper.readValue(upload(token).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray(),ImageManifest.class);
        upload(token).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value("1"));
        jpeg=ImageFixtures.image("jpeg",800,600,false); upload(token).andExpect(status().isConflict());
        jpeg=ImageFixtures.image("jpeg",640,480,false);
        mvc.perform(delete("/api/admin/properties/{p}/images/{i}",propertyId,first.primaryImageId()).param("expectedRevision","1").with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        upload(token).andExpect(status().isConflict()); assertEquals(1,images.findByPropertyId(propertyId).size());
        mvc.perform(delete("/api/admin/properties/{p}/images/{i}",propertyId,first.primaryImageId()).param("expectedRevision","1").with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
    }
    @Test void reorderAltPrimaryStaleAndExplicitReplacement() throws Exception {
        UUID first=uploaded().primaryImageId(); var two=uploaded(); UUID second=two.images().get(1).id(); assertEquals(first,two.primaryImageId());
        update(new ImageManifestRequest("2",first,List.of(new ImageManifestRequest.Item(second,"الصورة الثانية"),new ImageManifestRequest.Item(first,null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value("3")).andExpect(jsonPath("$.primaryImageId").value(first.toString()))
                .andExpect(jsonPath("$.images[0].id").value(second.toString())).andExpect(jsonPath("$.images[0].altText").value("الصورة الثانية"));
        update(new ImageManifestRequest("2",second,List.of(new ImageManifestRequest.Item(second,null),new ImageManifestRequest.Item(first,null)))).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/properties/{p}/images/{i}",propertyId,first).param("expectedRevision","3").with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/properties/{p}/images/{i}",propertyId,first).param("expectedRevision","3").param("replacementPrimaryImageId",second.toString())
                .with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/properties/{p}/images/{i}/display",propertyId,first)).andExpect(status().isNotFound());
        mvc.perform(get("/api/properties/{p}/images",propertyId)).andExpect(jsonPath("$.primaryImageId").value(second.toString())).andExpect(jsonPath("$.images[0].order").value(0));
    }
    @Test void rejectsMissingDuplicateForeignAndInvalidManifestFields() throws Exception {
        UUID id=uploaded().primaryImageId();
        for(var request:List.of(new ImageManifestRequest("1",id,List.of()), new ImageManifestRequest("1",id,List.of(new ImageManifestRequest.Item(id,null),new ImageManifestRequest.Item(id,null))),
                new ImageManifestRequest("1",UUID.randomUUID(),List.of(new ImageManifestRequest.Item(id,null))),new ImageManifestRequest("1",id,List.of(new ImageManifestRequest.Item(UUID.randomUUID(),null))),
                new ImageManifestRequest("1",id,List.of(new ImageManifestRequest.Item(id,"a".repeat(161)))))) update(request).andExpect(status().isBadRequest());
        for(String json:List.of("{}","null","{\"expectedRevision\":1,\"primaryImageId\":null,\"images\":[]}",
                "{\"expectedRevision\":\"1\",\"primaryImageId\":\""+id+"\",\"images\":[{\"id\":\""+id+"\"}]}"))
            mvc.perform(put("/api/admin/properties/{id}/images",propertyId).contentType("application/json").content(json).with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void sameImageIdCannotBeReadOrDeletedThroughAnotherProperty() throws Exception {
        UUID image=uploaded().primaryImageId(); Long other=tx.execute(s -> property(users.findById(userId).orElseThrow(),PropertyStatus.ACTIVE));
        mvc.perform(get("/api/properties/{p}/images/{i}/display",other,image)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/properties/{p}/images/{i}",other,image).param("expectedRevision","0").with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void licensedRightsAndFileCountAreValidated() throws Exception {
        mvc.perform(multipart("/api/admin/properties/{id}/images",propertyId).file(new MockMultipartFile("file","a.jpg","image/jpeg",jpeg))
                .param("uploadToken",UUID.randomUUID().toString()).param("rightsConfirmed","true").param("rightsBasis","LICENSED")
                .with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/properties/{id}/images",propertyId).file(new MockMultipartFile("file","a.jpg","image/jpeg",jpeg))
                .file(new MockMultipartFile("file","b.jpg","image/jpeg",jpeg)).param("uploadToken",UUID.randomUUID().toString())
                .param("rightsConfirmed","true").param("rightsBasis","OWNED").with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void legacySellerPublisherCannotViewPrivateImages() throws Exception {
        UUID id=uploaded().primaryImageId();
        tx.executeWithoutResult(s->{var publisher=users.findById(userId).orElseThrow();publisher.setRole(Role.SELLER);users.saveAndFlush(publisher);
            var property=properties.findById(propertyId).orElseThrow();property.setStatus(PropertyStatus.DRAFT);properties.saveAndFlush(property);});
        for(String suffix:List.of("/images","/images/"+id+"/display","/images/"+id+"/thumbnail"))
            mvc.perform(get("/api/properties/"+propertyId+suffix).with(user(email).roles("SELLER"))).andExpect(status().isNotFound());
    }
    @Test void missingStoredImageDoesNotBreakPropertyFacts() throws Exception {
        UUID id=uploaded().primaryImageId();var row=images.findById(id).orElseThrow();Files.delete(ROOT.resolve(row.storageKey+"-display.jpg"));
        mvc.perform(get("/api/properties/{p}/images/{i}/display",propertyId,id)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/properties/{p}",propertyId)).andExpect(status().isOk()).andExpect(jsonPath("$.price").value("9007199254740993.01"));
    }
    @Test void thirteenthImageRejectedAndNoFileLeak() throws Exception {
        for(int i=0;i<12;i++) uploaded(); upload(UUID.randomUUID()).andExpect(status().isConflict());
        assertEquals(12,images.findByPropertyId(propertyId).size());
        try(var files=Files.list(ROOT)) { assertEquals(24,files.filter(p -> p.toString().endsWith(".jpg")).count()); }
    }
    @Test void concurrentManifestEditsHaveOneWinnerAndOneStaleRevision() throws Exception {
        UUID first=uploaded().primaryImageId();var manifest=uploaded();UUID second=manifest.images().get(1).id();
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);var barrier=new java.util.concurrent.CyclicBarrier(2);
        try {
            var futures=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(UUID chosen:List.of(first,second)) futures.add(workers.submit(()->{
                barrier.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return update(new ImageManifestRequest("2",chosen,List.of(new ImageManifestRequest.Item(first,null),new ImageManifestRequest.Item(second,null))))
                        .andReturn().getResponse().getStatus();
            }));
            var results=new ArrayList<Integer>();for(var future:futures)results.add(future.get(10,java.util.concurrent.TimeUnit.SECONDS));
            Collections.sort(results);assertEquals(List.of(200,409),results);
            mvc.perform(get("/api/properties/{p}/images",propertyId)).andExpect(jsonPath("$.revision").value("3"));
        } finally {workers.shutdownNow();assertTrue(workers.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS));}
    }
    @Test void manifestRevisionAndPrimaryAreOneConsistentReadDuringConcurrentEdits() throws Exception {
        UUID first=uploaded().primaryImageId();UUID second=uploaded().images().get(1).id();
        var worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var writer=worker.submit(()->{
                for(int revision=3;revision<=22;revision++) {
                    UUID primary=revision%2==0?first:second;
                    update(new ImageManifestRequest(Integer.toString(revision-1),primary,List.of(new ImageManifestRequest.Item(first,null),new ImageManifestRequest.Item(second,null))))
                            .andExpect(status().isOk());
                }
                return null;
            });
            for(int i=0;i<30;i++) {
                var result=mvc.perform(get("/api/properties/{p}/images",propertyId)).andExpect(status().isOk()).andReturn();
                var snapshot=mapper.readValue(result.getResponse().getContentAsByteArray(),ImageManifest.class);
                assertEquals(Long.parseLong(snapshot.revision())%2==0?first:second,snapshot.primaryImageId());
            }
            writer.get(15,java.util.concurrent.TimeUnit.SECONDS);
        } finally {worker.shutdownNow();assertTrue(worker.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS));}
    }
    @Autowired PropertyImageService service;
    @Test void maintenanceRetainsRecentTombstonesAndOnlyPrunesAfterThirtyDays() throws Exception {
        UUID id=uploaded().primaryImageId();
        assertTrue(service.delete(propertyId,id,"1",null,email));assertTrue(service.maintenance(false).expiredTokens().isEmpty());
        tx.executeWithoutResult(s->{var row=images.findById(id).orElseThrow();row.deletedAt=java.time.Instant.now().minusSeconds(31L*86400);images.saveAndFlush(row);});
        assertEquals(List.of(id),service.maintenance(false).expiredTokens());assertTrue(images.findById(id).isPresent());
        assertEquals(List.of(id),service.maintenance(true).expiredTokens());assertTrue(images.findById(id).isEmpty());
    }
    @Test void failedUnlinkRevokesReadsAndRetryCompletesWithoutChangingRevisionAgain(@org.junit.jupiter.api.io.TempDir Path outside) throws Exception {
        UUID id=uploaded().primaryImageId(); var row=images.findById(id).orElseThrow();
        Path thumbnail=ROOT.resolve(row.storageKey+"-thumbnail.jpg"), canary=outside.resolve("canary");
        Files.writeString(canary,"owned synthetic canary"); Files.delete(thumbnail); Files.createSymbolicLink(thumbnail,canary);
        mvc.perform(delete("/api/admin/properties/{p}/images/{i}",propertyId,id).param("expectedRevision","1")
                .with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isAccepted());
        assertEquals(PropertyImage.State.DELETING,images.findById(id).orElseThrow().state);
        mvc.perform(get("/api/properties/{p}/images/{i}/display",propertyId,id)).andExpect(status().isNotFound());
        assertEquals("owned synthetic canary",Files.readString(canary)); Files.delete(thumbnail);
        mvc.perform(delete("/api/admin/properties/{p}/images/{i}",propertyId,id).param("expectedRevision","1")
                .with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/properties/{p}/images",propertyId)).andExpect(jsonPath("$.revision").value("2"));
    }
}
