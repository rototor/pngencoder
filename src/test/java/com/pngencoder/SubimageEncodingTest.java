package com.pngencoder;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubimageEncodingTest {
	@Test
	public void testSubimageEncoding() throws IOException {
		PngEncoderBufferedImageType[] typesToTest = new PngEncoderBufferedImageType[] {
				PngEncoderBufferedImageType.TYPE_INT_RGB, PngEncoderBufferedImageType.TYPE_INT_ARGB,
				PngEncoderBufferedImageType.TYPE_INT_ARGB_PRE, PngEncoderBufferedImageType.TYPE_INT_BGR,
				PngEncoderBufferedImageType.TYPE_3BYTE_BGR, PngEncoderBufferedImageType.TYPE_4BYTE_ABGR,
				PngEncoderBufferedImageType.TYPE_BYTE_GRAY, PngEncoderBufferedImageType.TYPE_USHORT_GRAY };

		for (PngEncoderBufferedImageType type : typesToTest) {
			final BufferedImage bufferedImage = PngEncoderTestUtil.createTestImage(type);
			validateImage(bufferedImage);
			validateImage(bufferedImage.getSubimage(10, 10, 50, 50));
		}
	}

	private void validateImage(BufferedImage image) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ImageIO.write(image, "PNG", outputStream);
		byte[] imgData2 = new PngEncoder().withBufferedImage(image).toBytes();
		BufferedImage img1 = ImageIO.read(new ByteArrayInputStream(outputStream.toByteArray()));
		BufferedImage img2 = ImageIO.read(new ByteArrayInputStream(imgData2));
		assertEquals(img1.getWidth(), img2.getWidth());
		assertEquals(img2.getHeight(), img2.getHeight());
		for (int y = 0; y < img1.getHeight(); y++) {
			for (int x = 0; x < img1.getWidth(); x++) {
				int rgb1 = img1.getRGB(x, y);
				int rgb2 = img2.getRGB(x, y);
				assertEquals(rgb1, rgb2);
			}
		}
	}
}
