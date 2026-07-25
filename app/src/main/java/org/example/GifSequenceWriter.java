package org.example;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public final class GifSequenceWriter implements Closeable {
    private final ImageWriter writer;
    private final ImageWriteParam params;
    private final IIOMetadata metadata;
    private final ImageOutputStream output;

    public GifSequenceWriter(ImageOutputStream output, int imageType, int delayMs, boolean loop) throws IOException {
        this.output = output;
        writer = ImageIO.getImageWritersBySuffix("gif").next();
        params = writer.getDefaultWriteParam();
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(imageType);
        metadata = writer.getDefaultImageMetadata(type, params);
        configureMetadata(metadata, delayMs, loop);
        writer.setOutput(output);
        writer.prepareWriteSequence(null);
    }

    public void write(BufferedImage image) throws IOException {
        writer.writeToSequence(new IIOImage(image, null, metadata), params);
    }

    @Override
    public void close() throws IOException {
        writer.endWriteSequence();
        writer.dispose();
    }

    private void configureMetadata(IIOMetadata metadata, int delayMs, boolean loop) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

        IIOMetadataNode graphicsControl = getOrCreate(root, "GraphicControlExtension");
        // Restoring the background between frames avoids ghost trails when transparent PNG sprites
        // are quantized into GIF frames. APNG remains the preferred format for full alpha fidelity.
        graphicsControl.setAttribute("disposalMethod", "restoreToBackgroundColor");
        graphicsControl.setAttribute("userInputFlag", "FALSE");
        graphicsControl.setAttribute("transparentColorFlag", "FALSE");
        graphicsControl.setAttribute("delayTime", Integer.toString(Math.max(1, delayMs / 10)));
        graphicsControl.setAttribute("transparentColorIndex", "0");

        if (loop) {
            IIOMetadataNode appExtensions = getOrCreate(root, "ApplicationExtensions");
            IIOMetadataNode appExtension = new IIOMetadataNode("ApplicationExtension");
            appExtension.setAttribute("applicationID", "NETSCAPE");
            appExtension.setAttribute("authenticationCode", "2.0");
            appExtension.setUserObject(new byte[] { 0x1, 0x0, 0x0 });
            appExtensions.appendChild(appExtension);
        }

        metadata.setFromTree(format, root);
    }

    private IIOMetadataNode getOrCreate(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
