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

import static org.junit.Assert.assertTrue;
import static org.nuxeo.labs.dam.converters.bulk.OptimizedRecomputeViewsAction.CONVERTER_NAME;

import java.io.File;
import java.util.HashMap;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;


@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({
        "nuxeo-dam-optimized-converter-core"
})
public class TestMultiOutputPictureConverter {

    @Inject
    ConversionService conversionService;

    @Test
    public void isLoaded() {
        assertTrue(conversionService.getRegistredConverters().contains(CONVERTER_NAME));
    }

    @Test
    public void testConverter() {
        File file = new File(getClass().getResource("/files/small.jpg").getPath());
        BlobHolder result = conversionService.convert(CONVERTER_NAME,new SimpleBlobHolder(new FileBlob(file,"image/jpeg")),new HashMap<>());
        Assert.assertEquals(5,result.getBlobs().size());

        //test conversions are cached correctly
        BlobHolder result2 = conversionService.convert(CONVERTER_NAME,new SimpleBlobHolder(new FileBlob(file,"image/jpeg")),new HashMap<>());
        Assert.assertEquals(5,result2.getBlobs().size());
    }

}
