package com.rushd.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The scoped parser restricts value to a Boolean or the field's exact enum type. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropertySpecificationCriterion(Integer min, Integer max, Object value,
                                              PropertyNeedsRequirement requirement) { }
