/*
 * (C) Copyright 2022 Nuxeo (http://nuxeo.com/) and others.
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

package org.nuxeo.labs.dam.converters.listeners;

import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.CTX_FORCE_VIEWS_GENERATION;
import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_FACET;
import static org.nuxeo.ecm.platform.picture.listener.PictureViewsGenerationListener.DISABLE_PICTURE_VIEWS_GENERATION_LISTENER;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.picture.api.adapters.AbstractPictureAdapter;
import org.nuxeo.ecm.platform.picture.listener.PictureChangedListener;
import org.nuxeo.runtime.api.Framework;

public class OptimizedPictureChangedListener extends PictureChangedListener {

    public static final String GENERATE_PICTURE_VIEW_EVENT = "generatePictureView";

    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }
        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        if (doc.hasFacet(PICTURE_FACET) && !doc.isProxy()) {
            if (triggersPictureViewsGeneration(event, doc)) {
                preFillPictureViews(docCtx.getCoreSession(), doc);
                //fire rendition generation event
                Boolean block = (Boolean) event.getContext().getProperty(DISABLE_PICTURE_VIEWS_GENERATION_LISTENER);
                if (block == null || Boolean.FALSE.equals(block)) {
                    CoreSession session = doc.getCoreSession();
                    fireEvent(session,doc);
                }
            }
        }
    }

    @Override
    protected boolean triggersPictureViewsGeneration(Event event, DocumentModel doc) {
        Property fileProp = doc.getProperty("file:content");
        Property viewsProp = doc.getProperty(AbstractPictureAdapter.VIEWS_PROPERTY);

        boolean forceGeneration = Boolean.TRUE.equals(doc.getContextData(CTX_FORCE_VIEWS_GENERATION));
        boolean emptyPictureViews = viewsProp.size() == 0;
        boolean emptyOrNotDirtyPictureViews = !viewsProp.isDirty() || emptyPictureViews;
        boolean fileChanged = DOCUMENT_CREATED.equals(event.getName()) || fileProp.isDirty();

        return forceGeneration || (emptyOrNotDirtyPictureViews && fileChanged);
    }

    protected void fireEvent(CoreSession session, DocumentModel document) {
        DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), document);
        Event event = ctx.newEvent(GENERATE_PICTURE_VIEW_EVENT);
        Framework.getService(EventService.class).fireEvent(event);
    }

}
