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

import static org.junit.Assert.assertEquals;
import static org.nuxeo.ecm.platform.picture.recompute.RecomputeViewsAction.RecomputeViewsComputation.PICTURE_VIEWS_GENERATION_DONE_EVENT;
import static org.nuxeo.labs.dam.converters.listeners.OptimizedPictureChangedListener.GENERATE_PICTURE_VIEW_EVENT;

import java.io.File;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.event.test.CapturingEventListener;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.picture.api.PictureView;
import org.nuxeo.ecm.platform.picture.api.adapters.MultiviewPicture;
import org.nuxeo.ecm.platform.picture.core.ImagingFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, CoreBulkFeature.class, ImagingFeature.class})
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({
        "nuxeo-dam-optimized-converter-core"
})
public class TestPictureConversionWorker {

    @Inject
    CoreSession session;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Test
    public void testJPEGFile() {
        CapturingEventListener listener = new CapturingEventListener(PICTURE_VIEWS_GENERATION_DONE_EVENT, GENERATE_PICTURE_VIEW_EVENT);

        File file = new File(getClass().getResource("/files/small.jpg").getPath());
        DocumentModel picture = session.createDocumentModel(session.getRootDocument().getPathAsString(),"picture","Picture");
        picture.setPropertyValue("file:content",new FileBlob(file,"image/jpeg"));
        picture = session.createDocument(picture);

        transactionalFeature.nextTransaction();

        assertEquals(1, listener.getCapturedEventCount(GENERATE_PICTURE_VIEW_EVENT));
        assertEquals(1, listener.getCapturedEventCount(PICTURE_VIEWS_GENERATION_DONE_EVENT));

        picture = session.getDocument(picture.getRef());
        MultiviewPicture multiviewPicture = picture.getAdapter(MultiviewPicture.class);
        Assert.assertEquals(5,multiviewPicture.getViews().length);

        PictureView thumbnail = multiviewPicture.getView("Thumbnail");
        Assert.assertNotNull(thumbnail);

        Blob thumbnailBlob = thumbnail.getBlob();
        Assert.assertEquals("image/jpeg",thumbnailBlob.getMimeType());
        Assert.assertTrue(thumbnailBlob.getFilename().endsWith("_thumbnail.jpeg"));
    }

    @Test
    public void testSVGfile() {
        File file = new File(getClass().getResource("/files/curvex.svg").getPath());
        DocumentModel picture = session.createDocumentModel(session.getRootDocument().getPathAsString(),"picture","Picture");
        picture.setPropertyValue("file:content",new FileBlob(file,"image/svg+xml"));
        picture = session.createDocument(picture);

        transactionalFeature.nextTransaction();

        picture = session.getDocument(picture.getRef());

        MultiviewPicture multiviewPicture = picture.getAdapter(MultiviewPicture.class);
        Assert.assertEquals(5,multiviewPicture.getViews().length);
    }

}