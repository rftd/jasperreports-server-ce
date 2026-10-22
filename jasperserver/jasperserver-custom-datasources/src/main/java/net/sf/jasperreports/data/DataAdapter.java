/*
 * OpenJRS Server compatibility bridge.
 *
 * Copyright (C) 2026 OpenJRS Server contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package net.sf.jasperreports.data;

/**
 * Legacy binary name used by the MongoDB connector built against JasperReports 6.
 *
 * <p>JasperReports 7 moved this contract to the dataadapters package.
 * Extending the new contract preserves assignment compatibility without
 * modifying or repackaging the vendor's MongoDB connector.</p>
 *
 * <p>Migration-only bridge: remove when the MongoDB connector is rebuilt
 * against JasperReports 7.</p>
 */
@Deprecated
public interface DataAdapter extends net.sf.jasperreports.dataadapters.DataAdapter {
}
