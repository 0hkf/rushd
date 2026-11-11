package com.rushd.images;

/** Deliberately public-safe messages: never pass filesystem/decoder exceptions here. */
public final class ImageFailure extends RuntimeException {
    private final int status;
    public ImageFailure(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
    public static ImageFailure invalid(String message) { return new ImageFailure(400, message); }
    public static ImageFailure unavailable() { return new ImageFailure(503, "Image storage is unavailable"); }
    public static ImageFailure missing() { return new ImageFailure(404, "Image not found"); }
}
