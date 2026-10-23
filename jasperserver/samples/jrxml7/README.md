# Native JasperReports 7 samples

`analysis/Employees.jrxml` and `analysis/EmployeeAccounts.jrxml` are the canonical
JR7 definitions of the bundled employee reports. They were migrated from the CE
catalog using the official JasperReports **7.1.0** `JRXmlWriter`; the original
copyright and AGPL notices are retained. Image alignment uses `hImageAlign`, and
line spacing belongs to the paragraph. Queries, fields, styles,
repository logo and the EmployeeID drilldown are preserved.

**BREAKING CHANGE — PDF accessibility:** only these two examples explicitly set
`net.sf.jasperreports.export.pdf.tagged=false`. They produce ordinary PDF because
JR7 Community's default tagger rejects tagged export. Accessibility support is
deferred; these exports do not claim tagged PDF or PDF/UA compliance. Structural
tag properties remain available in the JRXML for future work, but do not generate
PDF tags with tagging disabled. There is no global silent tagger configuration;
other reports retain their own settings.

The official Jackson loader loads these files directly. They do not depend on
the legacy report loader. Other samples and users' JR6 reports still use the
compatibility module until migrated and tested separately.

After editing the sources, synchronize the PostgreSQL/MySQL CE bootstrap
catalogs from the repository root:

```sh
python3 scripts/sync-jr7-samples.py --write
python3 scripts/sync-jr7-samples.py
```

The synchronizer preserves every other catalog entry and its metadata. CI checks
the catalogs and tests native loading/compilation, browser drilldown and PDF/XLSX
exports. The manifest records the original JR6 file hashes for existing installs.

## Existing local repository

Rebuilding the image preserves the database; it does not overwrite stored sample
reports. With the updated checkout and the server running, preview the migration:

```sh
python3 scripts/update-native-samples.py
```

The script prompts for the OpenJRS password (`jasperadmin` is the default user).
It updates only the two known original JRXML files. An already migrated file is
left alone, and a customized or unknown file stops the operation before updates.
To apply the migration with backups:

```sh
python3 scripts/update-native-samples.py --apply
```

Original JRXML and resource descriptors are saved under
`openjrs-sample-backups/<timestamp>/`. The REST update supplies each existing
resource version to detect concurrent edits. A failure stops further updates;
the pair is not a single database transaction. Rerunning skips files already
migrated. Data sources, input controls, permissions and other reports are not
replaced. Use `--base-url` and `--user` for a different local endpoint/account;
automation can supply `JRS_PASSWORD` without a command-line password argument.

Reload `/analysis/reports/Employees`, then follow an Accounts link to verify
EmployeeAccounts. No database or encryption-key volume reset is required.
