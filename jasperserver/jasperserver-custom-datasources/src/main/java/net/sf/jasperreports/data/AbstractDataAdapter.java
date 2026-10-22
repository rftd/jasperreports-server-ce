/*
 * OpenJRS Server compatibility bridge.
 *
 * Copyright (C) 2026 OpenJRS Server contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package net.sf.jasperreports.data;

/**
 * Legacy superclass referenced in the MongoDB connector's bytecode.
 *
 * <p>Delegate the name property and modern DataAdapter implementation to
 * JasperReports 7. The original MongoDbDataAdapterImpl can still extend its
 * historical superclass while being assignable to the current API.</p>
 *
 * <p>Migration-only bridge: not a substitute for a connector rebuild and
 * functional MongoDB integration tests.</p>
 */
@Deprecated
public abstract class AbstractDataAdapter
        extends net.sf.jasperreports.dataadapters.AbstractDataAdapter
        implements DataAdapter {
}
