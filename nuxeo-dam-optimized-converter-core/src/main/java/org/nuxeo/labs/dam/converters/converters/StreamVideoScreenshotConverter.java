package org.nuxeo.labs.dam.converters.converters;

import org.apache.commons.io.FilenameUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CloseableFile;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.cache.SimpleCachableBlobHolder;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandException;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandLineExecutorService;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.ecm.platform.commandline.executor.api.ExecResult;
import org.nuxeo.ecm.platform.video.convert.ScreenshotConverter;
import org.nuxeo.labs.dam.converters.BlobHelper;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import static org.nuxeo.ecm.platform.video.convert.Constants.INPUT_FILE_PATH_PARAMETER;
import static org.nuxeo.ecm.platform.video.convert.Constants.POSITION_PARAMETER;

public class StreamVideoScreenshotConverter extends ScreenshotConverter {

    @Override
    public BlobHolder convert(BlobHolder blobHolder, Map<String, Serializable> parameters) throws ConversionException {
        CommandLineExecutorService cles = Framework.getService(CommandLineExecutorService.class);
        CmdParameters params = cles.getDefaultCmdParameters();

        Blob blob = blobHolder.getBlob();
        String uriStr = BlobHelper.getDirectUrl(blob);

        if (uriStr != null) {
            params.addNamedParameter(INPUT_FILE_PATH_PARAMETER, uriStr);
            try {
                return process(parameters, params);
            } catch (IOException | CommandNotAvailable | CommandException e) {
                throw new ConversionException(e);
            }
        } else {
            try (CloseableFile source = blob.getCloseableFile("." + FilenameUtils.getExtension(blob.getFilename()))) {
                params.addNamedParameter(INPUT_FILE_PATH_PARAMETER, source.getFile().getAbsolutePath());
                return process(parameters, params);
            } catch (IOException | CommandNotAvailable | CommandException e) {
                throw new ConversionException(e);
            }
        }
    }

    protected BlobHolder process(Map<String, Serializable> parameters, CmdParameters params) throws CommandException, CommandNotAvailable, IOException {
        CommandLineExecutorService cles = Framework.getService(CommandLineExecutorService.class);
        Blob outBlob = Blobs.createBlobWithExtension(".jpeg");
        params.addNamedParameter("outFilePath", outBlob.getFile().getAbsolutePath());
        Double position = 0.0;
        if (parameters != null) {
            position = (Double) parameters.get(POSITION_PARAMETER);
            if (position == null) {
                position = 0.0;
            }
        }
        params.addNamedParameter(POSITION_PARAMETER, String.valueOf(position));
        ExecResult res = cles.execCommand(FFMPEG_SCREENSHOT_COMMAND, params);
        if (!res.isSuccessful()) {
            throw res.getError();
        }
        outBlob.setMimeType("image/jpeg");
        outBlob.setFilename(String.format("video-screenshot-%.2f-seconds.jpeg", position));

        return new SimpleCachableBlobHolder(outBlob);
    }

}
