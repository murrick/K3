# Linker execution and donor scope separation

The private `rotator` now receives separate ordered collections for rule execution and domain donors. A future execution frontier can retain the complete donor view instead of accidentally dropping substitutions. This commit does not select a frontier or skip any execution.

Base: `develop/3.8.0` at `1c943d02d39bd33ebd91fabb7c4190112ab49459`. The only production file changed is `kanger/src/org/kanger/Linker.java`.

## Preserved behavior

Both existing callers pass the same collection in both roles: descending `leftList`, then ascending `ruleList`. Donors remain the complete **active native Rule-set of that pass**, rather than all visible rules. Each direction retains its own donor order. Active-set expansion, generated-rule publication, fixed-point termination and semantic kernels are unchanged. No new feature flag, cache, shared index or production instrumentation is introduced.

`verify_structure.py` verifies byte-for-byte identity before `link` and from `rotateVariables` to EOF. It also checks the entire `link` and `rotator` regions after removing comments/whitespace and normalizing only the new parameters and their role expressions. The original 651 classes match the frozen baseline hashes. Seven shared compiled classes change, all belonging to Linker; nested class differences were not separately decoded.

## Qualification

Local runtime: Java 17.0.20, ECJ 3.33.0 with `-1.8`, fresh user profiles, serial JVMs, 512 MB heap. Maven is unavailable locally. All eight integrated optimization flags were tested together ON and OFF for the core gates. Five historical corpora and concurrency were tested ON.

| Check | Result |
| --- | --- |
| Native donor boundary | 44 reference checks and 88 split checks, in each flag mode |
| Rule order and checkpoint balance | Reference and split gate output identical |
| Completed hypothesis contract | Reference and split output identical |
| Native dependency witnesses | 31 checks per build/flag mode |
| Exact candidate replay | All 47 SON source-keyed compiled/collision/answer/accepted records match the frozen oracle in four modes; all small/external vectors match |
| Nested transactions | 20 operations per mode; complete state equal across all four modes; work counters equal between builds for each flag mode |
| Historical corpora | All five complete successfully for both builds; semantic output equal |
| Concurrent candidate gate | Three iterations complete for both builds |
| SON snapshots | Twelve full RAW/optimized/stat snapshots match frozen oracles |
| Native Rule-ID traces | Three corresponding samples match exactly: 135 Linker calls, 1,526 pass/direction groups and 158,970 rule visits per sample |

`KangerLinkerDonorScopeSafetyRunner` uses native facts `!seed(alpha); !seed(beta);` and a fresh child receiver `!@x ~seed(x);`. Full traversal and receiver-only donor scope agree with baseline. With only the receiver executed and all three rules retained as donors, both substitutions appear while the visit count is one. With empty execution and full donors, visits and substitutions remain zero. Donor order, six local factory checkpoint depths, parent reservation release and base positive/negative answers are checked. The new gate is included in the existing Java 8/21 CI matrix.

Corpus comparison removes timing rows. Only the existing concurrent `set_08_01`, `set_08_02` and `set_08_03` sections normalize process delivery order/intermediate shared totals and compare each contiguous solution/value block as a multiset, retaining duplicates and complete payloads. Per-process counts, rollback events, queries, answers, final totals and all other semantic rows remain checked. Exact sequential native traces are compared without this normalization.

One split corpus attempt exited 0 with empty stderr but truncated output before `set_06_0F` completed. It lacked `LATENT_CORPUS_PASS` and was rejected. The archived incomplete log is retained under `failed-attempts`; a fresh full retry passed. Its cause is unresolved. Some legacy runners additionally write secondary `.log` files; differing secondary logs are archived separately and are not the captured JVM output. All 36 accepted JVM logs have empty stderr and their required completion markers. This is qualification evidence, not a speedup claim.

## Reproduction

From the checkout root, with Java 17 and ECJ 3.33.0 at `../tooling/ecj.jar`:

```sh
python docs/linker-donor-universe/verify_structure.py
python docs/linker-donor-universe/build.py
python docs/linker-donor-universe/run.py
python docs/linker-donor-universe/analyze.py
```

The build uses the committed original base through `git show`, committed runner/oracle inputs, and temporary source/class directories at `../build`. Trace classes are built separately and never drive inference. `summary.json`, complete compressed JVM logs, class hashes and structural checks are in `docs/linker-donor-universe/`. CI status must be checked on the published commit; local qualification does not substitute for the Java 8/21 matrix.

The next architectural step is recording the state dependencies that require a rule to run again, including transaction rollback and donor changes. This separation provides the necessary donor boundary but does not prove any rule visit removable.
