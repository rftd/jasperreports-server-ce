// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package net.sf.jasperreports.web.util;

import net.sf.jasperreports.components.headertoolbar.HeaderToolbarElement;
import net.sf.jasperreports.components.headertoolbar.json.HeaderToolbarElementJsonHandler;
import net.sf.jasperreports.crosstabs.interactive.CrosstabInteractiveJsonHandler;
import net.sf.jasperreports.engine.JRPropertiesMap;
import net.sf.jasperreports.engine.export.GenericElementHandler;
import net.sf.jasperreports.engine.export.GenericElementHandlerBundle;
import net.sf.jasperreports.engine.fill.JRFillCrosstab;
import net.sf.jasperreports.engine.xml.JRXmlConstants;
import net.sf.jasperreports.extensions.ExtensionsRegistry;
import net.sf.jasperreports.extensions.ExtensionsRegistryFactory;
import net.sf.jasperreports.extensions.SingletonExtensionRegistry;
import net.sf.jasperreports.json.export.JsonExporter;

/** Reinstates the Community viewer metadata handlers omitted from JR7. */
public class LegacyInteractiveElementsExtensionsRegistryFactory implements ExtensionsRegistryFactory {
    @Override
    public ExtensionsRegistry createRegistry(String id, JRPropertiesMap properties) {
        return new SingletonExtensionRegistry<>(GenericElementHandlerBundle.class, new GenericElementHandlerBundle() {
            @Override
            public String getNamespace() {
                return JRXmlConstants.JASPERREPORTS_NAMESPACE;
            }

            @Override
            public GenericElementHandler getHandler(String name, String exporterKey) {
                if (!JsonExporter.JSON_EXPORTER_KEY.equals(exporterKey)) return null;
                if (HeaderToolbarElement.ELEMENT_NAME.equals(name)) return new HeaderToolbarElementJsonHandler();
                if (JRFillCrosstab.CROSSTAB_INTERACTIVE_ELEMENT_NAME.equals(name)) return new CrosstabInteractiveJsonHandler();
                return null;
            }
        });
    }
}
