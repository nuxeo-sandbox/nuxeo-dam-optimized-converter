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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.picture.PictureViewsGenerationWork;
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

import static org.junit.Assert.assertTrue;
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
        "org.nuxeo.ecm.actions",
        "org.nuxeo.binary.metadata"
})
@Deploy({
        "nuxeo-dam-optimized-converter-core:disable-default-picture-generation-contrib.xml"
})
@Ignore
public class TestPerformance {

    @Inject
    CoreSession session;

    @Test
    public void testPerfomanceWorker() {
        File file = new File(getClass().getResource("/files/small.jpg").getPath());
        DocumentModel picture = session.createDocumentModel(session.getRootDocument().getPathAsString(),"picture","Picture");
        picture.setPropertyValue("file:content",new FileBlob(file));
        picture = session.createDocument(picture);

        //test default worker
        long beginning = System.currentTimeMillis();
        PictureViewsGenerationWork defaultWorker = new PictureViewsGenerationWork(picture.getRepositoryName(),picture.getId(),"file:content");
        defaultWorker.work();
        defaultWorker.closeSession();
        long end = System.currentTimeMillis();
        long durationDefaultWorker = end - beginning;

        System.out.println("Default worker duration(ms): "+durationDefaultWorker);

        beginning = System.currentTimeMillis();
        PictureMultiConversionWorker worker = new PictureMultiConversionWorker(picture.getRepositoryName(),picture.getId(),"file:content");
        worker.work();
        worker.closeSession();
        end = System.currentTimeMillis();
        long durationWorker = end - beginning;

        System.out.println("Optimized worker duration(ms): "+durationWorker);

        assertTrue("custom worker is faster",durationWorker<durationDefaultWorker);

        TransactionHelper.startTransaction();

        picture = session.getDocument(picture.getRef());
        List<Map<String, Serializable>> views = (List<Map<String, Serializable>>) picture.getPropertyValue(VIEWS_PROPERTY);
        Assert.assertNotNull(views);
        Assert.assertEquals(4,views.size());
    }

}