# OpenJRS Server — maintenance and AI use

OpenJRS Server is an **independent, best-effort community fork**, not a vendor-supported distribution of JasperReports Server CE.

## How AI is used

Artificial intelligence tools may assist with:
- Understanding inherited source code and legacy build failures.
- Proposing dependency updates and Java API migrations.
- Preparing code changes, documentation, and CI scripts.
- Reviewing logs and helping draft regression tests.

AI-generated changes can be wrong. **A change should not be considered validated just because an AI suggested it or a build passed.**

## Contribution expectations

Changes should remain reviewable and traceable to the problem they address. Contributors are encouraged to:
1. Link the relevant issue or explain the motivation.
2. Preserve upstream and third-party license, attribution and copyright notices.
3. Identify compatibility or data-migration risks, especially for reports and stored repository resources.
4. Run appropriate compilation, integration and report-export checks and document results.
5. Avoid asserting that security, performance, or functionality was verified unless tests actually cover it.
6. State material use of AI assistance when it helps reviewers understand how changes were produced.

Maintainers and reviewers remain responsible for deciding which changes are accepted. There is no guarantee of continuous human review, support response times, security fixes or compatibility with upstream releases.

## Branching and release safety

The active `master` branch uses JasperReports 7.1.0 and native JR7 reports. The historical JR 6.20.3 baseline remains on `feature/jasperreports-7-migration` for comparison. Prepare new changes from `master`, keep unrelated migrations separate and preserve compatibility-sensitive internal identifiers. See [integration history and release checks](MERGING.md).

Before a production-oriented release, resolve known build integrity exceptions, verify third-party license obligations, assess security exposure of legacy dependencies, and run functional regression testing beyond the current CI smoke suite.

## Contact

Use repository issues and pull requests for OpenJRS-specific support and discussion; do not direct users to the original vendor for fork-specific assistance.
