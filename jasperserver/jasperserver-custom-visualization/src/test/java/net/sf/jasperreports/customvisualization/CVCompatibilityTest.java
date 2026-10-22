package net.sf.jasperreports.customvisualization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import net.sf.jasperreports.customvisualization.design.CVDesignComponent;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.component.*;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.export.*;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JRXmlWriter;
import net.sf.jasperreports.export.*;
import net.sf.jasperreports.json.export.JsonExporter;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.poi.export.JRXlsExporter;
import net.sf.jasperreports.components.items.StandardItemProperty;
import net.sf.jasperreports.components.items.StandardItem;
import net.sf.jasperreports.components.items.StandardItemData;
import net.sf.jasperreports.engine.type.EvaluationTimeEnum;
import org.junit.Test;

import static org.junit.Assert.*;

/** Exercises extension discovery and the real JR7 compile/fill/export pipeline. */
public class CVCompatibilityTest {
    private static final String MARKER = "OpenJRS compatibility";

    @Test
    public void discoversComponentAndAllExportHandlers() {
        DefaultJasperReportsContext context = DefaultJasperReportsContext.getInstance();
        List<ComponentsBundle> bundles = context.getExtensions(ComponentsBundle.class);
        ComponentManager manager = bundles.stream()
                .map(bundle -> bundle.getComponentManager(CVDesignComponent.class))
                .filter(java.util.Optional::isPresent).map(java.util.Optional::get)
                .findFirst().orElseThrow(() -> new AssertionError("CV component not registered"));
        assertNotNull(manager.getComponentCompiler(context));
        assertNotNull(manager.getComponentFillFactory(context));
        GenericElementHandlerBundle bundle = context.getExtensions(GenericElementHandlerBundle.class)
                .stream().filter(b -> CVConstants.NAMESPACE.equals(b.getNamespace()))
                .findFirst().orElseThrow(() -> new AssertionError("CV exporters not registered"));
        for (String key : new String[] {JRPdfExporter.PDF_EXPORTER_KEY,
                JRXlsExporter.XLS_EXPORTER_KEY, JRXlsxExporter.XLSX_EXPORTER_KEY,
                HtmlExporter.HTML_EXPORTER_KEY, JsonExporter.JSON_EXPORTER_KEY,
                JRGraphics2DExporter.GRAPHICS2D_EXPORTER_KEY,
                net.sf.jasperreports.engine.export.ooxml.JRDocxExporter.DOCX_EXPORTER_KEY,
                net.sf.jasperreports.engine.export.ooxml.JRPptxExporter.PPTX_EXPORTER_KEY,
                net.sf.jasperreports.engine.export.oasis.JROdtExporter.ODT_EXPORTER_KEY,
                net.sf.jasperreports.engine.export.oasis.JROdsExporter.ODS_EXPORTER_KEY,
                JRRtfExporter.RTF_EXPORTER_KEY}) {
            assertNotNull("Missing CV handler for " + key,
                    bundle.getHandler(CVConstants.COMPONENT_NAME, key));
        }
    }

    @Test
    public void roundTripsAndFillsCustomComponentWithJackson() throws Exception {
        JasperDesign design = design();
        CVDesignComponent component = new CVDesignComponent();
        component.setEvaluationTime(EvaluationTimeEnum.REPORT);
        component.setProcessingClass(DefaultProcessor.class.getName());
        component.addItemProperty(new StandardItemProperty("script", "sample.js", null));
        component.addItemProperty(new StandardItemProperty("title", null,
                new JRDesignExpression("\"Custom component\"")));
        StandardItemData data = new StandardItemData();
        StandardItem item = new StandardItem();
        item.addItemProperty(new StandardItemProperty("value", null, new JRDesignExpression("42")));
        data.addItem(item);
        component.addItemData(data);
        JRDesignComponentElement element = new JRDesignComponentElement();
        element.setX(0); element.setY(30); element.setWidth(180); element.setHeight(30);
        element.setComponent(component);
        ((JRDesignBand) design.getDetailSection().getBands()[0]).addElement(element);

        String xml = JRXmlWriter.writeReport(design, "UTF-8");
        JasperDesign restored = JRXmlLoader.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        CVComponent copy = (CVComponent) ((JRComponentElement)
                restored.getDetailSection().getBands()[0].getElements()[1]).getComponent();
        assertEquals(DefaultProcessor.class.getName(), copy.getProcessingClass());
        assertEquals(EvaluationTimeEnum.REPORT, copy.getEvaluationTime());
        assertEquals(2, copy.getItemProperties().size());
        assertEquals("sample.js", copy.getItemProperties().get(0).getValue());
        assertEquals("\"Custom component\"", copy.getItemProperties().get(1).getValueExpression().getText());
        assertEquals(1, copy.getItemData().size());
        assertEquals("42", copy.getItemData().get(0).getItems().get(0).getProperties().get(0).getValueExpression().getText());
        JasperPrint print = JasperFillManager.fillReport(JasperCompileManager.compileReport(restored),
                new java.util.HashMap<String, Object>(), new JREmptyDataSource());
        assertEquals(1, print.getPages().size());
        assertTrue(print.getPages().get(0).getElements().stream()
                .anyMatch(e -> e instanceof JRGenericPrintElement));
        StringBuilder html = new StringBuilder();
        HtmlExporter exporter = new HtmlExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleHtmlExporterOutput(html));
        exporter.exportReport();
        assertTrue("Custom visualization HTML template missing", html.toString().contains("cv-component"));
        assertTrue("Custom visualization data missing", html.toString().contains("Custom component"));
    }

    @Test
    public void roundTripsCompiledComponentAndPrintWithDeserializationFilterEnabled() throws Exception {
        SimpleJasperReportsContext context = new SimpleJasperReportsContext();
        net.sf.jasperreports.engine.util.DeserializationClassFilter filter =
                new net.sf.jasperreports.engine.util.DeserializationClassFilter(context);
        assertTrue("JR7 deserialization filter must stay enabled", filter.isFilteringEnabled());
        assertFalse("Unrelated classes must remain rejected", filter.isClassVisible(UntrustedPayload.class.getName()));
        JasperDesign design = design();
        CVDesignComponent component = new CVDesignComponent();
        component.addItemProperty(new StandardItemProperty("script", "sample.js", null));
        JRDesignComponentElement element = new JRDesignComponentElement();
        element.setX(0); element.setY(30); element.setWidth(180); element.setHeight(30);
        element.setComponent(component);
        ((JRDesignBand) design.getDetailSection().getBands()[0]).addElement(element);
        ByteArrayOutputStream compiled = new ByteArrayOutputStream();
        net.sf.jasperreports.engine.util.JRSaver.saveObject(JasperCompileManager.getInstance(context).compile(design), compiled);
        JasperReport restored = (JasperReport) net.sf.jasperreports.engine.util.JRLoader.loadObject(
                context, new ByteArrayInputStream(compiled.toByteArray()));
        JasperPrint print = JasperFillManager.getInstance(context).fill(restored,
                new java.util.HashMap<String, Object>(), new JREmptyDataSource());
        ByteArrayOutputStream filled = new ByteArrayOutputStream();
        net.sf.jasperreports.engine.util.JRSaver.saveObject(print, filled);
        JasperPrint reloadedPrint = (JasperPrint) net.sf.jasperreports.engine.util.JRLoader.loadObject(
                context, new ByteArrayInputStream(filled.toByteArray()));
        assertTrue(reloadedPrint.getPages().get(0).getElements().stream()
                .anyMatch(e -> e instanceof JRGenericPrintElement));
        ByteArrayOutputStream untrusted = new ByteArrayOutputStream();
        net.sf.jasperreports.engine.util.JRSaver.saveObject(new UntrustedPayload(), untrusted);
        try {
            net.sf.jasperreports.engine.util.JRLoader.loadObject(context,
                    new ByteArrayInputStream(untrusted.toByteArray()));
            fail("Unregistered serialized class was accepted");
        } catch (JRRuntimeException expected) {
            assertTrue(expected.getMessage().contains(UntrustedPayload.class.getName()));
        }
    }

    private static class UntrustedPayload implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }

    @Test
    public void exportsPdfWithCustomExtensionInstalled() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print()));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
        exporter.exportReport();
        assertTrue(new String(output.toByteArray(), 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
        assertTrue(output.size() > 500);
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(output.toByteArray());
        try {
            assertEquals(1, reader.getNumberOfPages());
            assertTrue(new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1).contains(MARKER));
        } finally {
            reader.close();
        }
    }

    @Test
    public void exportsXlsxWithCustomExtensionInstalled() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print()));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
        exporter.exportReport();
        boolean workbook = false, marker = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int size;
                while ((size = zip.read(buffer)) != -1) bytes.write(buffer, 0, size);
                workbook |= "xl/workbook.xml".equals(entry.getName());
                marker |= new String(bytes.toByteArray(), StandardCharsets.UTF_8).contains(MARKER);
            }
        }
        assertTrue("XLSX workbook missing", workbook);
        assertTrue("Report text missing from XLSX", marker);
    }

    @Test
    public void exportsHtmlAndCsvWithCustomExtensionInstalled() throws Exception {
        JasperPrint print = print();
        StringBuilder html = new StringBuilder();
        HtmlExporter htmlExporter = new HtmlExporter();
        htmlExporter.setExporterInput(new SimpleExporterInput(print));
        htmlExporter.setExporterOutput(new SimpleHtmlExporterOutput(html));
        htmlExporter.exportReport();
        assertTrue(html.toString().contains(MARKER));
        StringBuilder csv = new StringBuilder();
        JRCsvExporter csvExporter = new JRCsvExporter();
        csvExporter.setExporterInput(new SimpleExporterInput(print));
        csvExporter.setExporterOutput(new SimpleWriterExporterOutput(csv));
        csvExporter.exportReport();
        assertTrue(csv.toString().contains(MARKER));
    }

    private JasperPrint print() throws JRException {
        return JasperFillManager.fillReport(JasperCompileManager.compileReport(design()),
                new java.util.HashMap<String, Object>(), new JREmptyDataSource());
    }

    private JasperDesign design() {
        JasperDesign design = new JasperDesign();
        design.setName("cv-compatibility");
        design.setPageWidth(300); design.setPageHeight(200);
        design.setColumnWidth(260); design.setLeftMargin(20); design.setRightMargin(20);
        design.setTopMargin(20); design.setBottomMargin(20);
        JRDesignBand band = new JRDesignBand(); band.setHeight(70);
        JRDesignStaticText text = new JRDesignStaticText();
        text.setX(0); text.setY(0); text.setWidth(220); text.setHeight(20); text.setText(MARKER);
        band.addElement(text);
        ((JRDesignSection) design.getDetailSection()).addBand(band);
        return design;
    }
}
