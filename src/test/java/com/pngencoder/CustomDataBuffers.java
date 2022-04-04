package com.pngencoder;

import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

class CustomDataBuffers {
    static class CustomByteBuffer extends DataBuffer {
        ByteBuffer buffer;

        protected CustomByteBuffer(int size, final int numBanks) {
            super(DataBuffer.TYPE_BYTE, size, numBanks);
            buffer = ByteBuffer.allocateDirect(size * numBanks);
        }

        @Override
        public int getElem(int bank, int i) {
            return buffer.get(bank * size + i);
        }

        @Override
        public void setElem(int bank, int i, int val) {
            buffer.put(bank * size + i, (byte) (val & 0xFF));
        }
    }

    static class CustomShortBuffer extends DataBuffer {
        ShortBuffer buffer;

        protected CustomShortBuffer(int size, final int numBanks) {
            super(DataBuffer.TYPE_USHORT, size, numBanks);
            buffer = ShortBuffer.allocate(size * numBanks);
        }

        @Override
        public int getElem(int bank, int i) {
            return buffer.get(bank * size + i);
        }

        @Override
        public void setElem(int bank, int i, int val) {
            buffer.put(bank * size + i, (short) (val & 0xFFFF));
        }
    }

    static final class GenericRasterFactory {
        public WritableRaster createRaster(final SampleModel model, final DataBuffer buffer, final Point origin) {
            return new GenericWritableRaster(model, buffer, origin);
        }
    }

    private static final GenericRasterFactory RASTER_FACTORY = new GenericRasterFactory();

    static class GenericWritableRaster extends WritableRaster {
        public GenericWritableRaster(final SampleModel model, final DataBuffer buffer, final Point origin) {
            super(model, buffer, origin);
        }
    }

    static BufferedImage createCompatibleImage(int width, int height, SampleModel sm, ColorModel cm) {
        DataBuffer buffer;
        switch (sm.getTransferType()) {
            case DataBuffer.TYPE_BYTE:
                buffer = new CustomDataBuffers.CustomByteBuffer(width * height * sm.getNumDataElements(), 1);
                break;
            case DataBuffer.TYPE_USHORT:
                buffer = new CustomDataBuffers.CustomShortBuffer(width * height * sm.getNumDataElements(), 1);
                break;
            default:
                throw new RuntimeException("Not implemented!");
        }

        return new BufferedImage(cm, RASTER_FACTORY.createRaster(sm, buffer, new Point()), cm.isAlphaPremultiplied(),
                null);
    }

    static BufferedImage create8BitRGBA(int width, int height, ColorModel colorModel) {
        PixelInterleavedSampleModel model = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, width, height, 4, 4 * width, new int[]{0, 1, 2, 3});
        return createCompatibleImage(width, height, model, colorModel);
    }

    static BufferedImage create16BitRGBA(int width, int height) {
        ColorSpace colorSpace = ColorModel.getRGBdefault().getColorSpace();
        PixelInterleavedSampleModel model = new PixelInterleavedSampleModel(DataBuffer.TYPE_USHORT, width, height, 4, 4 * width, new int[]{0, 1, 2, 3});
        final ColorModel colorModel = new ComponentColorModel(colorSpace, true, false,
                ColorModel.TRANSLUCENT, DataBuffer.TYPE_USHORT);
        return createCompatibleImage(width, height, model, colorModel);
    }
}
