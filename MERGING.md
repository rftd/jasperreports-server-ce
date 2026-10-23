# OpenJRS Server — PR integration and release checks

The baseline, engine migration and branding are independent review scopes.
Keep a PR in Draft until its maintainers authorize integration; CI success
does not itself authorize a merge or a production release.

| PR | Base | Scope | Integration condition |
| --- | --- | --- | --- |
| [#1](https://github.com/rftd/jasperreports-server-ce/pull/1) | `master` | Recover the historical Java 8 / JR 6.20.3 build and runtime baseline | Review the baseline and its diagnostic integrity exceptions |
| [#3](https://github.com/rftd/jasperreports-server-ce/pull/3) | `master` | OpenJRS documentation and public UI identity | Review branding, notices and locale/browser checks against the baseline |
| [#2](https://github.com/rftd/jasperreports-server-ce/pull/2) | `master` | JR 7.1.0 engine, exports and Community viewer/component compatibility | Revalidate the combined tree and keep unresolved compatibility/release gates explicit |

The integration order is #1, #3, then #2. After the baseline reaches `master`,
target the remaining PRs at `master` and verify that their diffs contain only
their intended changes. Update each remaining branch with the integrated base
and rerun its checks before merging it. This validates the engine together with
the OpenJRS interface. Preserve the original branches for comparison; do not
copy engine changes into the branding PR just to combine the branches.

The engine branch's [validated CI run](https://github.com/rftd/jasperreports-server-ce/actions/runs/39871809564)
covers REST PDF/XLSX, login, HTML/pagination/zoom/search/hyperlinks, table
sort/filter/undo/redo and Community visualization rendering. This does not
establish full coverage of formatting, crosstab interaction, drill-down,
scheduling, historical JRXML structures or compiled `.jasper` compatibility.

Before a production release, resolve the SiteMesh provenance/integrity
difference and review all enumerated dependency changes. The production
Buildomatic inventory stays strict; the CI diagnostic allowance is not
production approval. Preserve upstream copyright, license and third-party
notices, and review the independent project name before a public release.

Rebranding does not change `/jasperserver`, Maven/Java package identities,
database names, report formats, authentication, or the platform toolchain.
A new viewer, frontend framework or build system belongs in later PRs.
