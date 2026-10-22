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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.jaspersoft.jasperserver.api.engine.jasperreports.util.ExportUtil;
import com.jaspersoft.jasperserver.api.metadata.xml.domain.impl.Argument;

import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.ReportContext;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.engine.export.AbstractHtmlExporter;
import net.sf.jasperreports.engine.export.HtmlResourceHandler;
import net.sf.jasperreports.engine.export.MapHtmlResourceHandler;
import net.sf.jasperreports.web.util.WebHtmlResourceHandler;

/*  2012-09-13  thorick: restored 24858 to fix build   */
/*  2012-09-13  thorick: backout 24858 to fix build
import com.jaspersoft.jasperserver.war.util.JRHtmlExportUtils;
*/
/*  2012-09-13  thorick: backout 24858 to fix build
import net.sf.jasperreports.engine.JasperReportsContext;
*/
/*  2012-09-13  thorick: backout 24858 to fix build
import org.springframework.beans.factory.annotation.Autowired;
*/
/*  2012-09-13  thorick: backout 24858 to fix build
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
*/
/*  2012-09-13  thorick: backout 24858 to fix build
import java.util.List;
*/

/**
 * @author sanda zaharia (shertage@users.sourceforge.net)
 * @@version $Id: HtmlWSExporter.java 19933 2010-12-11 15:27:37Z tmatyashovsky $
 */
@Service("remoteHtmlExporter")
@Scope("prototype")
public class HtmlExporter extends AbstractExporter {

    public static final String CONTEXT_PATH_PARAM_NAME = "contextPath";
    public static final String BASE_URL_PARAM_NAME = "baseUrl";
    public static final String REPORT_CONTEXT_PARAM_NAME = "reportContext";
    public static final String INTERACTIVE_PARAM_NAME = "interactive";
    public static final String ALLOW_INLINE_SCRIPTS_PARAM_NAME = "allowInlineScripts";

    @Resource
    private List<String> htmlReportHeaderIncludes;

    @Value("${deploy.base.url:}")
    private String deployBaseUrl;

    @Override
    public net.sf.jasperreports.engine.JRAbstractExporter<?, ?, ?, ?> createExporter() throws Exception {
        return ExportUtil.getInstance(getJasperReportsContext()).createHtmlExporter();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void configureExporter(net.sf.jasperreports.engine.JRAbstractExporter exporter, HashMap exportParameters,
            net.sf.jasperreports.export.SimpleReportExportConfiguration config,
            net.sf.jasperreports.export.ExporterOutput output, Map<String, Object> results) throws Exception {
        final String contextPath;
        if (exportParameters.get(BASE_URL_PARAM_NAME) != null)
            contextPath = (String) exportParameters.get(BASE_URL_PARAM_NAME);
        else if (deployBaseUrl != null && !deployBaseUrl.isEmpty())
            contextPath = deployBaseUrl;
        else
            contextPath = exportParameters.get(CONTEXT_PATH_PARAM_NAME) == null ? ""
                    : (String) exportParameters.get(CONTEXT_PATH_PARAM_NAME);

        StringBuilder htmlHeader = new StringBuilder();
        for (String currentInclude : htmlReportHeaderIncludes)
            htmlHeader.append(currentInclude.replace("{contextPath}", contextPath));
        net.sf.jasperreports.export.SimpleHtmlExporterConfiguration exporterConfig =
                new net.sf.jasperreports.export.SimpleHtmlExporterConfiguration();
        exporterConfig.setHtmlHeader(htmlHeader.toString());
        exporter.setConfiguration((net.sf.jasperreports.export.ExporterConfiguration) exporterConfig);
        Object ignoreMargins = exportParameters.get("ignorePageMargins");
        if (ignoreMargins != null)
            ((net.sf.jasperreports.export.SimpleHtmlReportConfiguration) config)
                    .setIgnorePageMargins(Boolean.valueOf(String.valueOf(ignoreMargins)));

        Map<String, byte[]> imagesMap = new LinkedHashMap<>();
        String imagePrefix = exportParameters.get(Argument.RUN_OUTPUT_IMAGES_URI) != null
                ? exportParameters.get(Argument.RUN_OUTPUT_IMAGES_URI).toString() : "images/";
        HtmlResourceHandler handler = new MapHtmlResourceHandler(
                new WebHtmlResourceHandler(imagePrefix + "{0}"), imagesMap);
        net.sf.jasperreports.export.SimpleHtmlExporterOutput htmlOutput =
                (net.sf.jasperreports.export.SimpleHtmlExporterOutput) output;
        htmlOutput.setImageHandler(handler);
        htmlOutput.setResourceHandler(handler);
        htmlOutput.setFontHandler(new WebHtmlResourceHandler(
                contextPath + "/reportresource?&font={0}"));
        results.put(com.jaspersoft.jasperserver.remote.ReportExporter.HTML_IMAGES_KEY, imagesMap);

        ReportContext reportContext = (ReportContext) exportParameters.get(REPORT_CONTEXT_PARAM_NAME);
        if (reportContext != null) reportContext.setParameterValue("contextPath", contextPath);
        exporter.setReportContext(reportContext);
        boolean allowInlineScripts = Boolean.TRUE.equals(exportParameters.get(ALLOW_INLINE_SCRIPTS_PARAM_NAME));
        boolean interactive = Boolean.TRUE.equals(exportParameters.get(INTERACTIVE_PARAM_NAME));
        if (interactive && allowInlineScripts)
            exporter.getJasperReportsContext().setProperty(
                    "com.jaspersoft.jasperreports.highcharts.html.export.type", "standalone");
    }

    @Override
    public String getContentType() {
        return "text/html";
    }

    @Override
    public JasperReportsContext getJasperReportsContext() {
        // return copy of jasper reports context to allow modifications
        SimpleJasperReportsContext context = new SimpleJasperReportsContext();
        context.setParent(super.getJasperReportsContext());
        return context;
    }
}
