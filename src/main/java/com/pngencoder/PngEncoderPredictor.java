package com.pngencoder;

import com.pngencoder.PngEncoderScanlineUtil.AbstractPNGLineConsumer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

class PngEncoderPredictor {

    int bytesPerPixel;

    public void encodeImageMultithreaded(BufferedImage image, int compressionLevel, OutputStream outputStream) throws IOException {
        int height = image.getHeight();
        int heightPerSlice = PngEncoderDeflaterExecutorService.NUM_THREADS_IS_AVAILABLE_PROCESSORS;

        /*
         * Schedule all tasks for all segments
         */
        List<CompletableFuture<byte[]>> resultList = new ArrayList<>();
        for (int y = 0; y < height; y += heightPerSlice) {
            final int yStart = y;
            CompletableFuture<byte[]> completableFuture = CompletableFuture.supplyAsync(() -> {
                ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
                Deflater def = PngEncoderDeflaterThreadLocalDeflater.getInstance(compressionLevel);
                DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(outBytes, def);
                try {
                    int heightToProcess = Math.min(heightPerSlice, height - yStart);
                    encodeImage(image, yStart, heightToProcess, deflaterOutputStream);
                    deflaterOutputStream.finish();
                    deflaterOutputStream.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return outBytes.toByteArray();
            }, PngEncoderDeflaterExecutorService.getInstance());
            resultList.add(completableFuture);
        }

        /*
         * Await the result
         */
        for (CompletableFuture<byte[]> completableFuture : resultList) {
            try {
                outputStream.write(completableFuture.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void encodeImage(BufferedImage image, int yStart, int height, OutputStream outputStream) throws IOException {
        PngEncoderScanlineUtil.EncodingMetaInfo metaInfo = PngEncoderScanlineUtil.getEncodingMetaInfo(image);
        bytesPerPixel = metaInfo.bytesPerPixel;
        /*
         * If we don't start at the first row we fetch the row before first, to ensure the prevRow
         * is correctly initialized.
         */
        int realYStart = yStart > 0 ? yStart - 1 : yStart;
        PngEncoderScanlineUtil.stream(image, realYStart, height, new AbstractPNGLineConsumer() {

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
                    boolean skipFirstRow = yStart > 0;
                    if (skipFirstRow) {
                        return;
                    }
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
                     * PNG Filters, see https://www.w3.org/TR/PNG-Filters.html
                     */
                    byte bSub = (byte) (x - a);
                    byte bUp = (byte) (x - b);
                    byte bAverage = (byte) (x - ((b + a) / 2));
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
}
