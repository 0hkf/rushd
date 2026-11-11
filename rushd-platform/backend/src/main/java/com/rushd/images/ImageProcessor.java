package com.rushd.images;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;
import com.drew.lang.ByteArrayReader;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.*;
import javax.imageio.stream.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.zip.CRC32;

@Component
public class ImageProcessor {
    public static final int MAX_SOURCE = 8 * 1024 * 1024;
    public record Processed(byte[] display, byte[] thumbnail, int displayWidth, int displayHeight,
                            int thumbnailWidth, int thumbnailHeight) {}
    public byte[] source(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ImageFailure.invalid("One image file is required");
        if (file.getSize() > MAX_SOURCE) throw new ImageFailure(413, "Image exceeds 8 MiB");
        try (var in = file.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_SOURCE + 1);
            if (bytes.length > MAX_SOURCE) throw new ImageFailure(413, "Image exceeds 8 MiB");
            return bytes;
        } catch (IOException e) { throw ImageFailure.invalid("Image cannot be read"); }
    }
    public Processed process(byte[] source, String filename, String mime) {
        if (source.length == 0) throw ImageFailure.invalid("Image is empty");
        if (source.length > MAX_SOURCE) throw new ImageFailure(413, "Image exceeds 8 MiB");
        if (filename == null || filename.length() > 200 || filename.contains("/") || filename.contains("\\")
                || filename.contains("..") || filename.codePoints().anyMatch(Character::isISOControl))
            throw ImageFailure.invalid("Invalid image filename");
        boolean png = source.length >= 8 && Arrays.equals(Arrays.copyOf(source, 8),
                new byte[]{(byte)137,80,78,71,13,10,26,10});
        boolean jpeg = source.length >= 4 && (source[0] & 255) == 255 && (source[1] & 255) == 216;
        String lower = filename.toLowerCase(Locale.ROOT);
        if ((!png && !jpeg) || (png && (!lower.endsWith(".png") || !"image/png".equals(mime)))
                || (jpeg && (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg")) || !"image/jpeg".equals(mime))))
            throw new ImageFailure(415, "Only matching JPEG or PNG files are supported");
        try {
            Metadata metadata;
            byte[] decodeSource = source;
            if (png) {
                var pixelSource = new ByteArrayOutputStream();
                metadata = validatePng(source, pixelSource);
                decodeSource = pixelSource.toByteArray();
            }
            else {
                validateJpeg(source);
                metadata = JpegMetadataReader.readMetadata(new ByteArrayInputStream(source), List.of(new ExifReader()));
            }
            for (var directory : metadata.getDirectories()) {
                if (directory.hasErrors()) throw ImageFailure.invalid("Invalid image metadata");
            }
            int orientation = 1;
            var exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                orientation = exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                if (orientation < 1 || orientation > 8) throw ImageFailure.invalid("Invalid image orientation");
            }
            // Memory streams prevent ImageIO's global disk cache from keeping source files.
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(decodeSource))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw ImageFailure.invalid("Invalid image content");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, false, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    dimensions(width, height);
                    var raw = reader.getRawImageType(0);
                    if (raw == null || (raw.getColorModel().getNumColorComponents() != 1
                            && raw.getColorModel().getNumColorComponents() != 3)
                            || Arrays.stream(raw.getColorModel().getComponentSize()).anyMatch(bits -> bits != 8))
                        throw new ImageFailure(415, "Only 8-bit RGB, RGBA or grayscale is supported");
                    boolean[] warning = {false};
                    reader.addIIOReadWarningListener((r, message) -> warning[0] = true);
                    BufferedImage decoded = reader.read(0);
                    if (warning[0] || decoded == null) throw ImageFailure.invalid("Damaged image content");
                    BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = rgb.createGraphics();
                    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
                    graphics.drawImage(decoded, 0, 0, null); graphics.dispose(); decoded.flush();
                    BufferedImage upright = orient(rgb, orientation);
                    BufferedImage display = resize(upright, 2560), thumbnail = resize(upright, 640);
                    byte[] displayBytes = jpeg(display, 2 * 1024 * 1024);
                    byte[] thumbnailBytes = jpeg(thumbnail, 256 * 1024);
                    Processed result = new Processed(displayBytes, thumbnailBytes, display.getWidth(), display.getHeight(),
                            thumbnail.getWidth(), thumbnail.getHeight());
                    display.flush(); thumbnail.flush(); upright.flush(); rgb.flush();
                    return result;
                } finally { reader.dispose(); }
            }
        } catch (ImageFailure e) { throw e; }
        catch (Exception e) { throw ImageFailure.invalid("Invalid or damaged image content"); }
    }
    static void dimensions(int width, int height) {
        if (Math.min(width, height) < 480 || Math.max(width, height) < 640 || width > 8000 || height > 8000
                || (long) width * height > 16_000_000) throw ImageFailure.invalid("Image dimensions are outside the supported limits");
    }
    private Metadata validatePng(byte[] bytes, ByteArrayOutputStream pixelSource) {
        pixelSource.write(bytes,0,8);
        int offset = 8; boolean header = false, data = false, end = false, dataEnded = false;
        Metadata metadata = new Metadata();
        while (offset < bytes.length) {
            if (bytes.length - offset < 12) throw ImageFailure.invalid("Truncated PNG");
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4).getInt());
            if (length > bytes.length - offset - 12L) throw ImageFailure.invalid("Invalid PNG chunk");
            int size = (int) length;
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            if (!type.matches("[a-zA-Z]{2}[A-Z][a-zA-Z]")) throw ImageFailure.invalid("Invalid PNG chunk type");
            if (Character.isUpperCase(type.charAt(0)) && !Set.of("IHDR","PLTE","IDAT","IEND").contains(type))
                throw new ImageFailure(415,"Unsupported critical PNG chunk");
            if (data && !type.equals("IDAT")) dataEnded = true;
            if (dataEnded && type.equals("IDAT")) throw ImageFailure.invalid("Non-contiguous PNG pixel data");
            if (data && (type.equals("PLTE") || type.equals("tRNS"))) throw ImageFailure.invalid("Invalid PNG chunk order");
            CRC32 crc = new CRC32(); crc.update(bytes, offset + 4, size + 4);
            long expected = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset + 8 + size, 4).getInt());
            if (crc.getValue() != expected) throw ImageFailure.invalid("Invalid PNG checksum");
            if (!header && !type.equals("IHDR")) throw ImageFailure.invalid("Invalid PNG header");
            if (type.equals("IHDR")) {
                if (header || size != 13) throw ImageFailure.invalid("Invalid PNG header");
                dimensions(ByteBuffer.wrap(bytes, offset + 8, 4).getInt(), ByteBuffer.wrap(bytes, offset + 12, 4).getInt());
                int depth = bytes[offset + 16] & 255, color = bytes[offset + 17] & 255;
                if (depth != 8 || !Set.of(0, 2, 4, 6).contains(color))
                    throw new ImageFailure(415, "Only 8-bit RGB, RGBA or grayscale PNG is supported");
                header = true;
            }
            if (Set.of("acTL", "fcTL", "fdAT").contains(type)) throw new ImageFailure(415, "Animated PNG is not supported");
            if (type.equals("eXIf")) new ExifReader().extract(new ByteArrayReader(Arrays.copyOfRange(bytes, offset + 8, offset + 8 + size)), metadata);
            if (type.equals("IDAT")) data = true;
            // Do not ask ImageIO to inflate unbounded zTXt/iTXt/iCCP ancillary metadata.
            // Preserve pixel data and transparency only; orientation was read separately above.
            if (Set.of("IHDR","IDAT","IEND","tRNS").contains(type)) pixelSource.write(bytes,offset,size+12);
            offset += size + 12;
            if (type.equals("IEND")) {
                if (size != 0 || !data || offset != bytes.length) throw ImageFailure.invalid("Invalid PNG ending");
                end = true; break;
            }
        }
        if (!header || !end) throw ImageFailure.invalid("Incomplete PNG");
        return metadata;
    }
    private void validateJpeg(byte[] bytes) {
        int p = 2; boolean entropy = false;
        while (p < bytes.length) {
            if (entropy) {
                while (p < bytes.length && (bytes[p] & 255) != 255) p++;
            }
            if (p >= bytes.length || (bytes[p++] & 255) != 255) throw ImageFailure.invalid("Invalid JPEG marker");
            while (p < bytes.length && (bytes[p] & 255) == 255) p++;
            if (p >= bytes.length) break;
            int marker = bytes[p++] & 255;
            if (entropy && (marker == 0 || marker >= 208 && marker <= 215)) continue;
            if (marker == 217) {
                if (p != bytes.length) throw ImageFailure.invalid("Trailing image content is not supported");
                return;
            }
            if (marker == 216 || marker == 0 || p + 2 > bytes.length) throw ImageFailure.invalid("Invalid JPEG structure");
            int size = (bytes[p] & 255) * 256 + (bytes[p + 1] & 255);
            if (size < 2 || size > bytes.length - p) throw ImageFailure.invalid("Truncated JPEG segment");
            p += size; entropy = marker == 218;
        }
        throw ImageFailure.invalid("Incomplete JPEG");
    }
    static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation == 1) return source;
        int w = source.getWidth(), h = source.getHeight();
        BufferedImage output = new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int tx = x, ty = y;
            switch (orientation) {
                case 2 -> tx = w - 1 - x;
                case 3 -> { tx = w - 1 - x; ty = h - 1 - y; }
                case 4 -> ty = h - 1 - y;
                case 5 -> { tx = y; ty = x; }
                case 6 -> { tx = h - 1 - y; ty = x; }
                case 7 -> { tx = h - 1 - y; ty = w - 1 - x; }
                case 8 -> { tx = y; ty = w - 1 - x; }
                default -> throw ImageFailure.invalid("Invalid image orientation");
            }
            output.setRGB(tx, ty, source.getRGB(x, y));
        }
        return output;
    }
    private BufferedImage resize(BufferedImage source, int max) {
        double ratio = Math.min(1d, (double) max / Math.max(source.getWidth(), source.getHeight()));
        if (ratio == 1d) return source;
        BufferedImage output = new BufferedImage(Math.max(1, (int) Math.round(source.getWidth() * ratio)),
                Math.max(1, (int) Math.round(source.getHeight() * ratio)), BufferedImage.TYPE_INT_RGB);
        var graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, output.getWidth(), output.getHeight(), null); graphics.dispose();
        return output;
    }
    private byte[] jpeg(BufferedImage image, int maxBytes) throws IOException {
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var buffer = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(buffer)) {
            writer.setOutput(output);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); parameters.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), parameters);
            output.flush();
        } finally { writer.dispose(); }
        if (buffer.size() > maxBytes) throw ImageFailure.invalid("Processed image exceeds the output size limit");
        return buffer.toByteArray();
    }
}
