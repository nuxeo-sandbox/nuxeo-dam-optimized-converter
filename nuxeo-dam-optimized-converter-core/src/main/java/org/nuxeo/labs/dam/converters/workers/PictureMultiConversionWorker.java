package org.nuxeo.labs.dam.converters.workers;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.binary.metadata.api.BinaryMetadataService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;
import org.nuxeo.ecm.core.convert.service.ConversionServiceImpl;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.picture.api.*;
import org.nuxeo.labs.dam.converters.converters.MultiOutputPictureConverter;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static org.nuxeo.ecm.core.api.CoreSession.ALLOW_VERSION_WRITE;
import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_INFO_PROPERTY;
import static org.nuxeo.ecm.platform.picture.api.adapters.AbstractPictureAdapter.VIEWS_PROPERTY;
import static org.nuxeo.labs.dam.converters.listeners.CustomPictureViewsGenerationListener.DISABLE_PICTURE_VIEWS_GENERATION_LISTENER;

/**
 * Create the picture:views field.
 * <br/>
 * WARNING: As the whole purpose of the plugin is to override the default conversions,
 * <b>items declared in "pictureConversion" extension point are ignored</b>, all that count
 * are the one declared in the XML extension (that can be overridden)
 * => See picture-converter-contrib.xml and its related commandline-contrib.xml
 * <br/>
 * TODO:
 * This is not the best way of doing things (having parameters in a converter contribution that are of no use for the
 * conversion itself) => this needs some refactoring
 * 
 * @since 9.10
 */
public class PictureMultiConversionWorker extends AbstractWork {

    private static final long serialVersionUID = 1L;

    public static final String CATEGORY_PICTURE_GENERATION = "pictureViewsGeneration";

    public static final String PICTURE_VIEWS_GENERATION_DONE_EVENT = "pictureViewsGenerationDone";

    public static final String CONVERTER_NAME = "MultiOutputPictureResize";

    public static final long BILLION = 1_000_000_000;

    protected static List<String> CONVERTER_PREFIXES = null;

    protected static List<String> CONVERTER_DESCRIPTIONS = null;

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

    protected void buildConverterInfo() {

        if (CONVERTER_PREFIXES == null) {
            CONVERTER_PREFIXES = new ArrayList<String>();
            CONVERTER_DESCRIPTIONS = new ArrayList<String>();

            ConverterDescriptor desc = ConversionServiceImpl.getConverterDescriptor(CONVERTER_NAME);
            ;
            Map<String, String> params = desc.getParameters();

            String prefixes[] = params.get(MultiOutputPictureConverter.OUTPUTS_PREFIXES_KEY).split(",");
            CONVERTER_PREFIXES = new ArrayList<String>(Arrays.asList(prefixes));
            // Clear underscores if any
            for (int i = 0; i < CONVERTER_PREFIXES.size(); i++) {
                String noUnderscore = StringUtils.removeEnd(CONVERTER_PREFIXES.get(i), "_");
                CONVERTER_PREFIXES.set(i, noUnderscore);
            }

            String descriptions[] = params.get(MultiOutputPictureConverter.VIEWS_DESCRIPTIONS_KEY).split(",");
            CONVERTER_DESCRIPTIONS = new ArrayList<String>(Arrays.asList(descriptions));
        }
    }

    protected List<String> getConverterPrefixes() {
        buildConverterInfo();
        return CONVERTER_PREFIXES;
    }

    protected List<String> getConverterDescriptions() {
        buildConverterInfo();
        return CONVERTER_DESCRIPTIONS;
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

        ImageInfo imageInfo;
        BlobHolder result = null;

        try {
            imageInfo = getImageInfo(blob);
            if (isBigImageFile(imageInfo, blob)) {
                BigPictureMultiConversionWorker worker = new BigPictureMultiConversionWorker(this.repositoryName,
                        this.docId, this.xpath);
                WorkManager wm = Framework.getService(WorkManager.class);
                wm.schedule(worker);
            } else {
                ConversionService conversionService = Framework.getService(ConversionService.class);
                result = conversionService.convert(CONVERTER_NAME, new SimpleBlobHolder(blob), new HashMap<>());
            }
        } catch (Exception e) {
            throw new NuxeoException("Could not transcode image file in " + workingDocument.getPath(), e);
        } finally {
            TransactionHelper.startTransaction();
        }

        if (result != null) {
            updateDocument(imageInfo, result, workingDocument);
        }

        TransactionHelper.commitOrRollbackTransaction();
        closeSession();

        setStatus("Done");
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

    protected boolean isBigImageFile(ImageInfo imageInfo, Blob blob) {
        return ((long) imageInfo.getWidth() * (long) imageInfo.getHeight()) > BILLION;
    }

    protected void updateDocument(ImageInfo imageInfo, BlobHolder result, DocumentModel workingDocument) {
        List<PictureView> viewList = buildViews(result.getBlobs());
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
        workingDocument.putContextData(DISABLE_PICTURE_VIEWS_GENERATION_LISTENER, Boolean.TRUE);
        workingDocument.putContextData(VersioningService.DISABLE_AUTO_CHECKOUT, Boolean.TRUE);
        session.saveDocument(workingDocument);

        firePictureViewsGenerationDoneEvent(workingDocument);
    }

    protected List<PictureView> buildViews(List<Blob> blobs) {
        List<PictureView> pictureViews = new ArrayList<>();

        ImagingService imagingService = Framework.getService(ImagingService.class);

        List<String> prefixes = getConverterPrefixes();
        for (Blob blob : blobs) {
            String filename = blob.getFilename();

            for (int i = 0; i < prefixes.size(); i++) {
                String prefix = prefixes.get(i);
                if (filename.startsWith(prefix)) {

                    ImageInfo imageInfo = imagingService.getImageInfo(blob);
                    PictureView view = new PictureViewImpl();

                    view.setTitle(prefix);
                    view.setBlob(blob);
                    view.setHeight(imageInfo.getHeight());
                    view.setWidth(imageInfo.getWidth());
                    // We know descriptions and prefixes arrays are in sync.
                    view.setDescription(getConverterDescriptions().get(i));
                    
                    view.setImageInfo(imageInfo);
                    view.setFilename(blob.getFilename());
                    pictureViews.add(view);
                    break;
                }
            }
        }

        // sort views
        Collections.sort(pictureViews, Comparator.comparingInt(PictureView::getHeight));
        return pictureViews;
    }

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

}