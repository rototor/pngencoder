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

                long estCompressSum = 0;        // Marker 0 for no predictor
                long estCompressSumSub = 1;     // Marker 1 for sub predictor
                long estCompressSumUp = 2;      // Marker 2 for up preditor
                long estCompressSumAvg = 3;     // Marker 3 for average predictor
                long estCompressSumPaeth = 4;   // Marker 4 for paeth predictor

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
                    /*
                     * Manual inlining of all pngFilters
                     */
                    byte bSub = (byte) (x - a);
                    assert bSub == pngFilterSub(x, a);
                    byte bUp = (byte) (x - b);
                    assert bUp == pngFilterUp(x, b);
                    byte bAverage = (byte) (x - ((b + a) / 2));
                    assert bAverage == pngFilterAverage(x, a, b);
                    byte bPaeth;
                    {
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
                        bPaeth = (byte) r;
                        assert bPaeth == pngFilterPaeth(x, a, b, c);
                    }

                    dataRawRowSub[i] = bSub;
                    dataRawRowUp[i] = bUp;
                    dataRawRowAverage[i] = bAverage;
                    dataRawRowPaeth[i] = bPaeth;

                    estCompressSum += Math.abs(x);
                    estCompressSumSub += Math.abs(bSub);
                    estCompressSumUp += Math.abs(bUp);
                    estCompressSumAvg += Math.abs(bAverage);
                    estCompressSumPaeth += Math.abs(bPaeth);
                }

                /*
                 * Choose which row to writer
                 * https://www.w3.org/TR/PNG-Encoders.html#E.Filter-selection
                 */
                byte[] rowToWrite = dataRawRowNone;
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

                outputStream.write(rowToWrite);
            }
        });
    }

    private byte[] dataRawRowNone;
    private byte[] dataRawRowSub;
    private byte[] dataRawRowUp;
    private byte[] dataRawRowAverage;
    private byte[] dataRawRowPaeth;

    /*
     * PNG Filters, see https://www.w3.org/TR/PNG-Filters.html
     */
    private static byte pngFilterSub(int x, int a) {
        return (byte) ((x) - (a));
    }

    private static byte pngFilterUp(int x, int b) {
        // Same as pngFilterSub, just called with the prior row
        return pngFilterSub(x, b);
    }

    private static byte pngFilterAverage(int x, int a, int b) {
        return (byte) (x - ((b + a) / 2));
    }

    /**
     * This method describes the algorythm. For performance reasons it has to be inlined in to the innerloop,
     * as the JVM will not inline this method, as it is to big...
     */
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
}
