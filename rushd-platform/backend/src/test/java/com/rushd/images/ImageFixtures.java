package com.rushd.images;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

final class ImageFixtures {
    private ImageFixtures() {}
    static java.nio.file.Path privateRoot() {
        try { return java.nio.file.Files.createTempDirectory("rushd-p10-owned-",java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))).toRealPath(); }
        catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static byte[] image(String format, int width, int height, boolean alpha) throws IOException {
        var image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(alpha ? new Color(0,0,0,0) : Color.RED); g.fillRect(0,0,width,height);
        if (!alpha) { g.setColor(Color.BLUE); g.fillRect(width/2, 0,width/2,height/2); }
        g.dispose(); var out = new ByteArrayOutputStream(); ImageIO.write(image, format, out); image.flush(); return out.toByteArray();
    }
    static byte[] orientation(byte[] jpeg, int orientation) throws IOException {
        // Minimal, synthetic little-endian TIFF IFD with only orientation; no real image metadata.
        byte[] tiff = ByteBuffer.allocate(26).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .put((byte)'I').put((byte)'I').putShort((short)42).putInt(8).putShort((short)1)
                .putShort((short)0x112).putShort((short)3).putInt(1).putShort((short)orientation).putShort((short)0).putInt(0).array();
        var out = new ByteArrayOutputStream(); out.write(jpeg,0,2);
        out.write(new byte[]{(byte)255,(byte)225,0,34,'E','x','i','f',0,0}); out.write(tiff); out.write(jpeg,2,jpeg.length-2);
        return out.toByteArray();
    }
    static byte[] pngChunk(byte[] png, String type, byte[] payload) throws IOException {
        var chunk = new ByteArrayOutputStream(); var data = new DataOutputStream(chunk);
        data.writeInt(payload.length); byte[] name = type.getBytes(StandardCharsets.US_ASCII); data.write(name); data.write(payload);
        CRC32 crc = new CRC32(); crc.update(name); crc.update(payload); data.writeInt((int)crc.getValue());
        var out = new ByteArrayOutputStream(); out.write(png,0,33); out.write(chunk.toByteArray()); out.write(png,33,png.length-33);
        return out.toByteArray();
    }
}
