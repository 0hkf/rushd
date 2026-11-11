package com.rushd.market;

import com.rushd.entity.*;
import com.rushd.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.io.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="rushd.market.enabled=true")
@AutoConfigureMockMvc
@Transactional
class MarketWorkflowIntegrationTest {
    @Autowired MarketWorkflow workflow;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    String admin,other,buyer;
    @BeforeAll static void schema(@Autowired JdbcTemplate jdbc) throws IOException {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name)='market_sources'",Integer.class)==0) {
            String sql=Files.readString(Path.of("db/20261111-market-references.sql")).replace("BEGIN;","").replace("COMMIT;","");
            for(String command:sql.split(";")) if(!command.isBlank()) jdbc.execute(command);
        }
    }
    @BeforeEach void setup() { admin=createUser(Role.ADMIN); other=createUser(Role.ADMIN); buyer=createUser(Role.BUYER); }
    String createUser(Role role) { User u=new User(); u.setEmail("market-"+UUID.randomUUID()+"@example.invalid");u.setName("Synthetic");u.setPassword("hash");u.setRole(role);return users.saveAndFlush(u).getEmail(); }
    UUID source() { UUID id=UUID.randomUUID();workflow.createSource(admin,MarketInputTest.node(MarketInputTest.source(id.toString())));return id; }
    UUID ready() { UUID id=source();workflow.upload(admin,id,new ByteArrayInputStream(XlsxEnvelopeTest.workbook()));return id; }
    com.fasterxml.jackson.databind.node.ObjectNode referenceBody(UUID id,UUID source) { var n=(com.fasterxml.jackson.databind.node.ObjectNode)MarketInputTest.node(MarketInputTest.facts()); n.put("id",id.toString());n.put("sourceId",source.toString());n.put("sourceReference","0009007199254740993");return n; }
    @Test void createsExactReferenceWithSharedAdminAccessAndImmutableHistory() {
        UUID source=ready(), id=UUID.randomUUID(); var body=referenceBody(id,source); var created=workflow.createReference(admin,body);
        assertEquals("99999999999999999.99",created.facts().price()); assertEquals("9007199254740993.01",created.facts().area());
        assertTrue(created.missingData().contains("MISSING_DETAILED_TYPE")); assertNull(created.facts().propertyCount());
        assertEquals(created,workflow.reference(other,id)); assertEquals(created,workflow.createReference(other,body));
        var update=body.deepCopy();update.remove(List.of("id","sourceId","sourceReference"));update.put("expectedRevision","0");update.put("changeReason","تصحيح اختباري");update.put("price","9007199254740993.01");update.putNull("area");
        assertEquals("1",workflow.updateReference(other,id,update).revision());
        assertEquals(409,assertThrows(MarketFailure.class,()->workflow.updateReference(admin,id,update)).status());
        var versions=workflow.versions(admin,id,20,0);assertEquals(2,versions.size());assertEquals(created.facts(),versions.get(1).facts());
        assertEquals("9007199254740993.01",workflow.reference(admin,id).facts().price());
        assertEquals(0,new java.math.BigDecimal("9007199254740993.01").compareTo(jdbc.queryForObject("SELECT price FROM market_reference_versions WHERE reference_id=? AND revision=1",java.math.BigDecimal.class,id)));
    }
    @Test void sourceCreationAndUploadRetriesNeverDuplicateOrReplaceOriginal() {
        UUID id=source(); var body=MarketInputTest.node(MarketInputTest.source(id.toString())); assertEquals("0",workflow.createSource(other,body).revision());
        long before=jdbc.queryForObject("SELECT used_bytes FROM market_storage_quota WHERE id=1",Long.class);
        byte[] bytes=XlsxEnvelopeTest.workbook();var uploaded=workflow.upload(admin,id,new ByteArrayInputStream(bytes));
        assertEquals(MarketWorkflow.hash(bytes),uploaded.sha256()); assertEquals(uploaded,workflow.upload(other,id,new ByteArrayInputStream(bytes)));
        assertEquals(before+bytes.length,jdbc.queryForObject("SELECT used_bytes FROM market_storage_quota WHERE id=1",Long.class));
        var different=XlsxEnvelopeTest.zip(Map.of("[Content_Types].xml","<Types><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>","xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>"));
        assertEquals(409,assertThrows(MarketFailure.class,()->workflow.upload(admin,id,new ByteArrayInputStream(different))).status());
        assertArrayEquals(bytes,workflow.download(other,id));
    }
    @Test void archiveKeepsOriginalAndFactsButBlocksNewUseUntilExplicitRestore() {
        UUID source=ready(),id=UUID.randomUUID();workflow.createReference(admin,referenceBody(id,source));
        var state=MarketInputTest.node("{\"expectedRevision\":\"0\",\"archived\":true,\"reason\":\"اختبار\"}");
        assertTrue(workflow.referenceState(other,id,state).archived()); assertEquals(2,workflow.versions(admin,id,20,0).size());
        var sourceState=MarketInputTest.node("{\"expectedRevision\":\"1\",\"archived\":true,\"reason\":\"إصدار قديم\"}");
        workflow.sourceState(admin,source,sourceState);assertTrue(workflow.reference(admin,id).missingData().contains("SOURCE_ARCHIVED"));
        assertArrayEquals(XlsxEnvelopeTest.workbook(),workflow.download(other,source));
        assertEquals(409,assertThrows(MarketFailure.class,()->workflow.createReference(admin,referenceBody(UUID.randomUUID(),source))).status());
        workflow.sourceState(other,source,MarketInputTest.node("{\"expectedRevision\":\"2\",\"archived\":false,\"reason\":\"استعادة\"}"));
        assertFalse(workflow.referenceState(admin,id,MarketInputTest.node("{\"expectedRevision\":\"1\",\"archived\":false,\"reason\":\"استعادة\"}")).archived());
        assertEquals(3,workflow.versions(admin,id,20,0).size());
    }
    @Test void disabledAndPersistedRoleAreCheckedBeforeReadingBodies() {
        var disabled=new MarketWorkflow(jdbc,users,clock,false);
        assertEquals(503,assertThrows(MarketFailure.class,()->disabled.sources("absent",20,0)).status());
        InputStream forbidden=new InputStream(){public int read(){throw new AssertionError("must not read");}};
        assertThrows(AccessDeniedException.class,()->workflow.upload(buyer,UUID.randomUUID(),forbidden));
        assertEquals(503,assertThrows(MarketFailure.class,()->disabled.upload(admin,UUID.randomUUID(),forbidden)).status());
        assertThrows(AccessDeniedException.class,()->workflow.source(buyer,UUID.randomUUID()));
    }
    @Test void archivedSourceBlocksFactEditsWithoutCreatingHistory() {
        UUID source=ready(),id=UUID.randomUUID(); workflow.createReference(admin,referenceBody(id,source));
        workflow.sourceState(other,source,MarketInputTest.node("{\"expectedRevision\":\"1\",\"archived\":true,\"reason\":\"archive\"}"));
        var update=(com.fasterxml.jackson.databind.node.ObjectNode)MarketInputTest.node(MarketInputTest.facts());update.put("expectedRevision","0");update.put("changeReason","blocked");
        assertEquals(409,assertThrows(MarketFailure.class,()->workflow.updateReference(admin,id,update)).status());
        assertEquals(1,workflow.versions(admin,id,20,0).size());
        assertTrue(workflow.reference(admin,id).missingData().contains("SOURCE_ARCHIVED"));
    }
    @Test void enforcesAuthorizationCsrfStrictPayloadAndPrivateDownloads() throws Exception {
        UUID id=ready(); String base="/api/admin/market";
        mvc.perform(get(base+"/sources")).andExpect(status().isUnauthorized());
        mvc.perform(get(base+"/sources").with(user(buyer).roles("BUYER"))).andExpect(status().isForbidden());
        mvc.perform(get(base+"/sources").with(user(buyer).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get(base+"/sources").with(user(admin).roles("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("original_bytes"))));
        mvc.perform(get(base+"/sources/"+id+"/original").with(user(buyer).roles("BUYER"))).andExpect(status().isForbidden());
        mvc.perform(get(base+"/sources/"+id+"/original").with(user(admin).roles("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;"))).andExpect(content().bytes(XlsxEnvelopeTest.workbook()));
        mvc.perform(put(base+"/sources/"+id+"/original").with(user(admin).roles("ADMIN")).contentType("application/octet-stream").content(new byte[]{1})).andExpect(status().isForbidden());
        mvc.perform(post(base+"/sources").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"title\":1,\"title\":2}")).andExpect(status().isBadRequest());
        mvc.perform(get(base+"/references?limit=51").with(user(admin).roles("ADMIN"))).andExpect(status().isBadRequest());
        var body=referenceBody(UUID.randomUUID(),id);
        mvc.perform(post(base+"/references").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.facts.price").value("99999999999999999.99")).andExpect(jsonPath("$.revision").value("0"));
    }
    @Test void duplicateSourceReferenceReturns409() throws Exception {
        UUID id=ready(),ref=UUID.randomUUID();var body=referenceBody(ref,id);workflow.createReference(admin,body);
        body.put("id",UUID.randomUUID().toString());mvc.perform(post("/api/admin/market/references").with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content(body.toString())).andExpect(status().isConflict());
    }
    @Test void invalidDecimalNeverCreatesAVersion() throws Exception {
        UUID id=ready(),ref=UUID.randomUUID();workflow.createReference(admin,referenceBody(ref,id));
        for(String value:List.of("0","-1","0.001","100000000000000000","1e2147483647")) {
            var update=(com.fasterxml.jackson.databind.node.ObjectNode)MarketInputTest.node(MarketInputTest.facts());update.put("expectedRevision","0");update.put("changeReason","invalid");update.put("price",value);
            mvc.perform(put("/api/admin/market/references/"+ref).with(user(admin).roles("ADMIN")).with(csrf()).contentType("application/json").content(update.toString())).andExpect(status().isBadRequest());
        }
        assertEquals(1,workflow.versions(admin,ref,20,0).size());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentUploadsCannotOverbookQuotaAndConcurrentEditsKeepBothHistoryAndWinner() throws Exception {
        UUID a=source(),b=source(),ref=UUID.randomUUID();
        long before=jdbc.queryForObject("SELECT used_bytes FROM market_storage_quota WHERE id=1",Long.class);
        byte[] bytes=XlsxEnvelopeTest.workbook(); var pool=Executors.newFixedThreadPool(2);
        try {
            jdbc.update("UPDATE market_storage_quota SET used_bytes=? WHERE id=1",100L*1024*1024-bytes.length);
            CountDownLatch start=new CountDownLatch(1);
            Callable<Integer> first=()->{start.await();try{workflow.upload(admin,a,new ByteArrayInputStream(bytes));return 200;}catch(MarketFailure ex){return ex.status();}};
            Callable<Integer> second=()->{start.await();try{workflow.upload(other,b,new ByteArrayInputStream(bytes));return 200;}catch(MarketFailure ex){return ex.status();}};
            var one=pool.submit(first);var two=pool.submit(second);start.countDown();
            assertEquals(Set.of(200,409),Set.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS)));
            assertEquals(100L*1024*1024,jdbc.queryForObject("SELECT used_bytes FROM market_storage_quota WHERE id=1",Long.class));
            UUID ready=workflow.source(admin,a).ready()?a:b;workflow.createReference(admin,referenceBody(ref,ready));
            var update=(com.fasterxml.jackson.databind.node.ObjectNode)MarketInputTest.node(MarketInputTest.facts());update.put("expectedRevision","0");update.put("changeReason","race");
            CountDownLatch edit=new CountDownLatch(1);
            var e1=pool.submit(()->{edit.await();try{workflow.updateReference(admin,ref,update);return 200;}catch(MarketFailure ex){return ex.status();}});
            var e2=pool.submit(()->{edit.await();try{workflow.updateReference(other,ref,update);return 200;}catch(MarketFailure ex){return ex.status();}});edit.countDown();
            assertEquals(Set.of(200,409),Set.of(e1.get(15,TimeUnit.SECONDS),e2.get(15,TimeUnit.SECONDS)));
            assertEquals(2,workflow.versions(admin,ref,20,0).size());
        } finally {
            pool.shutdownNow();pool.awaitTermination(15,TimeUnit.SECONDS);
            jdbc.update("DELETE FROM market_reference_versions WHERE reference_id=?",ref);jdbc.update("DELETE FROM market_references WHERE id=?",ref);
            jdbc.update("DELETE FROM market_sources WHERE id IN (?,?)",a,b);jdbc.update("UPDATE market_storage_quota SET used_bytes=? WHERE id=1",before);
            for(String email:List.of(admin,other,buyer)) users.findByEmail(email).ifPresent(users::delete);
        }
    }
}
