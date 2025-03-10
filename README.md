![PngEncoder Logo](https://user-images.githubusercontent.com/421009/85217670-be26ce00-b393-11ea-8741-4da520fc2dd2.png)


<img src="https://img.shields.io/maven-central/v/com.pngencoder/pngencoder"> <img src="https://img.shields.io/travis/pngencoder/pngencoder/develop"> <img src="https://img.shields.io/codecov/c/github/pngencoder/pngencoder/develop?token=305f39ec177948b3bde322c021debcdf">

- About 5 times faster than ImageIO (on a computer with 8 logical cores)
- Easy to use interface
- [Semantic Versioning](http://semver.org/)
- [MIT License](LICENSE)
- Java 8

## Examples

```java
import com.pngencoder.PngEncoder;
import com.pngencoder.PngEncoderBufferedImageConverter;

public class Examples {
    public static void encodeToOutputStream(BufferedImage bufferedImage, OutputStream outputStream) {
        new PngEncoder()
                .withBufferedImage(bufferedImage)
                .toStream(outputStream);
    }

    public static void encodeToFile(BufferedImage bufferedImage, File file) {
        new PngEncoder()
                .withBufferedImage(bufferedImage)
                .toFile(file);
    }

    public static void encodeToFilePath(BufferedImage bufferedImage, Path filePath) {
        new PngEncoder()
                .withBufferedImage(bufferedImage)
                .toFile(filePath);
    }

    public static void encodeToFileName(BufferedImage bufferedImage, String fileName) {
        new PngEncoder()
                .withBufferedImage(bufferedImage)
                .toFile(fileName);
    }

    public static byte[] encodeToBytes(BufferedImage bufferedImage) {
        return new PngEncoder()
                .withBufferedImage(bufferedImage)
                .toBytes();
    }

    public static byte[] encodeWithBestCompression(BufferedImage bufferedImage) {
        // Compression level 9 is the default and you actually need not set it.
        // It produces images with a size comparable to ImageIO.
        return new PngEncoder()
                .withBufferedImage(bufferedImage)
                .withCompressionLevel(9)
                .toBytes();
    }

    public static byte[] encodeWithBestSpeed(BufferedImage bufferedImage) {
        // If speed is more important than size you can lower the compression level.
        // 1 is about three times faster but the file becomes about three times larger.
        return new PngEncoder()
                .withBufferedImage(bufferedImage)
                .withCompressionLevel(1)
                .toBytes();
    }

    public static CompletableFuture<Void> encodeToFileInOtherThread(BufferedImage bufferedImage, File file) {
        // Perhaps all of the work can be done async to let the main thread continue?
        // More of a general performance tip that's not limited to this PngEncoder library.
        return CompletableFuture.runAsync(() -> new PngEncoder()
                .withBufferedImage(bufferedImage)
                .toFile(file));
    }

    public static byte[] encodeIntArgbData(int[] data, int width, int height) {
        // Creating the BufferedImage this way is almost instant.
        // It uses the underlying int[] data directly.
        BufferedImage bufferedImage = PngEncoderBufferedImageConverter.createFromIntArgb(data, width, height);
        return new PngEncoder()
                .withBufferedImage(bufferedImage)
                .toBytes();
    }

    public static byte[] encodeWithMultiThreadedCompressionDisabled(BufferedImage bufferedImage) {
        // By default the compression is done in multiple threads.
        // This improves the speed a lot, but you can disable it to compress in the invoking thread only.
        return new PngEncoder()
                .withBufferedImage(bufferedImage)
                .withMultiThreadedCompressionEnabled(false)
                .toBytes();
    }
}
```

## Maven Central

```xml
<dependency>
    <groupId>com.pngencoder</groupId>
    <artifactId>pngencoder</artifactId>
    <version>0.13.1</version>
</dependency>
```

https://search.maven.org/artifact/com.pngencoder/pngencoder

## PngEncoder vs ImageIO
The table below shows the number of images encoded per second, using PngEncoder vs ImageIO:
Image | PngEncoder | ImageIO | Times Faster
--- | --- | --- | ---
random1024x1024 | 36.324 | 5.150 | 7.1
logo2121x350 | 127.034 | 24.857 | 5.1
looklet4900x6000 | 0.159 | 0.029 | 5.5

Run yourself using [PngEncoderBenchmarkPngEncoderVsImageIO.java](src/test/java/com/pngencoder/PngEncoderBenchmarkPngEncoderVsImageIO.java)

Note that these numbers are for the default compression level 9 which produces images of about the same size as ImageIO. By lowering the compression level we can speed up encoding further. See the table below.

## Compression Speed vs Size

Compression Level | Speed | Size | Speed / Size
--- | --- | --- | ---
9 (default) | 1.00 | 1.00 | 1.00
8 | 1.10 | 1.14 | 0.97
7 | 1.36 | 1.24 | 1.10
6 | 1.40 | 1.23 | 1.14
5 | 1.46 | 1.29 | 1.13
4 | 1.49 | 1.30 | 1.15
3 | 2.33 | 2.30 | 1.02
2 | 2.31 | 2.42 | 0.96
1 | 2.31 | 2.50 | 0.92
0 | 3.11 | 206.80 | 0.02

Run yourself using [PngEncoderBenchmarkCompressionSpeedVsSize.java](src/test/java/com/pngencoder/PngEncoderBenchmarkCompressionSpeedVsSize.java)

In the table above we see that the "Speed / Size" column is close to 1 for all compression levels but 0. You likely want to avoid compression level 0 (no compression) if the file size matters at all. In comparison to using compression level 1 it's 35% faster, but the file size is a whopping 827%. That is likely not worth it.


## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md)


## Noteworthy Caveats
This library achieves the speedup mainly using multithreading. The performance tests above were run on a computer with 8 logical cores. So if you for example use a single core computer (perhaps in the cloud) the speedup will not be significant.

The file size is about 2% larger than images encoded by ImageIO. This small overhead is due to the multi-threaded compression.

This library will output either a truecolor ARGB or RGB file. It does not support grayscale and indexed PNG file output.

Support for metadata is currently close to zero. If you need comments in your PNG file, or advanced support for color profiles this library currently does not support that. If you just are interested in a simple SRGB profile there is an experimental method for it.

## Contributing
When contributing please respect the style guide in the [CONTRIBUTING.md](./CONTRIBUTING.md). If you use IntellJ IDEA you can import the  [looklet_intellij_code_style.xml](./looklet_intellij_code_style.xml)
to have IDEA setup with the right settings. 

## Looklet
We develop and use this library at https://www.looklet.com/ to quickly compress high quality fashion images for e-commerce.

Are you a skilled Java or React developer? Feel free to join in: https://www.looklet.com/career/job-openings/

<img src="https://user-images.githubusercontent.com/421009/90376713-2e418f80-e077-11ea-8018-9c79ecf9d519.jpg" width="200" alt="Looklet Fashion Image"/>
