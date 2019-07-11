package org.nuxeo.labs.dam.converters.converters;

import org.apache.commons.io.FilenameUtils;
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

public class MultiOutputPictureConverter extends CommandLineBasedConverter {

    public static final String SOURCE_FILE_PATH_KEY = "inputFilePath";
    public static final String OUT_DIR_PATH_KEY = "outDirPath";
    public static final String OUTPUTS_KEY = "outputs";
    public static final String OUTPUTS_RESIZES_NAMES_KEY = "outputsResizesNames";
    public static final String OUTPUTS_RESIZES_KEY = "outputsResizes";
    public static final String OUTPUTS_EXTENSIONS_KEY = "outputsExtensions";
    public static final String OUTPUTS_PREFIXES_KEY = "outputsPrefix";

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

            String outputs[] = initParameters.get(OUTPUTS_KEY).split(",");
            String outPutsResizesNames[] = initParameters.get(OUTPUTS_RESIZES_NAMES_KEY).split(",");
            String outPutsResizes[] = initParameters.get(OUTPUTS_RESIZES_KEY).split(",");
            String outPutsExtensions[] = initParameters.get(OUTPUTS_EXTENSIONS_KEY).split(",");
            String outPutsPrefixes[] = initParameters.get(OUTPUTS_PREFIXES_KEY).split(",");
            
            String sourceFileName = blobHolder.getBlob().getFilename();
            sourceFileName = FilenameUtils.removeExtension(sourceFileName);
            
            // We must assume all our arrays have the same size
            String targetFileName;
            for(int i = 0; i < outputs.length; i++) {
                targetFileName = outPutsPrefixes[i] + sourceFileName + outPutsExtensions[i];
                Path targetFilePath = Paths.get(outDirPath.toString(), targetFileName);
                cmdStringParams.put(outputs[i], targetFilePath.toString());
                cmdStringParams.put(outPutsResizesNames[i], outPutsResizes[i]);
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
