# Community custom visualizations on JasperReports 7

This module ports the LGPL Community visualization extension to the JasperReports
7 component contracts. It replaces `jasperreports-custom-visualization:6.21.5`;
that binary initializes a Digester extension referring to `XmlDigesterConfigurer`,
which JasperReports 7 removed. Global extension discovery makes the failure affect
ordinary reports as well as reports containing custom components.

The registry uses `DefaultComponentsBundle` indexed by component Java type, and
JRXML serialization uses Jackson subtype mappings and annotations. Digester
factories and the old XML writer are replaced by those JR7 contracts. There are
no placeholder JR6 engine classes or second reporting engine in this module.

The component compiler, dataset evaluation, processors, HTML/JSON templates,
RequireJS resources and all eleven export handlers are retained. PDF, XLS and
JSON handlers use their JR7 packages. The LGPL Chrome renderer from JR6 is
included because JR7 Community no longer ships that integration. Server-side
visualizations still require a configured Chrome/Chromium executable and
`net.sf.jasperreports.chrome.enabled=true`. HTML rendering uses the original
AMD component scripts. No proprietary Custom Visualization or MongoDB artifact
is introduced.

The WAR provides runtime dependencies; this module declares them as `provided`
to avoid changing unrelated dependency versions. Run the regression suite with
Java 8 from `jasperserver/`, using the same Maven repository settings as the
server build:

```sh
mvn -B -DbuildArtifactVersionName=ci \
  -Dji-repo-releases=https://jaspersoft.jfrog.io/jaspersoft/third-party-ce-artifacts \
  -Dji-repo-snapshots=https://jaspersoft.jfrog.io/jaspersoft/jr-ce-snapshots \
  -pl jasperserver-custom-visualization -am test
```

The tests cover extension discovery, every registered handler, Jackson round
tripping and filling a custom component, and ordinary PDF/XLSX/HTML/CSV exports.
They do not replace REST exports against the deployed server or interactive
browser tests. Historical JR6 XML syntax and compiled `.jasper` objects are not
made compatible by this module; report-format conversion is a separate JR7
migration concern.

`CVBrowserIT` is an additional required CI check, explicitly selected with
`-Dtest=CVBrowserIT -Dopenjrs.chrome.path=/path/to/google-chrome`. It runs an AMD
component script in Chrome, verifies the rendered SVG and exports that component
to PDF. The Chrome integration creates tabs with the CDP Target API, since the
old client's GET `/json/new` is rejected by current Chrome. Native WebSocket
connections omit Tyrus's synthetic browser Origin header; Chrome's origin
policy and sandbox remain enabled. Browser policy must permit the renderer's
temporary local HTML/script files.

JR7 also filters Java deserialization when the server reloads compiled reports
from its cache. The module registers only `CVDesignComponent` through the
official `StandardDeserializationClassWhitelistExtension`. The filter stays
enabled. A regression test reloads compiled/filled reports and verifies that
an unrelated serialized class is still rejected. The viewer browser check
additionally displays a custom SVG using a controlled AMD script fixture.

## Source provenance and license

Copyright and LGPL notices in recovered files are preserved. See `LICENSE`.
OpenJRS adaptations (2026): JR7 component registry, Jackson mappings, exporter
package migrations, `JRPrintElement.getMode()`, and module/test integration.

Sources were obtained from the published Community source archives on Maven
Central (not the proprietary `customvisualization` artifact):

| Archive | SHA-256 |
| --- | --- |
| `net.sf.jasperreports:jasperreports-custom-visualization:6.21.5:sources` | `bc6633c76c7fc61d0d5ce9e57b28badab25a290750c15c434a8daacaead12ea2` |
| `net.sf.jasperreports:jasperreports:6.21.5:sources` (Chrome integration) | `d13d73301e83f3b9bf7c3e9d7436c13e0242e90513c817f0f1773db6e7b54cba` |

The template utility is adapted from the LGPL `VelocityUtil` already recovered
in `jasperserver-war-jar`, in a component-specific package to avoid a dependency
cycle or duplicate classes. The JR7 contracts were checked against the official
[JasperReports 7.1.0 sources](https://github.com/Jaspersoft/jasperreports/tree/7.1.0),
especially `DefaultComponentsBundle`, `Component`, `ListComponent` and
`JacksonMappingExtensionsRegistryFactory`.

## PDF dependency alignment

JR 7.1.0's official `openpdf.classic.version` is `1.3.43.jaspersoft.2`.
The server's old `1.3.30.jaspersoft.2` lacks `BaseFont.setIncludeCidSet(boolean)`;
the PDF regression test reproduces that binary incompatibility. The parent POM
now uses the exact official JR7 dependency. Its Jaspersoft repository checksum
was verified before updating the diagnostic inventory allowance:

- SHA-1: `2583544F268D26889F2A5E3DA40F2AC5900B5602`
- SHA-256: `c8f2b3262b8128029f94fe9a789486bd252eddc06d475198c90583d94e7b7909`
- Source: `https://jaspersoft.jfrog.io/jaspersoft/third-party-ce-artifacts/com/github/librepdf/openpdf/1.3.43.jaspersoft.2/`

The production Buildomatic master inventory remains unchanged and strict. The
CI diagnostic gate permits only this exact replacement, in addition to the
previously documented differences; the historical SiteMesh provenance issue
remains unresolved.
