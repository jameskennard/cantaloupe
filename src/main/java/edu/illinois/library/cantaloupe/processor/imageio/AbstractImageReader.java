package edu.illinois.library.cantaloupe.processor.imageio;

import edu.illinois.library.cantaloupe.image.Crop;
import edu.illinois.library.cantaloupe.image.Format;
import edu.illinois.library.cantaloupe.image.Operation;
import edu.illinois.library.cantaloupe.image.OperationList;
import edu.illinois.library.cantaloupe.image.Scale;
import edu.illinois.library.cantaloupe.processor.Java2dUtil;
import edu.illinois.library.cantaloupe.image.Orientation;
import edu.illinois.library.cantaloupe.processor.ProcessorException;
import edu.illinois.library.cantaloupe.processor.ReductionFactor;
import edu.illinois.library.cantaloupe.processor.UnsupportedSourceFormatException;
import edu.illinois.library.cantaloupe.resolver.StreamSource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Abstract reader that supplies some base functionality and tries to be
 * efficient with most formats. Format-specific readers may override what they
 * need to.
 */
abstract class AbstractImageReader {

    // Note: methods that return BufferedImages (for Java 2D) are arranged
    // toward the beginning of the class; methods that return RenderedImages
    // (for JAI) are toward the end.

    private static Logger logger = LoggerFactory.
            getLogger(AbstractImageReader.class);

    private Format format;

    /** Set in setSource(). */
    protected ImageInputStream inputStream;

    /** Assigned by createReader(). */
    protected javax.imageio.ImageReader iioReader;

    /**
     * Initializes an instance.
     *
     * @param inputFile Image file to read.
     * @throws IOException
     */
    AbstractImageReader(File inputFile, Format format)
            throws IOException {
        setSource(inputFile);
        setFormat(format);
    }

    /**
     * Initializes an instance.
     *
     * @param streamSource Source of streams to read.
     * @throws IOException
     */
    AbstractImageReader(StreamSource streamSource,
                        Format format) throws IOException {
        setSource(streamSource);
        setFormat(format);
    }

    protected void createReader() throws IOException {
        Iterator<javax.imageio.ImageReader> it;
        if (format != null) {
            it = ImageIO.getImageReadersByMIMEType(
                    format.getPreferredMediaType().toString());
        } else {
            it = ImageIO.getImageReaders(inputStream);
        }
        if (it.hasNext()) {
            iioReader = it.next();
            iioReader.setInput(inputStream);
            logger.info("createReader(): using {}",
                    iioReader.getClass().getName());
        } else {
            throw new IOException("createReader(): unable to determine the " +
                    "format of the source image.");
        }
    }

    /**
     * Should be called when the reader is no longer needed.
     */
    void dispose() {
        try {
            IOUtils.closeQuietly(inputStream);
        } finally {
            if (iioReader != null) {
                iioReader.dispose();
                iioReader = null;
            }
        }
    }

    abstract Compression getCompression(int imageIndex) throws IOException;

    abstract Metadata getMetadata(int imageIndex) throws IOException;

    /**
     * @return The number of images contained inside the source image.
     * @throws IOException
     */
    int getNumResolutions() throws IOException {
        if (iioReader == null) {
            createReader();
        }
        // The boolean parameter tells getNumImages() whether to scan for
        // images, which seems to be necessary for some, but is slower.
        int numImages = iioReader.getNumImages(false);
        if (numImages == -1) {
            numImages = iioReader.getNumImages(true);
        }
        return numImages;
    }

    /**
     * Gets the dimensions of the source image.
     *
     * @return Dimensions in pixels
     * @throws IOException
     */
    Dimension getSize() throws IOException {
        if (iioReader == null) {
            createReader();
        }
        return getSize(iioReader.getMinIndex());
    }

    /**
     * Gets the dimensions of the image at the given index.
     *
     * @param imageIndex
     * @return Dimensions in pixels
     * @throws IOException
     */
    Dimension getSize(int imageIndex) throws IOException {
        if (iioReader == null) {
            createReader();
        }
        final int width = iioReader.getWidth(imageIndex);
        final int height = iioReader.getHeight(imageIndex);
        return new Dimension(width, height);
    }

    /**
     * @param imageIndex
     * @return Tile size of the image at the given index. If the image is not
     *         tiled, the full image dimensions are returned.
     * @throws IOException
     */
    Dimension getTileSize(int imageIndex) throws IOException {
        if (iioReader == null) {
            createReader();
        }
        final int width = iioReader.getTileWidth(imageIndex);
        final int height = iioReader.getTileHeight(imageIndex);
        return new Dimension(width, height);
    }

    void setFormat(Format format) {
        this.format = format;
    }

    void setSource(File inputFile) throws IOException {
        dispose();
        inputStream = new FileImageInputStream(inputFile);
    }

    void setSource(StreamSource streamSource) throws IOException {
        dispose();
        inputStream = streamSource.newImageInputStream();
    }

    ////////////////////////////////////////////////////////////////////////
    /////////////////////// BufferedImage methods //////////////////////////
    ////////////////////////////////////////////////////////////////////////

    /**
     * Expedient but not necessarily efficient method wrapping
     * {@link ImageIO#read} that reads a whole image (excluding subimages) in
     * one shot.
     *
     * @return BufferedImage guaranteed to not be of type
     *         {@link BufferedImage#TYPE_CUSTOM}.
     * @throws IOException
     */
    BufferedImage read() throws IOException {
        final BufferedImage image = ImageIO.read(inputStream);
        final BufferedImage rgbImage = Java2dUtil.convertCustomToRgb(image);
        if (rgbImage != image) {
            logger.warn("Converted image to RGB (this is very expensive)");
        }
        return rgbImage;
    }

    /**
     * <p>Attempts to read an image as efficiently as possible, utilizing its
     * tile layout and/or subimages, if possible.</p>
     *
     * <p>After reading, clients should check the reader hints to see whether
     * the returned image will require cropping.</p>
     *
     * @param ops
     * @param orientation     Orientation of the source image data as reported
     *                        by e.g. embedded metadata.
     * @param reductionFactor {@link ReductionFactor#factor} property will be
     *                        modified to reflect the reduction factor of the
     *                        returned image.
     * @param hints           Will be populated by information returned from
     *                        the reader.
     * @return BufferedImage best matching the given parameters, guaranteed to
     *         not be of {@link BufferedImage#TYPE_CUSTOM}. Clients should
     *         check the hints set to see whether they need to perform
     *         additional cropping.
     * @throws IOException
     * @throws ProcessorException
     */
    BufferedImage read(final OperationList ops,
                       final Orientation orientation,
                       final ReductionFactor reductionFactor,
                       final Set<ImageReader.Hint> hints)
            throws IOException, ProcessorException {
        if (iioReader == null) {
            createReader();
        }
        BufferedImage image;

        Crop crop = null;
        Scale scale = new Scale();
        scale.setMode(Scale.Mode.FULL);
        for (Operation op : ops) {
            if (op instanceof Crop) {
                crop = (Crop) op;
                crop.applyOrientation(orientation, getSize());
            }
        }
        if (crop != null) {
            final Dimension fullSize = new Dimension(
                    iioReader.getWidth(0), iioReader.getHeight(0));
            image = tileAwareRead(0, crop.getRectangle(fullSize), hints);
        } else {
            image = iioReader.read(0);
        }

        if (image == null) {
            throw new UnsupportedSourceFormatException(iioReader.getFormatName());
        }
        BufferedImage rgbImage = Java2dUtil.convertCustomToRgb(image);
        if (rgbImage != image) {
            logger.warn("Converted {} to RGB (this is very expensive)",
                    ops.getIdentifier());
        }
        return rgbImage;
    }

    /**
     * Reads the smallest image that can fulfill the given crop and scale from
     * a multi-resolution image.
     *
     * @param crop   Requested crop
     * @param scale  Requested scale
     * @param rf     {@link ReductionFactor#factor} will be set to the reduction
     *               factor of the returned image.
     * @param hints  Will be populated by information returned by the reader.
     * @return The smallest image fitting the requested crop and scale
     *         operations from the given reader.
     * @throws IOException
     */
    protected BufferedImage readSmallestUsableSubimage(
            final Crop crop,
            final Scale scale,
            final ReductionFactor rf,
            final Set<ImageReader.Hint> hints)
            throws IOException {
        final Dimension fullSize = new Dimension(
                iioReader.getWidth(0), iioReader.getHeight(0));
        final Rectangle regionRect = crop.getRectangle(fullSize);
        BufferedImage bestImage = null;
        if (scale.isNoOp()) {
            bestImage = tileAwareRead(0, regionRect, hints);
            logger.debug("readSmallestUsableSubimage(): using a {}x{} source " +
                            "image (0x reduction factor)",
                    bestImage.getWidth(), bestImage.getHeight());
        } else {
            // Pyramidal TIFFs will have > 1 image, each with half the
            // dimensions of the previous one. The boolean parameter tells
            // getNumImages() whether to scan for images, which seems to be
            // necessary for at least some files, but is slower. If it is
            // false, and getNumImages() can't find anything, it will return -1.
            int numImages = iioReader.getNumImages(false);
            if (numImages > 1) {
                logger.debug("readSmallestUsableSubimage(): " +
                        "detected {} subimage(s)", numImages);
            } else if (numImages == -1) {
                numImages = iioReader.getNumImages(true);
                if (numImages > 1) {
                    logger.debug("readSmallestUsableSubimage(): " +
                            "scan revealed {} subimage(s)", numImages);
                }
            }
            // At this point, we know how many images are available.
            if (numImages == 1) {
                bestImage = tileAwareRead(0, regionRect, hints);
                logger.debug("readSmallestUsableSubimage(): using a {}x{} " +
                                "source image (0x reduction factor)",
                        bestImage.getWidth(), bestImage.getHeight());
            } else if (numImages > 1) {
                // Loop through the reduced images from smallest to largest to
                // find the first one that can supply the requested scale
                for (int i = numImages - 1; i >= 0; i--) {
                    final int subimageWidth = iioReader.getWidth(i);
                    final int subimageHeight = iioReader.getHeight(i);

                    final double reducedScale = (double) subimageWidth /
                            (double) fullSize.width;
                    boolean fits = false;
                    if (scale.getPercent() != null) {
                        fits = (scale.getPercent() <= reducedScale);
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_WIDTH) {
                        fits = (scale.getWidth() / (double) regionRect.width <= reducedScale);
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_HEIGHT) {
                        fits = (scale.getHeight() / (double) regionRect.height <= reducedScale);
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_INSIDE) {
                        fits = (scale.getWidth() / (double) regionRect.width <= reducedScale &&
                                scale.getHeight() / (double) regionRect.height <= reducedScale);
                    } else if (scale.getMode() == Scale.Mode.NON_ASPECT_FILL) {
                        fits = (scale.getWidth() / (double) regionRect.width <= reducedScale &&
                                scale.getHeight() / (double) regionRect.height <= reducedScale);
                    }
                    if (fits) {
                        rf.factor = ReductionFactor.
                                forScale(reducedScale, 0).factor;
                        logger.debug("readSmallestUsableSubimage(): " +
                                        "subimage {}: {}x{} - fits! " +
                                        "({}x reduction factor)",
                                i + 1, subimageWidth, subimageHeight,
                                rf.factor);
                        final Rectangle reducedRect = new Rectangle(
                                (int) Math.round(regionRect.x * reducedScale),
                                (int) Math.round(regionRect.y * reducedScale),
                                (int) Math.round(regionRect.width * reducedScale),
                                (int) Math.round(regionRect.height * reducedScale));
                        bestImage = tileAwareRead(i, reducedRect, hints);
                        break;
                    } else {
                        logger.debug("readSmallestUsableSubimage(): " +
                                        "subimage {}: {}x{} - too small",
                                i + 1, subimageWidth, subimageHeight);
                    }
                }
            }
        }
        return bestImage;
    }

    /**
     * <p>Returns an image for the requested source area by reading the tiles
     * (or strips) of the source image and joining them into a single image.</p>
     *
     * <p>This method is intended to be compatible with all source images, no
     * matter the data layout (tiled, striped, etc.).</p>
     *
     * <p>This method may populate <code>hints</code> with
     * {@link ImageReader.Hint#ALREADY_CROPPED}, in which case
     * cropping will have already been performed according to the
     * <code>requestedSourceArea</code> parameter.</p>
     *
     * @param imageIndex   Index of the image to read from the ImageReader.
     * @param region       Image region to retrieve. The returned image will be
     *                     this size or smaller if it would overlap the right
     *                     or bottom edge of the source image.
     * @param hints        Will be populated with information returned from the
     *                     reader.
     * @return Image
     * @throws IOException
     */
    private BufferedImage tileAwareRead(final int imageIndex,
                                        final Rectangle region,
                                        final Set<ImageReader.Hint> hints)
            throws IOException {
        final Dimension imageSize = new Dimension(
                iioReader.getWidth(imageIndex),
                iioReader.getHeight(imageIndex));
        logger.debug("tileAwareRead(): acquiring region {},{}/{}x{} from {}x{} image",
                region.x, region.y, region.width, region.height,
                imageSize.width, imageSize.height);

        hints.add(ImageReader.Hint.ALREADY_CROPPED);
        final ImageReadParam param = iioReader.getDefaultReadParam();
        param.setSourceRegion(region);

        return iioReader.read(imageIndex, param);
    }

    ////////////////////////////////////////////////////////////////////////
    /////////////////////// RenderedImage methods //////////////////////////
    ////////////////////////////////////////////////////////////////////////

    /**
     * Reads an image (excluding subimages).
     *
     * @return RenderedImage
     * @throws IOException
     * @throws UnsupportedSourceFormatException
     */
    RenderedImage readRendered() throws IOException,
            UnsupportedSourceFormatException {
        if (iioReader == null) {
            createReader();
        }
        return iioReader.readAsRenderedImage(0,
                iioReader.getDefaultReadParam());
    }

    /**
     * <p>Attempts to reads an image as efficiently as possible, utilizing its
     * tile layout and/or subimages, if possible.</p>
     *
     * @param ops
     * @param orientation     Orientation of the source image data, e.g. as
     *                        reported by embedded metadata.
     * @param reductionFactor {@link ReductionFactor#factor} property will be
     *                        modified to reflect the reduction factor of the
     *                        returned image.
     * @return RenderedImage best matching the given parameters.
     * @throws IOException
     * @throws ProcessorException
     */
    RenderedImage readRendered(final OperationList ops,
                               final Orientation orientation,
                               final ReductionFactor reductionFactor)
            throws IOException, ProcessorException {
        if (iioReader == null) {
            createReader();
        }
        RenderedImage image;
        Crop crop = null;
        for (Operation op : ops) {
            if (op instanceof Crop) {
                crop = (Crop) op;
                crop.applyOrientation(orientation, getSize());
                break;
            }
        }
        if (crop != null) {
            image = iioReader.readAsRenderedImage(0,
                    iioReader.getDefaultReadParam());
        } else {
            image = iioReader.read(0);
        }
        if (image == null) {
            throw new UnsupportedSourceFormatException(iioReader.getFormatName());
        }
        return image;
    }

    /**
     * Reads the smallest image that can fulfill the given crop and scale from
     * a multi-resolution image.
     *
     * @param crop   Requested crop
     * @param scale  Requested scale
     * @param rf     {@link ReductionFactor#factor} will be set to the reduction
     *               factor of the returned image.
     * @return The smallest image fitting the requested crop and scale
     * operations from the given reader.
     * @throws IOException
     */
    protected RenderedImage readSmallestUsableSubimage(final Crop crop,
                                                       final Scale scale,
                                                       final ReductionFactor rf)
            throws IOException {
        final Dimension fullSize = new Dimension(
                iioReader.getWidth(0), iioReader.getHeight(0));
        final Rectangle regionRect = crop.getRectangle(fullSize);
        final ImageReadParam param = iioReader.getDefaultReadParam();
        RenderedImage bestImage = null;
        if (scale.isNoOp()) {
            bestImage = iioReader.readAsRenderedImage(0, param);
            logger.debug("readSmallestUsableSubimage(): using a {}x{} " +
                            "source image (0x reduction factor)",
                    bestImage.getWidth(), bestImage.getHeight());
        } else {
            // Pyramidal TIFFs will have > 1 image, each half the dimensions of
            // the next larger. The "true" parameter tells getNumImages() to
            // scan for images, which seems to be necessary for at least some
            // files, but is slower.
            int numImages = iioReader.getNumImages(false);
            if (numImages > 1) {
                logger.debug("readSmallestUsableSubimage(): detected {} " +
                        "subimage(s)", numImages - 1);
            } else if (numImages == -1) {
                numImages = iioReader.getNumImages(true);
                if (numImages > 1) {
                    logger.debug("readSmallestUsableSubimage(): " +
                            "scan revealed {} subimage(s)", numImages - 1);
                }
            }
            if (numImages == 1) {
                bestImage = iioReader.read(0, param);
                logger.debug("readSmallestUsableSubimage(): using a {}x{} " +
                                "source image (0x reduction factor)",
                        bestImage.getWidth(), bestImage.getHeight());
            } else if (numImages > 1) {
                // Loop through the reduced images from smallest to largest to
                // find the first one that can supply the requested scale
                for (int i = numImages - 1; i >= 0; i--) {
                    final int subimageWidth = iioReader.getWidth(i);
                    final int subimageHeight = iioReader.getHeight(i);

                    final double reducedScale = (double) subimageWidth /
                            (double) fullSize.width;
                    boolean fits = false;
                    if (scale.getPercent() != null) {
                        fits = (scale.getPercent() <= reducedScale);
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_WIDTH) {
                        fits = (scale.getWidth() / (double) regionRect.width <= reducedScale);
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_HEIGHT) {
                        fits = (scale.getHeight() / (double) regionRect.height <= reducedScale);
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_INSIDE) {
                        fits = (scale.getWidth() / (double) regionRect.width <= reducedScale &&
                                scale.getHeight() / (double) regionRect.height <= reducedScale);
                    } else if (scale.getMode() == Scale.Mode.NON_ASPECT_FILL) {
                        fits = (scale.getWidth() / (double) regionRect.width <= reducedScale &&
                                scale.getHeight() / (double) regionRect.height <= reducedScale);
                    }
                    if (fits) {
                        rf.factor = ReductionFactor.forScale(reducedScale, 0).factor;
                        logger.debug("readSmallestUsableSubimage(): " +
                                        "subimage {}: {}x{} - fits! " +
                                        "({}x reduction factor)",
                                i + 1, subimageWidth, subimageHeight,
                                rf.factor);
                        bestImage = iioReader.readAsRenderedImage(i, param);
                        break;
                    } else {
                        logger.debug("readSmallestUsableSubimage(): " +
                                        "subimage {}: {}x{} - too small",
                                i + 1, subimageWidth, subimageHeight);
                    }
                }
            }
        }
        return bestImage;
    }

}
