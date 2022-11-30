package org.nuxeo.labs.dam.converters.bulk;

import org.apache.commons.io.FilenameUtils;
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
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.ecm.platform.picture.api.ImagingService;
import org.nuxeo.ecm.platform.picture.api.PictureConversion;
import org.nuxeo.ecm.platform.picture.api.PictureView;
import org.nuxeo.ecm.platform.picture.api.PictureViewImpl;
import org.nuxeo.ecm.platform.picture.listener.PictureViewsGenerationListener;
import org.nuxeo.ecm.platform.picture.recompute.RecomputeViewsAction;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.nuxeo.ecm.core.api.CoreSession.ALLOW_VERSION_WRITE;
import static org.nuxeo.ecm.core.api.versioning.VersioningService.DISABLE_AUTOMATIC_VERSIONING;
import static org.nuxeo.ecm.core.api.versioning.VersioningService.DISABLE_AUTO_CHECKOUT;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.ecm.core.bulk.action.SetPropertiesAction.PARAM_DISABLE_AUDIT;
import static org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener.DISABLE_DUBLINCORE_LISTENER;
import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_INFO_PROPERTY;
import static org.nuxeo.ecm.platform.picture.api.adapters.AbstractPictureAdapter.VIEWS_PROPERTY;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

public class OptimizedRecomputeViewsAction extends RecomputeViewsAction {

    private static final Logger log = LogManager.getLogger(OptimizedRecomputeViewsAction.class);

    public static final String CONVERTER_NAME = "MultiOutputPictureResize";

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                       .addComputation(OptimizedRecomputeViewsComputation::new, //
                               Arrays.asList(INPUT_1 + ":" + ACTION_FULL_NAME, OUTPUT_1 + ":" + STATUS_STREAM))
                       .build();
    }

    public static class OptimizedRecomputeViewsComputation extends RecomputeViewsComputation {

        public OptimizedRecomputeViewsComputation() {
            super();
        }

        @Override
        protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> properties) {
            log.debug("Compute action: {} for doc ids: {}", ACTION_NAME, ids);
            for (String docId : ids) {
                pictureViewsHelper.newTransaction();

                DocumentModel workingDocument = session.getDocument(new IdRef(docId));
                Property fileProp = workingDocument.getProperty(xpath);
                Blob blob = (Blob) fileProp.getValue();
                if (blob == null) {
                    // do nothing
                    return;
                }

                ImageInfo imageInfo = getImageInfo(blob);

                ConversionService conversionService = Framework.getService(ConversionService.class);

                BlobHolder result = conversionService.convert(CONVERTER_NAME, new SimpleBlobHolder(blob), new HashMap<>());

                updateDocument(imageInfo, result, workingDocument,session);

                fireEvent(session, session.getDocument(new IdRef(docId)), PICTURE_VIEWS_GENERATION_DONE_EVENT);
            }
        }

        protected void updateDocument(ImageInfo imageInfo, BlobHolder result, DocumentModel workingDocument, CoreSession session) {
            List<PictureView> viewList = buildViews(workingDocument, result.getBlobs());
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
            workingDocument.putContextData(PARAM_DISABLE_AUDIT, Boolean.TRUE);
            workingDocument.putContextData(DISABLE_AUTO_CHECKOUT, Boolean.TRUE);
            workingDocument.putContextData(PictureViewsGenerationListener.DISABLE_PICTURE_VIEWS_GENERATION_LISTENER, Boolean.TRUE);
            workingDocument.putContextData(DISABLE_DUBLINCORE_LISTENER, Boolean.TRUE);
            workingDocument.putContextData(DISABLE_AUTOMATIC_VERSIONING, Boolean.TRUE);
            session.saveDocument(workingDocument);

        }

        protected ImageInfo getImageInfo(Blob blob) {
            ImagingService is = Framework.getService(ImagingService.class);
            return is.getImageInfo(blob);
        }

        protected List<PictureView> buildViews(DocumentModel workingDocument, List<Blob> blobs) {
            List<PictureView> pictureViews = new ArrayList<>();

            ImagingService imagingService = Framework.getService(ImagingService.class);
            List<PictureConversion> conversionList = imagingService.getPictureConversions();

            Map<String, PictureConversion> conversionMap = conversionList.stream().collect(
                    Collectors.toMap(x -> x.getId(), x -> x));

            MimetypeRegistry registry = Framework.getService(MimetypeRegistry.class);

            //get document name without extension
            String basename = FilenameUtils.getBaseName(workingDocument.getTitle());

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

                String extension = FilenameUtils.getExtension(blob.getFilename());
                String mimetype = registry.getMimetypeFromExtension(extension);

                blob.setMimeType(mimetype);
                blob.setFilename(String.format("%s_%s.%s",basename,id.toLowerCase(),extension));

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
            pictureViews.sort(Comparator.comparingInt(PictureView::getHeight));
            return pictureViews;
        }

        protected Long getLongValue(Object object) {
            if (object instanceof Integer) {
                return Long.valueOf(object.toString());
            } else {
                return null;
            }
        }

    }

}
