/*
 * Copyright (C) 2005-2023. Cloud Software Group, Inc. All Rights Reserved.
 * http://www.jaspersoft.com.
 *
 * Unless you have purchased a commercial license agreement from Jaspersoft,
 * the following license terms apply:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */


package com.jaspersoft.jasperserver.remote.exporters;

import com.jaspersoft.jasperserver.api.common.domain.ExecutionContext;
import com.jaspersoft.jasperserver.api.engine.common.service.EngineService;
import com.jaspersoft.jasperserver.api.engine.jasperreports.domain.impl.PaginationParameters;
import com.jaspersoft.jasperserver.api.metadata.xml.domain.impl.Argument;
import com.jaspersoft.jasperserver.remote.ReportExporter;
import com.jaspersoft.jasperserver.remote.services.ReportOutputPages;
import net.sf.jasperreports.engine.JRPropertiesHolder;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JRAbstractExporter;
import net.sf.jasperreports.export.*;
import net.sf.jasperreports.engine.export.*;
import net.sf.jasperreports.engine.export.ooxml.*;
import net.sf.jasperreports.engine.export.oasis.*;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.export.ExporterInputItem;
import net.sf.jasperreports.export.SimpleExporterInputItem;

import javax.annotation.Resource;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gtoffoli
 * @version $Id$
 */
public abstract class AbstractExporter implements ReportExporter {
	
    @Resource(name = "jasperReportsRemoteContext")
    private JasperReportsContext jasperReportsContext;
    
    private final String paginatedProperty;
    
    public AbstractExporter() {
    	this(null);
    }
    
    protected AbstractExporter(String paginatedProperty) {
    	this.paginatedProperty = paginatedProperty;
    }

    @Override
    public Map<String, Object> exportReport(JasperPrint jasperPrint, OutputStream output,
            EngineService engineService, HashMap exportParameters, ExecutionContext executionContext,
            String reportUnitURI) throws Exception {
        return exportReport(Collections.singletonList(new SimpleExporterInputItem(jasperPrint)),
                output, engineService, exportParameters, executionContext, reportUnitURI);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Map<String, Object> exportReport(List<ExporterInputItem> inputItems, OutputStream output,
            EngineService engineService, HashMap exportParameters, ExecutionContext executionContext,
            String reportUnitURI) throws Exception {
        JRAbstractExporter exporter = createExporter();
        SimpleReportExportConfiguration config = createReportConfiguration(exporter);
        Map<String, Object> results = new HashMap<>();
        exporter.setExporterInput(new SimpleExporterInput(inputItems));

        if (exportParameters.get(Argument.RUN_OUTPUT_PAGE) != null) {
            config.setPageIndex(Integer.parseInt(String.valueOf(
                    getSingleParameterValue(Argument.RUN_OUTPUT_PAGE, exportParameters))) - 1);
        } else if (exportParameters.get(Argument.RUN_OUTPUT_PAGES) != null) {
            ReportOutputPages pages = (ReportOutputPages) exportParameters.get(Argument.RUN_OUTPUT_PAGES);
            if (pages.getPage() != null) config.setPageIndex(pages.getPage() - 1);
            else if (pages.getStartPage() != null && pages.getEndPage() != null) {
                config.setStartPageIndex(pages.getStartPage() - 1);
                config.setEndPageIndex(pages.getEndPage() - 1);
            }
        }

        ExporterOutput exporterOutput;
        if (exporter instanceof AbstractHtmlExporter) {
            exporterOutput = new SimpleHtmlExporterOutput(output, "UTF-8");
        } else if (exporter instanceof JRAbstractCsvExporter || exporter instanceof JRRtfExporter
                || exporter.getClass().getName().endsWith(".JsonMetadataExporter")) {
            exporterOutput = new SimpleWriterExporterOutput(output, "UTF-8");
        } else if (exporter instanceof JRXmlExporter) {
            exporterOutput = new SimpleXmlExporterOutput(output, "UTF-8");
        } else {
            exporterOutput = new SimpleOutputStreamExporterOutput(output);
        }
        exporter.setExporterOutput(exporterOutput);
        configureExporter(exporter, exportParameters, config, exporterOutput, results);
        exporter.setConfiguration((ReportExportConfiguration) config);
        exporter.exportReport();
        return results;
    }

    public abstract JRAbstractExporter<?, ?, ?, ?> createExporter() throws Exception;

    protected SimpleReportExportConfiguration createReportConfiguration(JRAbstractExporter exporter) {
        if (exporter instanceof JRCsvMetadataExporter) return new SimpleCsvMetadataReportConfiguration();
        if (exporter instanceof JRCsvExporter) return new SimpleCsvReportConfiguration();
        if (exporter instanceof AbstractHtmlExporter) return new SimpleHtmlReportConfiguration();
        if (exporter instanceof net.sf.jasperreports.engine.export.ooxml.XlsxMetadataExporter)
            return new SimpleXlsxMetadataReportConfiguration();
        if (exporter instanceof JRXlsxExporter) return new SimpleXlsxReportConfiguration();
        if (exporter instanceof net.sf.jasperreports.poi.export.JRXlsExporter) return new SimpleXlsReportConfiguration();
        if (exporter instanceof JROdsExporter) return new SimpleOdsReportConfiguration();
        if (exporter instanceof JROdtExporter) return new SimpleOdtReportConfiguration();
        if (exporter instanceof JRPptxExporter) return new SimplePptxReportConfiguration();
        if (exporter instanceof JRDocxExporter) return new SimpleDocxReportConfiguration();
        if (exporter instanceof JRRtfExporter) return new SimpleRtfReportConfiguration();
        if (exporter instanceof JRXmlExporter) return new SimpleReportExportConfiguration();
        if (exporter.getClass().getName().endsWith(".JsonMetadataExporter"))
            return new net.sf.jasperreports.json.export.SimpleJsonMetadataReportConfiguration();
        throw new IllegalArgumentException("Unsupported JasperReports 7 exporter: " + exporter.getClass());
    }

    public void configureExporter(JRAbstractExporter exporter, HashMap exportParameters,
            SimpleReportExportConfiguration config, ExporterOutput output,
            Map<String, Object> results) throws Exception {
        // Specialized exporters configure their format-specific options.
    }

    protected Object getSingleParameterValue(String parameterName, Map<String, Object> exportParameters) {
        Object result = null;
        if (exportParameters.get(parameterName) != null) {
            if (exportParameters.get(parameterName) instanceof String[]) {
                if (((String[]) exportParameters.get(parameterName)).length > 0)
                    result = ((String[]) exportParameters.get(parameterName))[0];
            } else
                result = exportParameters.get(parameterName);
        }
        return result;
    }

	public JasperReportsContext getJasperReportsContext() {
		return jasperReportsContext;
	}
	
    @Override
	public PaginationParameters getPaginationParameters(JRPropertiesHolder propertiesHolder) {
		PaginationParameters pagination = new PaginationParameters();
		if (paginatedProperty != null) {
			Boolean paginated = JRPropertiesUtil.getInstance(jasperReportsContext).getBooleanProperty(
					propertiesHolder, paginatedProperty);
			pagination.setPaginated(paginated);
		}
		return pagination;
	}

}
