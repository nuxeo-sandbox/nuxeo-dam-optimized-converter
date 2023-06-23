package org.nuxeo.labs.dam.converters.converters;

import static org.nuxeo.ecm.platform.video.convert.Constants.INPUT_FILE_PATH_PARAMETER;
import static org.nuxeo.ecm.platform.video.convert.Constants.OUTPUT_FILE_NAME_PARAMETER;
import static org.nuxeo.ecm.platform.video.convert.Constants.OUTPUT_FILE_PATH_PARAMETER;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.platform.video.VideoInfo;
import org.nuxeo.ecm.platform.video.convert.VideoConversionConverter;
import org.nuxeo.labs.dam.converters.BlobHelper;

public class StreamVideoConversionConverter extends VideoConversionConverter {

    @Override
    protected Map<String, Blob> getCmdBlobParameters(BlobHolder blobHolder,
                                                     Map<String, Serializable> stringSerializableMap) throws ConversionException {
        Blob blob = blobHolder.getBlob();
        String uriStr = BlobHelper.getDirectUrl(blob);
        if (uriStr == null) {
            Map<String, Blob> cmdBlobParams = new HashMap<>();
            cmdBlobParams.put(INPUT_FILE_PATH_PARAMETER, blob);
            return cmdBlobParams;
        } else {
            return null;
        }
    }

    @Override
    protected Map<String, String> getCmdStringParameters(BlobHolder blobHolder, Map<String, Serializable> parameters)
            throws ConversionException {
        Map<String, String> cmdStringParams = new HashMap<>();

        Blob blob = blobHolder.getBlob();
        String uriStr = BlobHelper.getDirectUrl(blob);
        if (uriStr != null) {
            cmdStringParams.put(INPUT_FILE_PATH_PARAMETER, uriStr);
        }

        String baseDir = getTmpDirectory(parameters);
        Path tmpPath = new Path(baseDir).append(getTmpDirectoryPrefix() + "_" + UUID.randomUUID());

        File outDir = new File(tmpPath.toString());
        boolean dirCreated = outDir.mkdir();
        if (!dirCreated) {
            throw new ConversionException("Unable to create tmp dir for transformer output: " + outDir);
        }

        File outFile;
        try {
            outFile = File.createTempFile("videoConversion", getVideoExtension(), outDir);
        } catch (IOException e) {
            throw new ConversionException("Unable to get Blob for holder", e);
        }
        // delete the file as we need only the path for ffmpeg
        outFile.delete();
        cmdStringParams.put(OUTPUT_FILE_PATH_PARAMETER, outFile.getAbsolutePath());
        String baseName = FilenameUtils.getBaseName(blobHolder.getBlob().getFilename());
        cmdStringParams.put(OUTPUT_FILE_NAME_PARAMETER, baseName + getVideoExtension());

        VideoInfo videoInfo = (VideoInfo) parameters.get("videoInfo");
        if (videoInfo == null) {
            return cmdStringParams;
        }

        long width = videoInfo.getWidth();
        long height = videoInfo.getHeight();
        long newHeight = (Long) parameters.get("height");

        long newWidth = width * newHeight / height;
        if (newWidth % 2 != 0) {
            newWidth += 1;
        }

        cmdStringParams.put(OUTPUT_TMP_PATH, outDir.getAbsolutePath());
        cmdStringParams.put("width", String.valueOf(newWidth));
        cmdStringParams.put("height", String.valueOf(newHeight));
        return cmdStringParams;
    }

}
