package com.rushd.market;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.rushd.entity.PropertyType;
import com.rushd.validation.SafeDecimalPrecisionValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Local strict contract: does not change JSON parsing for existing APIs. */
final class MarketInput {
    static final Set<String> SOURCE = Set.of("id", "title", "officialUrl", "periodStart", "periodEnd", "obtainedDate", "licenceNote", "notes");
    static final Set<String> FACTS = Set.of("transactionDate", "city", "district", "sourceClassification", "propertyType", "areaMeaning", "evidenceNote", "price", "area", "propertyCount");
    static final Set<String> MEANINGS = Set.of("LAND", "UNIT_NET", "UNIT_GROSS", "TOTAL_BUILT");
    private static final JsonFactory FACTORY = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxNumberLength(128).maxStringLength(4000).build()).build();
    static final ObjectMapper JSON = new ObjectMapper(FACTORY).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));

    record Source(String title, String officialUrl, String periodStart, String periodEnd, String obtainedDate, String licenceNote, String notes) { }
    record Facts(String transactionDate, String city, String district, String sourceClassification, String propertyType,
                 String areaMeaning, String evidenceNote, String price, String area, Integer propertyCount) { }

    static JsonNode body(HttpServletRequest request, Set<String> allowed) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(16385);
            if (bytes.length > 16384) throw new MarketFailure(413, "طلب المرجع كبير جدًا.");
            try (JsonParser parser = FACTORY.createParser(bytes)) {
                JsonNode node = JSON.readTree(parser);
                if (node == null || !node.isObject() || parser.nextToken() != null) throw MarketFailure.invalid("JSON غير صالح.");
                var names = node.fieldNames();
                while (names.hasNext()) if (!allowed.contains(names.next())) throw MarketFailure.invalid("حقل غير معروف.");
                return node;
            }
        } catch (IOException | IllegalArgumentException ex) { throw MarketFailure.invalid("JSON غير صالح أو يتجاوز الحدود."); }
    }

    static String text(JsonNode node, String key, int max, boolean required) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            if (required) throw MarketFailure.invalid("الحقل مطلوب: " + key);
            return null;
        }
        if (!value.isTextual() || value.textValue().length() > max || value.textValue().isBlank()
                || value.textValue().codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
            throw MarketFailure.invalid("قيمة غير صالحة: " + key);
        return value.textValue(); // Preserve original text, including the source's city spelling.
    }
    static UUID uuid(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw MarketFailure.invalid("UUID غير صالح.");
        return UUID.fromString(value);
    }
    static long revision(JsonNode body) {
        String value = text(body, "expectedRevision", 19, true);
        if (!value.matches("0|[1-9][0-9]{0,18}")) throw MarketFailure.invalid("رقم المراجعة غير صالح.");
        try { long n = Long.parseLong(value); if (n == Long.MAX_VALUE) throw new NumberFormatException(); return n; }
        catch (NumberFormatException ex) { throw MarketFailure.invalid("رقم المراجعة غير صالح."); }
    }
    static boolean archived(JsonNode body) {
        if (!body.path("archived").isBoolean()) throw MarketFailure.invalid("حالة الأرشفة مطلوبة.");
        return body.get("archived").booleanValue();
    }
    static LocalDate date(String raw, Clock clock) {
        try {
            if (!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeException("date");
            LocalDate value = LocalDate.parse(raw);
            if (value.isBefore(LocalDate.of(1900, 1, 1)) || value.isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Riyadh")))))
                throw new DateTimeException("range");
            return value;
        } catch (DateTimeException ex) { throw MarketFailure.invalid("تاريخ غير صالح أو خارج المدى."); }
    }
    static Source source(JsonNode node, Clock clock) {
        String url = text(node, "officialUrl", 1000, true);
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !Set.of("moj.gov.sa", "www.moj.gov.sa").contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null)
                throw new IllegalArgumentException();
        } catch (Exception ex) { throw MarketFailure.invalid("يلزم رابط HTTPS رسمي لوزارة العدل دون منفذ أو بيانات دخول أو fragment."); }
        LocalDate start = date(text(node, "periodStart", 10, true), clock);
        LocalDate end = date(text(node, "periodEnd", 10, true), clock);
        LocalDate obtained = date(text(node, "obtainedDate", 10, true), clock);
        if (start.isAfter(end) || end.isAfter(obtained)) throw MarketFailure.invalid("ترتيب تواريخ المصدر غير صحيح.");
        return new Source(text(node, "title", 150, true), url, start.toString(), end.toString(), obtained.toString(),
                text(node, "licenceNote", 2000, true), text(node, "notes", 2000, false));
    }
    static Facts facts(JsonNode node, Clock clock, Source source) {
        // This is a full replacement. Explicit nulls distinguish unknown from omitted by mistake.
        for (String field : FACTS) if (!node.has(field)) throw MarketFailure.invalid("أرسل جميع حقول الحقائق، وnull للمجهول: " + field);
        LocalDate date = date(text(node, "transactionDate", 10, true), clock);
        if (date.isBefore(LocalDate.parse(source.periodStart())) || date.isAfter(LocalDate.parse(source.periodEnd())))
            throw MarketFailure.invalid("الصفقة خارج فترة المصدر.");
        String city = text(node, "city", 100, true);
        if (!Set.of("مكة", "مكة المكرمة", "makkah").contains(city.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)))
            throw MarketFailure.invalid("النطاق الحالي مدينة مكة فقط.");
        String type = text(node, "propertyType", 30, false);
        if (type != null) try { PropertyType.valueOf(type); } catch (IllegalArgumentException ex) { throw MarketFailure.invalid("نوع العقار غير صالح."); }
        String meaning = text(node, "areaMeaning", 30, false);
        if (meaning != null && !MEANINGS.contains(meaning)) throw MarketFailure.invalid("معنى المساحة غير صالح.");
        String evidence = text(node, "evidenceNote", 2000, false);
        if ((type != null || meaning != null) && evidence == null) throw MarketFailure.invalid("تحديد النوع أو معنى المساحة يحتاج دليلًا صريحًا.");
        String area = decimal(node, "area", false);
        if (meaning != null && area == null) throw MarketFailure.invalid("معنى المساحة يتطلب قيمة مساحة.");
        JsonNode count = node.get("propertyCount");
        Integer n = null;
        if (!count.isNull()) {
            if (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 1 || count.intValue() > 1000000)
                throw MarketFailure.invalid("عدد العقارات من 1 إلى 1000000 أو null.");
            n = count.intValue();
        }
        return new Facts(date.toString(), city, text(node, "district", 150, false), text(node, "sourceClassification", 100, false),
                type, meaning, evidence, decimal(node, "price", true), area, n);
    }
    static String decimal(JsonNode node, String key, boolean required) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            if (required) throw MarketFailure.invalid("السعر مطلوب.");
            return null;
        }
        if (!value.isNumber() && !value.isTextual()) throw MarketFailure.invalid("قيمة عشرية غير صالحة: " + key);
        String raw = value.asText();
        if (raw.length() > 128 || !raw.matches("-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))
            throw MarketFailure.invalid("قيمة عشرية غير صالحة: " + key);
        try {
            BigDecimal decimal = new BigDecimal(raw);
            if (!SafeDecimalPrecisionValidator.withinLimits(decimal, 17, 2) || decimal.signum() <= 0)
                throw new NumberFormatException();
            return decimal.setScale(2).toPlainString();
        } catch (ArithmeticException | NumberFormatException ex) { throw MarketFailure.invalid("القيمة موجبة وبحد 17 خانة صحيحة وخانتين عشريتين: " + key); }
    }
    static Set<String> fields(Set<String> base, String... extra) { Set<String> result = new HashSet<>(base); result.addAll(List.of(extra)); return result; }
    static String json(Object value) {
        try { return JSON.writeValueAsString(value); } catch (IOException ex) { throw new IllegalStateException(ex); }
    }
}
