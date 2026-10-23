// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package net.sf.jasperreports.customvisualization;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import net.sf.jasperreports.components.items.StandardItemProperty;
import net.sf.jasperreports.customvisualization.design.CVDesignComponent;
import net.sf.jasperreports.customvisualization.export.ChromeCVElementImageDataProvider;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.export.*;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.repo.SimpleRepositoryContext;
import org.junit.Test;
import static org.junit.Assert.*;

/** Explicit CI integration check requiring a sandbox-capable Chrome installation. */
public class CVBrowserIT {
    @Test(timeout = 120000)
    public void rendersCustomSvgAndExportsPdf() throws Exception {
        String executable = System.getProperty("openjrs.chrome.path");
        assertNotNull("Set -Dopenjrs.chrome.path to run this integration check", executable);
        Path output = java.nio.file.Paths.get("target", "cv-browser").toAbsolutePath();
        Files.createDirectories(output);
        SimpleJasperReportsContext context = new SimpleJasperReportsContext();
        context.setProperty("net.sf.jasperreports.chrome.enabled", "true");
        context.setProperty("net.sf.jasperreports.chrome.executable.path", executable);
        context.setProperty("net.sf.jasperreports.chrome.idle.timeout", "1000");
        context.setProperty("net.sf.jasperreports.chrome.page.timeout", "30000");
        context.setProperty("net.sf.jasperreports.chrome.argument.user-data-dir",
                Files.createTempDirectory("openjrs-cv-browser-").toString());

        JasperDesign design = new JasperDesign();
        design.setName("community-cv-browser");
        design.setPageWidth(300); design.setPageHeight(200); design.setColumnWidth(260);
        design.setLeftMargin(20); design.setRightMargin(20);
        design.setTopMargin(20); design.setBottomMargin(20);
        CVDesignComponent component = new CVDesignComponent();
        component.addItemProperty(new StandardItemProperty("script",
                getClass().getResource("/cv-browser-smoke.js").toExternalForm(), null));
        component.addItemProperty(new StandardItemProperty("module", "openjrs-cv-smoke", null));
        JRDesignComponentElement element = new JRDesignComponentElement();
        element.setX(0); element.setY(0); element.setWidth(200); element.setHeight(100);
        element.setComponent(component);
        JRDesignBand band = new JRDesignBand(); band.setHeight(110); band.addElement(element);
        ((JRDesignSection) design.getDetailSection()).addBand(band);
        JasperPrint print = JasperFillManager.getInstance(context).fill(
                JasperCompileManager.getInstance(context).compile(design),
                new HashMap<String, Object>(), new JREmptyDataSource());
        JRGenericPrintElement generic = (JRGenericPrintElement) print.getPages().get(0).getElements().get(0);
        byte[] svg = new ChromeCVElementImageDataProvider().getImageData(SimpleRepositoryContext.of(context), generic);
        String text = new String(svg, StandardCharsets.UTF_8);
        assertTrue("Custom script did not render its SVG circle", text.contains("circle"));
        assertTrue("Custom SVG color missing", text.contains("1264a3"));
        Files.write(output.resolve("visualization.svg"), svg);
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter(context);
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(pdf));
        exporter.exportReport();
        assertTrue(pdf.size() > 1000);
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf.toByteArray());
        try {
            assertEquals(1, reader.getNumberOfPages());
            assertTrue("PDF page content missing", reader.getPageContent(1).length > 0);
        } finally {
            reader.close();
        }
        Files.write(output.resolve("visualization.pdf"), pdf.toByteArray());
    }
}
