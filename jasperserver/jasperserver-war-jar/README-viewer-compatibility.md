# Community viewer integration with JasperReports 7

The server retains the Community viewer protocol while using one JR7 engine.
JR7 still emits interactive table/crosstab metadata but no longer includes the
old web action implementations and metadata export handlers. This module
registers the recovered actions through JR7 Jackson subtype mappings and
registers JSON handlers through `GenericElementHandlerBundle`. The existing
JR7 core classes are reused, including `HeaderToolbarElement`, table components,
crosstab data objects, and cache/compile/fill infrastructure.

The port retains table sorting/filtering, column resizing/moving/visibility,
text formatting, conditional formatting, command history and crosstab ordering
logic. Parameter/style contributors apply filters and conditional formatting
when a report is refilled. Exporter and Jackson packages, font/mode accessors
and sort-order accessors are adapted to the official 7.1.0 APIs.

`JRFillCrosstab.setOrderByColumnInfo()` in the official JR7 source loads the
concrete name `net.sf.jasperreports.interactivity.crosstabs.OrderByColumnInfoImpl`.
OpenJRS provides that data model by adapting the LGPL JR6 `OrderByColumnInfo`
implementation to the JR7 interface. It stores/serializes the order, measure and
column values; it is not an empty compatibility class. No proprietary
interactivity binary or source is used.

Validation includes the engine's real Jackson mapper, table sorting/filtering
through action execution and compile/fill, undo/redo, resizing and invalid width
rejection, metadata handler registration, and the exact crosstab model loading
contract. Browser checks cover the main viewer controls, custom components,
interactive table sorting/filtering and undo/redo through the actual controls.
These checks do not establish coverage of every report layout, formatting rule,
crosstab interaction, standalone legacy sort component or drill-down target.

## Sources and licenses

Recovered files retain their original Cloud Software Group LGPL notices. See
`LICENSE-viewer-LGPL`. The new registry integration and tests are OpenJRS code.
The table action/filter/formatting support and missing crosstab handlers/actions
were recovered from the published Community archive:

- `net.sf.jasperreports:jasperreports:6.21.5:sources` on Maven Central.
- SHA-256: `d13d73301e83f3b9bf7c3e9d7436c13e0242e90513c817f0f1773db6e7b54cba`.
- Official API comparison: <https://github.com/Jaspersoft/jasperreports/tree/7.1.0>.

The web context/actions/commands and resource handlers previously recovered in
this branch retain their existing notices. The viewer is preserved in this PR;
a replacement viewer is a separate project change.
