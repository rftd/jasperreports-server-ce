# OpenJRS Server — integrating the current PRs

The baseline, engine migration and branding are independent review scopes.
The current PRs remain Draft until their maintainers approve them; CI success
does not itself authorize a merge or a production release.

| PR | Base | Scope | Integration condition |
| --- | --- | --- | --- |
| [#1](https://github.com/rftd/jasperreports-server-ce/pull/1) | `master` | Recover the historical Java 8 / JR 6.20.3 build and runtime baseline | Review the baseline and its diagnostic integrity exceptions |
| [#3](https://github.com/rftd/jasperreports-server-ce/pull/3) | `feature/jasperreports-7-migration` | OpenJRS documentation and public UI identity | Review branding, notices and locale/browser checks against the baseline |
| [#2](https://github.com/rftd/jasperreports-server-ce/pull/2) | `feature/jasperreports-7-migration` | JR 7.1.0 engine, exports and Community viewer/component compatibility | Complete compatibility review and keep unresolved release gates explicit |

Integrate the reviewed baseline first. After #1 reaches `master`, retarget #3
and #2 to `master` and verify that their diffs contain only their intended
changes. Branding can then be integrated independently of the experimental
engine upgrade. Update the remaining PR with the resulting base and rerun its
checks before merging it. Do not copy engine changes into the branding PR just
to combine the branches.

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
