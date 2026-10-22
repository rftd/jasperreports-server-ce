// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package org.openjrs.reports.compat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
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
