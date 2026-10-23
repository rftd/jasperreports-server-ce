# OpenJRS Server — branding and attribution

## Project identity

- **Public project name:** OpenJRS Server
- **Short name:** OpenJRS
- **Description:** Independent community continuation and modernization of JasperReports Server Community Edition.
- **Origin:** Fork based on JasperReports Server Community Edition (JRS CE) source code.
- **Maintainers:** Independent community contributors; not the original vendor.

## Independence and trademark notice

OpenJRS Server is **not affiliated with, sponsored by, endorsed by, or supported by** Jaspersoft, Cloud Software Group, or other original-project rights holders. The JasperReports/Jaspersoft names are used for historical attribution and identification only. Trademarks and brand assets remain the property of their owners.

The name **OpenJRS Server** is a proposed independent identity for this fork; availability and trademark clearance have **not** been formally confirmed. Do not claim to provide an official replacement, official vendor support, or vendor certification.

## Scope of this phase

This rebranding changes documentation and public interface identity: login,
navigation/header, browser titles, the footer About link, the About dialog and
community links. The default UI uses a text wordmark and an original typographic
favicon. Vendor artwork is retained as inherited files and is not repurposed as
the OpenJRS identity. Core identity strings are updated in all eight existing
locales; additional project descriptions are provided in English and Portuguese.

Package coordinates, copyright headers, dependency identities, WAR file name,
servlet context path, database objects, REST endpoints and report formats retain
their existing identifiers. Renaming those internals requires a separate reviewed
migration. See [MERGING.md](MERGING.md) for integration and release constraints.

## Preservation of upstream notices

Keep existing copyright notices, licenses, attribution and third-party notices on inherited code. Do not rewrite those notices to suggest that OpenJRS authors created the original source or that the original vendor supports the fork.

## Transparent AI-assisted work

Documentation and code changes may be proposed or prepared with AI assistance. Contributors remain responsible for reviewing changes, checking licenses and dependencies, reproducing reported bugs, and running applicable tests. Automated CI success is necessary but not sufficient proof of security or production suitability.
