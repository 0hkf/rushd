package com.rushd.market;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MarketInputTest {
    static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"),ZoneOffset.UTC);
    static JsonNode node(String json) { try { return MarketInput.JSON.readTree(json); } catch(Exception ex) { throw new AssertionError(ex); } }
    static JsonNode parse(String json,Set<String> fields) { var r=new MockHttpServletRequest(); r.setContent(json.getBytes(StandardCharsets.UTF_8)); return MarketInput.body(r,fields); }
    static String source(String id) { return """
        {"id":"%s","title":"مصدر اختباري","officialUrl":"https://www.moj.gov.sa/ar/OpenData/","periodStart":"2026-04-01","periodEnd":"2026-06-30","obtainedDate":"2026-11-10","licenceNote":"دليل اختباري فقط","notes":null}
        """.formatted(id); }
    static String facts() { return """
        {"transactionDate":"2026-05-01","city":"مكة","district":null,"sourceClassification":"سكني","propertyType":null,"areaMeaning":null,"evidenceNote":null,"price":"99999999999999999.99","area":"9007199254740993.01","propertyCount":null}
        """; }
    @ParameterizedTest @ValueSource(strings={"0","-0.01","100000000000000000","0.001","1.230","1e2147483647","1e-2147483647","NaN","Infinity","1,000","١٫٢"," 1.2 ","+1"})
    void rejectsOutOfContractDecimals(String raw) { assertThrows(MarketFailure.class,()->MarketInput.decimal(node("{\"price\":\""+raw+"\"}"),"price",true)); }
    @ParameterizedTest @ValueSource(strings={"0.01","99999999999999999.99","9007199254740993.01","1e2","1.20"})
    void acceptsExactBoundsWithoutNumberConversion(String raw) { assertEquals(new java.math.BigDecimal(raw).setScale(2).toPlainString(),MarketInput.decimal(node("{\"price\":\""+raw+"\"}"),"price",true)); }
    @Test void numericJsonDoesNotEraseExtraFractionalZeros() { assertThrows(MarketFailure.class,()->MarketInput.decimal(parse("{\"price\":1.230}",Set.of("price")),"price",true)); }
    @ParameterizedTest @ValueSource(strings={"http://moj.gov.sa/x","https://moj.gov.sa.evil.invalid/","https://evil.invalid/","https://user@moj.gov.sa/","https://moj.gov.sa:443/","https://moj.gov.sa/#x","https://moj.gov.sa\\@evil.invalid/"})
    void rejectsUnsafeOrUnofficialUrls(String url) { var n=(com.fasterxml.jackson.databind.node.ObjectNode)node(source(java.util.UUID.randomUUID().toString())); n.put("officialUrl",url); assertThrows(MarketFailure.class,()->MarketInput.source(n,CLOCK)); }
    @ParameterizedTest @ValueSource(strings={"1899-12-31","2026-11-12","2026-02-30","26-01-01","2026-1-01"})
    void rejectsInvalidDates(String date) { assertThrows(MarketFailure.class,()->MarketInput.date(date,CLOCK)); }
    @ParameterizedTest @ValueSource(strings={"مكة"," مكة   المكرمة "," Makkah ","MAKKAH"})
    void acceptsOnlyExplicitMakkahAliasesPreservingRawValue(String city) { var n=(com.fasterxml.jackson.databind.node.ObjectNode)node(facts()); n.put("city",city); assertEquals(city,MarketInput.facts(n,CLOCK,MarketInput.source(node(source(java.util.UUID.randomUUID().toString())),CLOCK)).city()); }
    @Test void unknownFactsAreNotInventedAndEvidenceIsRequired() {
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)node(facts()); var source=MarketInput.source(node(source(java.util.UUID.randomUUID().toString())),CLOCK);
        assertNull(MarketInput.facts(n,CLOCK,source).propertyCount()); n.put("propertyType","LAND");
        assertThrows(MarketFailure.class,()->MarketInput.facts(n,CLOCK,source)); n.put("evidenceNote","synthetic evidence");
        assertEquals("LAND",MarketInput.facts(n,CLOCK,source).propertyType()); n.put("areaMeaning","LAND"); n.putNull("area");
        assertThrows(MarketFailure.class,()->MarketInput.facts(n,CLOCK,source));
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"price\":1,\"price\":2}","{\"unknown\":1}","{\"price\":1} {}","{\"price\":1e9999999999}","[]"})
    void strictJsonEnvelope(String json) {
        if (json.equals("{}")) assertNotNull(parse(json,Set.of("price")));
        else assertThrows(MarketFailure.class,()->parse(json,Set.of("price")));
    }
    @Test void fullReplacementAndEnumNamesOnly() {
        var n=(com.fasterxml.jackson.databind.node.ObjectNode)node(facts()); var s=MarketInput.source(node(source(java.util.UUID.randomUUID().toString())),CLOCK);
        n.remove("area"); assertThrows(MarketFailure.class,()->MarketInput.facts(n,CLOCK,s));
        n.putNull("area"); n.put("propertyType",0); assertThrows(MarketFailure.class,()->MarketInput.facts(n,CLOCK,s));
        n.putNull("propertyType"); n.put("propertyCount",0); assertThrows(MarketFailure.class,()->MarketInput.facts(n,CLOCK,s));
        n.put("propertyCount",1000001); assertThrows(MarketFailure.class,()->MarketInput.facts(n,CLOCK,s));
        n.put("propertyCount",1); n.put("city","جدة"); assertThrows(MarketFailure.class,()->MarketInput.facts(n,CLOCK,s));
    }
    @Test void exactTextAndDateBoundariesAndBodyLimit() {
        var s=(com.fasterxml.jackson.databind.node.ObjectNode)node(source(java.util.UUID.randomUUID().toString()));
        s.put("title","x".repeat(150));s.put("licenceNote","x".repeat(2000));s.put("periodStart","1900-01-01");s.put("obtainedDate","2026-11-11");
        assertNotNull(MarketInput.source(s,CLOCK));s.put("title","x".repeat(151));assertThrows(MarketFailure.class,()->MarketInput.source(s,CLOCK));
        s.put("title","x");s.put("periodEnd","2026-11-11");s.put("obtainedDate","2026-11-10");assertThrows(MarketFailure.class,()->MarketInput.source(s,CLOCK));
        var request=new MockHttpServletRequest();request.setContent(new byte[16385]);assertEquals(413,assertThrows(MarketFailure.class,()->MarketInput.body(request,Set.of())).status());
        var f=(com.fasterxml.jackson.databind.node.ObjectNode)node(facts());var metadata=MarketInput.source(node(source(java.util.UUID.randomUUID().toString())),CLOCK);
        f.put("transactionDate",metadata.periodStart());f.put("propertyCount",1);f.put("district","x".repeat(150));assertNotNull(MarketInput.facts(f,CLOCK,metadata));
        f.put("transactionDate",metadata.periodEnd());f.put("propertyCount",1000000);assertNotNull(MarketInput.facts(f,CLOCK,metadata));
        f.put("district","x".repeat(151));assertThrows(MarketFailure.class,()->MarketInput.facts(f,CLOCK,metadata));
    }
}
