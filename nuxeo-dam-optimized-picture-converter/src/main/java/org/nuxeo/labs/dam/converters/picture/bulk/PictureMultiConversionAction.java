package org.nuxeo.labs.dam.converters.picture.bulk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.binary.metadata.api.BinaryMetadataService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.ecm.platform.picture.api.ImagingService;
import org.nuxeo.ecm.platform.picture.api.PictureConversion;
import org.nuxeo.ecm.platform.picture.api.PictureView;
import org.nuxeo.ecm.platform.picture.api.PictureViewImpl;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.nuxeo.ecm.core.api.CoreSession.ALLOW_VERSION_WRITE;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_INFO_PROPERTY;
import static org.nuxeo.ecm.platform.picture.api.adapters.AbstractPictureAdapter.VIEWS_PROPERTY;
import static org.nuxeo.ecm.platform.picture.listener.PictureViewsGenerationListener.DISABLE_PICTURE_VIEWS_GENERATION_LISTENER;
import static org.nuxeo.ecm.platform.picture.recompute.RecomputeViewsAction.PARAM_XPATH;
import static org.nuxeo.ecm.platform.picture.recompute.RecomputeViewsAction.RecomputeViewsComputation.PICTURE_VIEWS_GENERATION_DONE_EVENT;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

public class PictureMultiConversionAction implements StreamProcessorTopology {

    private static final Logger log = LogManager.getLogger(PictureMultiConversionAction.class);

    public static final String ACTION_NAME = "multiRecomputeViews";
    public static final String ACTION_FULL_NAME = "bulk/" + ACTION_NAME;

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                .addComputation(PictureMultiConversionComputation::new,
                        Arrays.asList(INPUT_1 + ":" + ACTION_FULL_NAME, OUTPUT_1 + ":" + STATUS_STREAM))
                .build();
    }

    public static class PictureMultiConversionComputation extends AbstractBulkComputation {

        public static final String CONVERTER_NAME = "MultiOutputPictureResize";
        public static final long BILLION = 1_000_000_000;
        protected String xpath;

        public PictureMultiConversionComputation() {
            super(ACTION_FULL_NAME);
        }

        @Override
        public void startBucket(String bucketKey) {
            BulkCommand command = getCurrentCommand();
            xpath = command.getParam(PARAM_XPATH);
        }

        @Override
        protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> map) {
            for (String docId : ids) {
                if (!session.exists(new IdRef(docId))) {
                    log.debug("Doc id doesn't exist: {}", docId);
                    return;
                }

                DocumentModel workingDocument = session.getDocument(new IdRef(docId));
                Property fileProp = workingDocument.getProperty(xpath);
                Blob blob = (Blob) fileProp.getValue();
                if (blob == null) {
                    return;
                }

                ImageInfo imageInfo;
                BlobHolder result;

                try {
                    imageInfo = getImageInfo(blob);
                    /*if (isBigImageFile(imageInfo)) {
                        BigPictureMultiConversionWorker worker = new BigPictureMultiConversionWorker(this.repositoryName,
                                docId, this.xpath);
                        WorkManager wm = Framework.getService(WorkManager.class);
                        wm.schedule(worker);
                    } else {
                        ConversionService conversionService = Framework.getService(ConversionService.class);
                        result = conversionService.convert(CONVERTER_NAME, new SimpleBlobHolder(blob), new HashMap<>());
                    }*/
                    ConversionService conversionService = Framework.getService(ConversionService.class);
                    result = conversionService.convert(CONVERTER_NAME, new SimpleBlobHolder(blob), new HashMap<>());
                } catch (Exception e) {
                    throw new NuxeoException("Could not transcode image file in " + workingDocument.getPath(), e);
                }

                if (result != null) {
                    updateDocument(session, imageInfo, result, workingDocument);
                }
            }
        }

        protected ImageInfo getImageInfo(Blob blob) {
            try {
                BinaryMetadataService service = Framework.getService(BinaryMetadataService.class);
                Map<String, Object> metadata = service.readMetadata(blob, true);
                Map<String, Serializable> info = new HashMap<>();
                info.put(ImageInfo.WIDTH, getLongValue(metadata.get("ImageWidth")));
                info.put(ImageInfo.HEIGHT, getLongValue(metadata.get("ImageHeight")));
                info.put(ImageInfo.COLOR_SPACE, (Serializable) metadata.get("ColorMode"));
                info.put(ImageInfo.FORMAT, (Serializable) metadata.get("FileType"));

                Object bitDepth = metadata.get("BitDepth");
                if (bitDepth != null) {
                    info.put(ImageInfo.DEPTH, getLongValue(bitDepth));
                } else {
                    Object bitsPerSample = metadata.get("BitsPerSample");
                    if (bitsPerSample != null) {
                        info.put(ImageInfo.DEPTH, getLongValue(bitsPerSample));
                    }
                }
                return ImageInfo.fromMap(info);
            } catch (NuxeoException e) {
                ImagingService is = Framework.getService(ImagingService.class);
                return is.getImageInfo(blob);
            }
        }

        protected Long getLongValue(Object object) {
            if (object instanceof Integer) {
                return Long.valueOf(object.toString());
            } else {
                return null;
            }
        }

        protected boolean isBigImageFile(ImageInfo imageInfo) {
            return ((long) imageInfo.getWidth() * (long) imageInfo.getHeight()) > BILLION;
        }

        protected void updateDocument(CoreSession session, ImageInfo imageInfo, BlobHolder result,
                                      DocumentModel workingDocument) {
            List<PictureView> viewList = buildViews(result.getBlobs());
            List<Map<String, Serializable>> views = new ArrayList<>();
            for (PictureView pictureView : viewList) {
                views.add(pictureView.asMap());
            }

            workingDocument.setPropertyValue(PICTURE_INFO_PROPERTY, (Serializable) imageInfo.toMap());
            workingDocument.setPropertyValue(VIEWS_PROPERTY, (Serializable) views);

            if (workingDocument.isVersion()) {
                workingDocument.putContextData(ALLOW_VERSION_WRITE, Boolean.TRUE);
            }
            workingDocument.putContextData("disableNotificationService", Boolean.TRUE);
            workingDocument.putContextData("disableAuditLogger", Boolean.TRUE);
            workingDocument.putContextData(DISABLE_PICTURE_VIEWS_GENERATION_LISTENER, Boolean.TRUE);
            workingDocument.putContextData(VersioningService.DISABLE_AUTO_CHECKOUT, Boolean.TRUE);
            session.saveDocument(workingDocument);

            firePictureViewsGenerationDoneEvent(session, workingDocument);
        }

        protected List<PictureView> buildViews(List<Blob> blobs) {
            List<PictureView> pictureViews = new ArrayList<>();

            ImagingService imagingService = Framework.getService(ImagingService.class);
            List<PictureConversion> conversionList = imagingService.getPictureConversions();

            Map<String, PictureConversion> conversionMap = conversionList.stream().collect(
                    Collectors.toMap(x -> x.getId(), x -> x));

            for (Blob blob : blobs) {
                String filename = blob.getFilename();
                int index = filename.indexOf("Output");
                if (index <= 0) {
                    continue;
                }
                String id = filename.substring(0, index);
                PictureConversion conversion = conversionMap.get(id);
                if (conversion == null) {
                    continue;
                }

                PictureView view = new PictureViewImpl();
                ImageInfo imageInfo = imagingService.getImageInfo(blob);
                view.setTitle(id);
                view.setBlob(blob);
                view.setHeight(imageInfo.getHeight());
                view.setWidth(imageInfo.getWidth());
                view.setDescription(conversion.getDescription());
                view.setImageInfo(imageInfo);
                view.setFilename(blob.getFilename());
                pictureViews.add(view);
            }

            //sort views
            Collections.sort(pictureViews, Comparator.comparingInt(PictureView::getHeight));
            return pictureViews;
        }

        protected void firePictureViewsGenerationDoneEvent(CoreSession session, DocumentModel doc) {
            DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), doc);
            Event event = ctx.newEvent(PICTURE_VIEWS_GENERATION_DONE_EVENT);
            Framework.getService(EventService.class).fireEvent(event);
        }

    }

}