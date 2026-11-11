package com.rushd.favorites;

public class FavoriteFailure extends RuntimeException {
    private final int status;
    public FavoriteFailure(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
