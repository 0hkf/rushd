# Quality matrix: 15,000 scenarios

This is an opt-in audit/regression harness, not application functionality or a load test. It uses the project's existing dependencies. It does **not** fix the defects it finds, change the normal `npm test` command, or treat known defects as expected successes.

Run from the project root:

```bash
bash scripts/quality-15000/run.sh
```

Requirements: local Docker Unix socket already running, cached `postgres:16`, Maven, JDK with `java`/`javac`, Node, curl and lsof. The script refuses occupied port18096, unsupported inherited runtime overrides, unsupported project Maven option files, remote Docker and caller-supplied database/URL arguments. It does not install dependencies, pull images or restart services.

## Counting

| Layer | Scenarios | Nature |
|---|---:|---|
| Frontend | 9,000 | Actual service functions, no rendered React DOM or simulated browser clicks |
| Java | 4,000 | Actual DTO validation, rules, mapper/serializer, matching evidence, JWT, limiter and BCrypt in-process |
| HTTP/PostgreSQL | 2,000 | Real owned Spring process, reviewed schema, persistence, access control, matching, favorites and image lifecycle |
| Total | 15,000 | A complete run must produce all rows; incomplete runs are infrastructure failures |

These are data-driven scenarios in 44 groups, not 15,000 distinct business requirements or a coverage percentage. Multiple assertions, HTTP requests and idempotency retries within one scenario count **once**. Existing 621 backend and 305 frontend tests are separate, and reruns do not increase the count. The case recorder rejects repeated IDs and repeated group/input/expected scenarios.

Inputs and oracles are fixed contract patterns; expected decimal results use independently generated integer/scaled values, not the production parser to calculate its own answer. Several scenarios deliberately fail against the current application. Runtime IDs/timestamps/UUIDs can vary; case IDs and contract patterns are stable.

## Files

- `frontend.mjs`: 18 groups for precision/localized input, forms, specifications, needs, URLs, comparisons, favorites parsing, image manifests, contact policy, request generations and the local-demo guard.
- `BackendMatrix.java`: 10 groups. No database/network connection. Produces a synthetic JPEG fixture using ImageIO; no user photograph is read.
- `http.mjs`: 16 groups. Fixture creation is setup, not additional cases. Full property roundtrips count once per fixture; failure responses are checked against the desired public contract.
- `cases.mjs`: output and assertion recording. A failed assertion remains a failed row; it is not skipped.
- `ownership.mjs`: verifies exact Docker ID/name/label, loopback PG mapping, process command identity/datasource and the HTTP listener PID before any HTTP fixture write. Direct execution against an unowned server is refused.
- `run.sh`: compiles using existing Maven dependencies, creates a unique tmpfs PostgreSQL, applies baseline/P04/P10/P13 **only there**, starts its own loopback backend with image/favorite flags enabled **only there**, runs the matrix, then stops only owned resources.
- `summarize.mjs`: verifies exact per-layer counts, validates result IDs and produces SHA-256 fingerprints, `summary.json` and `cases.csv`.

## Results and cleanup

The launcher prints `RESULTS=...` under a new private `rushd-test-results/quality-15000-*` directory in the physically resolved temporary root (`TMPDIR`, otherwise `/tmp`), outside the repository. It refuses result-file overwrite (`wx`/`CREATE_NEW`). Detailed records are `frontend.jsonl`, `backend.jsonl`, `http.jsonl`, each containing ID/group/input/expected/actual/status. `cases.csv` is an index; detailed inputs are in JSONL. `summary.json` contains the failures, group counts and file hashes.

Exit codes: **0** complete/all pass; **2** complete with assertion failures; **70** infrastructure/isolation refusal. Other unexpected process failures also prevent a complete summary. Do not report a partial file as 15,000 executed cases. A script killed by a signal can retain partial private artifacts; never rerun a partial JSONL as a complete report.

Synthetic database records disappear with the owned tmpfs container. Image originals are not retained by the application; processed fixture images are deleted through their API lifecycle. Logs, the generated synthetic source JPEG, the empty private image-store lock file, compiled test classes and result files remain as local evidence. No actual JWT/refresh cookie or user credential is intentionally written by the case recorder. Review logs before sharing them.

Do not invoke the HTTP phases manually. Do not run another `mvn clean` while this backend is using `target/classes`. Run the existing PostgreSQL verifier and the default Maven suite separately afterward. Do not apply the test SQL or enable images/favorites on the project database to make the harness pass.

## Limits

- No code-coverage percentage, React DOM suite, 15,000 visual checks, mobile-device certification, exhaustive penetration test or production TLS/proxy test is claimed.
- Node service tests do not prove responsive layout. See the separate visual audit and browser smoke observations.
- Concurrent registration probes use real overlapping HTTP requests, not control of every thread schedule. A deterministic service/database race regression should supplement them during repair.
- Year-boundary fixtures currently target the October2026 contract. Review the `ageAsOfYear` oracle when the current calendar year changes; do not change product behavior to satisfy an outdated fixture.
- Security/dependency advisories, network exposure, onboarding decisions and pending feature activation also require separate review; they are not made safe merely by a large passing count.

Recorded baseline: [results](../../docs/quality-15000-results-20261025.md), [remediation prompt](../../docs/quality-15000-remediation-prompt.md), [earlier audit](../../docs/comprehensive-audit-20261025.md).

## Approved password-contract update — 25 October 2026

Case IDs and counts stay unchanged. The user approved explicit rejection above 72 UTF-8 bytes: registration400, login401 without trying a prefix. The two HTTP password cases now assert oversized registration400, valid boundary registration201, mutated oversized login401 and correct boundary login200. The 20 Java cases exercise the actual application `BoundedBCryptPasswordEncoder`, checking the valid boundary first and rejecting prefix/suffix mutations. They no longer test the raw vulnerable library directly; passing them **does not establish that the dependency CVE has been upgraded**. Separate regression tests cover encode rejection and byte lengths. The underlying BOM upgrade remains a separate approval gate.

The targeted auth phase waits one default60-second rate window after the business cases, so fixture login calls do not consume its budget. Runtime login/refresh limits are not raised,429 is not treated as success and retries/waits do not add cases. Keep this pacing aligned with the declared default policy if that policy is explicitly changed later.
