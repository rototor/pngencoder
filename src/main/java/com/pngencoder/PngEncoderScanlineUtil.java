package com.pngencoder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;

class PngEncoderScanlineUtil {
	private PngEncoderScanlineUtil() {
	}

	/**
	 * Consumer for the image rows as bytes. Every row has the predictor marker as
	 * first byte (with 0 for no predictor encoding), after that all image bytes
	 * follow.
	 */
	static abstract class AbstractPNGLineConsumer {
		abstract void consume(byte[] currRow, byte[] prevRow) throws IOException;
	}

	static class ByteBufferPNGLineConsumer extends AbstractPNGLineConsumer {
		byte[] bytes;
		int currentOffset;

		ByteBufferPNGLineConsumer(int byteCount) {
			bytes = new byte[byteCount];
		}

		void consume(byte[] currRow, byte[] prevRow) {
			System.arraycopy(currRow, 0, bytes, currentOffset, currRow.length);
			currentOffset += currRow.length;
		}
	}

	static byte[] get(BufferedImage bufferedImage) throws IOException {
		final int width = bufferedImage.getWidth();
		final int height = bufferedImage.getHeight();

		final PngEncoderBufferedImageType type = PngEncoderBufferedImageType.valueOf(bufferedImage);

		WritableRaster raster = bufferedImage.getRaster();
		if (type == PngEncoderBufferedImageType.TYPE_INT_RGB) {
			return getIntRgb(raster, width, height);
		}

		if (type == PngEncoderBufferedImageType.TYPE_INT_ARGB) {
			return getIntArgb(raster, width, height);
		}

		// TODO: TYPE_INT_ARGB_PRE

		if (type == PngEncoderBufferedImageType.TYPE_INT_BGR) {
			return getIntBgr(raster, width, height);
		}

		if (type == PngEncoderBufferedImageType.TYPE_3BYTE_BGR) {
			return get3ByteBgr(raster, width, height);
		}

		if (type == PngEncoderBufferedImageType.TYPE_4BYTE_ABGR) {
			return get4ByteAbgr(raster, width, height);
		}

		// TODO: TYPE_4BYTE_ABGR_PRE

		// TODO: TYPE_USHORT_565_RGB
		// TODO: TYPE_USHORT_555_RGB

		if (type == PngEncoderBufferedImageType.TYPE_BYTE_GRAY) {
			return getByteGray(raster, width, height);
		}

		if (type == PngEncoderBufferedImageType.TYPE_USHORT_GRAY) {
			return getUshortGray(raster, width, height);
		}

		// Fallback for unsupported type.
		final int[] elements = bufferedImage.getRGB(0, 0, width, height, null, 0, width);
		if (bufferedImage.getTransparency() == Transparency.OPAQUE) {
			return getIntRgb(elements, width, height);
		} else {
			return getIntArgb(elements, width, height);
		}
	}

	static void stream(BufferedImage bufferedImage, int yStart, int height, AbstractPNGLineConsumer consumer)
			throws IOException {
		final int width = bufferedImage.getWidth();
		final int imageHeight = bufferedImage.getHeight();
		assert (height <= imageHeight - yStart);

		final PngEncoderBufferedImageType type = PngEncoderBufferedImageType.valueOf(bufferedImage);

		WritableRaster raster = bufferedImage.getRaster();
		switch (type) {
		case TYPE_INT_RGB:
			getIntRgb(raster, yStart, width, height, consumer);
			break;
		case TYPE_INT_ARGB:
			getIntArgb(raster, yStart, width, height, consumer);
			break;

		// TODO: TYPE_INT_ARGB_PRE
		case TYPE_INT_BGR:
			getIntBgr(raster, yStart, width, height, consumer);
			break;
		case TYPE_3BYTE_BGR:
			get3ByteBgr(raster, yStart, width, height, consumer);
			break;
		case TYPE_4BYTE_ABGR:
			get4ByteAbgr(raster, yStart, width, height, consumer);
			break;
		// TODO: TYPE_4BYTE_ABGR_PRE
		// TODO: TYPE_USHORT_565_RGB
		// TODO: TYPE_USHORT_555_RGB
		case TYPE_BYTE_GRAY:
			getByteGray(raster, yStart, width, height, consumer);
			break;
		case TYPE_USHORT_GRAY:
			getUshortGray(raster, yStart, width, height, consumer);
			break;
		default:
			// Fallback for unsupported type.
			final int[] elements = bufferedImage.getRGB(0, yStart, width, height, null, 0, width);
			if (bufferedImage.getTransparency() == Transparency.OPAQUE) {
				getIntRgb(elements, yStart, width, height, consumer);
			} else {
				getIntArgb(elements, yStart, width, height, consumer);
			}
		}
	}

	static byte[] getIntRgb(int[] elements, int width, int height) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(rowByteSize * height);
		getIntRgb(elements, 0, width, height, consumer);
		return consumer.bytes;

	}

	static void getIntRgb(int[] elements, int yStart, int width, int height, AbstractPNGLineConsumer consumer)
			throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		byte[] currLine = new byte[rowByteSize];
		byte[] prevLine = new byte[rowByteSize];

		for (int y = yStart; y < height; y++) {
			int yOffset = y * width;

			int rowByteOffset = 1;

			for (int x = 0; x < width; x++) {
				final int element = elements[yOffset + x];
				currLine[rowByteOffset++] = (byte) (element >> 16); // R
				currLine[rowByteOffset++] = (byte) (element >> 8); // G
				currLine[rowByteOffset++] = (byte) element; // B
			}
			consumer.consume(currLine, prevLine);
			{
				byte[] b = currLine;
				currLine = prevLine;
				prevLine = b;
			}
		}
	}

	static byte[] getIntArgb(int[] elements, int width, int height) throws IOException {
		final int channels = 4;
		final int rowByteSize = 1 + channels * width;
		ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(rowByteSize * height);
		getIntArgb(elements, 0, width, height, consumer);
		return consumer.bytes;
	}

	static void getIntArgb(int[] elements, int yStart, int width, int height, AbstractPNGLineConsumer consumer)
			throws IOException {
		final int channels = 4;
		final int rowByteSize = 1 + channels * width;
		byte[] currLine = new byte[rowByteSize];
		byte[] prevLine = new byte[rowByteSize];

		for (int y = yStart; y < height; y++) {
			int yOffset = y * width;

			int rowByteOffset = 1;
			for (int x = 0; x < width; x++) {
				final int element = elements[yOffset + x];
				currLine[rowByteOffset++] = (byte) (element >> 16); // R
				currLine[rowByteOffset++] = (byte) (element >> 8); // G
				currLine[rowByteOffset++] = (byte) element; // B
				currLine[rowByteOffset++] = (byte) (element >> 24); // A
			}
			consumer.consume(currLine, prevLine);
			{
				byte[] b = currLine;
				currLine = prevLine;
				prevLine = b;
			}
		}
	}

	static byte[] getIntRgb(WritableRaster imageRaster, int width, int height) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(rowByteSize * height);
		getIntRgb(imageRaster, 0, width, height, consumer);
		return consumer.bytes;
	}

	static void getIntRgb(WritableRaster imageRaster, int yStart, int width, int height,
			AbstractPNGLineConsumer consumer) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		byte[] currLine = new byte[rowByteSize];
		byte[] prevLine = new byte[rowByteSize];
		final int[] elements = new int[width];
		for (int y = yStart; y < height; y++) {
			imageRaster.getDataElements(0, y, width, 1, elements);

			int rowByteOffset = 1;
			for (int x = 0; x < width; x++) {
				final int element = elements[x];
				currLine[rowByteOffset++] = (byte) (element >> 16); // R
				currLine[rowByteOffset++] = (byte) (element >> 8); // G
				currLine[rowByteOffset++] = (byte) element; // B
			}

			consumer.consume(currLine, prevLine);

			{
				byte[] b = currLine;
				currLine = prevLine;
				prevLine = b;
			}
		}

	}

	static byte[] getIntArgb(WritableRaster imageRaster, int width, int height) throws IOException {
		final int channels = 4;
		final int rowByteSize = 1 + channels * width;
		ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(rowByteSize * height);
		getIntArgb(imageRaster, 0, width, height, consumer);
		return consumer.bytes;
	}

	static void getIntArgb(WritableRaster imageRaster, int yStart, int width, int height,
			AbstractPNGLineConsumer consumer) throws IOException {
		final int channels = 4;
		final int rowByteSize = 1 + channels * width;
		final int[] elements = new int[width];
		byte[] currLine = new byte[rowByteSize];
		byte[] prevLine = new byte[rowByteSize];

		for (int y = yStart; y < height; y++) {
			imageRaster.getDataElements(0, y, width, 1, elements);

			int rowByteOffset = 1;

			for (int x = 0; x < width; x++) {
				final int element = elements[x];
				currLine[rowByteOffset++] = (byte) (element >> 16); // R
				currLine[rowByteOffset++] = (byte) (element >> 8); // G
				currLine[rowByteOffset++] = (byte) element; // B
				currLine[rowByteOffset++] = (byte) (element >> 24); // A
			}

			consumer.consume(currLine, prevLine);

			{
				byte[] b = currLine;
				currLine = prevLine;
				prevLine = b;
			}
		}
	}

	static byte[] getIntBgr(WritableRaster imageRaster, int width, int height) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(rowByteSize * height);
		getIntBgr(imageRaster, 0, width, height, consumer);
		return consumer.bytes;

	}

	static void getIntBgr(WritableRaster imageRaster, int yStart, int width, int height,
			AbstractPNGLineConsumer consumer) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		final int[] elements = new int[width];
		byte[] currLine = new byte[rowByteSize];
		byte[] prevLine = new byte[rowByteSize];

		for (int y = yStart; y < height; y++) {
			imageRaster.getDataElements(0, y, width, 1, elements);

			int rowByteOffset = 1;

			for (int x = 0; x < width; x++) {
				final int element = elements[x];
				currLine[rowByteOffset++] = (byte) (element); // R
				currLine[rowByteOffset++] = (byte) (element >> 8); // G
				currLine[rowByteOffset++] = (byte) (element >> 16); // B
			}
			consumer.consume(currLine, prevLine);
			{
				byte[] b = currLine;
				currLine = prevLine;
				prevLine = b;
			}
		}
	}

	static byte[] get3ByteBgr(WritableRaster imageRaster, int width, int height) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(rowByteSize * height);
		get3ByteBgr(imageRaster, 0, width, height, consumer);
		return consumer.bytes;
	}

	static void get3ByteBgr(WritableRaster imageRaster, int yStart, int width, int height,
			AbstractPNGLineConsumer consumer) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		final byte[] elements = new byte[width * 3];
		byte[] currLine = new byte[rowByteSize];
		byte[] prevLine = new byte[rowByteSize];
		for (int y = yStart; y < height; y++) {
			imageRaster.getDataElements(0, y, width, 1, elements);

			currLine[0] = 0;
			System.arraycopy(elements, 0, currLine, 1, elements.length);
			consumer.consume(currLine, prevLine);
			{
				byte[] b = currLine;
				currLine = prevLine;
				prevLine = b;
			}
		}
	}

	static byte[] get4ByteAbgr(WritableRaster imageRaster, int width, int height) throws IOException {
		final int channels = 4;
		final int rowByteSize = 1 + channels * width;
		ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(rowByteSize * height);
		get4ByteAbgr(imageRaster, 0, width, height, consumer);
		return consumer.bytes;
	}

	static void get4ByteAbgr(WritableRaster imageRaster, int yStart, int width, int height,
			AbstractPNGLineConsumer consumer) throws IOException {
		final int channels = 4;
		final int rowByteSize = 1 + channels * width;
		final byte[] elements = new byte[width * 4];
		byte[] currLine = new byte[rowByteSize];
		byte[] prevLine = new byte[rowByteSize];
		for (int y = yStart; y < height; y++) {
			imageRaster.getDataElements(0, y, width, 1, elements);

			currLine[0] = 0;
			System.arraycopy(elements, 0, currLine, 1, elements.length);
			consumer.consume(currLine, prevLine);
			{
				byte[] b = currLine;
				currLine = prevLine;
				prevLine = b;
			}
		}
	}

	static byte[] getByteGray(WritableRaster imageRaster, int width, int height) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(rowByteSize * height);
		getByteGray(imageRaster, 0, width, height, consumer);
		return consumer.bytes;

	}

	static void getByteGray(WritableRaster imageRaster, int yStart, int width, int height,
			AbstractPNGLineConsumer consumer) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		final byte[] elements = new byte[width];
		byte[] currLine = new byte[rowByteSize];
		byte[] prevLine = new byte[rowByteSize];
		for (int y = yStart; y < height; y++) {
			imageRaster.getDataElements(0, y, width, 1, elements);

			int rowByteOffset = 1;

			for (int x = 0; x < width; x++) {
				byte grayColorValue = elements[x];
				currLine[rowByteOffset++] = grayColorValue; // R
				currLine[rowByteOffset++] = grayColorValue; // G
				currLine[rowByteOffset++] = grayColorValue; // B
			}
			consumer.consume(currLine, prevLine);
			{
				byte[] b = currLine;
				currLine = prevLine;
				prevLine = b;
			}
		}
	}

	static byte[] getUshortGray(WritableRaster imageRaster, int width, int height) throws IOException {
		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(rowByteSize * height);
		getUshortGray(imageRaster, 0, width, height, consumer);
		return consumer.bytes;
	}

	static void getUshortGray(WritableRaster imageRaster, int yStart, int width, int height,
			AbstractPNGLineConsumer consumer) throws IOException {

		final int channels = 3;
		final int rowByteSize = 1 + channels * width;
		final short[] elements = new short[width];
		byte[] currLine = new byte[rowByteSize];
		byte[] prevLine = new byte[rowByteSize];

		for (int y = yStart; y < height; y++) {
			imageRaster.getDataElements(0, y, width, 1, elements);

			int rowByteOffset = 1;

			for (int x = 0; x < width; x++) {
				byte grayColorValue = (byte) (elements[x] >> 8);

				currLine[rowByteOffset++] = grayColorValue; // R
				currLine[rowByteOffset++] = grayColorValue; // G
				currLine[rowByteOffset++] = grayColorValue; // B
			}

			consumer.consume(currLine, prevLine);
			{
				byte[] b = currLine;
				currLine = prevLine;
				prevLine = b;
			}

		}
	}
}
