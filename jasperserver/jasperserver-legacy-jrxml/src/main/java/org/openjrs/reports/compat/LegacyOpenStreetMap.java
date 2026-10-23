// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package org.openjrs.reports.compat;

import java.util.HashSet;
import java.util.Set;
import javax.xml.XMLConstants;
import net.sf.jasperreports.engine.JRException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Basic legacy maps use the Community visualization renderer and Leaflet/OSM. */
final class LegacyOpenStreetMap {
    static Element convert(Element source, Document document) throws JRException {
        String namespace = source.getNamespaceURI();
        if (namespace != null && !namespace.isEmpty()
                && !"http://jasperreports.sourceforge.net/jasperreports/components".equals(namespace)) {
            throw new JRException("Unsupported legacy map namespace: " + namespace);
        }
        Element component = document.createElement("component");
        component.setAttribute("kind", "customvisualization");
        NamedNodeMap attrs = source.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())
                    || XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(attr.getNamespaceURI())) continue;
            String name = attr.getNodeName();
            if ("evaluationTime".equals(name) || "evaluationGroup".equals(name)) {
                component.setAttribute(name, attr.getNodeValue());
            } else {
                throw new JRException("Legacy OpenStreetMap conversion does not support map attribute: " + name);
            }
        }
        value(component, "script", "org/openjrs/maps/openjrs-osm-map.js");
        value(component, "css", "org/openjrs/maps/leaflet.css");
        value(component, "module", "openjrs-osm-map");
        value(component, "renderAsPng", "true");
        expression(component, "tileUrl", "System.getProperty(\"openjrs.map.tile.url\", "
                + "\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\")");
        Set<String> found = new HashSet<String>();
        for (Node child = source.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element)) continue;
            Element element = (Element) child;
            if (!java.util.Objects.equals(namespace, element.getNamespaceURI())) {
                throw new JRException("Unsupported legacy map expression namespace");
            }
            String tag = element.getLocalName() == null ? element.getTagName() : element.getLocalName();
            String property;
            if ("latitudeExpression".equals(tag)) property = "latitude";
            else if ("longitudeExpression".equals(tag)) property = "longitude";
            else if ("zoomExpression".equals(tag)) property = "zoom";
            else throw new JRException("Legacy OpenStreetMap conversion does not support map content: " + tag);
            if (!found.add(property)) throw new JRException("Duplicate legacy map expression: " + tag);
            expression(component, property, element.getTextContent());
        }
        if (!found.contains("latitude")) value(component, "latitude", "0");
        if (!found.contains("longitude")) value(component, "longitude", "0");
        if (!found.contains("zoom")) value(component, "zoom", "1");
        return component;
    }

    private static Element property(Element component, String name) {
        Element item = component.getOwnerDocument().createElement("itemProperty");
        item.setAttribute("name", name);
        component.appendChild(item);
        return item;
    }

    private static void value(Element component, String name, String value) {
        property(component, name).setAttribute("value", value);
    }

    private static void expression(Element component, String name, String text) {
        // JR7 ItemPropertyDeserializer reads XML expression text directly.
        property(component, name).appendChild(component.getOwnerDocument().createCDATASection(text));
    }

    static String sampleExpression(Element element) {
        String original = element.getTextContent();
        String text = original.trim();
        Element root = element.getOwnerDocument().getDocumentElement();
        if ("TableReport".equals(root.getAttribute("name"))
                && "hyperlinkParameterExpression".equals(element.getLocalName() == null ? element.getTagName() : element.getLocalName())
                && "jr.uri".equals(((Element) element.getParentNode()).getAttribute("name"))
                && "\"/reports/MapReport.jasper\"".equals(text)) {
            return "\"/reports/interactive/MapReport\"";
        }
        if (!"MapReport".equals(root.getAttribute("name"))) return original;
        if ("\"Google Map\"".equals(text)) return "\"OpenStreetMap\"";
        // The shipped sample parsed missing request parameters and used
        // Integer.getInteger (a system-property lookup) instead of parsing zoom.
        for (String parameter : new String[] {"latitude", "longitude", "zoom"}) {
            String getter = "$P{REPORT_CONTEXT}.getParameterValue(\"" + parameter + "\")";
            String parser = "zoom".equals(parameter) ? "Integer.getInteger" : "Float.parseFloat";
            if (text.equals(parser + "((String)" + getter + ")")) {
                String type = "zoom".equals(parameter) ? "Integer" : "Float";
                return "($P{REPORT_CONTEXT} == null || " + getter + " == null || String.valueOf("
                        + getter + ").trim().isEmpty() ? null : "
                        + type + ".valueOf(String.valueOf(" + getter + ").trim()))";
            }
        }
        return original;
    }
}
