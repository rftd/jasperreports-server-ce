# Native JasperReports 7 Community samples

All seven JRXML definitions in the PostgreSQL/MySQL CE catalogs now use the
native JasperReports **7.1.0** format and the official Jackson loader:

| Source | Report resource |
| --- | --- |
| `analysis/Employees.jrxml` | `/analysis/reports/Employees` |
| `analysis/EmployeeAccounts.jrxml` | `/analysis/reports/EmployeeAccounts` |
| `analysis/FoodmartSalesMondrianReport.jrxml` | `/analysis/reports/FoodmartSalesMondrianReport` |
| `interactive/CustomersReport.jrxml` | `/reports/interactive/CustomersReport` |
| `interactive/OrdersReport.jrxml` | CustomersReport's OrdersReport subreport |
| `interactive/TableReport.jrxml` | `/reports/interactive/TableReport` |
| `interactive/MapReport.jrxml` | `/reports/interactive/MapReport` |

Definitions were written using the official JR7 `JRXmlWriter`. Original source
copyright/license comments are retained. Queries, styles, table columns,
subreport parameters, repository resources and employee/city drilldown remain.
MapReport directly embeds the Community visualization with Leaflet/OpenStreetMap;
it needs no automatic Google map adapter or Google API key.

**BREAKING CHANGE:** the JR6 XML adapter has been removed from the reactor and
WAR. Users must migrate JR6 JRXML/JRTX with Jaspersoft Studio 7 or later and
recompile old `.jasper` files with JR7. An image rebuild preserves existing
repository files; it does not migrate them. The viewer and Community visualization
component remain available independently of report source conversion.

**BREAKING CHANGE — PDF accessibility:** Employees, EmployeeAccounts and the
Foodmart MDX sample explicitly set `net.sf.jasperreports.export.pdf.tagged=false`.
They produce ordinary PDF because JR7 Community's default tagger rejects tagged
export. There is no global silent tagger override. Accessibility support is
deferred; these examples make no tagged PDF or PDF/UA compliance claim.

Standalone sample and test-fixture JRXML sources are also native. The auxiliary
ChartPart book example uses a Community pie chart to count customers by country
instead of its former commercial Highcharts component. Specialized examples
retain their plugin requirements: HQL and WebScraper need their real query
executers installed, and Groovy needs its JR7 compiler module. Source-format
migration does not supply missing optional integrations. Historical Pro catalogs
are outside the Community bootstrap and this conversion.

As specified by JR7, former 3D chart types render in 2D with the newer JFreeChart.

After editing canonical sources, synchronize and verify the CE catalogs:

```sh
python3 scripts/sync-jr7-samples.py --write
python3 scripts/sync-jr7-samples.py
python3 scripts/verify-native-reports.py
```

The synchronizer preserves other catalog entries and their metadata. CI checks
native loading/compilation, original report semantics, deployed browser navigation
and exports. The WAR check additionally rejects any packaged copy of the removed
adapter. The manifest records original JR6 hashes for existing-install updates.

## Existing local repository

With the server running and an updated checkout, preview the sample migration:

```sh
python3 scripts/update-native-samples.py
```

The script prompts for the OpenJRS password (`jasperadmin` is the default user).
It only updates the seven known original JRXML resources, skips already migrated
files and refuses customized/unknown content before performing any update.
Apply with backups:

```sh
python3 scripts/update-native-samples.py --apply
```

Original JRXML and resource descriptors are saved under
`openjrs-sample-backups/<timestamp>/`. Every original is backed up before the first
write. REST resource versions detect concurrent edits. The updates are not one
database transaction; a failure stops further writes, and rerunning skips files
already migrated. Data sources, input controls, permissions and other reports are
preserved. Use `--base-url` and `--user` for another endpoint/account; automation
may supply `JRS_PASSWORD` without a command-line password argument.

Update these resources before deploying without the adapter when possible.
No database or encryption-key reset is required. User-authored JR6 reports need
their own migration; this updater deliberately refuses to rewrite them.
