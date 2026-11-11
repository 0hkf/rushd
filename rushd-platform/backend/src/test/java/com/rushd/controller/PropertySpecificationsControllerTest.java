package com.rushd.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.*;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.AuthCookieService;
import com.rushd.service.JwtService;
import com.rushd.validation.PropertySpecificationFields;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.rushd.controller.PropertySpecificationsFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertySpecificationsControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PropertyRepository properties;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired EntityManager entities;
    Cookie admin;

    @BeforeEach void setup() { admin=access(Role.ADMIN); }

    @ParameterizedTest
    @EnumSource(PropertyType.class)
    void everyTypeRoundTripsItsApplicableFactsThroughCreateUpdateAndAllPropertyReads(PropertyType type) throws Exception {
        Map<String,Object> spec=sample(type,false);
        Map<String,Object> body=body(type,spec);
        JsonNode created=create(body);
        long id=created.get("id").asLong();
        assertFacts(created,spec);
        fresh(); assertFacts(read(id),spec);
        assertFacts(listed(id,false),spec); assertFacts(listed(id,true),spec);
        Map<String,Object> patch=values("parkingSpaces",1000);
        body.put("specifications",patch);
        assertFacts(update(id,body),merge(spec,patch));
        fresh(); assertFacts(read(id),merge(spec,patch));
        assertEquals(0,new BigDecimal("9007199254740993.01").compareTo(properties.findById(id).orElseThrow().getPrice()));
    }

    @Test void wholeCommercialScopeAndAllApprovedFieldsAreCovered() throws Exception {
        Map<String,Object> spec=sample(PropertyType.COMMERCIAL,true);
        long id=create(body(PropertyType.COMMERCIAL,spec)).get("id").asLong();
        fresh(); assertFacts(read(id),spec);
        Set<String> covered=new HashSet<>();
        for(PropertyType type:PropertyType.values()) covered.addAll(sample(type,false).keySet());
        covered.addAll(spec.keySet());
        assertEquals(37,covered.size());
        assertEquals(PropertySpecificationFields.BY_NAME.keySet(),covered);
    }

    @Test void legacyRequestsEmptyObjectsAndNullFactsRemainBackwardCompatible() throws Exception {
        Map<String,Object> legacy=body(PropertyType.LAND,null);
        legacy.put("area",new BigDecimal("500.25")); legacy.put("price",new BigDecimal("100.50"));
        JsonNode created=create(legacy); long id=created.get("id").asLong();
        assertEmptyFacts(created);
        fresh(); assertEmptyFacts(read(id));
        legacy.put("specifications",values()); assertEmptyFacts(update(id,legacy));
        Map<String,Object> allNull=new LinkedHashMap<>(); PropertySpecificationFields.ALL.forEach(f->allNull.put(f.name(),null));
        legacy.put("specifications",allNull); assertEmptyFacts(update(id,legacy));
        fresh(); assertNull(properties.findById(id).orElseThrow().getSpecifications());
    }

    @Test void absentAndEmptyGroupsPreserveFactsAndExplicitChildNullClearsOnlyIt() throws Exception {
        Map<String,Object> spec=sample(PropertyType.APARTMENT,false);
        Map<String,Object> body=body(PropertyType.APARTMENT,spec);
        long id=create(body).get("id").asLong();
        body.remove("specifications"); assertFacts(update(id,body),spec);
        body.put("specifications",values()); assertFacts(update(id,body),spec);
        Map<String,Object> patch=values("hasElevator",null,"hasFittedKitchen",false,"parkingSpaces",0);
        body.put("specifications",patch); assertFacts(update(id,body),merge(spec,patch));
        fresh(); assertFacts(read(id),merge(spec,patch));
        assertEquals(8,properties.findById(id).orElseThrow().getSpecifications().getEstimatedAgeYears());
        body.put("specifications",values("estimatedAgeYears",null,"ageAsOfYear",null));
        JsonNode cleared=update(id,body); assertTrue(cleared.at("/specifications/estimatedAgeYears").isNull());
        assertTrue(cleared.at("/specifications/ageAsOfYear").isNull());
        assertFalse(cleared.at("/specifications/hasFittedKitchen").asBoolean());
    }

    @Test void linkedAreaConflictDoesNotUnlinkOrPartiallySaveAndExplicitCorrectionWorks() throws Exception {
        Map<String,Object> spec=sample(PropertyType.LAND,false);
        Map<String,Object> body=body(PropertyType.LAND,spec);
        long id=create(body).get("id").asLong(); fresh();
        var before=properties.findById(id).orElseThrow().getUpdatedAt();
        body.remove("specifications"); body.put("area","600.25"); body.put("title","Must not save");
        rejectUpdate(id,body); fresh();
        Property unchanged=properties.findById(id).orElseThrow();
        assertEquals("P04 property",unchanged.getTitle()); assertEquals(before,unchanged.getUpdatedAt());
        assertEquals(PropertyAreaSource.LAND,unchanged.getSpecifications().getPrimaryAreaSource());
        assertEquals(0,new BigDecimal("500.25").compareTo(unchanged.getArea()));
        body.put("specifications",values("landArea","600.25")); update(id,body); fresh();
        assertEquals(0,new BigDecimal("600.25").compareTo(properties.findById(id).orElseThrow().getSpecifications().getLandArea()));
        body.put("area","700.25"); body.put("specifications",values("primaryAreaSource",null));
        update(id,body); fresh();
        Property unlinked=properties.findById(id).orElseThrow();
        assertNull(unlinked.getSpecifications().getPrimaryAreaSource());
        assertEquals(0,new BigDecimal("600.25").compareTo(unlinked.getSpecifications().getLandArea()));
    }

    @Test void missingSourceClearOfLinkedValueAndNetOverGrossAreRejectedAtomically() throws Exception {
        Map<String,Object> spec=sample(PropertyType.APARTMENT,false);
        Map<String,Object> body=body(PropertyType.APARTMENT,spec);
        long id=create(body).get("id").asLong();
        for(Map<String,Object> patch:List.of(values("unitNetArea",null),values("unitNetArea","600.01"),
                values("primaryAreaSource","LAND"),values("unitNetArea","500.24"))) {
            body.put("specifications",patch); rejectUpdate(id,body); fresh(); assertFacts(read(id),spec);
        }
        // Equal numerical values with different scales are not a conflict.
        body.put("area","500.250"); // Old field still rejects excess scale rather than silently rounding.
        rejectUpdate(id,body);
        Map<String,Object> exact=values("landArea","500.0","primaryAreaSource","LAND");
        Map<String,Object> equalBody=body(PropertyType.LAND,exact); equalBody.put("area","500.00");
        assertFacts(create(equalBody),exact);
    }

    @Test void typeChangeRequiresExplicitRemovalAndRetainsCompatibleFacts() throws Exception {
        Map<String,Object> spec=sample(PropertyType.APARTMENT,false);
        Map<String,Object> body=body(PropertyType.APARTMENT,spec);
        long id=create(body).get("id").asLong();
        body.put("type","LAND"); body.remove("specifications"); rejectUpdate(id,body); fresh(); assertFacts(read(id),spec);
        Map<String,Object> clear=new LinkedHashMap<>(); spec.keySet().forEach(k->clear.put(k,null));
        clear.put("landArea","500.25"); clear.put("primaryAreaSource","LAND"); clear.put("parkingSpaces",0);
        body.put("specifications",clear); update(id,body); fresh();
        Property changed=properties.findById(id).orElseThrow(); assertEquals(PropertyType.LAND,changed.getType());
        assertNull(changed.getSpecifications().getBedrooms()); assertEquals(0,changed.getSpecifications().getParkingSpaces());
        body.put("type","FARM"); body.remove("specifications");
        update(id,body); // Compatible land area / source / parking are preserved across types.
    }

    @Test void commercialScopeChangeChecksMergedStateAndDoesNotRequireRepeatingStoredScope() throws Exception {
        Map<String,Object> spec=sample(PropertyType.COMMERCIAL,false);
        Map<String,Object> body=body(PropertyType.COMMERCIAL,spec);
        long id=create(body).get("id").asLong();
        body.put("specifications",values("floorNumber",-10)); update(id,body);
        body.put("specifications",values("commercialScope","WHOLE_BUILDING")); rejectUpdate(id,body);
        body.put("specifications",values("commercialScope","WHOLE_BUILDING","floorNumber",null,
                "unitNetArea",null,"unitGrossArea",null,"primaryAreaSource",null,"floorCount",200));
        update(id,body); fresh();
        assertEquals(CommercialScope.WHOLE_BUILDING,properties.findById(id).orElseThrow().getSpecifications().getCommercialScope());
    }

    @Test void inappropriateFalseAndZeroAndInvalidUnitTotalsDoNotSave() throws Exception {
        Map<String,Object> land=body(PropertyType.LAND,null);
        long count=properties.count();
        for(Map<String,Object> invalid:List.of(values("bedrooms",0),values("hasElevator",false),values("floorNumber",0))) {
            land.put("specifications",invalid); rejectCreate(land); assertEquals(count,properties.count());
        }
        Map<String,Object> building=body(PropertyType.BUILDING,values("totalUnits",10,"residentialUnits",6,"commercialUnits",5));
        rejectCreate(building); assertEquals(count,properties.count());
        building.put("specifications",values("residentialUnits",6));
        JsonNode partial=create(building); assertTrue(partial.at("/specifications/totalUnits").isNull());
    }

    @Test void maximumNamedAreasAndUnderConstructionUnknownsKeepExactValuesWithoutInference() throws Exception {
        for(String field:List.of("landArea","unitNetArea","unitGrossArea","totalBuiltArea")) {
            Map<String,Object> body=body(PropertyType.OTHER,values(field,"9999999999.99"));
            long id=create(body).get("id").asLong(); fresh();
            assertEquals("9999999999.99",read(id).at("/specifications/"+field).asText());
            body.put("specifications",values(field,"0.01")); update(id,body); fresh();
            assertEquals("0.01",read(id).at("/specifications/"+field).asText());
        }
        JsonNode zero=create(body(PropertyType.APARTMENT,values("estimatedAgeYears",0,"ageAsOfYear",2026,
                "bedrooms",0,"hasFittedKitchen",true)));
        assertTrue(zero.at("/specifications/condition").isNull());
        assertTrue(zero.at("/specifications/furnishing").isNull());
        assertFalse(zero.get("specifications").has("yearBuilt"));
        JsonNode construction=create(body(PropertyType.APARTMENT,values("condition","UNDER_CONSTRUCTION")));
        assertTrue(construction.at("/specifications/estimatedAgeYears").isNull());
        assertTrue(construction.at("/specifications/ageAsOfYear").isNull());
    }

    @Test void existingAuthorisationCsrfVisibilityAndPublisherProtectionsStillApplyToNewFacts() throws Exception {
        Map<String,Object> body=body(PropertyType.LAND,values("landArea","500.25"));
        long id=create(body).get("id").asLong();
        long publisher=properties.findById(id).orElseThrow().getPublisher().getId();
        for(Role role:List.of(Role.BUYER,Role.SELLER)) {
            Cookie token=access(role);
            mvc.perform(post("/api/properties").with(csrf()).cookie(token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/properties/{id}",id).with(csrf()).cookie(token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/properties").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/properties/{id}",id).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isForbidden());
        body.put("publisherId",99999); body.put("status","DRAFT"); update(id,body); fresh();
        assertEquals(publisher,properties.findById(id).orElseThrow().getPublisher().getId());
        mvc.perform(get("/api/properties/{id}",id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/properties/{id}",id).cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.specifications.landArea").isString());
    }

    private Cookie access(Role role) {
        User user=new User(); user.setName("P04 "+role); user.setEmail("p04-"+role.name().toLowerCase(Locale.ROOT)+"-"+users.count()+"@example.invalid");
        user.setPassword("unused-hash"); user.setRole(role); users.saveAndFlush(user);
        return new Cookie(AuthCookieService.ACCESS,jwt.generateAccessToken(user.getEmail()));
    }
    private void fresh() { properties.flush(); entities.clear(); }
    private JsonNode create(Map<String,Object> body) throws Exception { return response(post("/api/properties"),body,201); }
    private JsonNode update(long id,Map<String,Object> body) throws Exception { return response(put("/api/properties/{id}",id),body,200); }
    private void rejectCreate(Map<String,Object> body) throws Exception { response(post("/api/properties"),body,400); }
    private void rejectUpdate(long id,Map<String,Object> body) throws Exception { response(put("/api/properties/{id}",id),body,400); }
    private JsonNode response(MockHttpServletRequestBuilder request,Map<String,Object> body,int status) throws Exception {
        var result=mvc.perform(request.with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().is(status)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
    private JsonNode read(long id) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/properties/{id}",id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode listed(long id,boolean adminList) throws Exception {
        var request=get(adminList?"/api/admin/properties":"/api/properties").param("size","100");
        if(adminList)request.cookie(admin);
        JsonNode page=mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        for(JsonNode item:page.get("content"))if(item.get("id").asLong()==id)return item;
        throw new AssertionError("Missing property in listing");
    }
    private Map<String,Object> merge(Map<String,Object> first,Map<String,Object> second) {
        Map<String,Object> merged=new LinkedHashMap<>(first); merged.putAll(second); return merged;
    }
    private void assertEmptyFacts(JsonNode property) {
        JsonNode spec=property.get("specifications"); assertEquals(37,spec.size()); spec.forEach(v->assertTrue(v.isNull()));
    }
    private void assertFacts(JsonNode property,Map<String,Object> expected) {
        JsonNode spec=property.get("specifications"); assertNotNull(spec); assertEquals(37,spec.size());
        for(var field:PropertySpecificationFields.ALL) {
            JsonNode actual=spec.get(field.name()); assertNotNull(actual,field.name()); Object value=expected.get(field.name());
            if(value==null)assertTrue(actual.isNull(),field.name());
            else if(field.type()==BigDecimal.class) { assertTrue(actual.isTextual()); assertEquals(0,new BigDecimal(value.toString()).compareTo(new BigDecimal(actual.asText()))); }
            else if(value instanceof Boolean) { assertTrue(actual.isBoolean()); assertEquals(value,actual.booleanValue()); }
            else if(value instanceof Integer) { assertTrue(actual.isIntegralNumber()); assertEquals(value,actual.intValue()); }
            else assertEquals(value.toString(),actual.asText());
        }
    }
}
