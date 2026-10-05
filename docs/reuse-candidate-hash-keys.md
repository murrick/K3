# Bounded candidate hash key reuse

## Decision

**Do not integrate or enable by default.** The bounded cache produces a small observed allocation reduction versus OFF, but no consistent timing benefit. ON is slower than the clean baseline in both sequence directions. Preserve this as a qualified default-OFF experiment; no additional timing series is warranted without a new hypothesis.

## Scope

Baseline: `1c943d02d39bd33ebd91fabb7c4190112ab49459`, verified live `develop/3.8.0` before starting. Code submitted to CI: `ddd648d8568e82e5db0ba1d4fdc7e88c0fd1b889`. This standalone prototype includes none of the preceding default-OFF experiments.

Flag: `kanger.experiment.reuseCandidateHashKeys`, **default false**. Only production file changed: `kanger/src/org/kanger/storage/Escalera.java`.

The preceding key-probe diagnostic observed 3,884,221 compact candidate lookups per warm optimization, all outside the ordinary Integer cache range. A simulated 256-slot per-owner key cache hit on 80.19–80.77% of those lookups. That suggested roughly 50 MB of nominal Integer allocation avoidance, against approximately 4.8 GB overall allocation. It did not establish a speedup.

## Implementation and semantics

Each enabled Escalera allocates a 256-entry `Integer[]`. The internal exact-Escalera compact lookup computes `(hash ^ (hash >>> 16)) & 255`, reads one local immutable key, and reuses it only if its numeric value equals the requested hash. Otherwise it stores an `Integer.valueOf(hash)` result in that slot. Every lookup then calls the original `idsByHash.get` with the exact requested key. OFF returns the ordinary `Integer.valueOf(hash)` directly and allocates no key array.

Only keys are reused. No candidate set, missing result, semantic object, or map entry is cached. The private HashMap, inner HashSet instances, insertion/removal operations, index rebuilding, public `find`, mutable returned ownership, snapshot ordering, and exact-class/custom-cache fallback are retained. Key-array allocation applies to every enabled Escalera instance, including subclasses, although subclasses retain the original lookup fallback. Keys need no invalidation when the index changes because they retain no lookup result.

A concurrent replacement may lose a reuse opportunity. It cannot change the local immutable Integer or cause a lookup under a different key. The new race qualification uses a fully built, read-only index safely published before thread start. It does not add support for concurrent structural mutations of Escalera.

## Qualification

Local Java 17, ECJ Java 8 language target:

- New runner: 1,411 OFF checks and 1,423 ON checks, plus 20,000 concurrent key/lookup probes per mode. Includes signed and extreme hashes, ordinary Integer-cache boundaries, replacement in one slot, random keys, repeated-key identity when enabled, absent-to-present transition, live-index mutation, mark/release, clear, root replacement, and public-result ownership.
- Compact snapshots: 23 checks per mode, including original custom-cache callback count and exception identity, reentrant mutation, ordering, and retained snapshots.
- Resident comparisons: 48 checks and three custom reads per mode; single TValue lookup: 15 checks per mode.
- Reopen, corpus, transaction runner (20 operations), and candidate concurrency (three iterations) pass OFF and ON. Transaction states are byte-identical.

Dedicated CI covers Java 8/21/26, prior eight optimizations OFF/ON, and this new flag OFF/ON. It also runs cross-context predicate projection tests. General CI, server, distribution, and qualification-isolation workflows run on the same code commit.

All five workflows passed on the submitted code commit; all 20 jobs are green. The dedicated six-job Java 8/21/26 matrix passed with prior optimizations OFF/ON and the new flag OFF/ON. The final status of all workflows is retained in `ci-status.json`:

- [Dedicated key reuse qualification](https://github.com/murrick/K3/actions/runs/37355830431)
- [KANGER CI](https://github.com/murrick/K3/actions/runs/37355830402)
- [Server](https://github.com/murrick/K3/actions/runs/37355830377)
- [Distribution](https://github.com/murrick/K3/actions/runs/37355830295)
- [Qualification isolation](https://github.com/murrick/K3/actions/runs/37355830543)

## Benchmark method

Six sequential JVMs: **clean → OFF → ON → ON → OFF → clean**. Java 17, 512 MB heap, eight integrated flags explicitly enabled. Each JVM runs six ordinary `?$x son(John,x);` queries and separate hypothesis optimizations; discard the first two samples and report each JVM's median of the remaining four. Wall time, main-thread CPU time, and main-thread allocated bytes are measured for the optimization, without sampling or diagnostic instrumentation.

The clean build restores only Escalera from the baseline. Production differences are asserted to comprise only that file. The SonProfileRunner class is verified byte-identical in clean and experimental builds. The analyzer compares all 36 complete raw and optimized hypothesis texts and last Linker statistics against the archived clean oracle. Timing medians are exploratory observations, not statistical confidence estimates.

## Results

| Sequence | Mode | Wall seconds | Main CPU seconds | Main allocated MB |
| --- | --- | ---: | ---: | ---: |
| 1 | Clean | 4.688 | 4.589 | 4,808.4 |
| 2 | OFF | 4.813 | 4.708 | 4,802.8 |
| 3 | ON | 4.716 | 4.629 | 4,769.8 |
| 4 | ON | 4.700 | 4.598 | 4,721.5 |
| 5 | OFF | 4.660 | 4.559 | 4,805.3 |
| 6 | Clean | 4.569 | 4.469 | 4,931.5 |

MB is decimal. Comparing ON with OFF, allocation reduction is **0.69% forward / 1.75% reverse**. Wall-time reduction changes sign: **+2.03% / -0.86%**; main CPU reduction is **+1.67% / -0.84%**. Comparing ON with clean, wall time worsens **0.59% / 2.87%**, and CPU time worsens **0.88% / 2.87%**.

ON allocation reduction versus clean is 0.80% / 4.26%, but OFF also differs from clean by 0.12% / 2.56%. The large reverse allocation difference therefore cannot all be credited to the enabled key cache. The preceding nominal 50 MB estimate is not an exact prediction of total measured allocation; the JVM can alter other allocation behavior between independently warmed processes. No specific JIT, escape-analysis, or GC cause has been established here.

All 36 full RAW/OPTIMIZED snapshots and last Linker statistics match the clean oracle; all benchmark stderr files are empty. Each query retains 18 raw hypotheses, six optimized hypotheses, an unknown logical result, and zero solutions/values. Last Linker statistics are passes 6, rule visits 1,292, rotations 6,824, domain pairs 13,281, and unifications 3,080.

## Reproduction

From the repository root, with Java and `../tooling/ecj.jar` available:

```bash
bash docs/reuse-candidate-hash-keys/build-local.sh
python docs/reuse-candidate-hash-keys/build-reference.py
python docs/reuse-candidate-hash-keys/qualify.py
python docs/reuse-candidate-hash-keys/run-benchmark.py
python docs/reuse-candidate-hash-keys/analyze.py
```

The evidence directory retains full logs, empty stderr files, transaction states/work scripts, expected snapshot oracle, build and run scripts, JVM manifest, and benchmark summary. The dedicated workflow is `.github/workflows/reuse-candidate-hash-keys.yml`.
