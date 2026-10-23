# OpenJRS Server — integration and release checks

## Integrated changes

The following PRs are merged and included in `master`:

| PR | Scope |
| --- | --- |
| [#1](https://github.com/rftd/jasperreports-server-ce/pull/1) | Historical Java 8 / JR 6.20.3 build and runtime baseline |
| [#3](https://github.com/rftd/jasperreports-server-ce/pull/3) | OpenJRS documentation and public UI identity |
| [#2](https://github.com/rftd/jasperreports-server-ce/pull/2) | JR 7.1.0 engine and Community viewer/component integration |
| [#4](https://github.com/rftd/jasperreports-server-ce/pull/4) | Local Docker Compose environment |
| [#5](https://github.com/rftd/jasperreports-server-ce/pull/5) | Removal of the Jaspersoft vendor heartbeat |
| [#6](https://github.com/rftd/jasperreports-server-ce/pull/6) | Native JR7 Community reports, removal of the JR6 adapter, Table/Map fixes and OpenStreetMap |

PRs #6, #5 and #4 were integrated in that order through their stacked bases.
The resulting `master` commit
[`927e53a5`](https://github.com/rftd/jasperreports-server-ce/commit/927e53a57759da1cba43b22dba7338028eeadd9f)
has the same source tree as the tested PR #6 head. The historical JR 6.20.3
baseline remains available on `feature/jasperreports-7-migration`.

The combined tree passed [Legacy full build](https://github.com/rftd/jasperreports-server-ce/actions/runs/40007634169)
and [Local Docker environment](https://github.com/rftd/jasperreports-server-ce/actions/runs/40007633970).
Coverage includes frontend/WAR, authentication, REST PDF/XLSX, branding, viewer
pagination/zoom/search/hyperlinks, interactive table sort/filter/undo/redo,
native employee/Foodmart MDX reports, Table/Map drilldown, map image exports,
heartbeat removal, persistence/key protection and sandboxed Chromium visualizations.
The local Docker installation was also confirmed functional. This does not
establish full coverage of formatting, crosstab interaction, scheduling or all
user-authored report/component types. JR6 sources and compiled `.jasper` files
require explicit migration; PDF accessibility remains deferred.

## Future changes and release checks

Prepare new changes on branches based on `master`, with reviewable scope and
appropriate checks. Keep PRs in Draft until integration is authorized; passing
CI alone does not authorize a merge or a production release. See
[MIGRATION.md](MIGRATION.md) for report-format changes and existing-install updates.

Before a production release, resolve the SiteMesh provenance/integrity
difference and review all enumerated dependency changes. The production
Buildomatic inventory stays strict; the CI diagnostic allowance is not
production approval. Preserve upstream copyright, license and third-party
notices, and review the independent project name before a public release.

Keep `/jasperserver`, Maven/Java package identities, database names and the
Java 8 / Node 14 / Tomcat 9 / PostgreSQL 14 toolchain stable in unrelated changes.
A new viewer, frontend framework or build system belongs in separate PRs.
