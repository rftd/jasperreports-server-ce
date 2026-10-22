// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package net.sf.jasperreports.web.actions;

import java.util.*;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.jackson.util.JacksonUtil;
import net.sf.jasperreports.repo.JasperDesignCache;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InteractiveTableActionsTest {
    private final SimpleJasperReportsContext context = new SimpleJasperReportsContext();
    private final SimpleReportContext reportContext = new SimpleReportContext();
    private JasperDesignCache cache;
    private String tableId;

    private void prepare() throws Exception {
        JasperDesign design = JRXmlLoader.load(getClass().getResourceAsStream("/interactive-table.jrxml"));
        tableId = design.getTitle().getElements()[0].getUUID().toString();
        reportContext.setParameterValue(JRParameter.REPORT_LOCALE, Locale.US);
        cache = JasperDesignCache.getInstance(context, reportContext);
        cache.set("test-table", design);
    }

    private void action(String json) throws Exception {
        AbstractAction action = JacksonUtil.getInstance(context).loadAsList("[" + json + "]", AbstractAction.class).get(0);
        action.init(context, reportContext);
        action.run();
    }

    private List<String> rows() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(JRParameter.REPORT_CONTEXT, reportContext);
        parameters.put(JRParameter.REPORT_LOCALE, Locale.US);
        JasperPrint print = JasperFillManager.getInstance(context).fill(cache.getJasperReport("test-table"),
                parameters, new JREmptyDataSource(0));
        List<String> rows = new ArrayList<>();
        for (JRPrintPage page : print.getPages()) collect(page.getElements(), rows);
        return rows;
    }

    private void collect(List<JRPrintElement> elements, List<String> rows) {
        for (JRPrintElement element : elements) {
            if (element instanceof JRPrintFrame) collect(((JRPrintFrame) element).getElements(), rows);
            if (element instanceof JRPrintText) {
                String text = ((JRPrintText) element).getFullText();
                if (Arrays.asList("zeta", "alpha", "beta").contains(text)) rows.add(text);
            }
        }
    }

    @Test
    void sortsAndUndoesRedoesThroughTheViewerActionProtocol() throws Exception {
        prepare();
        assertEquals(Arrays.asList("zeta", "alpha", "beta"), rows());
        action("{\"actionName\":\"sort\",\"sortData\":{\"tableUuid\":\"" + tableId
                + "\",\"sortColumnName\":\"value\",\"sortColumnType\":\"Field\",\"sortOrder\":\"Asc\"}}");
        assertEquals(Arrays.asList("alpha", "beta", "zeta"), rows());
        action("{\"actionName\":\"undo\"}");
        assertEquals(Arrays.asList("zeta", "alpha", "beta"), rows());
        action("{\"actionName\":\"redo\"}");
        assertEquals(Arrays.asList("alpha", "beta", "zeta"), rows());
    }

    @Test
    void filtersAndUndoesThroughTheViewerActionProtocol() throws Exception {
        prepare();
        action("{\"actionName\":\"filter\",\"filterData\":{\"tableUuid\":\"" + tableId
                + "\",\"fieldName\":\"value\",\"fieldValueStart\":\"alpha\",\"filterType\":\"Text\","
                + "\"filterTypeOperator\":\"EQUALS\",\"isField\":true}}");
        assertEquals(Collections.singletonList("alpha"), rows());
        action("{\"actionName\":\"undo\"}");
        assertEquals(Arrays.asList("zeta", "alpha", "beta"), rows());
    }

    @Test
    void resizesAndUndoesThroughTheViewerActionProtocol() throws Exception {
        prepare();
        action("{\"actionName\":\"resize\",\"resizeColumnData\":{\"tableUuid\":\"" + tableId
                + "\",\"columnIndex\":0,\"width\":180,\"direction\":\"right\"}}");
        net.sf.jasperreports.components.table.StandardTable table =
                (net.sf.jasperreports.components.table.StandardTable) ((JRDesignComponentElement)
                cache.getJasperDesign("test-table", false).getTitle().getElements()[0]).getComponent();
        assertEquals(180, table.getColumns().get(0).getWidth());
        assertEquals(Arrays.asList("zeta", "alpha", "beta"), rows());
        action("{\"actionName\":\"undo\"}");
        assertEquals(240, table.getColumns().get(0).getWidth());
        assertThrows(ActionException.class, () -> action("{\"actionName\":\"resize\",\"resizeColumnData\":{\"tableUuid\":\""
                + tableId + "\",\"columnIndex\":0,\"width\":-1}}"));
    }

    @Test
    void suppliesTheConcreteCrosstabOrderModelRequiredByJr7() {
        net.sf.jasperreports.interactivity.crosstabs.OrderByColumnInfoImpl info =
                new net.sf.jasperreports.interactivity.crosstabs.OrderByColumnInfoImpl();
        info.setOrder(net.sf.jasperreports.engine.type.SortOrderEnum.DESCENDING);
        info.setMeasureIndex(2);
        net.sf.jasperreports.crosstabs.fill.calculation.ColumnValueInfo value =
                new net.sf.jasperreports.crosstabs.fill.calculation.ColumnValueInfo();
        value.setValueType(String.class.getName()); value.setValue("region");
        info.setColumnValues(Collections.singletonList(value));
        String json = JacksonUtil.getInstance(context).getJsonString(info);
        net.sf.jasperreports.crosstabs.fill.calculation.OrderByColumnInfo restored =
                net.sf.jasperreports.util.JsonLoader.getInstance(context).loadObject(json,
                        "net.sf.jasperreports.interactivity.crosstabs.OrderByColumnInfoImpl");
        assertEquals(info.getOrder(), restored.getOrder());
        assertEquals(2, restored.getMeasureIndex());
        assertEquals("region", restored.getColumnValues().get(0).getValue());
    }

    @Test
    void discoversInteractiveMetadataAndMapsRemainingActions() {
        assertNotNull(net.sf.jasperreports.engine.export.GenericElementHandlerEnviroment.getInstance(context)
                .getElementHandler(net.sf.jasperreports.components.headertoolbar.HeaderToolbarElement.ELEMENT_TYPE,
                        net.sf.jasperreports.json.export.JsonExporter.JSON_EXPORTER_KEY));
        for (String name : Arrays.asList("resize", "move", "editTextElement", "hideUnhideColumns",
                "conditionalFormatting", "sortica", "filterica", "sortXTabByColumn", "sortXTabRowGroup")) {
            assertEquals(1, JacksonUtil.getInstance(context).loadAsList(
                    "[{\"actionName\":\"" + name + "\"}]", AbstractAction.class).size());
        }
    }
}
