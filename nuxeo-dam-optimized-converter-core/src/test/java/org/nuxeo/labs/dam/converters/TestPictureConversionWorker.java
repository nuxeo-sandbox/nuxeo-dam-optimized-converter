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
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.labs.dam.converters.workers.BigPictureMultiConversionWorker;
import org.nuxeo.labs.dam.converters.workers.PictureMultiConversionWorker;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

import javax.inject.Inject;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.nuxeo.ecm.platform.picture.api.adapters.AbstractPictureAdapter.VIEWS_PROPERTY;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({
        "nuxeo-dam-optimized-converter-core",
        "org.nuxeo.ecm.platform.picture.api",
        "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.ecm.platform.picture.convert",
        "org.nuxeo.ecm.platform.tag",
        "org.nuxeo.binary.metadata",
        "nuxeo-dam-optimized-converter-core:disable-default-picture-generation-contrib.xml",
        "nuxeo-dam-optimized-converter-core:disable-big-picture-queue-contrib.xml",
})
public class TestPictureConversionWorker {

    @Inject
    CoreSession session;

    @Inject
    WorkManager wm;

    @Test
    public void testRegularWorkerWithSmallFile() {
        File file = new File(getClass().getResource("/files/small.jpg").getPath());
        DocumentModel picture = session.createDocumentModel(session.getRootDocument().getPathAsString(),"picture","Picture");
        picture.setPropertyValue("file:content",new FileBlob(file,"image/jpeg"));
        picture = session.createDocument(picture);
        PictureMultiConversionWorker worker = new PictureMultiConversionWorker(picture.getRepositoryName(),picture.getId(),"file:content");
        worker.work();

        TransactionHelper.startTransaction();

        picture = session.getDocument(picture.getRef());
        List<Map<String, Serializable>> views = (List<Map<String, Serializable>>) picture.getPropertyValue(VIEWS_PROPERTY);
        Assert.assertNotNull(views);
        Assert.assertEquals(4,views.size());

        Map<String, Serializable> thumbnail = views.get(0);
        Assert.assertEquals("Thumbnail",thumbnail.get("title"));

        Map<String, Serializable> fullhd = views.get(3);
        Assert.assertEquals("FullHD",fullhd.get("title"));
    }

    @Test
    public void testRegularWorkerWithSVGfile() {
        File file = new File(getClass().getResource("/files/curvex.svg").getPath());
        DocumentModel picture = session.createDocumentModel(session.getRootDocument().getPathAsString(),"picture","Picture");
        picture.setPropertyValue("file:content",new FileBlob(file,"image/svg+xml"));
        picture = session.createDocument(picture);
        PictureMultiConversionWorker worker = new PictureMultiConversionWorker(picture.getRepositoryName(),picture.getId(),"file:content");
        worker.work();

        TransactionHelper.startTransaction();

        picture = session.getDocument(picture.getRef());
        List<Map<String, Serializable>> views = (List<Map<String, Serializable>>) picture.getPropertyValue(VIEWS_PROPERTY);
        Assert.assertNotNull(views);
        Assert.assertEquals(4,views.size());
    }


    @Test
    @Deploy({
            "nuxeo-dam-optimized-converter-core:mock-picture-converter-contrib.xml"
    })
    public void testRegularWorkerWithBigFile() {
        File file = new File(getClass().getResource("/files/big.jpg").getPath());
        DocumentModel picture = session.createDocumentModel(session.getRootDocument().getPathAsString(),"picture","Picture");
        picture.setPropertyValue("file:content",new FileBlob(file,"image/jpeg"));
        picture = session.createDocument(picture);
        PictureMultiConversionWorker worker = new PictureMultiConversionWorker(picture.getRepositoryName(),picture.getId(),"file:content");
        worker.work();
        Assert.assertEquals(wm.listWork(BigPictureMultiConversionWorker.CATEGORY,null).size(),1);
    }

    @Test
    @Deploy({
            "nuxeo-dam-optimized-converter-core:mock-picture-converter-contrib.xml"
    })
    public void testBigWorkerWithBigFile() {
        File file = new File(getClass().getResource("/files/big.jpg").getPath());
        DocumentModel picture = session.createDocumentModel(
                session.getRootDocument().getPathAsString(),
                "picture",
                "Picture");
        picture.setPropertyValue("file:content",new FileBlob(file,"image/jpeg"));
        picture = session.createDocument(picture);
        BigPictureMultiConversionWorker worker =
                new BigPictureMultiConversionWorker(
                        picture.getRepositoryName(),
                        picture.getId(),
                        "file:content");
        worker.work();

        TransactionHelper.startTransaction();

        picture = session.getDocument(picture.getRef());
        List<Map<String, Serializable>> views =
                (List<Map<String, Serializable>>) picture.getPropertyValue(VIEWS_PROPERTY);
        Assert.assertNotNull(views);
        Assert.assertEquals(1,views.size());
        Map<String, Serializable> thumbnail = views.get(0);
        Assert.assertEquals("Small",thumbnail.get("title"));
    }

}