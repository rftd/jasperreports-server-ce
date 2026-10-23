// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package org.openjrs.reports;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JacksonReportLoader;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native loading and real compilation/fill contracts; no JR6 adapter on the test classpath. */
public class NativeReportSamplesTest {
    @Test
    public void loadsNativeEmployeeSamples() throws Exception {
        for (String name : new String[] {"Employees", "EmployeeAccounts"}) {
            byte[] xml = Files.readAllBytes(Paths.get("..", "samples", "jrxml7", "analysis", name + ".jrxml"));
            JasperReportsContext context = DefaultJasperReportsContext.getInstance();
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

    @Test
    public void loadsNativeCustomersReportAndSubreports() throws Exception {
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
                    .invoke(null, new ByteArrayInputStream(Files.readAllBytes(Paths.get("..", "samples", "jrxml7", "interactive", "CustomersReport.jrxml"))));
            Class<?> compiler = webapp.loadClass("net.sf.jasperreports.engine.JasperCompileManager");
            Object report = compiler.getMethod("compileReport", webapp.loadClass("net.sf.jasperreports.engine.design.JasperDesign"))
                    .invoke(null, design);
            assertEquals("CustomersReport", report.getClass().getMethod("getName").invoke(report));
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
        String xml = "<jasperReport name=\"xpathNative\" pageWidth=\"300\" pageHeight=\"200\" columnWidth=\"260\" leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\">"
                + "<query language=\"xPath\">/rows/row</query><field name=\"value\" class=\"java.lang.String\"><description>value</description></field>"
                + "<detail><band height=\"20\"><element kind=\"textField\" x=\"0\" y=\"0\" width=\"240\" height=\"20\"><expression>$F{value}</expression></element></band></detail></jasperReport>";
        JasperDesign design = JRXmlLoader.load(context, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        HashMap<String,Object> parameters = new HashMap<String,Object>();
        parameters.put("XML_DATA_DOCUMENT", net.sf.jasperreports.engine.util.JRXmlUtils.parse(
                new ByteArrayInputStream("<rows><row><value>XPath value</value></row></rows>".getBytes(StandardCharsets.UTF_8))));
        JasperPrint print = JasperFillManager.getInstance(context).fill(
                JasperCompileManager.getInstance(context).compile(design), parameters);
        assertEquals("XPath value", ((JRPrintText) print.getPages().get(0).getElements().get(0)).getFullText());
    }

    @Test
    public void compilesEveryCommunityCatalogDefinitionWithOnlyTheNativeLoader() throws Exception {
        for (String catalog : new String[] {"postgresql", "mysql"}) {
            int reports = 0;
            try (ZipFile zip = new ZipFile(Paths.get("..", "buildomatic", "install_resources", "export",
                    "js-catalog-" + catalog + "-ce.zip").toFile())) {
                java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".data")) continue;
                    byte[] data = read(zip.getInputStream(entry));
                    if (!new String(data, StandardCharsets.UTF_8).contains("<jasperReport")) continue;
                    JasperDesign design = JacksonReportLoader.instance().loadReport(
                            DefaultJasperReportsContext.getInstance(), data).get();
                    assertNotNull(entry.getName(), JasperCompileManager.compileReport(design));
                    reports++;
                }
            }
            assertEquals(catalog, 7, reports);
        }
    }

    @Test
    public void rejectsJr6SyntaxInsteadOfConvertingIt() throws Exception {
        String old = "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" name=\"old\"><queryString>SELECT 1</queryString></jasperReport>";
        try {
            JRXmlLoader.load(new ByteArrayInputStream(old.getBytes(StandardCharsets.UTF_8)));
            fail("JR6 input must require explicit migration");
        } catch (JRException expected) {
            assertNotNull(expected.getMessage());
        }
        for (net.sf.jasperreports.engine.xml.ReportLoader loader : DefaultJasperReportsContext.getInstance()
                .getExtensions(net.sf.jasperreports.engine.xml.ReportLoader.class)) {
            assertFalse(loader.getClass().getName().contains("LegacyReportLoader"));
        }
    }

    @Test
    public void loadsStandaloneExamplesAndProductionFixturesInNativeFormat() throws Exception {
        Path root = Paths.get("..");
        int reports = 0;
        for (String directory : new String[] {"samples", "production-tests/src/test/resources", "jasperserver-remote-tests/src/test/resources", "jasperserver-remote-tests/test-backup", "jasperserver-war-jar/src/test/resources"}) {
            try (java.util.stream.Stream<Path> files = Files.walk(root.resolve(directory))) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".jrxml"))::iterator) {
                    byte[] data = Files.readAllBytes(file);
                    String xml = new String(data, StandardCharsets.UTF_8);
                    assertFalse(file.toString(), xml.contains("<queryString") || xml.contains("<reportElement"));
                    // These two extension examples require separately installing their
                    // real query-executer plugins; no substitute executor is used here.
                    if (file.getFileName().toString().equals("hqlTest.jrxml")
                            || file.getFileName().toString().equals("webscraperQEtest.jrxml")) {
                        try {
                            JacksonReportLoader.instance().loadReport(DefaultJasperReportsContext.getInstance(), data);
                            fail("Missing optional query plugin should be reported");
                        } catch (JRRuntimeException expected) {
                            assertTrue(expected.getMessage().contains("No query executer factory"));
                        }
                    } else {
                        assertTrue(file.toString(), JacksonReportLoader.instance().loadReport(
                                DefaultJasperReportsContext.getInstance(), data).isPresent());
                    }
                    reports++;
                }
            }
        }
        assertEquals(53, reports);
    }

    @Test
    public void compilesNativeChartsCrosstabsAndBookParts() throws Exception {
        for (String name : new String[] {"BookReport", "ChartPart", "CrosstabPart", "TOCPart", "TablePart", "StandardChartsReport", "StandardChartsAegeanReport", "StandardChartsEyeCandyReport"}) {
            JasperDesign design = JRXmlLoader.load(Paths.get("..", "production-tests", "src", "test", "resources", "reports", "jasper", name + ".jrxml").toFile());
            assertNotNull(name, JasperCompileManager.compileReport(design));
        }
    }

    @Test
    public void fillsAndExportsNativeCommunityCharts() throws Exception {
        JasperDesign design = JRXmlLoader.load(Paths.get("..", "samples", "reports", "chartSamples.jrxml").toFile());
        java.util.List<java.util.Map<String, ?>> rows = new java.util.ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            java.util.Map<String, Object> row = new HashMap<>();
            row.put("id", index); row.put("account_name", "Account " + index);
            row.put("account_type", "Customer"); row.put("industry", "Industry " + index);
            rows.add(row);
        }
        JasperPrint print = JasperFillManager.fillReport(JasperCompileManager.compileReport(design),
                new HashMap<String, Object>(), new net.sf.jasperreports.engine.data.JRMapCollectionDataSource(rows));
        assertFalse(print.getPages().isEmpty());
        assertTrue(print.getPages().stream().flatMap(page -> page.getElements().stream())
                .anyMatch(element -> element instanceof JRPrintImage));
        assertTrue(JasperExportManager.exportReportToPdf(print).length > 1000);
    }

    @Test
    public void loadsImportFixtureReportsAndStyleTemplateNatively() throws Exception {
        int definitions = 0;
        for (String fixture : new String[] {"samples-export/samples-ce-export.zip", "encryption-import/encryption-resource.zip"}) {
            try (ZipFile zip = new ZipFile(Paths.get("..", "production-tests", "src", "test", "resources", fixture).toFile())) {
                java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".data")) continue;
                    byte[] data = read(zip.getInputStream(entry));
                    String xml = new String(data, StandardCharsets.UTF_8);
                    if (xml.contains("<jasperReport")) {
                        assertTrue(entry.getName(), JacksonReportLoader.instance().loadReport(
                                DefaultJasperReportsContext.getInstance(), data).isPresent());
                        definitions++;
                    } else if (xml.contains("<jasperTemplate")) {
                        JRTemplate template = JacksonReportLoader.instance().loadTemplate(
                                DefaultJasperReportsContext.getInstance(), data).get();
                        assertTrue(template.getStyles().length > 0);
                        assertEquals("Arial", template.getStyles()[0].getFontName());
                        definitions++;
                    }
                }
            }
        }
        assertEquals(14, definitions);
    }

    @Test
    public void exportsNativeMdxLayoutAsOrdinaryPdf() throws Exception {
        JasperDesign design = JRXmlLoader.load(Paths.get("..", "samples", "jrxml7", "analysis",
                "FoodmartSalesMondrianReport.jrxml").toFile());
        assertEquals("mdx", design.getQuery().getLanguage());
        assertEquals("false", design.getPropertiesMap().getProperty("net.sf.jasperreports.export.pdf.tagged"));
        java.util.Map<String, Object> row = new HashMap<>();
        for (JRField field : design.getFields()) {
            row.put(field.getName(), String.class.equals(field.getValueClass()) ? "CA" : Integer.valueOf(100));
        }
        JasperPrint print = JasperFillManager.fillReport(JasperCompileManager.compileReport(design),
                new HashMap<String, Object>(), new net.sf.jasperreports.engine.data.JRMapCollectionDataSource(
                        java.util.Collections.<java.util.Map<String, ?>>singletonList(row)));
        assertFalse(print.getPages().isEmpty());
        assertTrue(JasperExportManager.exportReportToPdf(print).length > 1000);
    }
}
