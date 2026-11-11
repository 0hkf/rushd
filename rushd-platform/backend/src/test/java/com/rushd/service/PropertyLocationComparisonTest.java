package com.rushd.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyLocationComparisonTest {
    @Test
    void oneDistrictPrefixIsRemovedWithoutInventingAnEmptyOrRepeatedName() {
        assertNull(PropertyLocationComparison.districtKey(null));
        assertNull(PropertyLocationComparison.districtKey("\u00a0 \t"));
        assertNull(PropertyLocationComparison.districtKey("حي"));
        assertNull(PropertyLocationComparison.districtKey(" حي  حي "));
        assertEquals("الياسمين", PropertyLocationComparison.districtKey("\u2003حي\u00a0الياسمين\n"));
        assertEquals("حي الياسمين", PropertyLocationComparison.districtKey("حي حي الياسمين"));
        assertEquals("حياة", PropertyLocationComparison.districtKey("حياة"));
        assertEquals("حيالياسمين", PropertyLocationComparison.districtKey("حيالياسمين"));
        assertEquals("ال_%نوارية", PropertyLocationComparison.districtKey("حي ال_%نوارية"));
    }

    @Test
    void cityNamesAreAnExplicitWhitelistNotFuzzyMatching() {
        assertTrue(PropertyLocationComparison.isMakkah("مكة"));
        assertTrue(PropertyLocationComparison.isMakkah(" مكة\u202f المكرمة "));
        assertTrue(PropertyLocationComparison.isMakkah("mAkKAH"));
        assertFalse(PropertyLocationComparison.isMakkah("Mecca"));
        assertFalse(PropertyLocationComparison.isMakkah("مكه"));
        assertFalse(PropertyLocationComparison.isMakkah("مكةالمكرمة"));
        assertFalse(PropertyLocationComparison.isMakkah("مكة المكرمة الجديدة"));
        assertFalse(PropertyLocationComparison.isMakkah(null));
    }
}
