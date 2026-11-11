package com.rushd.drafts;

/** Generic status and message only; never include private draft content. */
public final class DraftFailure extends RuntimeException {
    private final int status;
    public DraftFailure(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
