package com.pngencoder;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Objects;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubimageEncodingTest {
    @Test
    public void testSubimageEncoding() throws IOException {
        PngEncoderBufferedImageType[] typesToTest = new PngEncoderBufferedImageType[]{
                PngEncoderBufferedImageType.TYPE_BYTE_GRAY, PngEncoderBufferedImageType.TYPE_INT_RGB,
                PngEncoderBufferedImageType.TYPE_INT_ARGB, PngEncoderBufferedImageType.TYPE_INT_BGR,
                PngEncoderBufferedImageType.TYPE_3BYTE_BGR, PngEncoderBufferedImageType.TYPE_4BYTE_ABGR,
                PngEncoderBufferedImageType.TYPE_USHORT_GRAY
        };

        for (PngEncoderBufferedImageType type : typesToTest) {
            final BufferedImage bufferedImage = PngEncoderTestUtil.createTestImage(type);
            testImageEncoders(type, bufferedImage);
        }
    }

    private void testImageEncoders(PngEncoderBufferedImageType type, BufferedImage bufferedImage) throws IOException {
        PngEncoder plainCompressor = new PngEncoder().withPredictorEncoding(false).withCompressionLevel(0).withMultiThreadedCompressionEnabled(false);
        PngEncoder predictorCompressor = plainCompressor.withPredictorEncoding(true);
        PngEncoder multithreadCompressor = plainCompressor.withMultiThreadedCompressionEnabled(true);
        PngEncoder multithreadPredictorCompressor = plainCompressor.withMultiThreadedCompressionEnabled(true).withPredictorEncoding(true);
        for (PngEncoder encoder : new PngEncoder[]{
                plainCompressor,
                predictorCompressor,
                multithreadCompressor,
                multithreadPredictorCompressor
        }) {
            validateImage(type, bufferedImage, encoder);
            validateImage(type, bufferedImage.getSubimage(10, 10, 50, 50), encoder);
        }
    }

    @Test
    public void testUShortCustom() throws IOException {
        final BufferedImage sourceImage = PngEncoderTestUtil.createTestImage(PngEncoderBufferedImageType.TYPE_4BYTE_ABGR);
        BufferedImage imgRGBA = CustomDataBuffers.create16BitRGBA(sourceImage.getWidth(), sourceImage.getHeight());
        Graphics2D graphics = imgRGBA.createGraphics();
        graphics.drawImage(sourceImage, 0, 0, null);
        graphics.dispose();
        testImageEncoders(PngEncoderBufferedImageType.TYPE_CUSTOM, imgRGBA);
    }

    @Test
    public void testIntCustom() throws IOException {
        final BufferedImage sourceImage = ImageIO
                .read(Objects.requireNonNull(PngEncoderTest.class.getResourceAsStream("/png-encoder-logo.png")));
        BufferedImage imgRGBA = CustomDataBuffers.createInt8BitRGBA(sourceImage.getWidth(), sourceImage.getHeight());
        Graphics2D graphics = imgRGBA.createGraphics();
        graphics.drawImage(sourceImage, 0, 0, null);
        graphics.dispose();
        testImageEncoders(PngEncoderBufferedImageType.TYPE_CUSTOM, imgRGBA);
    }

    @Test
    public void testUShort() throws IOException {
        final BufferedImage sourceImage = PngEncoderTestUtil.createTestImage(PngEncoderBufferedImageType.TYPE_4BYTE_ABGR);
        ColorSpace targetCS = ColorModel.getRGBdefault().getColorSpace();
        int dataBufferType = DataBuffer.TYPE_USHORT;
        final ColorModel colorModel = new ComponentColorModel(targetCS, true, false,
                ColorModel.TRANSLUCENT, dataBufferType);
        WritableRaster targetRaster = Raster.createInterleavedRaster(dataBufferType, sourceImage.getWidth(), sourceImage.getHeight(),
                targetCS.getNumComponents() + 1, new Point(0, 0));

        BufferedImage imgRGBA = new BufferedImage(colorModel, targetRaster, false, new Hashtable<>());
        Graphics2D graphics = imgRGBA.createGraphics();
        graphics.drawImage(sourceImage, 0, 0, null);
        graphics.dispose();
        testImageEncoders(PngEncoderBufferedImageType.TYPE_CUSTOM, imgRGBA);
    }

    @Test
    public void testByteCustom() throws IOException {
        final BufferedImage sourceImage = PngEncoderTestUtil.createTestImage(PngEncoderBufferedImageType.TYPE_4BYTE_ABGR);
        BufferedImage imgRGBA = CustomDataBuffers.create8BitRGBA(sourceImage.getWidth(), sourceImage.getHeight(), sourceImage.getColorModel());
        Graphics2D graphics = imgRGBA.createGraphics();
        graphics.drawImage(sourceImage, 0, 0, null);
        graphics.dispose();
        testImageEncoders(PngEncoderBufferedImageType.TYPE_CUSTOM, imgRGBA);
    }

    private void validateImage(PngEncoderBufferedImageType type, BufferedImage image, PngEncoder encoder) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", outputStream);
        byte[] imgData2 = encoder.withBufferedImage(image).toBytes();
        BufferedImage img1 = ImageIO.read(new ByteArrayInputStream(outputStream.toByteArray()));
        BufferedImage img2 = ImageIO.read(new ByteArrayInputStream(imgData2));
        assertEquals(img1.getWidth(), img2.getWidth());
        assertEquals(img2.getHeight(), img2.getHeight());
        for (int y = 0; y < img1.getHeight(); y++) {
            for (int x = 0; x < img1.getWidth(); x++) {
                long rgbSource = image.getRGB(x, y) & 0xFFFFFFFFL;
                long rgb1 = img1.getRGB(x, y) & 0xFFFFFFFFL;
                long rgb2 = img2.getRGB(x, y) & 0xFFFFFFFFL;
                assertEquals(rgbSource, rgb1, "Source compare failure with type " + type + " "
                        + Long.toString(rgbSource, 16) + " != " + Long.toString(rgb1, 16));
                assertEquals(rgb1, rgb2, "Compare failure with type " + type + " " + Long.toString(rgb1, 16) + " != "
                        + Long.toString(rgb2, 16));
            }
        }
    }
}
