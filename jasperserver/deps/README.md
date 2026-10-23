# WAR dependency integrity

The JR7 inventory is enforced without diagnostic exceptions. A failed
`buildomatic/js-ant build-ce` stops both CI and Docker builds; it cannot be
converted into success by recognizing a checksum-error log message.

## Reviewed artifacts

On 2026-10-23, fresh downloads from Maven Central and Jaspersoft's official
Community repositories were compared with their published SHA-1 files and the
JARs in the tested WAR. SHA-256, file size, POM SHA-256 and source/checksum URLs
are recorded in [`war-deps-provenance.json`](war-deps-provenance.json).

The review covers:

- Eighteen new or changed inventory entries: fourteen version replacements,
  three additions (BeanUtils2, SOAP API and XMP Core), and the SiteMesh checksum
  change described below. Only these independently verified entries were
  promoted; the other 308 inventory entries retain their historical hashes.
- Seventeen retired coordinates: the fourteen replaced versions, `jcommon`,
  redundant `jakarta.xml.bind-api`, and the removed proprietary MongoDB adapter.
  Their old versions/hashes remain recorded for comparison, not acceptance.
- All sixteen official JasperReports 7.1.0 JARs in the WAR, including fonts.
  The inherited Ant scanner excludes `jasperreports-*`; the additional verifier
  now explicitly pins these files rather than relying on that exclusion.

No library versions or application binaries are upgraded by this inventory
promotion. The existing WAR already contains the reviewed bytes.

## SiteMesh republication

Coordinate: `opensymphony:sitemesh:2.2.1-Jaspersoft-1.0.2`.

| Evidence | Value |
| --- | --- |
| Historical SHA-1 | `4F4096D355E9AC51B80882572CD1AAAB65C87AD2` |
| Current official SHA-1 | `BA2777C37B1FAC17A69D8A4B064BE3B8BF7DC954` |
| Current official SHA-256 | `92e5ef821f9ae4f761a0d1972ee26beb82d629ab36e2f882c7a74d67c620af1d` |
| Size | 135,597 bytes |
| Repository creation | 2016-05-05T22:01:14.050Z |
| Repository last modification | 2024-07-25T10:40:07.100Z |

The artifact is in **`jrs-ce-addons-releases`**, not `third-party-ce-artifacts`.
The [official storage metadata](https://jaspersoft.jfrog.io/artifactory/api/storage/jrs-ce-addons-releases/opensymphony/sitemesh/2.2.1-Jaspersoft-1.0.2/sitemesh-2.2.1-Jaspersoft-1.0.2.jar),
[published SHA-1](https://jaspersoft.jfrog.io/jaspersoft/jrs-ce-addons-releases/opensymphony/sitemesh/2.2.1-Jaspersoft-1.0.2/sitemesh-2.2.1-Jaspersoft-1.0.2.jar.sha1)
and [published SHA-256](https://jaspersoft.jfrog.io/jaspersoft/jrs-ce-addons-releases/opensymphony/sitemesh/2.2.1-Jaspersoft-1.0.2/sitemesh-2.2.1-Jaspersoft-1.0.2.jar.sha256)
agree with both the fresh download and tested WAR. The repository records a
republication under the same version after the historical inventory was made.

The manifest identifies Maven 3.8.6 / JDK 17.0.10 as build tools. The 101 Java
classes use class-file major versions 48 (100 classes) and 51 (one class), both
supported by the Java 8 runtime. The published POM repackages the older
`2.2.1-JasperSoft` dependency while replacing `DefaultFactory.class`.

The historical binary was not recovered and the current `-sources.jar` URL
returns 404. This review does **not** claim that only ZIP metadata changed, that
the old/new classes are identical, or that the binary was rebuilt reproducibly
from source. It establishes the provenance and exact bytes of the current
official artifact. Its OpenSymphony redistribution notice remains in
[`TIB_js-jrs-ce_license.txt`](../docs/licenses/TIB_js-jrs-ce_license.txt).

## Build enforcement

The original Ant comparison remains in place and requires all 326 inventory
entries to match. The additional Python guard checks the actual WAR:

- SHA-1 for all 326 inventory libraries; SHA-256 for all 34 reviewed artifacts.
- Exact filenames for those libraries, the sixteen JR7 JARs, and twenty-five
  in-tree module outputs using the specified build version.
- Missing, unexpected and duplicate JAR entries. Unknown `jasperreports-*` or
  `jasperserver-*` filenames are not accepted just because of their prefix.
- Agreement between the reviewed inventory and provenance manifest.

The twenty-five in-tree outputs are built/tested from the current sources; their
names are fixed but their bytes are not pinned across source changes. The guard
runs offline against committed pins; it never trusts a newly fetched checksum
automatically during a build.

From the repository root, after building with `buildArtifactVersionName=ci`:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/verify-war-deps.py \
  --master jasperserver/deps/master-war-deps-ce.txt \
  --current jasperserver/target/deps/current-war-deps-ce.txt \
  --war jasperserver/jasperserver-war/target/jasperserver.war \
  --project-version ci
```

Use the actual build version for manual builds with another
`buildArtifactVersionName`. The old diagnostic script and its accepted drift
sets have been removed.

For a future dependency change, inspect the dependency graph and official
coordinates, download from the recorded publisher, compare published checksums
and compute SHA-256 independently. Review removals, notices and compatibility,
then update both inventory and provenance in the same PR. Run guard rejection
tests and full build/Docker runtime tests before merging. Never regenerate the
inventory blindly from an unreviewed WAR.

This closes the enumerated migration inventory exception. It does not constitute
a security audit of all historical dependencies, a signature verification claim,
or approval for production deployment. Broader regression, licensing and legacy
dependency security review remain release work.
