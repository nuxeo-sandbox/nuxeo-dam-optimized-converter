package org.nuxeo.labs.dam.converters.converters;

import org.apache.commons.io.FilenameUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CloseableFile;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolderWithProperties;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.platform.commandline.executor.api.*;
import org.nuxeo.ecm.platform.video.convert.StoryboardConverter;
import org.nuxeo.labs.dam.converters.BlobHelper;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.nuxeo.ecm.platform.video.convert.Constants.*;

public class StreamVideoStoryboardConverter extends StoryboardConverter {

    @Override
    public BlobHolder convert(BlobHolder blobHolder, Map<String, Serializable> parameters) throws ConversionException {
        CommandLineExecutorService cles = Framework.getService(CommandLineExecutorService.class);
        CmdParameters params = cles.getDefaultCmdParameters();

        Blob blob = blobHolder.getBlob();
        String uriStr = BlobHelper.getDirectUrl(blob);

        if (uriStr!=null) {
            params.addNamedParameter(INPUT_FILE_PATH_PARAMETER, uriStr);
            try {
                return process(parameters,params,blob);
            } catch (IOException | CommandNotAvailable | CommandException e) {
                throw new ConversionException(e);
            }
        } else {
            try (CloseableFile source = blob.getCloseableFile("." + FilenameUtils.getExtension(blob.getFilename()))) {
                params.addNamedParameter(INPUT_FILE_PATH_PARAMETER, source.getFile().getAbsolutePath());
                return process(parameters,params,blob);
            } catch (IOException | CommandNotAvailable | CommandException e) {
                throw new ConversionException(e);
            }
        }
    }

    protected BlobHolder process(Map<String, Serializable> parameters, CmdParameters params, Blob blob) throws CommandException, CommandNotAvailable, IOException {
        CommandLineExecutorService cles = Framework.getService(CommandLineExecutorService.class);

        // Build the empty output structure
        Map<String, Serializable> properties = new HashMap<>();
        List<Blob> blobs = new ArrayList<>();
        List<Double> timecodes = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        properties.put("timecodes", (Serializable) timecodes);
        properties.put("comments", (Serializable) comments);
        SimpleBlobHolderWithProperties bh = new SimpleBlobHolderWithProperties(blobs, properties);

        Double duration = (Double) parameters.get("duration");
        if (duration == null) {
            log.warn(String.format("Cannot extract storyboard for file '%s'" + " with missing duration info.",
                    blob.getFilename()));
            return bh;
        }

        // add the command line parameters for the storyboard extraction and run it
        int numberOfThumbnails = getNumberOfThumbnails(parameters);
        for (int i = 0; i < numberOfThumbnails; i++) {
            double timecode = BigDecimal.valueOf(i * duration / numberOfThumbnails)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            Blob thumbBlob = Blobs.createBlobWithExtension(".jpeg");
            params.addNamedParameter(OUTPUT_FILE_PATH_PARAMETER, thumbBlob.getFile().getAbsolutePath());
            params.addNamedParameter(POSITION_PARAMETER, String.valueOf(timecode));
            fillWidthAndHeightParameters(params, parameters);
            ExecResult result = cles.execCommand(FFMPEG_SCREENSHOT_RESIZE_COMMAND, params);
            if (!result.isSuccessful()) {
                throw result.getError();
            }
            thumbBlob.setMimeType("image/jpeg");
            thumbBlob.setFilename(String.format(Locale.ENGLISH, "%.2f-seconds.jpeg", timecode));
            blobs.add(thumbBlob);
            timecodes.add(timecode);
            comments.add(String.format("%s %d", blob.getFilename(), i));
        }
        return bh;
    }


}
