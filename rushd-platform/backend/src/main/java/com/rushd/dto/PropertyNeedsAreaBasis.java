package com.rushd.dto;

import com.rushd.entity.Property;
import com.rushd.validation.PropertySpecificationFields;
import java.math.BigDecimal;

/** One explicitly chosen measurement; never fall back from a named measurement. */
public enum PropertyNeedsAreaBasis {
    ADVERTISED(null), LAND("landArea"), UNIT_NET("unitNetArea"),
    UNIT_GROSS("unitGrossArea"), TOTAL_BUILT("totalBuiltArea");

    private final String field;
    PropertyNeedsAreaBasis(String field) { this.field = field; }
    public String field() { return field; }
    public BigDecimal read(Property property) {
        if (this == ADVERTISED) return property.getArea();
        return property.getSpecifications() == null ? null
                : (BigDecimal) PropertySpecificationFields.BY_NAME.get(field).read().apply(property.getSpecifications());
    }
}
