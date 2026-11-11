package com.rushd.dto;

/** Requested evidence only. No inferred facts, user identity or publisher information. */
public record PropertySpecificationMatch(String field, PropertySpecificationCriterion requested,
                                          Object actual, PropertyCriterionMatch outcome) { }
