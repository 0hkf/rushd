package com.rushd.controller;

import com.rushd.entity.PropertyType;
import java.util.LinkedHashMap;
import java.util.Map;

final class PropertySpecificationsFixtures {
    private PropertySpecificationsFixtures() { }
    static Map<String, Object> values(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }
    static Map<String, Object> body(PropertyType type, Map<String, Object> specifications) {
        Map<String, Object> body = values("title", "P04 property", "type", type.name(),
                "listingType", "SALE", "city", "مكة المكرمة", "district", "النوارية",
                "area", "500.25", "price", "9007199254740993.01", "purpose", "RESIDENTIAL", "status", "ACTIVE");
        if (specifications != null) {
            body.put("specifications", specifications);
            Object source = specifications.get("primaryAreaSource");
            if (source != null) {
                String field = switch (source.toString()) {
                case "LAND" -> "landArea";
                case "UNIT_NET" -> "unitNetArea";
                case "UNIT_GROSS" -> "unitGrossArea";
                case "TOTAL_BUILT" -> "totalBuiltArea";
                    default -> null; // Malformed-source cases still need a valid legacy body.
                };
                if (field != null && specifications.get(field) != null) body.put("area", specifications.get(field));
            }
        }
        return body;
    }
    static Map<String, Object> sample(PropertyType type, boolean wholeCommercial) {
        Map<String, Object> spec = new LinkedHashMap<>();
        switch (type) {
            case LAND -> spec.putAll(values("landArea", "500.25", "primaryAreaSource", "LAND", "parkingSpaces", 0,
                    "electricityConnected", true, "waterConnected", false, "sewerConnected", true));
            case APARTMENT -> {
                spec.putAll(values("unitNetArea", "500.25", "unitGrossArea", "600.00", "primaryAreaSource", "UNIT_NET",
                        "bedrooms", 3, "bathrooms", 2, "livingRooms", 0, "floorNumber", -1));
                buildingFacts(spec, true); equipment(spec, true);
            }
            case VILLA -> {
                spec.putAll(values("landArea", "400.00", "totalBuiltArea", "600.25", "primaryAreaSource", "TOTAL_BUILT",
                        "bedrooms", 4, "bathrooms", 3, "livingRooms", 2, "floorCount", 2));
                buildingFacts(spec, true); equipment(spec, true);
            }
            case BUILDING -> {
                spec.putAll(values("landArea", "400.00", "totalBuiltArea", "600.25", "primaryAreaSource", "TOTAL_BUILT",
                        "floorCount", 2, "totalUnits", 10, "residentialUnits", 6, "commercialUnits", 4,
                        "parkingSpaces", 0, "hasElevator", true, "hasAirConditioning", false,
                        "hasPool", true, "hasYard", false, "hasPrivateRoof", true,
                        "electricityConnected", true, "waterConnected", false, "sewerConnected", true));
                buildingFacts(spec, false);
            }
            case COMMERCIAL -> {
                spec.putAll(values("commercialScope", wholeCommercial ? "WHOLE_BUILDING" : "UNIT", "commercialSubtype", "OFFICE",
                        "bathrooms", 1, "parkingSpaces", 0, "hasElevator", true, "hasAirConditioning", false,
                        "hasFittedKitchen", true, "hasPool", false, "hasYard", true, "hasBalcony", false, "hasPrivateRoof", true,
                        "electricityConnected", true, "waterConnected", false, "sewerConnected", true));
                if (wholeCommercial) spec.putAll(values("landArea", "400.00", "totalBuiltArea", "600.25",
                        "primaryAreaSource", "TOTAL_BUILT", "floorCount", 2, "totalUnits", 10, "residentialUnits", 0, "commercialUnits", 10));
                else spec.putAll(values("unitNetArea", "500.25", "unitGrossArea", "600.00", "primaryAreaSource", "UNIT_NET", "floorNumber", 0));
                buildingFacts(spec, false);
            }
            case FARM -> spec.putAll(values("landArea", "500.25", "totalBuiltArea", "600.00", "primaryAreaSource", "LAND",
                    "parkingSpaces", 0, "hasPool", true, "hasYard", false, "electricityConnected", true,
                    "hasIrrigation", false, "wellCount", 2, "waterSource", "WELL"));
            case OTHER -> {
                spec.putAll(values("landArea", "500.25", "unitNetArea", "80.00", "unitGrossArea", "100.00",
                        "totalBuiltArea", "600.00", "primaryAreaSource", "LAND", "otherTypeDescription", "عقار آخر موصوف صراحة"));
                equipment(spec, true);
            }
        }
        return spec;
    }
    private static void buildingFacts(Map<String, Object> spec, boolean furnishing) {
        spec.putAll(values("estimatedAgeYears", 8, "ageAsOfYear", 2026, "condition", "USED", "finishing", "FULL"));
        if (furnishing) spec.put("furnishing", "UNFURNISHED");
    }
    private static void equipment(Map<String, Object> spec, boolean all) {
        spec.putAll(values("hasElevator", true, "hasAirConditioning", false, "hasFittedKitchen", true,
                "hasMaidRoom", false, "hasDriverRoom", true, "hasPool", false, "hasYard", true,
                "hasBalcony", false, "hasPrivateRoof", true, "parkingSpaces", 0));
    }
}
