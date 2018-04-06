package org.nuxeo.labs.dam.converters.mock;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.extension.Converter;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

public class MockConverter implements Converter {
    @Override
    public void init(ConverterDescriptor descriptor) {
        return;
    }

    @Override
    public BlobHolder convert(BlobHolder blobHolder, Map<String, Serializable> parameters) throws ConversionException {
        Blob blob = new FileBlob(new File(getClass().getResource("/files/small.jpg").getPath()));
        blob.setFilename("SmallOutputFilePath.jpg");
        blob.setMimeType("image/jpeg");
        return new SimpleBlobHolder(blob);
    }
}
