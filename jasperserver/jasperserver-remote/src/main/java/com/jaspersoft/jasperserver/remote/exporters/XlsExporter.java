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
import com.jaspersoft.jasperserver.api.engine.jasperreports.common.XlsExportParametersBean;
import com.jaspersoft.jasperserver.api.engine.jasperreports.util.ExportUtil;
import net.sf.jasperreports.engine.JasperPrint;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Giulio Toffoli (original sanda zaharia (shertage@users.sourceforge.net))
 * @version $Id$
 */
@Service("remoteXlsExporter")
@Scope("prototype")
public class XlsExporter extends AbstractExporter {

    public static final String ONE_PAGE_PER_SHEET = "onePagePerSheet";
    @Resource(name = "xlsExportParameters")
    private XlsExportParametersBean exportParams;

    @Resource
    protected MessageSource messageSource;

    public XlsExporter() {
    	super(XlsExportParametersBean.PROPERTY_XLS_PAGINATED);
	}
    
    @Override
    public net.sf.jasperreports.engine.JRAbstractExporter<?, ?, ?, ?> createExporter() throws Exception {
        return ExportUtil.getInstance(getJasperReportsContext()).createXlsExporter();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void configureExporter(net.sf.jasperreports.engine.JRAbstractExporter exporter, HashMap exportParameters,
            net.sf.jasperreports.export.SimpleReportExportConfiguration config,
            net.sf.jasperreports.export.ExporterOutput output, Map<String, Object> results) throws Exception {
        applySpreadsheetSettings(exportParams, exportParameters,
                (net.sf.jasperreports.export.AbstractXlsReportConfiguration) config);
        net.sf.jasperreports.export.AbstractXlsExporterConfiguration exporterConfig =
                createSpreadsheetExporterConfiguration(exporter);
        exporterConfig.setCreateCustomPalette(Boolean.TRUE);
        exporter.setConfiguration((net.sf.jasperreports.export.ExporterConfiguration) exporterConfig);
    }

    static void applySpreadsheetSettings(XlsExportParametersBean exportParams, HashMap exportParameters,
            net.sf.jasperreports.export.AbstractXlsReportConfiguration config) {
        if (exportParams == null) return;
        if (exportParams.isOverrideReportHints()) config.setOverrideHints(Boolean.TRUE);
        if (exportParams.getOnePagePerSheet() != null)
            config.setOnePagePerSheet(exportParams.getOnePagePerSheet());
        if (exportParameters.get(ONE_PAGE_PER_SHEET) != null)
            config.setOnePagePerSheet(Boolean.valueOf(String.valueOf(
                    exportParameters.get(ONE_PAGE_PER_SHEET))));
        if (exportParams.getDetectCellType() != null)
            config.setDetectCellType(exportParams.getDetectCellType());
        if (exportParams.getRemoveEmptySpaceBetweenRows() != null)
            config.setRemoveEmptySpaceBetweenRows(exportParams.getRemoveEmptySpaceBetweenRows());
        if (exportParams.getRemoveEmptySpaceBetweenColumns() != null)
            config.setRemoveEmptySpaceBetweenColumns(exportParams.getRemoveEmptySpaceBetweenColumns());
        if (exportParams.getWhitePageBackground() != null)
            config.setWhitePageBackground(exportParams.getWhitePageBackground());
        if (exportParams.getIgnoreGraphics() != null)
            config.setIgnoreGraphics(exportParams.getIgnoreGraphics());
        if (exportParams.getCollapseRowSpan() != null)
            config.setCollapseRowSpan(exportParams.getCollapseRowSpan());
        if (exportParams.getIgnoreCellBorder() != null)
            config.setIgnoreCellBorder(exportParams.getIgnoreCellBorder());
        if (exportParams.getFontSizeFixEnabled() != null)
            config.setFontSizeFixEnabled(exportParams.getFontSizeFixEnabled());
        if (exportParams.getMaximumRowsPerSheet() != null)
            config.setMaxRowsPerSheet(exportParams.getMaximumRowsPerSheet());
        if (exportParams.getXlsFormatPatternsMap() != null && !exportParams.getXlsFormatPatternsMap().isEmpty())
            config.setFormatPatternsMap(exportParams.getXlsFormatPatternsMap());
    }

    static net.sf.jasperreports.export.AbstractXlsExporterConfiguration createSpreadsheetExporterConfiguration(
            net.sf.jasperreports.engine.JRAbstractExporter exporter) {
        if (exporter instanceof net.sf.jasperreports.engine.export.ooxml.XlsxMetadataExporter)
            return new net.sf.jasperreports.export.SimpleXlsxMetadataExporterConfiguration();
        if (exporter instanceof net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter)
            return new net.sf.jasperreports.export.SimpleXlsxExporterConfiguration();
        if (exporter instanceof net.sf.jasperreports.engine.export.oasis.JROdsExporter)
            return new net.sf.jasperreports.export.SimpleOdsExporterConfiguration();
        return new net.sf.jasperreports.export.SimpleXlsExporterConfiguration();
    }

    @Override
    public Map<String, Object> exportReport(JasperPrint jasperPrint, OutputStream output, EngineService engineService, HashMap exportParameters, ExecutionContext executionContext, String reportUnitURI) throws Exception {
        return super.exportReport(jasperPrint, output, engineService, exportParameters, executionContext, reportUnitURI);
    }

    /**
     * @param exportParams The exportParams to set.
     */
    public void setExportParams(XlsExportParametersBean exportParams) {
        this.exportParams = exportParams;
    }

    @Override
    public String getContentType() {
        return "application/xls";
    }
}
