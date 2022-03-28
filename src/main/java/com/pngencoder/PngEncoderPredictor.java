package com.pngencoder;

import com.pngencoder.PngEncoderScanlineUtil.AbstractPNGLineConsumer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

class PngEncoderPredictor {

	int bytesPerPixel;

	public void encodeImage(BufferedImage image, int yStart, int height, OutputStream outputStream) throws IOException {
		bytesPerPixel = image.getColorModel().hasAlpha() ? 4 : 3;
		PngEncoderScanlineUtil.stream(image, yStart, height, new AbstractPNGLineConsumer() {
			@Override
			void consume(byte[] currRow, byte[] prevRow) throws IOException {
				dataRawRowNone = currRow;
				if (dataRawRowSub == null) {
					dataRawRowSub = new byte[currRow.length];
					dataRawRowUp = new byte[currRow.length];
					dataRawRowAverage = new byte[currRow.length];
					dataRawRowPaeth = new byte[currRow.length];

					dataRawRowSub[0] = 1;
					dataRawRowUp[0] = 2;
					dataRawRowAverage[0] = 3;
					dataRawRowPaeth[0] = 4;

				}

				// c | b
				// -----
				// a | x
				//
				// x => current pixel
				int bLen = currRow.length;
				assert currRow.length == prevRow.length;
				assert currRow[0] == 0;
				assert prevRow[0] == 0;

				int bpp = bytesPerPixel;
				int a = 0;
				int c = 0;
				for (int i = 1; i < bLen; i++) {
					int x = currRow[i] & 0xFF;
					int b = prevRow[i] & 0xFF;
					if (i > bpp) {
						a = currRow[i - bpp] & 0xFF;
						c = prevRow[i - bpp] & 0xFF;
					}
					dataRawRowSub[i] = pngFilterSub(x, a);
					dataRawRowUp[i] = pngFilterUp(x, b);
					dataRawRowAverage[i] = pngFilterAverage(x, a, b);
					dataRawRowPaeth[i] = pngFilterPaeth(x, a, b, c);
				}

				outputStream.write(chooseDataRowToWrite());
			}
		});
	}

	private byte[] dataRawRowNone;
	private byte[] dataRawRowSub;
	private byte[] dataRawRowUp;
	private byte[] dataRawRowAverage;
	private byte[] dataRawRowPaeth;

	private byte[] chooseDataRowToWrite() {
		byte[] rowToWrite = dataRawRowNone;
		long estCompressSum = estCompressSum(dataRawRowNone);
		long estCompressSumSub = estCompressSum(dataRawRowSub);
		long estCompressSumUp = estCompressSum(dataRawRowUp);
		long estCompressSumAvg = estCompressSum(dataRawRowAverage);
		long estCompressSumPaeth = estCompressSum(dataRawRowPaeth);
		if (estCompressSum > estCompressSumSub) {
			rowToWrite = dataRawRowSub;
			estCompressSum = estCompressSumSub;
		}
		if (estCompressSum > estCompressSumUp) {
			rowToWrite = dataRawRowUp;
			estCompressSum = estCompressSumUp;
		}
		if (estCompressSum > estCompressSumAvg) {
			rowToWrite = dataRawRowAverage;
			estCompressSum = estCompressSumAvg;
		}
		if (estCompressSum > estCompressSumPaeth) {
			rowToWrite = dataRawRowPaeth;
		}
		return rowToWrite;
	}

	/*
	 * PNG Filters, see https://www.w3.org/TR/PNG-Filters.html
	 */
	private static byte pngFilterSub(int x, int a) {
		return (byte) ((x & 0xFF) - (a & 0xFF));
	}

	private static byte pngFilterUp(int x, int b) {
		// Same as pngFilterSub, just called with the prior row
		return pngFilterSub(x, b);
	}

	private static byte pngFilterAverage(int x, int a, int b) {
		return (byte) (x - ((b + a) / 2));
	}

	private static byte pngFilterPaeth(int x, int a, int b, int c) {
		int p = a + b - c;
		int pa = Math.abs(p - a);
		int pb = Math.abs(p - b);
		int pc = Math.abs(p - c);
		final int pr;
		if (pa <= pb && pa <= pc) {
			pr = a;
		} else if (pb <= pc) {
			pr = b;
		} else {
			pr = c;
		}

		int r = x - pr;
		return (byte) (r);
	}

	private static long estCompressSum(byte[] dataRawRowSub) {
		long sum = 0;
		for (byte aDataRawRowSub : dataRawRowSub) {
			// https://www.w3.org/TR/PNG-Encoders.html#E.Filter-selection
			sum += Math.abs(aDataRawRowSub);
		}
		return sum;
	}

}
