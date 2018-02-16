package org.nuxeo.labs.dam.converters.mock;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

public class MockHttpBlobprovider implements BlobProvider {

    @Override
    public void initialize(String blobProviderId, Map<String, String> properties) throws IOException {
        //nothing to do
    }

    @Override
    public void close() {
        //nothing to do
    }

    @Override
    public Blob readBlob(BlobInfo blobInfo) throws IOException {
        return new SimpleManagedBlob(blobInfo);
    }

    @Override
    public String writeBlob(Blob blob) throws IOException {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public boolean supportsUserUpdate() {
        return false;
    }

    @Override
    public InputStream getStream(ManagedBlob blob) throws IOException {
        /* This blob provider manages blob accessible with a simpleURL so all we have to do
         * is get the URL from the blob key, open an HTTP connection and return the inputStream
         */
        String urlStr = extractUrl(blob);
        URL url = new URL(urlStr);
        URLConnection connection = url.openConnection();
        return connection.getInputStream();
    }

    @Override
    public URI getURI(ManagedBlob blob, BlobManager.UsageHint hint, HttpServletRequest servletRequest) {
        try {
            return new URI(extractUrl(blob));
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     *
     * @param blob the URL of the blob is stored in the key variable
     * @return the blob URL
     */
    protected String extractUrl(ManagedBlob blob) {
        String key = blob.getKey();
        // strip prefix (the name of the provider)
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        return key;
    }

}
