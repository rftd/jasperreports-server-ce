# OpenJRS Server

**An independent, community-maintained continuation of JasperReports Server Community Edition.**

[Leia em português](README.pt-BR.md) · [Build & setup](jasperserver/README.md) · [Migration status](MIGRATION.md) · [Maintenance policy](MAINTENANCE.md)

## About this project

**OpenJRS Server** is an independent fork and modernization effort based on the source code of **JasperReports Server Community Edition (JRS CE)**. Its purpose is to preserve the community edition, recover a reproducible build, and gradually update its dependencies and reporting engine.

The OpenJRS name identifies **this fork**, not an official release of the upstream product. The original project's names, credits, copyrights, and third-party notices remain relevant to the inherited source code.

### Independent project — no upstream support

**OpenJRS Server is not affiliated with, endorsed by, sponsored by, or supported by Jaspersoft, Cloud Software Group, or the original project's other rights holders.** Issues and changes in this fork must be handled by its independent maintainers and contributors. Do not contact the original vendor expecting support for OpenJRS Server.

The terms *JasperReports* and *Jaspersoft* are used only to identify the project's origin and compatibility. Trademarks belong to their respective owners.

## Current status

This is a **work-in-progress modernization fork**, not a certified production-ready replacement.

| Area | Status |
| --- | --- |
| Historical source baseline | JasperReports Server CE 8.2.0 / JasperReports Library 6.20.3 |
| Legacy frontend build | Verified in CI with Node.js 14 and Yarn Classic |
| Legacy backend build | Verified in CI with Java 8, Maven, and Ant; produces a WAR |
| Runtime smoke test | Verified in CI with PostgreSQL 14 and Tomcat 9 |
| Functional smoke tests | CI verifies REST authentication, PDF/XLSX exports, viewer controls, interactive tables, Community visualizations and the OpenJRS interface |
| Current reporting engine | **JasperReports Library 7.1.0, experimental**; broader compatibility and dependency integrity remain under review |

This development tree includes the baseline recovered in [PR #1](https://github.com/rftd/jasperreports-server-ce/pull/1), the OpenJRS identity from [PR #3](https://github.com/rftd/jasperreports-server-ce/pull/3), and the experimental engine migration from [PR #2](https://github.com/rftd/jasperreports-server-ce/pull/2). Integration does not constitute a production release. The original JR 6.20.3 baseline remains available on `feature/jasperreports-7-migration`.

### AI-assisted maintenance

**Artificial intelligence is used to assist maintenance and modernization**, including source analysis, documentation, migration planning, and preparation of code changes. AI-generated suggestions can be incorrect or introduce regressions. The project aims to use human oversight, review, and automated builds/tests to validate changes; AI involvement is **not** a claim that a change is automatically safe, correct, or fully reviewed.

See [MAINTENANCE.md](MAINTENANCE.md) for how contributions and AI-assisted changes should be handled.

## Build and development

For a local Docker Compose environment, see the [Docker guide](docker/local/README.md).

**BREAKING CHANGE:** report sources and style templates must use native JasperReports
7 XML. The automatic JR6 adapter has been removed. All bundled Community catalog
reports are migrated; existing databases and user reports require explicit migration.
See [the migration procedure](MIGRATION.md#native-jr7-reports--breaking-change).

The Jaspersoft heartbeat has been removed, including vendor data collection,
scheduled transmission and the opt-in dialog. Its internal handlers and classes
are removed; see the [compatibility change](MIGRATION.md#vendor-heartbeat-removal--breaking-change).

The codebase still uses its **original internal directory names, Maven coordinates, Java packages, WAR name, and application context path** (for example, `jasperserver/` and `/jasperserver`). These are kept deliberately for compatibility while the public-facing brand is introduced in documentation, login, navigation and the About dialog. **Do not rename internal identifiers merely to change the public-facing name.**

- [Backend build guide](jasperserver/README.md)
- [Frontend build guide](jasperserver-ui/ce/README.md)
- [Migration notes and constraints](MIGRATION.md)
- [GitHub Actions — legacy full build](.github/workflows/legacy-full-build.yml)

The legacy build toolchain contains obsolete or unsupported components. Its successful CI tests are **not a security audit** or a recommendation to deploy it unmodified on the public internet.

## Licensing and attribution

OpenJRS Server builds on pre-existing code. **Original copyright and license notices must be preserved.** Original source file headers identify the **GNU Affero General Public License, version 3 or later**, unless a separate commercial license applies. Individual bundled third-party components can have other licenses; their notices and redistribution terms must be checked separately.

The independent name **OpenJRS Server does not remove or replace any upstream copyright, license, trademark, or attribution obligations**. This README is not a substitute for the licenses associated with individual files or dependencies.

See [BRANDING.md](BRANDING.md) for identity and attribution conventions, and [MERGING.md](MERGING.md) for the PR integration order and outstanding release gates.

## Contributions and support

Use the repository's [Issues](https://github.com/rftd/jasperreports-server-ce/issues) and pull requests for discussion and contributions. Community maintenance is best-effort; **there is no vendor support, warranty, or guaranteed security-update schedule**.

Please include reproducible steps, relevant logs, and the affected branch/version in bug reports.
