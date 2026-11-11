package com.rushd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.*;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.AuthCookieService;
import com.rushd.service.JwtService;
import com.rushd.validation.PropertySpecificationFields;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Year;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;
import static com.rushd.controller.PropertySpecificationsFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PropertySpecificationsValidationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;
    @MockBean UserRepository users;
    @MockBean PropertyRepository properties;
    Cookie adminAccess;

    @BeforeEach
    void setup() {
        User admin = new User(); admin.setId(1L); admin.setName("P04 binding");
        admin.setEmail("p04-binding@example.invalid"); admin.setPassword("unused"); admin.setRole(Role.ADMIN);
        when(users.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        adminAccess = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(admin.getEmail()));
        when(properties.save(any(Property.class))).thenAnswer(call -> { Property p = call.getArgument(0); p.setId(91L); return p; });
        Property existing = new Property(); existing.setPublisher(admin); existing.setId(91L);
        existing.setType(PropertyType.LAND); existing.setArea(new BigDecimal("500.25")); existing.setTitle("Original");
        when(properties.findById(91L)).thenReturn(Optional.of(existing));
        when(properties.findByIdForUpdate(91L)).thenReturn(Optional.of(existing));
    }
    static Stream<PropertySpecificationFields.Field<?>> fields() { return PropertySpecificationFields.ALL.stream(); }

    @ParameterizedTest
    @MethodSource("fields")
    void wrongJsonTypesAndBoundsAreRejectedBeforeAnyPropertyRepositoryAccess(PropertySpecificationFields.Field<?> field) throws Exception {
        List<Object> invalid = new ArrayList<>(List.of(List.of(1), Map.of("value", 1)));
        if (field.type() == Integer.class) {
            invalid.addAll(List.of("1", true, new BigDecimal("1.0"), new BigDecimal("1E+100"),
                    new BigInteger("999999999999999999999999"), field.min()-1, field.max()+1));
            if (field.name().equals("ageAsOfYear")) invalid.add(Year.now(ZoneId.of("Asia/Riyadh")).getValue()+1);
        } else if (field.type() == BigDecimal.class) {
            invalid.addAll(List.of(1, true, "0", "-0.01", "10000000000", "0.001", "1.000", "NaN", "Infinity",
                    "1E3", "0E+2147483647", "1e999999999", "١٫٥", "۱٫۵", "1,50", "", " 1.00 ", "1".repeat(1000)));
        } else if (field.type() == Boolean.class) {
            invalid.addAll(List.of(0, 1, "true", "false", "", new BigDecimal("1.0")));
        } else if (field.type().isEnum()) {
            invalid.addAll(List.of(0, "0", true, "", "UNKNOWN", "invalid", " "));
        } else {
            invalid.addAll(List.of(0, true, "x".repeat(101)));
        }
        for (Object value : invalid) {
            Map<String,Object> spec = values(field.name(), value);
            addAgePair(spec, field.name());
            assertRejectedBoth(body(PropertyType.OTHER, spec));
        }
        verifyNoInteractions(properties);
    }

    @ParameterizedTest
    @MethodSource("fields")
    void acceptedValuesAndExplicitNullBindWithoutCoercion(PropertySpecificationFields.Field<?> field) throws Exception {
        List<Object> accepted = new ArrayList<>();
        if (field.type() == Integer.class) {
            accepted.add(field.min()); accepted.add(field.name().equals("ageAsOfYear")
                    ? Year.now(ZoneId.of("Asia/Riyadh")).getValue() : field.max());
        } else if (field.type() == BigDecimal.class) accepted.addAll(List.of("0.01", "9999999999.99"));
        else if (field.type() == Boolean.class) accepted.addAll(List.of(true,false));
        else if (field.type().isEnum()) for (Object value : field.type().getEnumConstants()) accepted.add(value.toString());
        else accepted.add("x".repeat(100));
        accepted.add(null);
        for (Object value : accepted) {
            Map<String,Object> spec = values(field.name(), value);
            addAgePair(spec, field.name());
            PropertyType type = suitableType(field.name());
            if (field.name().equals("primaryAreaSource") && value != null) {
                String source = switch (value.toString()) { case "LAND" -> "landArea"; case "UNIT_NET" -> "unitNetArea";
                    case "UNIT_GROSS" -> "unitGrossArea"; default -> "totalBuiltArea"; };
                spec.put(source,"500.25");
            }
            Map<String,Object> requestBody = body(type, spec);
            for (boolean update : new boolean[]{false,true}) {
                var result = mvc.perform((update ? put("/api/properties/91") : post("/api/properties"))
                        .with(csrf()).cookie(adminAccess).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(requestBody)))
                        .andExpect(update ? status().isOk() : status().isCreated()).andReturn();
                var actual = mapper.readTree(result.getResponse().getContentAsByteArray()).get("specifications").get(field.name());
                if (value == null) assertTrue(actual.isNull(),field.name());
                else if (value instanceof Boolean) assertTrue(actual.isBoolean(),field.name());
                else if (value instanceof Integer) assertTrue(actual.isIntegralNumber(),field.name());
                else assertTrue(actual.isTextual(),field.name());
            }
            // Each accepted case is independent; do not let earlier mocked updates supply hidden state.
            properties.findById(91L).orElseThrow().setSpecifications(null);
        }
        verify(properties, atLeastOnce()).findByIdForUpdate(91L);
    }

    @Test
    void malformedGroupsUnknownAndDuplicateKeysAndIncompleteAgePairsAreRejected() throws Exception {
        for (Object value : Arrays.asList(null, true, 1, "", List.of(), values("unknown", 1),
                values("estimatedAgeYears", 0), values("ageAsOfYear", 2026),
                values("estimatedAgeYears", null), values("estimatedAgeYears", 8, "ageAsOfYear", null))) {
            Map<String,Object> request = body(PropertyType.OTHER,null); request.put("specifications",value);
            assertRejectedBoth(request);
        }
        String base=mapper.writeValueAsString(body(PropertyType.OTHER,null));
        for(String duplicate:List.of("{\"hasPool\":true,\"hasPool\":false}","{\"wellCount\":0,\"wellCount\":null}")) {
            String json=base.substring(0,base.length()-1)+",\"specifications\":"+duplicate+"}";
            for(boolean update:new boolean[]{false,true}) mvc.perform((update?put("/api/properties/91"):post("/api/properties"))
                    .with(csrf()).cookie(adminAccess).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(properties);
    }

    private void assertRejectedBoth(Map<String,Object> body) throws Exception {
        String json=mapper.writeValueAsString(body);
        for(boolean update:new boolean[]{false,true}) mvc.perform((update?put("/api/properties/91"):post("/api/properties"))
                .with(csrf()).cookie(adminAccess).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
    }
    private static void addAgePair(Map<String,Object> spec,String field) {
        if(field.equals("estimatedAgeYears")) spec.put("ageAsOfYear",spec.get(field)==null?null:2026);
        if(field.equals("ageAsOfYear")) spec.put("estimatedAgeYears",spec.get(field)==null?null:8);
    }
    private static PropertyType suitableType(String field) {
        return switch(field) {
            case "bedrooms","bathrooms","livingRooms","floorNumber","estimatedAgeYears","ageAsOfYear","condition","finishing","furnishing" -> PropertyType.APARTMENT;
            case "floorCount","totalUnits","residentialUnits","commercialUnits" -> PropertyType.BUILDING;
            case "electricityConnected","waterConnected","sewerConnected" -> PropertyType.LAND;
            case "wellCount","waterSource","hasIrrigation" -> PropertyType.FARM;
            case "commercialScope","commercialSubtype" -> PropertyType.COMMERCIAL;
            default -> PropertyType.OTHER;
        };
    }
}
