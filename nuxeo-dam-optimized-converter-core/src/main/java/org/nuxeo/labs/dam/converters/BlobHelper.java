package org.nuxeo.labs.dam.converters;

import java.io.IOException;
import java.net.URI;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

public class BlobHelper {

    public static String getDirectUrl(Blob blob) {
        String uriStr = null;
        if (blob instanceof ManagedBlob) {
            ManagedBlob managedBlob = (ManagedBlob) blob;
            BlobProvider blobProvider = Framework.getService(BlobManager.class).getBlobProvider(blob);
            try {
                URI uri = blobProvider.getURI(managedBlob, BlobManager.UsageHint.DOWNLOAD,null);
                if (uri != null) {
                    uriStr = uri.toString();
                }
            } catch (IOException e) {
                // just continue
            }
        }
        return uriStr;
    }

}
