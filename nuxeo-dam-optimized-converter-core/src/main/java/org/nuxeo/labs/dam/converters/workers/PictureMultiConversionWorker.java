package org.nuxeo.labs.dam.converters.workers;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.picture.api.*;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.nuxeo.ecm.core.api.CoreSession.ALLOW_VERSION_WRITE;
import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_INFO_PROPERTY;
import static org.nuxeo.ecm.platform.picture.api.adapters.AbstractPictureAdapter.VIEWS_PROPERTY;

public class PictureMultiConversionWorker extends AbstractWork {

    private static final long serialVersionUID = 1L;

    public static final String CATEGORY_PICTURE_GENERATION = "pictureViewsGeneration";

    public static final String PICTURE_VIEWS_GENERATION_DONE_EVENT = "pictureViewsGenerationDone";

    public static final String CONVERTER_NAME = "MultiOutputPictureResize";

    protected final String xpath;

    public PictureMultiConversionWorker(String repositoryName, String docId, String xpath) {
        super(repositoryName + ':' + docId + ':' + xpath + ":pictureView");
        setDocument(repositoryName, docId);
        this.xpath = xpath;
    }

    @Override
    public String getCategory() {
        return CATEGORY_PICTURE_GENERATION;
    }

    @Override
    public String getTitle() {
        return "Picture views generation";
    }

    @Override
    public void work() {
        setProgress(Progress.PROGRESS_INDETERMINATE);
        setStatus("Extracting");

        openSystemSession();
        if (!session.exists(new IdRef(docId))) {
            setStatus("Document does not exist");
            return;
        }

        DocumentModel workingDocument = session.getDocument(new IdRef(docId));
        Property fileProp = workingDocument.getProperty(xpath);
        Blob blob = (Blob) fileProp.getValue();
        if (blob == null) {
            // do nothing
            return;
        }

        TransactionHelper.commitOrRollbackTransaction();

        setStatus("Running conversions");

        List<PictureView> viewList;
        ImageInfo imageInfo = null;

        try {
            ImagingService imagingService = Framework.getService(ImagingService.class);
            imageInfo = imagingService.getImageInfo(blob);
            ConversionService conversionService = Framework.getService(ConversionService.class);
            BlobHolder result = conversionService.convert(CONVERTER_NAME, new SimpleBlobHolder(blob), new HashMap<>());
            viewList = buildViews(result.getBlobs());

        } finally {
            TransactionHelper.startTransaction();
        }

        List<Map<String, Serializable>> views = new ArrayList<>();
        for (PictureView pictureView : viewList) {
            views.add(pictureView.asMap());
        }

        workingDocument.setPropertyValue(PICTURE_INFO_PROPERTY, (Serializable) imageInfo.toMap());
        workingDocument.setPropertyValue(VIEWS_PROPERTY, (Serializable) views);

        setStatus("Saving");
        if (workingDocument.isVersion()) {
            workingDocument.putContextData(ALLOW_VERSION_WRITE, Boolean.TRUE);
        }
        workingDocument.putContextData("disableNotificationService", Boolean.TRUE);
        workingDocument.putContextData("disableAuditLogger", Boolean.TRUE);
        workingDocument.putContextData(VersioningService.DISABLE_AUTO_CHECKOUT, Boolean.TRUE);
        session.saveDocument(workingDocument);

        firePictureViewsGenerationDoneEvent(workingDocument);

        TransactionHelper.commitOrRollbackTransaction();
        closeSession();

        setStatus("Done");
    }

    /**
     * Fire a {@code PICTURE_VIEWS_GENERATION_DONE_EVENT} event when no other PictureMultiConversionWorker is scheduled
     * for this document.
     *
     * @since 5.8
     */
    protected void firePictureViewsGenerationDoneEvent(DocumentModel doc) {
        WorkManager workManager = Framework.getService(WorkManager.class);
        List<String> workIds = workManager.listWorkIds(CATEGORY_PICTURE_GENERATION, null);
        int worksCount = 0;
        for (String workId : workIds) {
            if (workId.equals(getId())) {
                if (++worksCount > 1) {
                    // another work scheduled
                    return;
                }
            }
        }
        DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), doc);
        Event event = ctx.newEvent(PICTURE_VIEWS_GENERATION_DONE_EVENT);
        Framework.getService(EventService.class).fireEvent(event);
    }


    protected List<PictureView> buildViews(List<Blob> blobs) {
        List<PictureView> pictureViews = new ArrayList<>();

        ImagingService imagingService = Framework.getService(ImagingService.class);
        List<PictureConversion> conversionList = imagingService.getPictureConversions();

        Map<String, PictureConversion> conversionMap = conversionList.stream().collect(
                Collectors.toMap(x -> x.getId(), x -> x));

        for(Blob blob:blobs) {
            String filename = blob.getFilename();
            int index = filename.indexOf("Output");
            if (index<=0) {
                continue;
            }
            String id = filename.substring(0,index);
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
            pictureViews.add(view);
        }

        return pictureViews;
    }

}