// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package org.openjrs.reports.compat;

import net.sf.jasperreports.engine.JRPropertiesMap;
import net.sf.jasperreports.engine.xml.ReportLoader;
import net.sf.jasperreports.extensions.ExtensionsRegistry;
import net.sf.jasperreports.extensions.ExtensionsRegistryFactory;
import net.sf.jasperreports.extensions.SingletonExtensionRegistry;

/** Adds legacy format adaptation through the public JR7 ReportLoader contract. */
public final class LegacyReportLoaderFactory implements ExtensionsRegistryFactory {
    @Override
    public ExtensionsRegistry createRegistry(String id, JRPropertiesMap properties) {
        return new SingletonExtensionRegistry<ReportLoader>(ReportLoader.class, new LegacyReportLoader());
    }
}
