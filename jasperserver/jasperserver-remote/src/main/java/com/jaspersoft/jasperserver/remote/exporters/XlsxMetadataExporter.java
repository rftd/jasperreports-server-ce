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
import net.sf.jasperreports.engine.JasperPrint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sanda Zaharia
 */
@Service("remoteXlsxMetadataExporter")
@Scope("prototype")
public class XlsxMetadataExporter extends AbstractExporter{

    private static Logger log = LogManager.getLogger(XlsxMetadataExporter.class);

    public static final String ONE_PAGE_PER_SHEET = "onePagePerSheet";
    @Resource(name = "xlsExportParameters")
    private XlsExportParametersBean exportParams;

    @Resource
    protected MessageSource messageSource;

    public XlsxMetadataExporter() {
        super(XlsExportParametersBean.PROPERTY_XLS_PAGINATED);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void configureExporter(net.sf.jasperreports.engine.JRAbstractExporter exporter, HashMap exportParameters,
            net.sf.jasperreports.export.SimpleReportExportConfiguration config,
            net.sf.jasperreports.export.ExporterOutput output, Map<String, Object> results) throws Exception {
        XlsExporter.applySpreadsheetSettings(exportParams, exportParameters,
                (net.sf.jasperreports.export.AbstractXlsReportConfiguration) config);
        net.sf.jasperreports.export.AbstractXlsExporterConfiguration exporterConfig =
                XlsExporter.createSpreadsheetExporterConfiguration(exporter);
        exporterConfig.setCreateCustomPalette(Boolean.TRUE);
        exporter.setConfiguration((net.sf.jasperreports.export.ExporterConfiguration) exporterConfig);
    }

    @Override
    public Map<String, Object> exportReport(JasperPrint jasperPrint, OutputStream output, EngineService engineService, HashMap exportParameters, ExecutionContext executionContext, String reportUnitURI) throws Exception {
        Map<String, Object> exportResult = super.exportReport(jasperPrint, output, engineService, exportParameters, executionContext, reportUnitURI);
        return exportResult;
    }

    /**
     * @param exportParams The exportParams to set.
     */
    public void setExportParams(XlsExportParametersBean exportParams) {
        this.exportParams = exportParams;
    }

    @Override
    public net.sf.jasperreports.engine.JRAbstractExporter<?, ?, ?, ?> createExporter() throws Exception {
        return new net.sf.jasperreports.engine.export.ooxml.XlsxMetadataExporter(getJasperReportsContext());
    }

    @Override
    public String getContentType() {
        return "application/xlsx";
    }

}
