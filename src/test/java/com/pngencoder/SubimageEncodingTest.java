package com.pngencoder;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

        PngEncoder plainCompressor = new PngEncoder().withPredictorEncoding(false).withCompressionLevel(0).withMultiThreadedCompressionEnabled(false);
        PngEncoder predictorCompressor = plainCompressor.withPredictorEncoding(true);
        PngEncoder multithreadCompressor = plainCompressor.withMultiThreadedCompressionEnabled(true);
        PngEncoder multithreadPredictorCompressor = plainCompressor.withMultiThreadedCompressionEnabled(true).withPredictorEncoding(true);
        for (PngEncoderBufferedImageType type : typesToTest) {
            final BufferedImage bufferedImage = PngEncoderTestUtil.createTestImage(type);
            for (PngEncoder encoder : new PngEncoder[]{plainCompressor, predictorCompressor, multithreadCompressor, multithreadPredictorCompressor}) {
                validateImage(type, bufferedImage, encoder);
                validateImage(type, bufferedImage.getSubimage(10, 10, 50, 50), encoder);
            }
        }
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
