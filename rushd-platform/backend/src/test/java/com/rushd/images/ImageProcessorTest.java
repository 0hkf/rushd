package com.rushd.images;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ImageProcessorTest {
    private final ImageProcessor processor = new ImageProcessor();
    @ParameterizedTest @ValueSource(strings = {"jpeg", "png"})
    void validFilesBecomeMetadataFreeJpegsWithoutUpscaling(String format) throws Exception {
        byte[] source = ImageFixtures.image(format,640,480,false);
        var result = processor.process(source,"fixture."+format, "image/"+format);
        assertEquals(640,result.displayWidth()); assertEquals(480,result.displayHeight());
        assertEquals(640,result.thumbnailWidth()); assertEquals(480,result.thumbnailHeight());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(result.display())));
        assertNull(ImageMetadataReader.readMetadata(new ByteArrayInputStream(result.display())).getFirstDirectoryOfType(ExifIFD0Directory.class));
    }
    @Test void transparencyIsFlattenedOnWhite() throws Exception {
        var result = processor.process(ImageFixtures.image("png",640,480,true),"a.png","image/png");
        Color pixel = new Color(ImageIO.read(new ByteArrayInputStream(result.display())).getRGB(20,20));
        assertTrue(pixel.getRed()>250 && pixel.getGreen()>250 && pixel.getBlue()>250);
    }
    @ParameterizedTest @ValueSource(ints = {1,2,3,4,5,6,7,8})
    void exifOrientationCorrectedAndRemoved(int orientation) throws Exception {
        byte[] source = ImageFixtures.orientation(ImageFixtures.image("jpeg",640,480,false),orientation);
        var result = processor.process(source,"a.jpg","image/jpeg");
        assertEquals(orientation>=5?480:640,result.displayWidth());
        assertEquals(orientation>=5?640:480,result.displayHeight());
        assertNull(ImageMetadataReader.readMetadata(new ByteArrayInputStream(result.display())).getFirstDirectoryOfType(ExifIFD0Directory.class));
        var sourceImage = ImageIO.read(new ByteArrayInputStream(source));
        BufferedImage expected = ImageProcessor.orient(sourceImage,orientation);
        BufferedImage actual = ImageIO.read(new ByteArrayInputStream(result.display()));
        assertEquals(new Color(expected.getRGB(20,20)).getRed(),new Color(actual.getRGB(20,20)).getRed(),5);
    }
    @ParameterizedTest @CsvSource({"479,640","480,639","8001,480","4001,4000","2147483647,2147483647","0,640"})
    void rejectsDimensionLimitsBeforeFullDecode(int width,int height) {
        assertEquals(400,assertThrows(ImageFailure.class,()->ImageProcessor.dimensions(width,height)).status());
    }
    @ParameterizedTest @CsvSource({"480,640","640,480","8000,2000","4000,4000"})
    void acceptsDimensionBoundaries(int width,int height) { assertDoesNotThrow(()->ImageProcessor.dimensions(width,height)); }
    @ParameterizedTest @ValueSource(strings = {"a.svg","a.gif","a.webp","a.heic","a.zip","../a.jpg","a/fixture.jpg","a\\fixture.jpg","a\u0000.jpg"})
    void rejectsUnsupportedOrUnsafeNames(String filename) throws Exception {
        assertThrows(ImageFailure.class,()->processor.process(ImageFixtures.image("jpeg",640,480,false),filename,"image/jpeg"));
    }
    @Test void filenameLengthBoundaryAndMismatch() throws Exception {
        byte[] source = ImageFixtures.image("jpeg",640,480,false);
        assertDoesNotThrow(()->processor.process(source,"a".repeat(196)+".jpg","image/jpeg"));
        assertThrows(ImageFailure.class,()->processor.process(source,"a".repeat(197)+".jpg","image/jpeg"));
        assertEquals(415,assertThrows(ImageFailure.class,()->processor.process(source,"a.png","image/png")).status());
        assertEquals(415,assertThrows(ImageFailure.class,()->processor.process(source,"a.jpg","text/plain")).status());
    }
    @Test void rejectsFakeTruncatedAndTrailingPolyglot() throws Exception {
        byte[] source = ImageFixtures.image("jpeg",640,480,false);
        for (byte[] invalid : List.of(new byte[]{1,2,3},Arrays.copyOf(source,source.length-1),Arrays.copyOf(source,source.length+10)))
            assertThrows(ImageFailure.class,()->processor.process(invalid,"a.jpg","image/jpeg"));
        byte[] png = ImageFixtures.image("png",640,480,false);
        assertThrows(ImageFailure.class,()->processor.process(Arrays.copyOf(png,png.length+1),"a.png","image/png"));
        png[40] ^= 1; assertThrows(ImageFailure.class,()->processor.process(png,"a.png","image/png"));
    }
    @Test void rejectsAnimationAnd16bitPng() throws Exception {
        byte[] png = ImageFixtures.pngChunk(ImageFixtures.image("png",640,480,false),"acTL",new byte[8]);
        assertEquals(415,assertThrows(ImageFailure.class,()->processor.process(png,"a.png","image/png")).status());
        var image = new BufferedImage(640,480,BufferedImage.TYPE_USHORT_GRAY); var out = new ByteArrayOutputStream();
        ImageIO.write(image,"png",out);
        assertEquals(415,assertThrows(ImageFailure.class,()->processor.process(out.toByteArray(),"a.png","image/png")).status());
    }
    @Test void sourceExactLimitAndOneMoreByte() {
        byte[] exact=new byte[ImageProcessor.MAX_SOURCE];
        assertEquals(exact.length,processor.source(new org.springframework.mock.web.MockMultipartFile("file","a.jpg","image/jpeg",exact)).length);
        assertEquals(413,assertThrows(ImageFailure.class,()->processor.source(new org.springframework.mock.web.MockMultipartFile("file","a.jpg","image/jpeg",new byte[ImageProcessor.MAX_SOURCE+1]))).status());
    }
    @ParameterizedTest @ValueSource(ints={640,2560})
    void excessiveOutputIsRejectedWithoutSilentQualityReduction(int edge) throws Exception {
        BufferedImage noise=new BufferedImage(edge,edge,BufferedImage.TYPE_INT_RGB);var random=new Random(91010);
        for(int y=0;y<edge;y++)for(int x=0;x<edge;x++)noise.setRGB(x,y,random.nextInt(1<<24));
        var out=new ByteArrayOutputStream();String format=edge==640?"png":"jpeg";assertTrue(ImageIO.write(noise,format,out));
        var failure=assertThrows(ImageFailure.class,()->processor.process(out.toByteArray(),"noise."+format,"image/"+format));
        assertEquals(400,failure.status());assertTrue(failure.getMessage().contains("output size"));
    }
    @Test void pngTextMetadataIsNotRetained() throws Exception {
        String canary="P10-SYNTHETIC-GPS-XMP-CANARY";
        byte[] source=ImageFixtures.pngChunk(ImageFixtures.image("png",640,480,false),"tEXt",("Comment\0"+canary).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var result=processor.process(source,"a.png","image/png");
        assertFalse(new String(result.display(),java.nio.charset.StandardCharsets.ISO_8859_1).contains(canary));
        assertFalse(new String(result.thumbnail(),java.nio.charset.StandardCharsets.ISO_8859_1).contains(canary));
    }
    @Test void validCmykJpegIsNotAcceptedAsRgb() throws Exception {
        var cmyk=new java.awt.color.ColorSpace(java.awt.color.ColorSpace.TYPE_CMYK,4) {
            public float[] toRGB(float[] c){return new float[]{1-c[0],1-c[1],1-c[2]};}
            public float[] fromRGB(float[] c){return new float[]{1-c[0],1-c[1],1-c[2],0};}
            public float[] toCIEXYZ(float[] c){return toRGB(c);}
            public float[] fromCIEXYZ(float[] c){return fromRGB(c);}
        };
        var model=new java.awt.image.ComponentColorModel(cmyk,false,false,java.awt.Transparency.OPAQUE,java.awt.image.DataBuffer.TYPE_BYTE);
        var raster=java.awt.image.Raster.createInterleavedRaster(java.awt.image.DataBuffer.TYPE_BYTE,640,480,4,null);
        var image=new BufferedImage(model,raster,false,null);var out=new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image,"jpeg",out));
        assertEquals(415,assertThrows(ImageFailure.class,()->processor.process(out.toByteArray(),"cmyk.jpg","image/jpeg")).status());
    }
    @ParameterizedTest @CsvSource({"4000,4000","8000,2000"})
    void actualImagesAtPixelAndEdgeLimitsCanBeProcessed(int width,int height) throws Exception {
        var result=processor.process(ImageFixtures.image("jpeg",width,height,false),"limit.jpg","image/jpeg");
        assertEquals(2560,result.displayWidth());assertTrue(result.thumbnailWidth()<=640);
    }
    @Test void compressedAncillaryMetadataIsStrippedWithoutInflation() throws Exception {
        // Not a metadata parser: even a malformed compressed text payload is removed, not decoded.
        byte[] source=ImageFixtures.pngChunk(ImageFixtures.image("png",640,480,false),"zTXt",new byte[]{'X',0,0,1,2,3,4});
        assertDoesNotThrow(()->processor.process(source,"metadata.png","image/png"));
    }
    @Test void grayscaleAndProgressiveJpegRemainSupported() throws Exception {
        var gray=new BufferedImage(640,480,BufferedImage.TYPE_BYTE_GRAY);var out=new ByteArrayOutputStream();ImageIO.write(gray,"png",out);
        assertDoesNotThrow(()->processor.process(out.toByteArray(),"gray.png","image/png"));
        var writer=ImageIO.getImageWritersByFormatName("jpeg").next();var progressive=new ByteArrayOutputStream();
        try(var target=new javax.imageio.stream.MemoryCacheImageOutputStream(progressive)) {
            writer.setOutput(target);var params=writer.getDefaultWriteParam();params.setProgressiveMode(javax.imageio.ImageWriteParam.MODE_DEFAULT);
            writer.write(null,new javax.imageio.IIOImage(new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB),null,null),params);target.flush();
        } finally {writer.dispose();}
        assertDoesNotThrow(()->processor.process(progressive.toByteArray(),"progressive.jpg","image/jpeg"));
    }
    @Test void sourceByteLimitCheckedEvenForForgedMultipartLength() {
        var file = new org.springframework.mock.web.MockMultipartFile("file","a.jpg","image/jpeg",new byte[ImageProcessor.MAX_SOURCE+1]);
        assertEquals(413,assertThrows(ImageFailure.class,()->processor.source(file)).status());
    }
    @Test void resizePreservesRatio() throws Exception {
        var result = processor.process(ImageFixtures.image("jpeg",3200,2400,false),"a.jpg","image/jpeg");
        assertEquals(2560,result.displayWidth()); assertEquals(1920,result.displayHeight());
        assertEquals(640,result.thumbnailWidth()); assertEquals(480,result.thumbnailHeight());
    }
}
