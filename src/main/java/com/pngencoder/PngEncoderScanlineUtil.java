package com.pngencoder;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
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

    static class EncodingMetaInfo {
        int channels;
        int bytesPerPixel;
        int rowByteSize;
        boolean hasAlpha;
        ICC_Profile colorProfile;
    }

    static EncodingMetaInfo getEncodingMetaInfo(BufferedImage bufferedImage) {
        EncodingMetaInfo info = new EncodingMetaInfo();
        int width = bufferedImage.getWidth();
        final PngEncoderBufferedImageType type = PngEncoderBufferedImageType.valueOf(bufferedImage);
        ColorSpace colorSpace = bufferedImage.getColorModel().getColorSpace();
        boolean needToFallBackTosRGB = false;
        if (!colorSpace.isCS_sRGB() && colorSpace instanceof ICC_ColorSpace) {
            info.colorProfile = ((ICC_ColorSpace) colorSpace).getProfile();
            switch (colorSpace.getType()) {
                case ColorSpace.TYPE_RGB:
                case ColorSpace.TYPE_GRAY:
                    break;
                default:
                    needToFallBackTosRGB = true;
                    break;
            }
        }

        switch (type) {
            case TYPE_INT_ARGB:
            case TYPE_INT_ARGB_PRE:
            case TYPE_4BYTE_ABGR:
            case TYPE_4BYTE_ABGR_PRE:
                info.channels = 4;
                info.bytesPerPixel = 4;
                info.hasAlpha = true;
                break;
            case TYPE_INT_BGR:
            case TYPE_3BYTE_BGR:
            case TYPE_USHORT_565_RGB:
            case TYPE_USHORT_555_RGB:
            case TYPE_INT_RGB:
                info.channels = 3;
                info.bytesPerPixel = 3;
                break;
            case TYPE_BYTE_GRAY:
                info.channels = 1;
                info.bytesPerPixel = 1;
                break;
            case TYPE_USHORT_GRAY:
                info.channels = 1;
                info.bytesPerPixel = 2;
                break;
            default:
                SampleModel sampleModel = bufferedImage.getRaster().getSampleModel();
                info.hasAlpha = bufferedImage.getTransparency() != Transparency.OPAQUE;

                /*
                 * Default sRGB byte encoding
                 */
                if (!info.hasAlpha) {
                    info.channels = 3;
                    info.bytesPerPixel = 3;
                } else {
                    info.channels = 4;
                    info.bytesPerPixel = 4;
                }

                /*
                 * When it is a UShort GRAY or RGB buffer we can write it as 16 bit image.
                 */
                if (!needToFallBackTosRGB && bufferedImage.getRaster().getDataBuffer() instanceof DataBufferUShort) {
                    info.channels = sampleModel.getNumBands();
                    info.bytesPerPixel = info.channels * 2;
                }
                break;
        }

        info.rowByteSize = 1 + info.bytesPerPixel * width;
        return info;
    }

    static byte[] get(BufferedImage bufferedImage) throws IOException {
        final int height = bufferedImage.getHeight();
        EncodingMetaInfo encodingMetaInfo = getEncodingMetaInfo(bufferedImage);
        ByteBufferPNGLineConsumer consumer = new ByteBufferPNGLineConsumer(encodingMetaInfo.rowByteSize * height);
        stream(bufferedImage, 0, height, consumer);
        return consumer.bytes;
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
                getByteGray(bufferedImage, yStart, width, height, consumer);
                break;
            case TYPE_USHORT_GRAY:
                getUshortGray(bufferedImage, yStart, width, height, consumer);
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

    static void getIntRgb(WritableRaster imageRaster, int yStart, int width, int height,
            AbstractPNGLineConsumer consumer) throws IOException {
        final int channels = 3;
        final int rowByteSize = 1 + channels * width;
        byte[] currLine = new byte[rowByteSize];
        byte[] prevLine = new byte[rowByteSize];

        if (imageRaster.getSampleModel() instanceof SinglePixelPackedSampleModel) {
            SinglePixelPackedSampleModel sampleModel = (SinglePixelPackedSampleModel) imageRaster.getSampleModel();
            int scanlineStride = sampleModel.getScanlineStride();
            assert sampleModel.getNumBands() == 3;
            assert sampleModel.getBitOffsets()[0] == 16;
            assert sampleModel.getBitOffsets()[1] == 8;
            assert sampleModel.getBitOffsets()[2] == 0;
            int[] rawInts = ((DataBufferInt) imageRaster.getDataBuffer()).getData();

            int linePtr = scanlineStride * (yStart - imageRaster.getSampleModelTranslateY())
                    - imageRaster.getSampleModelTranslateX();
            for (int y = yStart; y < height; y++) {
                int pixelPtr = linePtr;

                int rowByteOffset = 1;
                for (int x = 0; x < width; x++) {
                    final int element = rawInts[pixelPtr++];
                    currLine[rowByteOffset++] = (byte) (element >> 16); // R
                    currLine[rowByteOffset++] = (byte) (element >> 8); // G
                    currLine[rowByteOffset++] = (byte) element; // B
                }

                linePtr += scanlineStride;
                consumer.consume(currLine, prevLine);

                {
                    byte[] b = currLine;
                    currLine = prevLine;
                    prevLine = b;
                }
            }
        } else {
            throw new IllegalStateException("TYPE_INT_RGB must have a SinglePixelPackedSampleModel");
        }
    }

    static void getIntArgb(WritableRaster imageRaster, int yStart, int width, int height,
            AbstractPNGLineConsumer consumer) throws IOException {
        final int channels = 4;
        final int rowByteSize = 1 + channels * width;
        byte[] currLine = new byte[rowByteSize];
        byte[] prevLine = new byte[rowByteSize];

        if (imageRaster.getSampleModel() instanceof SinglePixelPackedSampleModel) {
            SinglePixelPackedSampleModel sampleModel = (SinglePixelPackedSampleModel) imageRaster.getSampleModel();
            int scanlineStride = sampleModel.getScanlineStride();
            assert sampleModel.getNumBands() == 4;
            assert sampleModel.getBitOffsets()[0] == 16;
            assert sampleModel.getBitOffsets()[1] == 8;
            assert sampleModel.getBitOffsets()[2] == 0;
            assert sampleModel.getBitOffsets()[3] == 24;
            int[] rawInts = ((DataBufferInt) imageRaster.getDataBuffer()).getData();

            int linePtr = scanlineStride * (yStart - imageRaster.getSampleModelTranslateY())
                    - imageRaster.getSampleModelTranslateX();

            for (int y = yStart; y < height; y++) {
                int pixelPtr = linePtr;

                int rowByteOffset = 1;
                for (int x = 0; x < width; x++) {
                    final int element = rawInts[pixelPtr++];
                    currLine[rowByteOffset++] = (byte) (element >> 16); // R
                    currLine[rowByteOffset++] = (byte) (element >> 8); // G
                    currLine[rowByteOffset++] = (byte) element; // B
                    currLine[rowByteOffset++] = (byte) (element >> 24); // A
                }

                linePtr += scanlineStride;
                consumer.consume(currLine, prevLine);

                {
                    byte[] b = currLine;
                    currLine = prevLine;
                    prevLine = b;
                }
            }
        } else {
            throw new IllegalStateException("TYPE_INT_RGB must have a SinglePixelPackedSampleModel");
        }
    }

    static void getIntBgr(WritableRaster imageRaster, int yStart, int width, int height,
            AbstractPNGLineConsumer consumer) throws IOException {
        final int channels = 3;
        final int rowByteSize = 1 + channels * width;
        byte[] currLine = new byte[rowByteSize];
        byte[] prevLine = new byte[rowByteSize];

        if (imageRaster.getSampleModel() instanceof SinglePixelPackedSampleModel) {
            SinglePixelPackedSampleModel sampleModel = (SinglePixelPackedSampleModel) imageRaster.getSampleModel();
            int scanlineStride = sampleModel.getScanlineStride();
            assert sampleModel.getNumBands() == 3;
            assert sampleModel.getBitOffsets()[0] == 0;
            assert sampleModel.getBitOffsets()[1] == 8;
            assert sampleModel.getBitOffsets()[2] == 16;
            int[] rawInts = ((DataBufferInt) imageRaster.getDataBuffer()).getData();

            int linePtr = scanlineStride * (yStart - imageRaster.getSampleModelTranslateY())
                    - imageRaster.getSampleModelTranslateX();
            for (int y = yStart; y < height; y++) {
                int pixelPtr = linePtr;

                int rowByteOffset = 1;
                for (int x = 0; x < width; x++) {
                    final int element = rawInts[pixelPtr++];
                    currLine[rowByteOffset++] = (byte) element; // R
                    currLine[rowByteOffset++] = (byte) (element >> 8); // G
                    currLine[rowByteOffset++] = (byte) (element >> 16); // B
                }

                linePtr += scanlineStride;
                consumer.consume(currLine, prevLine);

                {
                    byte[] b = currLine;
                    currLine = prevLine;
                    prevLine = b;
                }
            }
        } else {
            throw new IllegalStateException("TYPE_INT_BGR must have a SinglePixelPackedSampleModel");
        }
    }

    static void get3ByteBgr(WritableRaster imageRaster, int yStart, int width, int height,
            AbstractPNGLineConsumer consumer) throws IOException {
        final int channels = 3;
        final int rowByteSize = 1 + channels * width;
        byte[] currLine = new byte[rowByteSize];
        byte[] prevLine = new byte[rowByteSize];
        DataBufferByte dataBufferByte = (DataBufferByte) imageRaster.getDataBuffer();
        if (imageRaster.getSampleModel() instanceof PixelInterleavedSampleModel) {
            PixelInterleavedSampleModel sampleModel = (PixelInterleavedSampleModel) imageRaster.getSampleModel();
            byte[] rawBytes = dataBufferByte.getData();
            int scanlineStride = sampleModel.getScanlineStride();
            int pixelStride = sampleModel.getPixelStride();

            assert pixelStride == 3;
            int linePtr = scanlineStride * (yStart - imageRaster.getSampleModelTranslateY())
                    - imageRaster.getSampleModelTranslateX() * pixelStride;

            for (int y = yStart; y < height; y++) {
                int pixelPtr = linePtr;
                int writePtr = 1;
                for (int x = 0; x < width; x++) {
                    byte b = rawBytes[pixelPtr++];
                    byte g = rawBytes[pixelPtr++];
                    byte r = rawBytes[pixelPtr++];
                    currLine[writePtr++] = r;
                    currLine[writePtr++] = g;
                    currLine[writePtr++] = b;
                }
                linePtr += scanlineStride;
                consumer.consume(currLine, prevLine);
                {
                    byte[] b = currLine;
                    currLine = prevLine;
                    prevLine = b;
                }
            }
        } else {
            throw new IllegalStateException("3ByteBgr must have a PixelInterleavedSampleModel");
        }
    }

    static void get4ByteAbgr(WritableRaster imageRaster, int yStart, int width, int height,
            AbstractPNGLineConsumer consumer) throws IOException {
        final int channels = 4;
        final int rowByteSize = 1 + channels * width;
        byte[] currLine = new byte[rowByteSize];
        byte[] prevLine = new byte[rowByteSize];
        DataBufferByte dataBufferByte = (DataBufferByte) imageRaster.getDataBuffer();
        if (imageRaster.getSampleModel() instanceof PixelInterleavedSampleModel) {
            PixelInterleavedSampleModel sampleModel = (PixelInterleavedSampleModel) imageRaster.getSampleModel();
            byte[] rawBytes = dataBufferByte.getData();
            int scanlineStride = sampleModel.getScanlineStride();
            int pixelStride = sampleModel.getPixelStride();

            assert pixelStride == 4;
            int linePtr = scanlineStride * (yStart - imageRaster.getSampleModelTranslateY())
                    - imageRaster.getSampleModelTranslateX() * pixelStride;
            for (int y = yStart; y < height; y++) {
                int pixelPtr = linePtr;
                int writePtr = 1;
                for (int x = 0; x < width; x++) {
                    byte a = rawBytes[pixelPtr++];
                    byte b = rawBytes[pixelPtr++];
                    byte g = rawBytes[pixelPtr++];
                    byte r = rawBytes[pixelPtr++];
                    currLine[writePtr++] = r;
                    currLine[writePtr++] = g;
                    currLine[writePtr++] = b;
                    currLine[writePtr++] = a;
                }
                linePtr += scanlineStride;
                consumer.consume(currLine, prevLine);
                {
                    byte[] b = currLine;
                    currLine = prevLine;
                    prevLine = b;
                }
            }
        } else {
            throw new IllegalStateException("4ByteAbgr must have a PixelInterleavedSampleModel");
        }
    }

    static void getByteGray(BufferedImage image, int yStart, int width, int height, AbstractPNGLineConsumer consumer)
            throws IOException {
        WritableRaster imageRaster = image.getRaster();
        float[] convertValue = new float[1];
        ColorSpace colorSpace = image.getColorModel().getColorSpace();

        final int channels = 3;
        final int rowByteSize = 1 + channels * width;
        byte[] currLine = new byte[rowByteSize];
        byte[] prevLine = new byte[rowByteSize];

        DataBufferByte dataBufferByte = (DataBufferByte) imageRaster.getDataBuffer();
        if (imageRaster.getSampleModel() instanceof PixelInterleavedSampleModel) {
            PixelInterleavedSampleModel sampleModel = (PixelInterleavedSampleModel) imageRaster.getSampleModel();
            byte[] rawBytes = dataBufferByte.getData();
            int scanlineStride = sampleModel.getScanlineStride();
            int pixelStride = sampleModel.getPixelStride();

            assert pixelStride == 1;
            int linePtr = scanlineStride * (yStart - imageRaster.getSampleModelTranslateY())
                    - imageRaster.getSampleModelTranslateX() * pixelStride;
            for (int y = yStart; y < height; y++) {
                int pixelPtr = linePtr;
                int writePtr = 1;
                for (int x = 0; x < width; x++) {
                    byte grayColorValueRaw = rawBytes[pixelPtr++];
                    convertValue[0] = ((float) (grayColorValueRaw & 0xFF)) / 255f;
                    byte grayColorValue = (byte) ((int) (colorSpace.toRGB(convertValue)[0] * 255f + 0.5f));
                    currLine[writePtr++] = grayColorValue;
                    currLine[writePtr++] = grayColorValue;
                    currLine[writePtr++] = grayColorValue;
                }
                linePtr += scanlineStride;
                consumer.consume(currLine, prevLine);
                {
                    byte[] b = currLine;
                    currLine = prevLine;
                    prevLine = b;
                }
            }
        } else {
            throw new IllegalStateException("TYPE_BYTE_GRAY must have a PixelInterleavedSampleModel");
        }
    }

    static void getUshortGray(BufferedImage image, int yStart, int width, int height, AbstractPNGLineConsumer consumer)
            throws IOException {
        System.err.println("WARNING: TYPE_USHORT_GRAY is not really supported. Gamma is very off...");
        WritableRaster imageRaster = image.getRaster();
        float[] convertValue = new float[1];
        ColorSpace colorSpace = image.getColorModel().getColorSpace();

        final int channels = 3;
        final int rowByteSize = 1 + channels * width;
        byte[] currLine = new byte[rowByteSize];
        byte[] prevLine = new byte[rowByteSize];

        DataBufferUShort dataBufferUShort = (DataBufferUShort) imageRaster.getDataBuffer();
        if (imageRaster.getSampleModel() instanceof PixelInterleavedSampleModel) {
            PixelInterleavedSampleModel sampleModel = (PixelInterleavedSampleModel) imageRaster.getSampleModel();
            short[] rawShorts = dataBufferUShort.getData();
            int scanlineStride = sampleModel.getScanlineStride();
            int pixelStride = sampleModel.getPixelStride();

            assert pixelStride == 1;
            int linePtr = scanlineStride * (yStart - imageRaster.getSampleModelTranslateY())
                    - imageRaster.getSampleModelTranslateX() * pixelStride;
            for (int y = yStart; y < height; y++) {
                int pixelPtr = linePtr;
                int writePtr = 1;
                for (int x = 0; x < width; x++) {
                    short grayColorValueRaw = rawShorts[pixelPtr++];
                    convertValue[0] = ((float) (grayColorValueRaw & 0xFFFF)) / 32767.0f;
                    byte grayColorValue = (byte) (((int) (colorSpace.toRGB(convertValue)[0] * 32767.0f + 0.5f)) >> 8);
                    currLine[writePtr++] = grayColorValue;
                    currLine[writePtr++] = grayColorValue;
                    currLine[writePtr++] = grayColorValue;
                }
                linePtr += scanlineStride;
                consumer.consume(currLine, prevLine);
                {
                    byte[] b = currLine;
                    currLine = prevLine;
                    prevLine = b;
                }
            }
        } else {
            throw new IllegalStateException("TYPE_USHORT_GRAY must have a PixelInterleavedSampleModel");
        }
    }
}
