# OpenJRS Server — JasperReports Server CE migration baseline

## Native JR7 reports — BREAKING CHANGE

OpenJRS accepts **native JasperReports 7.1.0 JRXML/JRTX only**. The automatic
JR6 XML conversion module, its dependency and its extension registration have
been removed. JR6 sources must be migrated before import; JR6 compiled `.jasper`
files must be regenerated with JR7. Keeping a database volume or rebuilding
an image does not migrate the reports stored in that database.

All seven JRXML definitions in the PostgreSQL/MySQL Community catalogs are native:
Employees, EmployeeAccounts, FoodmartSalesMondrianReport, CustomersReport,
OrdersReport (subreport), TableReport and MapReport. Standalone Community examples
and production/remote-test fixtures have also been rewritten with the official
JR7 writer. The Community import-test packages contain another thirteen native
JRXML definitions and one native JRTX style template. Source copyright/license
comments are retained. Historical Pro/Visualize.js archives and old `script-revs`
upgrade catalogs are outside the Community bootstrap and this migration.

For user reports, use [Jaspersoft Studio 7 or later](https://github.com/Jaspersoft/jasperreports/blob/7.1.0/changes.txt)
to migrate source files and style templates, review renamed APIs/component modules,
recompile with JR7, and validate data sources, subreports, interactions and exports
before import. There is no JR6 runtime or automatic fallback. The existing web
viewer and the Community visualization implementation retain their JR7 integration.

The parent aligns Jackson with JR7's declared versions (2.22.3, annotations 2.22)
and JFreeChart with 1.5.6. Even the official native JR7 pie example fails with
the former Jackson 2.13.4 polymorphic plot merge. The auxiliary ChartPart book
example uses a Community pie chart for customer counts by country in place of
its commercial Highcharts component; title, bookmarks and book navigation remain.
New JFreeChart no longer needs `jcommon`; Jackson's JAXB provider also removes
the redundant `jakarta.xml.bind-api` path while `jaxb-api` remains. Twelve new
artifact hashes, including the resolved Activation API version, were checked
against Maven Central and recorded in
[`jr7-native-reports-provenance.json`](jasperserver/deps/jr7-native-reports-provenance.json).
The diagnostic allowance pins these exact replacements. The original strict
Buildomatic inventory and the unresolved SiteMesh review remain unchanged.

**BREAKING CHANGE — PDF accessibility:** bundled examples/fixtures that requested
tagged PDF now set
`net.sf.jasperreports.export.pdf.tagged=false` and export ordinary, untagged PDF.
JasperReports 7.1.0 Community's default PDF tagger rejects tagged export; the
official silent factory also produces untagged output. This is not accessible
tagged PDF or a PDF/UA compliance claim. PDF accessibility is deferred to separate
work. Existing structural tag properties remain in the JRXML for that work but
have no effect while tagging is disabled. In the main CE catalog this affects
Employees, EmployeeAccounts and Foodmart; auxiliary AllAccounts, SalesByMonth,
ParamMany and import-package examples also use ordinary PDF. This changes sample definitions:
there is no global silent tagger or override of users' report settings.

Existing databases preserve their stored reports on image rebuild. From an updated
checkout with the local server running, `python3 scripts/update-native-samples.py`
previews the upgrade and `--apply` backs up and updates only known unmodified
originals among all seven catalog JRXML files. Customized files are refused, versions are checked and volumes are
preserved. See [native sample sources and upgrade instructions](jasperserver/samples/jrxml7/README.md).

## Table Report and OpenStreetMap example

The shipped Table Report stores native JR7 hyperlink parameters and current
stretch aliases directly in its JRXML. Its city
drilldown points to `/reports/interactive/MapReport` and retains coordinates.

The native MapReport renders through the Community visualization
integration using bundled Leaflet 1.9.4 and OpenStreetMap tiles. Google Maps and
its API key are no longer used for these maps. The shipped Map Report keeps its
default center and accepts latitude, longitude and numeric zoom parameters;
missing/blank request parameters use its defaults. An existing database needs
the explicit sample update described above before the old adapter is removed.

**Compatibility change:** MapReport now uses OpenStreetMap/Leaflet directly.
User reports with Google map components require their own explicit component
migration; no automatic map conversion remains.

The browser can pan and zoom the map; PDF/XLSX images use sandboxed Chromium,
configured through `chrome.path` as for other Community visualizations. Export
waits for tile loading; missing tiles do not silently produce a successful blank
map. Runtime network access to the tile provider is required. The default public
OSM endpoint is for light interactive use; follow its tile policy and use an
appropriate provider for sustained traffic. JVM option
`-Dopenjrs.map.tile.url=https://your-provider/{z}/{x}/{y}.png` selects another tile
endpoint. Preserve the attribution required by the provider and map data.

CI uses deterministic local tiles to test the renderer, coordinates, pan/zoom,
table drilldown and image exports without crawling a public map service. It does
not verify availability of the public OSM service from each deployment network.
Leaflet's provenance/license and OSM policy links are packaged in
`org/openjrs/maps/NOTICE.md` in the Community visualization module.

## Vendor heartbeat removal — BREAKING CHANGE

OpenJRS removes the Jaspersoft heartbeat subsystem: outbound HTTP client,
system/database/browser collectors, weekly Quartz job, opt-in dialog, and the
`/heartbeat.html` and `/heartbeatInfo.html` handlers. These handlers now return
HTTP 404. Extensions that imported the removed `war.common.Heartbeat*` classes
or injected `concreteHeartbeatBean` must remove that integration.

Legacy `heartbeat.*` and `jrs.config.enable.heartbeat*` settings no longer enable
collection or transmission. Previously persisted consent/cache files are not
read. No repository data or encryption keys need to be deleted for this change;
rebuild and recreate the application container to replace an existing image.

The WAR verification checks the packaged backend and frontend for the removed
client and wiring. Browser tests check that the dialog/configuration is absent,
the former handlers return 404, and no heartbeat browser requests occur during
login and report viewing. This change addresses the vendor heartbeat, not a
general audit of every possible external connection or third-party extension.

Upstream copyright and license notices remain in place.

## Build and dependency integrity

The historical JR 6.20.3 baseline and the JR 7.1.0 migration are integrated into
`master`; see [integration history and release checks](MERGING.md). The current
build still uses Java 8, Maven/Ant, Node.js 14, Yarn Classic, Tomcat 9 and
PostgreSQL 14. Use the [Docker guide](docker/local/README.md) for the tested local
setup and the [backend guide](jasperserver/README.md) for inherited manual tasks.

The frontend still depends on archived source snapshots of former private Git
packages. From the repository root, the bootstrap verifies and prepares them:

```bash
python3 scripts/prepare-legacy-frontend.py --check
python3 scripts/prepare-legacy-frontend.py --prepare
```

Build with the pinned Node/Yarn toolchain and publish the UI overlay before the
backend build, as shown in the Dockerfile and CI workflows. Restore the temporary
manifest rewrites with `python3 scripts/prepare-legacy-frontend.py --restore`;
do not commit rewritten manifests or generated dependencies. These archives are
not a complete offline installation: public npm/Maven dependencies are still
required. The bootstrap rejects archive links and path traversal and preserves
original manifests for restoration.

Both workflows exercise the combined engine and interface. Diagnostic CI accepts
only the enumerated dependency coordinates and hashes after the original strict
Buildomatic inventory reports its expected differences. This does not approve
the SiteMesh discrepancy or other dependency changes for production. Production
release review must also cover licensing, legacy dependency security and broader
functional regression testing.

## MongoDB connector removal (JasperReports 7 migration)

The legacy Jaspersoft MongoDB adapter (`com.jaspersoft.connectors.mongodb:js-mongodb-datasource:3.10.2`) is **not included** in this OpenJRS migration. Jaspersoft clarified in [jasperreports#578](https://github.com/Jaspersoft/jasperreports/issues/578) that the MongoDB connector is no longer included with the Community Edition from version 7 onward. The legacy connector is incompatible with the moved JasperReports 7 adapter APIs.

OpenJRS removes the proprietary adapter dependency, its connector module, example classes, Spring bean registrations, and the temporary compatibility shims. **Do not assume that existing MongoDB-backed reports will work after upgrading.** MongoDB JDBC-related source code and configuration are separate from this adapter and remain for independent review; a driver might still be required for that path. The original baseline is preserved on its own branch.

The historical WAR checksum inventory and diagnostic exception must be revisited deliberately after this dependency removal; a changed WAR inventory is not automatically a successful or production-approved build.

## JasperReports 7 interactive web API breaking change

The JasperReports 7.1.0 library removed several types used directly by the 8.2.0 server web UI, including `net.sf.jasperreports.web.WebReportContext`, `net.sf.jasperreports.web.actions.Action` / `AbstractAction` / `MultiAction`, `net.sf.jasperreports.web.commands.CommandStack`, and legacy web-resource handling APIs. These types are not present in the JasperReports 7.1.0 source tree; adding another normal JasperReports module does not restore the old interactive viewer protocol.

The migration therefore requires an explicit plan to port or replace the interactive report context/actions/commands/asset-serving integration, including compatibility tests for drill-down, interactive actions, pagination and viewer rendering. This work must not be bypassed by stubbing out actions or deleting core interactive features. The JSON exporter and Jackson helper were moved to `jasperreports-json` and `net.sf.jasperreports.jackson.util`; their dependency/import corrections are handled separately.

The migration branch now retains the Community viewer compatibility classes and
registers their action subtypes through JR7's
`JacksonMappingExtensionsRegistryFactory`. This restores `saveZoom`, search,
save and undo/redo deserialization. Image/font/default web-resource handlers
are registered separately. The action resource bundle is recovered from the
LGPL JasperReports 6.21.5 source archive; existing copyright notices remain intact.

The table/crosstab action implementations, JSON metadata handlers and table
filter/style contributors are also ported from the LGPL Community source.
See `jasperserver-war-jar/README-viewer-compatibility.md` for provenance and the
JR7 API changes, including its concrete crosstab order-model contract.

`ViewerActionMappingsTest` checks the engine mapper and resource registration.
`scripts/test-legacy-viewer.py` exercises UI authentication, HTML rendering,
pagination, zoom, highlighted search matches, a real reference hyperlink and
a Community custom visualization using a controlled AMD script fixture.
It also sorts and filters an interactive table and validates undo/redo through
the actual browser controls. `InteractiveTableActionsTest` executes actions
against the design cache and checks the filled output, resizing validation
and the JR7 crosstab order-model loading contract.
The browser test requires Playwright and a sandbox-capable Chrome executable;
it creates and deletes uniquely named reports in the disposable CI repository.
It does not establish full coverage of formatting, save, crosstab interactions,
drill-down or every report/component type. A replacement viewer belongs in a
separate PR; the current migration preserves this viewer.

Native sample sources and the Community visualization port have their own
scope and limitations documented in `jasperserver/samples/jrxml7/README.md` and
`jasperserver/jasperserver-custom-visualization/README.md`. Passing the diagnostic CI does
not approve the unresolved production WAR inventory differences.
