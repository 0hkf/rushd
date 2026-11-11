package com.rushd.dto;

import java.time.Instant;
import java.util.List;

/** A dated, unsaved snapshot. Values in evidence are the public facts actually used. */
public record SelectedPropertyEvaluation(String propertyId, Instant evaluatedAt, String rulesVersion,
        PropertyNeedsResponse needs, String factsFingerprint, Classification classification,
        Coverage coverage, int preferredCount, int preferredMatchedCount, List<Evidence> evidence) {
    public enum Classification { NOT_SUITABLE, INSUFFICIENT_DATA, BASIC_MATCH, VERY_SUITABLE, SUITABLE, PARTIALLY_SUITABLE }
    public enum Outcome { MATCHED, MISMATCHED, UNKNOWN, NOT_COMPARABLE }
    public record Coverage(long matched, long mismatched, long unknown, long notComparable) { }
    public record Evidence(String id, PropertyNeedsRequirement requirement, Object requested, Object actual,
                           String unit, Outcome outcome, String reasonCode) { }
}
