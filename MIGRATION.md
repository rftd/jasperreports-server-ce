# OpenJRS Server — JasperReports Server CE migration baseline

## Table Report and OpenStreetMap example

The shipped legacy Table Report now converts hyperlink parameter expressions
and deprecated stretch aliases using JR7's corresponding contracts. Its city
drilldown points to `/reports/interactive/MapReport` and retains coordinates.

Basic legacy map components are rendered through the Community visualization
integration using bundled Leaflet 1.9.4 and OpenStreetMap tiles. Google Maps and
its API key are no longer used for these maps. The shipped Map Report keeps its
default center and accepts latitude, longitude and numeric zoom parameters;
missing/blank request parameters use its defaults. Old repository JRXML is
adapted on load, so existing local volumes need not be reset or reimported.

**Compatibility change:** this adapter covers latitude/longitude/zoom maps.
Advanced Google map features (markers/datasets, routes, custom styles, and map
attributes) require their own migration. Unsupported content fails explicitly
instead of being discarded. This does not establish complete JR6 map compatibility.

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

> Historical build-recovery notes. See the [project README](README.md#current-status) for current branch status and verified tests. The JasperReports 7.1.0 migration is maintained separately in [PR #2](https://github.com/rftd/jasperreports-server-ce/pull/2).

This branch starts with **build recovery**, not a JasperReports 7 runtime upgrade. The current application and its saved reports must remain unchanged until the original build is reproducible.

## Baseline observed in the source

- Server parent: `jasperserver/pom.xml` (24 Maven modules); Java compiler targets 8.
- JasperReports engine: 6.20.3; Spring 5.3.26; Spring Security 5.7.2; Hibernate 5.6.11.Final.
- Web UI: `jasperserver-ui/ce`, Lerna 3 / Yarn Classic, with archived Git dependencies.
- Original build flow requires publishing the web UI overlay into the local Maven repository before `buildomatic/js-ant build-all-ce`.
- `jasperserver/settings.xml` configures an HTTP Maven mirror, which needs separate verification and remediation.

## Restore historical private frontend dependencies

The UI manifests reference `git@git.jaspersoft.com` (no longer assumed accessible). The source tree also has ten corresponding archived source snapshots in `jasperserver-ui/ce/yarn-offline-mirror`. The bootstrap below validates and extracts those snapshots locally, then temporarily rewrites only the matching `package.json` references to local `file:` paths.

From the **repository root**:

```bash
python3 scripts/prepare-legacy-frontend.py --check
python3 scripts/prepare-legacy-frontend.py --prepare
cd jasperserver-ui/ce
yarn install --no-lockfile
# Once dependencies resolve: yarn run clean && yarn run build
cd ../..
python3 scripts/prepare-legacy-frontend.py --restore
```

**Important limitations:**

- The archived snapshots cover the historical private Git dependencies only. Other packages still require access to public npm registries or an existing Yarn cache; this is **not a complete offline installation**.
- Use Python 3.9+ and a legacy-compatible Node/Yarn toolchain (the original UI README identifies Node 14 and Yarn Classic 1.22). This is for build recovery only; do not deploy an unsupported Node version to production.
- `--no-lockfile` avoids changing the original Yarn lockfile during this diagnostic run. Do not commit generated lockfiles or temporarily rewritten manifests.
- `--restore` restores byte-for-byte manifest backups; the generated `.legacy-private-deps` directory is ignored by Git.
- The archive verifier rejects links and path traversal. Review the vendor packages and their licenses before redistribution. The script does not imply that they are security-patched.
- If the bootstrap fails, stop and record the error; do not proceed to JasperReports 7 or Jakarta EE migration yet.

## Build gates for the next phases

1. Verify the historical frontend install and compile (and publish its `jrs-ui` overlay).
2. Resolve the legacy Maven artifact repositories and compile all original server modules without changing engine versions.
3. Provision a disposable PostgreSQL/Tomcat 9 environment and run smoke tests for authentication, repository navigation, PDF/XLSX exports, parameters, schedules, REST endpoints, and security.
4. Establish tests and a compatibility matrix for JasperReports 6 → 7 report compilation, import/export and persistence.
5. Only then move JasperReports and platform upgrades in separate reviewable changes.

**No build, deployment or functional smoke test has been executed by this initial preflight patch.**

## MongoDB connector removal (JasperReports 7 migration)

The legacy Jaspersoft MongoDB adapter (`com.jaspersoft.connectors.mongodb:js-mongodb-datasource:3.10.2`) is **not included** in this OpenJRS migration. Jaspersoft clarified in [jasperreports#578](https://github.com/Jaspersoft/jasperreports/issues/578) that the MongoDB connector is no longer included with the Community Edition from version 7 onward. The legacy connector is incompatible with the moved JasperReports 7 adapter APIs.

The experimental branch removes the proprietary adapter dependency, its connector module, example classes, Spring bean registrations, and the temporary compatibility shims. **Do not assume that existing MongoDB-backed reports will work after upgrading.** MongoDB JDBC-related source code and configuration are separate from this adapter and remain for independent review; a driver might still be required for that path. The original baseline is preserved on its own branch.

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

The JR7 legacy XML loader and Community visualization port have their own
scope and limitations documented in `jasperserver-legacy-jrxml/README.md` and
`jasperserver-custom-visualization/README.md`. Passing the diagnostic CI does
not approve the unresolved production WAR inventory differences.
