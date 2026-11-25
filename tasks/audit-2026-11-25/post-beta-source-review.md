# Post-beta source review

## Finding: PB-SOURCE-001 (P2)

A periodic check could correctly identify a newer list, then fail to install it.
`checkForUpdate` stores the observed Last-Modified date without fetching the
body (SourceModel:420). Previously the next GET sent that date as
`If-Modified-Since`. For a source without an ETag, a server using date
validation can answer 304, even though the phone still has the older rules.
The all-304 path then retains the old active generation. This is distinct from
the earlier never-downloaded/unknown-date fix.

The request builder now uses `HostsSource.hasNewerOnlineCopy()` (SourceModel:1821).
A known-newer revision bypasses the HTTP cache and sends neither validator.
Once the new body is parsed and committed, normal conditional requests resume.
This uses the same decision as the source list and Discover; this lane did not
edit those callers or the entity. No parse, selection, storage or publication
policy was changed.

## Verification

- **Host regression:** direct JDK 21 compilation and JUnit 4.13.2 against local
  dependencies. Before the guard, with only the side-effect-free request builder
  made package-static for testing: 9 tests, 1 failure. Expected a missing
  If-Modified-Since; got `Tue, 24 Nov 2026 16:48:50 GMT`, the newly observed
  revision. The known-body validator control and all seven existing catalog
  tests passed. Final run with the shared predicate: **9 passed**, 0 failed.
  These tests build actual OkHttp requests; none sends one.
- **Device handoff (compiled, not run):**
  `SourceModelHttpConditionalTest#checkThenRefreshDownloadsNewRevisionBeforeAcceptingNotModified`.
  Its existing local TLS fixture has one enabled, previously fetched list and
  no ETag. HEAD advertises a newer revision; the server answers 304 only for
  that revision's date. The first full GET must replace the old blocked host;
  the next full GET must validate the installed revision and keep its generation
  and rule. The old behavior would retain the old host at the first refresh.
  No external service, live feed or device was contacted here.
- **Catalog:** actual accessors returned 66 entries, 66 distinct URLs, 3
  defaults, 7 Balanced and 13 Aggressive. A curated entry classified as ADS,
  directory metadata changed it to FILTERLISTS, the built-in user URL took
  precedence as USER, and an unknown URL classified as CUSTOM. No availability
  or rendered-screen claim.
- **Errors:** resolved all 18 distinct enum resource keys against the default
  error strings. DOWNLOAD_FAILED gives Sources recovery guidance; NO_CONNECTION
  retains connection guidance. This was a mapping check, not a dialog test.

## Prior Evidence And Deltas

Prior broad reads: `data-files.csv` (catalog and source model),
`remaining-support-files.csv` (error mappings), and their companion reviews.
The three pre-edit SHA-256s matched the stale shipped rows in `files.csv`.
Final full-file hashes and coverage are in `post-beta-source-files.csv`.

| File | Prior reviewed SHA-256 | Prior review to shipped; this lane's change |
| --- | --- | --- |
| SourceModel | `b20a872ce49db903ec318e3eaf451e3edbdd93e812e1f4f78f4a7e0dc19f29db` | Shipped added the FILE static import and the check-only branch clearing a future local file date when the provider lacks a date or reports an earlier one. Reversing just these edits in memory reproduced the prior hash. Reversing only this lane's package-static builder declaration, guard comment and predicate reproduces shipped `0ef9af51f1628c76c55ca975c54ae1317fe4b59563144d09fb88f5e28f15df86`. |
| FilterListCatalog | `7435dcfdd64a6f9424c2f5ab16ee993520849db877cf72121b5631b3e8573d26` | Compared the catalog and its CatalogEntry class in the retained phase-four debug APK and the local debug APK. Disassemblies match exactly with only source-line directives removed. The prior text of the comments was not reconstructed; all current text was read. No edit here. |
| HostError | `8fc371cd2bc1f0c2b215be8c23cfb82d16f54d590f4e41d149f97a79d7a7b0a6` | Replacing only DOWNLOAD_FAILED's shipped detail key with its previous `error_no_connection_details` key reproduces the prior hash. No edit here. |

The historical APK is under `app/build/reports/audit-2026-11-25/phase-four-apks/`;
`app/build/reports/audit-2026-11-25/phase-four-build-inputs.csv` records the
earlier catalog and model hashes.
Removing the new conditional-response test and its two imports also reproduces
the prior test hash `8bfb54e012a035da08dc23785dd9c7e2a19e2c4641a3da19374a4c018f31cf8a`.

## Coverage Boundary

No unread lines remain in the three assigned production files. For SourceModel
the review followed file and URL input into SourceLoader, staged metadata,
single/batch promotion, unchanged and failed-source carry-forward, cancellation,
unsafe-generation discard, disabled-source cleanup, and progress/error exits.
These paths were read; their Android, parser-internal and storage behavior was
not re-executed here. The new Android test requires device execution. No DB
tests, external network probes, Gradle, emulator, Git, global ledgers or
other owners' files were used for changes.
