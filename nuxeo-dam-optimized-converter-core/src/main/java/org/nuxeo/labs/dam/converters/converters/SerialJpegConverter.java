package org.nuxeo.labs.dam.converters.converters;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.cache.SimpleCachableBlobHolder;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.convert.plugins.CommandLineBasedConverter;
import org.nuxeo.runtime.api.Framework;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SerialJpegConverter extends CommandLineBasedConverter {

    public static final String SOURCE_FILE_PATH_KEY = "inputFilePath";
    public static final String OUT_DIR_PATH_KEY = "outDirPath";
    public static final String[] OUTPUTS_FILENAME_KEY = {"FullHDOutputFilePath","MediumOutputFilePath","SmallOutputFilePath","ThumbnailOutputFilePath"};

    @Override
    protected Map<String, Blob> getCmdBlobParameters(BlobHolder blobHolder, Map<String, Serializable> parameters) throws ConversionException {
        Map<String, Blob> cmdBlobParams = new HashMap<>();
        cmdBlobParams.put(SOURCE_FILE_PATH_KEY, blobHolder.getBlob());
        return cmdBlobParams;
    }

    @Override
    protected Map<String, String> getCmdStringParameters(BlobHolder blobHolder, Map<String, Serializable> parameters) throws ConversionException {
        String tmpDir = getTmpDirectory(parameters);
        Path tmpDirPath = tmpDir != null ? Paths.get(tmpDir) : null;
        try {
            Path outDirPath = tmpDirPath != null ? Files.createTempDirectory(tmpDirPath, null)
                    : Framework.createTempDirectory(null);

            Map<String, String> cmdStringParams = new HashMap<>();
            cmdStringParams.put(OUT_DIR_PATH_KEY, outDirPath.toString());

            for(String targetFileName : OUTPUTS_FILENAME_KEY) {
                Path targetFilePath = Paths.get(outDirPath.toString(), targetFileName+".jpg");
                cmdStringParams.put(targetFileName, targetFilePath.toString());
            }

            // pass all converter parameters to the command line
            for (Map.Entry<String, Serializable> entry : parameters.entrySet()) {
                cmdStringParams.put(entry.getKey(), (String) entry.getValue());
            }
            // pass all the converter descriptor parameters to the commandline
            for (Map.Entry<String, String> entry : initParameters.entrySet()) {
                cmdStringParams.put(entry.getKey(), entry.getValue());

            }
            return cmdStringParams;
        } catch (IOException e) {
            throw new ConversionException(e.getMessage(), e);
        }
    }

    @Override
    protected BlobHolder buildResult(List<String> cmdOutput, CmdParameters cmdParams) throws ConversionException {
        String outputPath = cmdParams.getParameter(OUT_DIR_PATH_KEY);
        List<Blob> blobs = new ArrayList<>();
        File outputDir = new File(outputPath);
        if (outputDir.exists() && outputDir.isDirectory()) {
            File[] files = outputDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    try {
                        Blob blob = Blobs.createBlob(file);
                        blob.setFilename(file.getName());
                        blobs.add(blob);
                    } catch (IOException e) {
                        throw new ConversionException("Cannot create blob", e);
                    }
                }
            }
        }
        return new SimpleCachableBlobHolder(blobs);
    }

}
