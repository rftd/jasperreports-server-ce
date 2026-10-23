// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package org.openjrs.reports.compat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRTemplate;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JacksonReportLoader;
import net.sf.jasperreports.engine.xml.ReportLoader;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/**
 * Translates the JR6 XML structure into JR7 XML before the official Jackson loader
 * builds and validates the design. Expressions, datasets and report resources are
 * retained; unknown properties are rejected by Jackson, never silently removed.
 * No JR6 reporting engine or compiled-object deserialization is involved.
 */
public final class LegacyReportLoader implements ReportLoader {
    private static final String LEGACY_NS = "http://jasperreports.sourceforge.net/jasperreports";
    private static final Set<String> NAMESPACES = set(LEGACY_NS,
            "http://jasperreports.sourceforge.net/jasperreports/components",
            "http://www.jaspersoft.com/cvcomponent");
    private static final Set<String> ELEMENTS = set("staticText", "textField", "line", "rectangle",
            "ellipse", "image", "frame", "subreport", "break", "componentElement", "genericElement", "crosstab");
    private static final Set<String> SINGLE_BANDS = set("background", "title", "pageHeader", "pageFooter",
            "columnHeader", "columnFooter", "lastPageFooter", "summary", "noData");
    private static final Set<String> COMMON_WRAPPERS = set("reportElement", "textElement", "graphicElement");
    private static final Set<String> EXPRESSION_NAMES = set("textFieldExpression", "imageExpression",
            "subreportExpression", "groupExpression", "variableExpression", "subreportParameterExpression",
            "datasetParameterExpression", "tableParameterExpression");

    private static Set<String> set(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    @Override
    public Optional<JasperDesign> loadReport(JasperReportsContext context, byte[] data) throws JRException {
        Document source = parse(data);
        if (source == null || !"jasperReport".equals(name(source.getDocumentElement()))
                || !isLegacy(source.getDocumentElement())) {
            return Optional.empty();
        }
        try {
            byte[] converted = convert(source);
            return JacksonReportLoader.instance().loadReport(context, converted);
        } catch (Exception e) {
            throw new JRException("Cannot convert legacy JRXML to JasperReports 7 without losing report content", e);
        }
    }

    @Override
    public Optional<JRTemplate> loadTemplate(JasperReportsContext context, byte[] data) {
        Document source = parse(data);
        if (source == null || !"jasperTemplate".equals(name(source.getDocumentElement()))
                || !isLegacy(source.getDocumentElement())) {
            return Optional.empty();
        }
        try {
            return JacksonReportLoader.instance().loadTemplate(context, convert(source));
        } catch (Exception e) {
            throw new net.sf.jasperreports.engine.JRRuntimeException("Cannot convert legacy style template", e);
        }
    }

    private boolean isLegacy(Element root) {
        if (LEGACY_NS.equals(root.getNamespaceURI())) return true;
        if (root.getNamespaceURI() != null && !root.getNamespaceURI().isEmpty()) return false;
        for (String tag : new String[] {"queryString", "reportElement", "textElement", "textFieldExpression"}) {
            if (root.getElementsByTagName(tag).getLength() != 0) return true;
        }
        return hasLegacyBoolean(root);
    }

    private boolean hasLegacyBoolean(Element element) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            if (attrs.item(i).getNodeName().matches("is[A-Z].*")) return true;
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && hasLegacyBoolean((Element) child)) return true;
        }
        return false;
    }

    private Document parse(byte[] data) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            // The WAR's Xerces 2.12.1-sp1 does not recognize ACCESS_EXTERNAL_*.
            // Rejecting every DOCTYPE, disabling validation/XInclude and both
            // external entity kinds prevents external resolution on that parser too.
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException e) throws SAXParseException { throw e; }
                public void error(SAXParseException e) throws SAXParseException { throw e; }
                public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
            });
            return builder.parse(new ByteArrayInputStream(data));
        } catch (Exception e) {
            // Other ReportLoaders may recognize this input. Do not resolve DTDs or entities.
            return null;
        }
    }

    private byte[] convert(Document source) throws Exception {
        Document output = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        output.appendChild(convertElement(source.getDocumentElement(), output));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(bytes, "UTF-8");
        try {
            writeElement(output.getDocumentElement(), writer);
        } finally {
            writer.close();
        }
        return bytes.toByteArray();
    }

    private void writeElement(Element element, XMLStreamWriter writer) throws Exception {
        writer.writeStartElement(name(element));
        // Jackson XML 2.13 loses unwrapped collections when a polymorphic kind
        // follows other attributes. JR7's native writer emits kind first too.
        if (element.hasAttribute("kind")) writer.writeAttribute("kind", element.getAttribute("kind"));
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            if (!"kind".equals(attr.getName())) writer.writeAttribute(attr.getName(), attr.getValue());
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) writeElement((Element) child, writer);
            else if (child.getNodeType() == Node.CDATA_SECTION_NODE) writer.writeCData(child.getNodeValue());
            else if (child.getNodeType() == Node.TEXT_NODE) writer.writeCharacters(child.getNodeValue());
        }
        writer.writeEndElement();
    }

    private Element convertElement(Element old, Document document) throws JRException {
        String ns = old.getNamespaceURI();
        if (ns != null && !ns.isEmpty() && !NAMESPACES.contains(ns)) {
            throw new JRException("Unsupported legacy extension namespace: " + ns);
        }
        String tag = name(old);
        boolean reportElement = ELEMENTS.contains(tag);
        String target = reportElement ? "element" : renameTag(tag);
        Element result = document.createElement(target);
        if (reportElement) result.setAttribute("kind", "componentElement".equals(tag) ? "component" : tag);
        if ("column".equals(tag) && ns != null && ns.endsWith("/components")) result.setAttribute("kind", "single");
        if ("columnGroup".equals(tag)) {
            result = (Element) document.renameNode(result, null, "column");
            result.setAttribute("kind", "group");
        }
        copyAttributes(old, result, tag);
        for (Node child = old.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                Element element = (Element) child;
                String childTag = name(element);
                if ((reportElement && COMMON_WRAPPERS.contains(childTag))
                        || ("conditionalStyle".equals(tag) && "style".equals(childTag))
                        || (SINGLE_BANDS.contains(tag) && "band".equals(childTag))) {
                    flatten(element, result, tag, document);
                } else if (reportElement && "componentElement".equals(tag)) {
                    Element component = convertElement(element, document);
                    component = (Element) document.renameNode(component, null, "component");
                    component.setAttribute("kind", childTag);
                    result.appendChild(component);
                } else if (reportElement && "font".equals(childTag)) {
                    copyFont(element, result);
                } else {
                    result.appendChild(convertElement(element, document));
                }
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                result.appendChild(document.importNode(child, true));
            }
        }
        return result;
    }

    private void flatten(Element wrapper, Element result, String owner, Document document) throws JRException {
        copyAttributes(wrapper, result, owner);
        for (Node child = wrapper.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                if ("font".equals(name(child))) copyFont((Element) child, result);
                else result.appendChild(convertElement((Element) child, document));
            }
        }
    }

    private void copyFont(Element font, Element target) {
        NamedNodeMap attrs = font.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String key = attr.getName();
            if ("size".equals(key)) key = "fontSize";
            else if ("name".equals(key)) key = "fontName";
            target.setAttribute(renameAttribute(key, "font"), attr.getValue());
        }
    }

    private void copyAttributes(Element from, Element to, String owner) {
        NamedNodeMap attrs = from.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())
                    || XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(attr.getNamespaceURI())) continue;
            String key = attr.getName();
            if ("class".equals(key) && (EXPRESSION_NAMES.contains(name(from))
                    || name(from).endsWith("Expression"))) continue; // JR6 deprecated expression class; JR7 infers it.
            if ("isStretchWithOverflow".equals(key)) {
                if (Boolean.parseBoolean(attr.getValue())) to.setAttribute("textAdjust", "StretchHeight");
                continue;
            }
            to.setAttribute(renameAttribute(key, owner), attr.getValue());
        }
    }

    private String renameAttribute(String key, String owner) {
        if ("hyperlinkType".equals(key)) return "linkType";
        if ("hyperlinkTarget".equals(key)) return "linkTarget";
        if ("textAlignment".equals(key)) return "hTextAlign";
        if ("verticalAlignment".equals(key)) return "image".equals(owner) ? "vImageAlign" : "vTextAlign";
        if ("horizontalAlignment".equals(key)) return "hImageAlign";
        if (key.matches("is[A-Z].*")) return Character.toLowerCase(key.charAt(2)) + key.substring(3);
        return key;
    }

    private String renameTag(String tag) {
        if ("queryString".equals(tag)) return "query";
        if ("subDataset".equals(tag)) return "dataset";
        if ("subreportParameter".equals(tag) || "datasetParameter".equals(tag)) return "parameter";
        if ("fieldDescription".equals(tag) || "parameterDescription".equals(tag)
                || "scriptletDescription".equals(tag)) return "description";
        if (EXPRESSION_NAMES.contains(tag)) return "expression";
        if ("listContents".equals(tag)) return "contents";
        return tag;
    }

    private static String name(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }
}
