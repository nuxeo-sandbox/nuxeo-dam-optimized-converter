/*
 * (C) Copyright 2015-2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Michael Vachette
 */
package org.nuxeo.labs.dam.converters;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.platform.video.VideoHelper;
import org.nuxeo.ecm.platform.video.VideoInfo;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({
        "nuxeo-dam-optimized-converter-core",
        "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.ecm.platform.tag",
        "org.nuxeo.ecm.platform.video",
        "nuxeo-dam-optimized-converter-core:mock-blobprovider-conrib.xml"
})
public class TestStreamVideoConverter {

    @Inject
    ConversionService conversionService;

    @Inject
    BlobManager blobManager;

    @Test
    public void isLoaded() {
        assertTrue(conversionService.getRegistredConverters().contains("convertToMP4"));
        assertTrue(conversionService.getRegistredConverters().contains("videoStoryboard"));
        assertTrue(conversionService.getRegistredConverters().contains("videoScreenshot"));
    }

    @Test
    public void testConverterWithLocalFile() {
        Blob blob = getLocalBlob();
        VideoInfo videoInfo = VideoHelper.getVideoInfo(blob);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put("height", 100l);
        parameters.put("videoInfo", videoInfo);
        BlobHolder result = conversionService.convert("convertToMP4",new SimpleBlobHolder(blob),parameters);
        Assert.assertEquals(1,result.getBlobs().size());
    }

    @Test
    public void testStoryboardWithLocalFile() {
        Blob blob = getLocalBlob();
        Map<String, Serializable> parameters = new HashMap<>();
        VideoInfo videoInfo = VideoHelper.getVideoInfo(blob);
        parameters.put("duration",videoInfo.getDuration());
        BlobHolder result = conversionService.convert("videoStoryboard",new SimpleBlobHolder(blob),parameters);
        Assert.assertEquals(9,result.getBlobs().size());
    }

    @Test
    public void testScreenshotWithLocalFile() {
        Blob blob = getLocalBlob();
        Map<String, Serializable> parameters = new HashMap<>();
        BlobHolder result = conversionService.convert("videoScreenshot",new SimpleBlobHolder(blob),parameters);
        Assert.assertEquals(1,result.getBlobs().size());
    }

    @Test
    public void testConverterWithRemoteFile() throws IOException {
        Blob blob = getRemoteBlob();
        VideoInfo videoInfo = VideoHelper.getVideoInfo(blob);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put("height", 100l);
        parameters.put("videoInfo", videoInfo);
        BlobHolder result = conversionService.convert("convertToMP4",new SimpleBlobHolder(blob),parameters);
        Assert.assertEquals(1,result.getBlobs().size());
    }

    @Test
    public void testStoryboardWithRemoteFile() throws IOException {
        Blob blob = getRemoteBlob();
        VideoInfo videoInfo = VideoHelper.getVideoInfo(blob);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put("duration",videoInfo.getDuration());
        BlobHolder result = conversionService.convert("videoStoryboard",new SimpleBlobHolder(blob),parameters);
        Assert.assertEquals(9,result.getBlobs().size());
    }

    @Test
    public void testScreenshotWithRemoteFile() throws IOException {
        Blob blob = getRemoteBlob();
        Map<String, Serializable> parameters = new HashMap<>();
        BlobHolder result = conversionService.convert("videoScreenshot",new SimpleBlobHolder(blob),parameters);
        Assert.assertEquals(1,result.getBlobs().size());
    }


    protected Blob getRemoteBlob() throws IOException {
        BlobProvider myProvider = blobManager.getBlobProvider("mockprovider");
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = "mockprovider:https://github.com/nuxeo-sandbox/nuxeo-dam-optimized-converter/raw/master/nuxeo-dam-optimized-converter-core/src/test/resources/files/nuxeo.3gp";
        blobInfo.mimeType = "video/mp4";
        blobInfo.filename = "nuxeo.3gp";
        blobInfo.length = 3563674l;
        return myProvider.readBlob(blobInfo);
    }

    protected Blob getLocalBlob() {
        File file = new File(getClass().getResource("/files/nuxeo.3gp").getPath());
        return new FileBlob(file,"video/3gp");
    }
}