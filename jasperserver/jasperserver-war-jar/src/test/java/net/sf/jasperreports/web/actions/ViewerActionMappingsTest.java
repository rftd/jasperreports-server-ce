// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package net.sf.jasperreports.web.actions;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.jackson.util.JacksonUtil;
import net.sf.jasperreports.web.util.WebResourceHandler;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ViewerActionMappingsTest {
    @Test
    void readsZoomAndSearchUsingTheEngineMapper() {
        List<AbstractAction> actions = JacksonUtil.getInstance(DefaultJasperReportsContext.getInstance())
                .loadAsList("[{\"actionName\":\"saveZoom\",\"zoomValue\":\"1.25\"},"
                        + "{\"actionName\":\"search\",\"searchData\":{\"searchString\":\"Alfreds\",\"caseSensitive\":false}}]",
                        AbstractAction.class);
        assertEquals(2, actions.size());
        assertTrue(actions.get(0) instanceof SaveZoomAction);
        assertEquals("1.25", ((SaveZoomAction) actions.get(0)).getZoomValue());
        assertTrue(actions.get(1) instanceof SearchAction);
        assertEquals("Alfreds", ((SearchAction) actions.get(1)).getSearchData().getSearchString());
    }

    @Test
    void readsSaveAndUndoRedoActions() {
        List<AbstractAction> actions = JacksonUtil.getInstance(DefaultJasperReportsContext.getInstance())
                .loadAsList("[{\"actionName\":\"save\"},{\"actionName\":\"undo\"},"
                        + "{\"actionName\":\"undoAll\"},{\"actionName\":\"redo\"}]", AbstractAction.class);
        assertEquals(4, actions.size());
        assertTrue(actions.get(0) instanceof SaveAction);
        assertTrue(actions.get(1) instanceof UndoAction);
        assertTrue(actions.get(2) instanceof UndoAllAction);
        assertTrue(actions.get(3) instanceof RedoAction);
    }

    @Test
    void rejectsUnregisteredActionTypes() {
        assertThrows(RuntimeException.class, () -> JacksonUtil.getInstance(DefaultJasperReportsContext.getInstance())
                .loadAsList("[{\"actionName\":\"java.lang.Runtime\"}]", AbstractAction.class));
    }

    @Test
    void discoversImageFontAndDefaultResourceHandlersAndErrorMessages() {
        List<WebResourceHandler> handlers = DefaultJasperReportsContext.getInstance().getExtensions(WebResourceHandler.class);
        assertTrue(handlers.stream().anyMatch(h -> h instanceof net.sf.jasperreports.web.util.ImageWebResourceHandler));
        assertTrue(handlers.stream().anyMatch(h -> h instanceof net.sf.jasperreports.web.util.FontWebResourceHandler));
        assertTrue(handlers.stream().anyMatch(h -> h instanceof net.sf.jasperreports.web.util.DefaultWebResourceHandler));
        assertNotNull(getClass().getResource("/net/sf/jasperreports/web/actions/messages.properties"));
    }
}
