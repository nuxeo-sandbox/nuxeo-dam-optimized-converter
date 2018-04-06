package org.nuxeo.labs.dam.converters.workers;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.util.HashMap;

public class BigPictureMultiConversionWorker extends PictureMultiConversionWorker {

    private static final long serialVersionUID = 1L;

    public static final String CATEGORY = "bigPictureViewsGeneration";

    public BigPictureMultiConversionWorker(String repositoryName, String docId, String xpath) {
        super(repositoryName, docId, xpath);
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public String getTitle() {
        return "Big Picture Views Generation";
    }

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

        ImageInfo imageInfo;
        BlobHolder result;

        try {
            imageInfo = getImageInfo(blob);
            ConversionService conversionService = Framework.getService(ConversionService.class);
            result = conversionService.convert(CONVERTER_NAME, new SimpleBlobHolder(blob), new HashMap<>());
        } catch (Exception e) {
            throw new NuxeoException(e);
        } finally {
            TransactionHelper.startTransaction();
        }

        updateDocument(imageInfo, result, workingDocument);

        TransactionHelper.commitOrRollbackTransaction();
        closeSession();

        setStatus("Done");
    }

}