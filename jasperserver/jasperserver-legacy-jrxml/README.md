# Legacy report XML on JasperReports 7

JR7's official release notes state that JR6 JRXML/JRTX and compiled `.jasper`
objects are incompatible with the new engine. This module restores reading
supported legacy XML through the public `ReportLoader` extension point. It
converts the XML tree in memory and delegates to JR7's official
`JacksonReportLoader`; report resources in the repository stay unchanged.

The conversion retains expressions, parameters, queries, fields, datasets,
styles, fonts, hyperlinks, band/element geometry, subreports and table/list
components. It translates structural wrappers and renamed attributes, including
`hyperlinkType` -> `linkType`, and emits the component `kind` attribute first as
JR7's writer does. This avoids a Jackson XML 2.13 buffering issue with unwrapped
collections. Custom Visualization still requires its own registered component
implementation if that optional feature is enabled.

This is not a promise that every historical extension or report is supported.
Unknown namespaces and unsupported properties fail conversion; elements are not
removed to make loading succeed. In particular, chart-specific conversion and
old serialized `.jasper` objects are not implemented here. They need explicit
migration; Jaspersoft Studio 7 can convert older source formats. Native JR7 XML
continues through the standard loader.

The XML parser prohibits DOCTYPE declarations and external entities, and disables
validation and XInclude. It uses security features supported by the WAR's
`org.codelibs.xerces:xercesImpl:2.12.1-sp1`; that parser rejects the newer
`ACCESS_EXTERNAL_*` JAXP attributes. Its exact implementation is on the test
classpath too. The adapter does not load a JR6 engine or fetch schemas.

From `jasperserver/`, with Java 8 and the normal Maven repository settings:

```sh
mvn -B -DbuildArtifactVersionName=ci -pl jasperserver-legacy-jrxml -am test
```

The regression tests read and compile both actual CustomersReport definitions
from the unchanged PostgreSQL CE sample catalog, assert fields, subreport
parameters and all six table columns, and exercise legacy text, styling,
HTML/CSV exports, hyperlinks, native JR7 delegation, unsupported content and
external-entity rejection. Server REST and browser checks remain separate.

Official references:

- [JR7 source-format compatibility notes](https://github.com/Jaspersoft/jasperreports/blob/7.1.0/changes.txt)
- [ReportLoader contract](https://github.com/Jaspersoft/jasperreports/blob/7.1.0/core/src/main/java/net/sf/jasperreports/engine/xml/ReportLoader.java)
- [Native JR7 examples](https://github.com/Jaspersoft/jasperreports/tree/7.1.0/demo/samples)

## Compiling inside Tomcat

The engine now includes the official `jasperreports-jdt:7.1.0` module. Without
that extracted module, JR7 falls back to an external `javac` whose JVM classpath
does not contain Tomcat's webapp libraries. The module brings the original
`ecj:3.21.0` back, with the exact hash already in the unchanged production
inventory. A regression test compiles through an isolated webapp-style class
loader with an empty process classpath, instead of relying on the test runner's
classpath to make compilation pass.

The server XPath factory setting is updated to
`net.sf.jasperreports.jaxen.util.xml.JaxenXPathExecuterFactory`, matching the
JR7 Jaxen module. A real XML query/fill test reads that setting from the server
configuration, rather than replacing the query executor with a test double.
