// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package org.openjrs.reports.compat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyReportLoaderTest {
    @Test
    public void loadsNativeEmployeeSamplesWithoutTheLegacyAdapter() throws Exception {
        for (String name : new String[] {"Employees", "EmployeeAccounts"}) {
            byte[] xml = Files.readAllBytes(Paths.get("..", "samples", "jrxml7", "analysis", name + ".jrxml"));
            JasperReportsContext context = DefaultJasperReportsContext.getInstance();
            assertFalse("Native samples must not need the legacy loader",
                    new LegacyReportLoader().loadReport(context, xml).isPresent());
            JasperDesign design = net.sf.jasperreports.engine.xml.JacksonReportLoader.instance()
                    .loadReport(context, xml).get();
            assertEquals(name, design.getName());
            assertEquals("false", design.getPropertiesMap().getProperty("net.sf.jasperreports.export.pdf.tagged"));
            assertTrue(design.getQuery().getText().contains("Employees".equals(name) ? "FROM users" : "FROM accounts"));
            if ("EmployeeAccounts".equals(name)) {
                assertTrue(design.getQuery().getText().contains("assigned_user_id = $P{EmployeeID}"));
            }
            if ("Employees".equals(name)) {
                JRImage logo = (JRImage) design.getTitle().getElements()[0];
                assertEquals(net.sf.jasperreports.engine.type.HorizontalImageAlignEnum.RIGHT,
                        logo.getHorizontalImageAlign());
                assertEquals("\"repo:/images/JRLogo\"", logo.getExpression().getText());
            }
            assertEquals(5, design.getStyles().length);
            assertNotNull(JasperCompileManager.compileReport(design));
            try (ZipFile zip = new ZipFile(Paths.get("..", "buildomatic", "install_resources", "export",
                    "js-catalog-postgresql-ce.zip").toFile())) {
                assertArrayEquals(xml, read(zip.getInputStream(zip.getEntry(
                        "resources/analysis/reports/" + name + "_files/" + name + "JRXML.data"))));
            }
            if ("Employees".equals(name)) {
                JRFrame row = (JRFrame) design.getDetailSection().getBands()[0].getElements()[0];
                JRTextField link = (JRTextField) row.getElements()[4];
                assertEquals("ReportExecution", link.getLinkType());
                assertEquals("\"/analysis/reports/EmployeeAccounts\"",
                        link.getHyperlinkParameters()[0].getValueExpression().getText());
                assertEquals("$F{id}", link.getHyperlinkParameters()[1].getValueExpression().getText());
            }
        }
    }

    private static byte[] read(InputStream stream) throws Exception {
        try (InputStream input = stream; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
            return output.toByteArray();
        }
    }

    private static final String OLD = "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" "
            + "name=\"legacy\" pageWidth=\"300\" pageHeight=\"200\" columnWidth=\"260\" leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\">"
            + "<style name=\"default\" isDefault=\"true\" fontName=\"SansSerif\" fontSize=\"10\" isBold=\"true\"/>"
            + "<queryString>select preserved_query</queryString>"
            + "<field name=\"value\" class=\"java.lang.String\"><fieldDescription>preserved_description</fieldDescription></field>"
            + "<detail><band height=\"40\"><textField isBlankWhenNull=\"true\" hyperlinkType=\"Reference\">"
            + "<reportElement x=\"0\" y=\"0\" width=\"240\" height=\"20\" isRemoveLineWhenBlank=\"true\"/>"
            + "<textElement textAlignment=\"Center\"><font size=\"12\" isItalic=\"true\"/></textElement>"
            + "<textFieldExpression class=\"java.lang.String\"><![CDATA[$F{value}]]></textFieldExpression>"
            + "<hyperlinkReferenceExpression><![CDATA[\"https://example.com/report\"]]></hyperlinkReferenceExpression>"
            + "</textField></band></detail></jasperReport>";

    @Test
    public void preservesDesignAndExportsLegacyReport() throws Exception {
        JasperDesign design = load(OLD);
        assertEquals("select preserved_query", design.getQuery().getText());
        assertEquals("preserved_description", design.getFields()[0].getDescription());
        JRTextField text = (JRTextField) design.getDetailSection().getBands()[0].getElements()[0];
        assertEquals("$F{value}", text.getExpression().getText());
        assertEquals(HorizontalTextAlignEnum.CENTER, text.getHorizontalTextAlign());
        assertEquals(Float.valueOf(12), text.getOwnFontSize());
        assertTrue(text.isItalic()); assertTrue(text.isBold()); assertTrue(text.isBlankWhenNull());
        assertTrue(text.isRemoveLineWhenBlank());
        assertEquals("\"https://example.com/report\"", text.getHyperlinkReferenceExpression().getText());
        JRDataSource source = new net.sf.jasperreports.engine.data.JRMapCollectionDataSource(
                java.util.Collections.<java.util.Map<String,?>>singletonList(java.util.Collections.singletonMap("value", "Legacy content")));
        JasperPrint print = JasperFillManager.fillReport(JasperCompileManager.compileReport(design),
                new HashMap<String,Object>(), source);
        StringBuilder html = new StringBuilder();
        HtmlExporter exporter = new HtmlExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleHtmlExporterOutput(html)); exporter.exportReport();
        assertTrue(html.toString().contains("Legacy content"));
        assertTrue(html.toString().contains("https://example.com/report"));
        StringBuilder csv = new StringBuilder();
        JRCsvExporter csvExporter = new JRCsvExporter();
        csvExporter.setExporterInput(new SimpleExporterInput(print));
        csvExporter.setExporterOutput(new SimpleWriterExporterOutput(csv)); csvExporter.exportReport();
        assertTrue(csv.toString().contains("Legacy content"));
    }

    @Test
    public void loadsActualCustomersReportAndSubreportsWithoutChangingCatalog() throws Exception {
        int loaded = 0;
        try (ZipFile zip = new ZipFile(Paths.get("..", "buildomatic", "install_resources", "export", "js-catalog-postgresql-ce.zip").toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith("resources/reports/interactive/CustomersReport_files/")
                        && entry.getName().endsWith(".data")) {
                    try (InputStream stream = zip.getInputStream(entry)) {
                        JasperDesign design = JRXmlLoader.load(stream);
                        assertNotNull(design.getName());
                        if ("CustomersReport".equals(design.getName())) {
                            assertEquals("/Northwind/Customers", design.getQuery().getText().trim());
                            assertEquals(2, design.getFields().length);
                            JRSubreport subreport = (JRSubreport) design.getDetailSection().getBands()[0].getElements()[3];
                            assertEquals(6, subreport.getParameters().length);
                            assertEquals("\"repo:OrdersReport\"", subreport.getExpression().getText().trim());
                        } else {
                            assertEquals("OrdersReport", design.getName());
                            assertEquals(1, design.getDatasets().length);
                            assertEquals(8, design.getDatasets()[0].getFields().length);
                            net.sf.jasperreports.components.table.TableComponent table =
                                    (net.sf.jasperreports.components.table.TableComponent) ((JRComponentElement)
                                    design.getTitle().getElements()[0]).getComponent();
                            assertEquals(6, table.getColumns().size());
                        }
                        assertNotNull(JasperCompileManager.compileReport(design));
                        loaded++;
                    }
                }
            }
        }
        assertEquals("Both shipped report definitions must execute this check", 2, loaded);
    }

    @Test
    public void loadsShippedTableWithDrilldownParameters() throws Exception {
        try (ZipFile zip = new ZipFile(Paths.get("..", "buildomatic", "install_resources", "export", "js-catalog-postgresql-ce.zip").toFile());
             InputStream stream = zip.getInputStream(zip.getEntry("resources/reports/interactive/TableReport_files/TableReport.data"))) {
            JasperDesign design = JRXmlLoader.load(stream);
            assertEquals("TableReport", design.getName());
            assertEquals(8, design.getDatasets()[0].getFields().length);
            net.sf.jasperreports.components.table.TableComponent table =
                    (net.sf.jasperreports.components.table.TableComponent) ((JRComponentElement)
                    design.getSummary().getElements()[0]).getComponent();
            net.sf.jasperreports.components.table.StandardColumnGroup group =
                    (net.sf.jasperreports.components.table.StandardColumnGroup) table.getColumns().get(0);
            JRTextField drilldown = (JRTextField) group.getGroupHeaders().get(0).getCell().getElements()[0];
            assertEquals(3, drilldown.getHyperlinkParameters().length);
            assertEquals("_report", drilldown.getHyperlinkParameters()[0].getName());
            assertEquals("\"/reports/interactive/MapReport\"", drilldown.getHyperlinkParameters()[0].getValueExpression().getText());
            assertEquals("$V{latitude}", drilldown.getHyperlinkParameters()[1].getValueExpression().getText());
            assertEquals("$V{longitude}", drilldown.getHyperlinkParameters()[2].getValueExpression().getText());
            assertNotNull(JasperCompileManager.compileReport(design));
        }
    }

    @Test
    public void fillsShippedMapWithDefaultAndRequestedCoordinates() throws Exception {
        try (ZipFile zip = new ZipFile(Paths.get("..", "buildomatic", "install_resources", "export", "js-catalog-postgresql-ce.zip").toFile());
             InputStream stream = zip.getInputStream(zip.getEntry("resources/reports/interactive/MapReport_files/MapReport.data"))) {
            JasperDesign design = JRXmlLoader.load(stream);
            assertEquals("MapReport", design.getName());
            assertTrue(((JRComponentElement) design.getSummary().getElements()[0]).getComponent()
                    instanceof net.sf.jasperreports.customvisualization.CVComponent);
            JasperReport report = JasperCompileManager.compileReport(design);
            for (int mode : new int[] {0, 1, 2}) {
                boolean supplied = mode == 1;
                HashMap<String,Object> parameters = new HashMap<String,Object>();
                if (mode != 0) {
                    SimpleReportContext request = new SimpleReportContext();
                    request.setParameterValue("latitude", supplied ? "-20.4697" : " ");
                    request.setParameterValue("longitude", supplied ? "-54.6201" : "");
                    request.setParameterValue("zoom", supplied ? "9" : "");
                    parameters.put(JRParameter.REPORT_CONTEXT, request);
                }
                JasperPrint print = JasperFillManager.fillReport(report, parameters, new JREmptyDataSource(0));
                JRGenericPrintElement map = null;
                for (JRPrintElement element : print.getPages().get(0).getElements()) {
                    if (element instanceof JRGenericPrintElement) map = (JRGenericPrintElement) element;
                }
                assertNotNull(map);
                java.util.Map<?,?> config = (java.util.Map<?,?>) map.getParameterValue("configuration");
                assertNotNull(config);
                assertEquals("openjrs-osm-map", config.get("module"));
                assertEquals(supplied ? -20.4697 : 44.435, Double.parseDouble(config.get("latitude").toString()), 0.0001);
                assertEquals(supplied ? -54.6201 : 26.096, Double.parseDouble(config.get("longitude").toString()), 0.0001);
                assertEquals(supplied ? 9 : 12, Integer.parseInt(config.get("zoom").toString()));
            }
        }
    }

    @Test
    public void preservesLegacyDynamicVisualizationItemProperty() throws Exception {
        String xml = "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" name=\"dynamicCV\">"
                + "<title><band height=\"100\"><componentElement><reportElement x=\"0\" y=\"0\" width=\"100\" height=\"100\"/>"
                + "<c:customvisualization xmlns:c=\"http://www.jaspersoft.com/cvcomponent\">"
                + "<c:itemProperty name=\"module\" value=\"test\"/>"
                + "<c:itemProperty name=\"latitude\"><c:valueExpression><![CDATA[42.21f]]></c:valueExpression></c:itemProperty>"
                + "</c:customvisualization></componentElement></band></title></jasperReport>";
        net.sf.jasperreports.customvisualization.CVComponent cv =
                (net.sf.jasperreports.customvisualization.CVComponent) ((JRComponentElement) load(xml).getTitle().getElements()[0]).getComponent();
        assertEquals("42.21f", cv.getItemProperties().get(1).getValueExpression().getText());
    }

    @Test(expected = JRException.class)
    public void rejectsUnsupportedMapContentInsteadOfDroppingIt() throws Exception {
        String xml = "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" name=\"advancedMap\">"
                + "<title><band height=\"100\"><componentElement><reportElement x=\"0\" y=\"0\" width=\"100\" height=\"100\"/>"
                + "<c:map xmlns:c=\"http://jasperreports.sourceforge.net/jasperreports/components\">"
                + "<c:markerData/></c:map></componentElement></band></title></jasperReport>";
        load(xml);
    }

    @Test
    public void preservesDeprecatedStretchAliases() throws Exception {
        for (String[] mapping : new String[][] {{"RelativeToBandHeight", "ContainerHeight"},
                {"RelativeToTallestObject", "ElementGroupHeight"}}) {
            JasperDesign design = load(OLD.replace("isRemoveLineWhenBlank=\"true\"",
                    "stretchType=\"" + mapping[0] + "\" isRemoveLineWhenBlank=\"true\""));
            assertEquals(mapping[1], design.getDetailSection().getBands()[0].getElements()[0].getStretchType().getName());
        }
    }

    @Test
    public void compilesWithWebappLibrariesOutsideTheJvmClasspath() throws Exception {
        String classpath = System.getProperty("java.class.path");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        String[] paths = classpath.split(java.io.File.pathSeparator);
        java.net.URL[] urls = new java.net.URL[paths.length];
        for (int i = 0; i < paths.length; i++) urls[i] = new java.io.File(paths[i]).toURI().toURL();
        try (java.net.URLClassLoader webapp = new java.net.URLClassLoader(urls, null)) {
            Thread.currentThread().setContextClassLoader(webapp);
            System.setProperty("java.class.path", "");
            Class<?> loader = webapp.loadClass("net.sf.jasperreports.engine.xml.JRXmlLoader");
            Object design = loader.getMethod("load", InputStream.class)
                    .invoke(null, new ByteArrayInputStream(OLD.getBytes(StandardCharsets.UTF_8)));
            Class<?> compiler = webapp.loadClass("net.sf.jasperreports.engine.JasperCompileManager");
            Object report = compiler.getMethod("compileReport", webapp.loadClass("net.sf.jasperreports.engine.design.JasperDesign"))
                    .invoke(null, design);
            assertEquals("legacy", report.getClass().getMethod("getName").invoke(report));
        } finally {
            System.setProperty("java.class.path", classpath);
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    public void fillsXPathReportUsingTheActualServerFactorySetting() throws Exception {
        java.util.Properties server = new java.util.Properties();
        try (InputStream input = java.nio.file.Files.newInputStream(Paths.get("..", "jasperserver-war", "src", "main", "webappAdditions", "WEB-INF", "classes", "jasperreports.properties"))) {
            server.load(input);
        }
        SimpleJasperReportsContext context = new SimpleJasperReportsContext();
        String key = "net.sf.jasperreports.xpath.executer.factory";
        context.setProperty(key, server.getProperty(key));
        String xml = OLD.replace("<queryString>select preserved_query</queryString>",
                "<queryString language=\"xPath\">/rows/row</queryString>")
                .replace("preserved_description", "value");
        JasperDesign design = JRXmlLoader.load(context, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        HashMap<String,Object> parameters = new HashMap<String,Object>();
        parameters.put("XML_DATA_DOCUMENT", net.sf.jasperreports.engine.util.JRXmlUtils.parse(
                new ByteArrayInputStream("<rows><row><value>XPath value</value></row></rows>".getBytes(StandardCharsets.UTF_8))));
        JasperPrint print = JasperFillManager.getInstance(context).fill(
                JasperCompileManager.getInstance(context).compile(design), parameters);
        assertEquals("XPath value", ((JRPrintText) print.getPages().get(0).getElements().get(0)).getFullText());
    }

    @Test(expected = JRException.class)
    public void rejectsUnsupportedContentInsteadOfDroppingIt() throws Exception {
        load(OLD.replace("<detail>", "<inventedElement/><detail>"));
    }

    @Test
    public void refusesExternalEntitiesAndDtds() throws Exception {
        byte[] input = ("<!DOCTYPE jasperReport [<!ENTITY xxe SYSTEM 'file:///does-not-exist-openjrs'>]>" + OLD)
                .getBytes(StandardCharsets.UTF_8);
        assertFalse(new LegacyReportLoader().loadReport(DefaultJasperReportsContext.getInstance(), input).isPresent());
    }

    @Test
    public void delegatesNativeVersionSevenXml() throws Exception {
        String nativeXml = "<jasperReport name=\"native\"><detail><band height=\"20\"><element kind=\"staticText\" x=\"0\" y=\"0\" width=\"100\" height=\"20\"><text>Native</text></element></band></detail></jasperReport>";
        assertFalse(new LegacyReportLoader().loadReport(DefaultJasperReportsContext.getInstance(), nativeXml.getBytes(StandardCharsets.UTF_8)).isPresent());
        assertEquals("native", load(nativeXml).getName());
    }

    private JasperDesign load(String xml) throws JRException {
        return JRXmlLoader.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
