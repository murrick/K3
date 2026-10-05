# Lazy internal variable list for Domain binding

Prototype baseline `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, fetched 2026-10-05. Branch `experiment/3.8.0-lazy-binding-variables`. Flag `kanger.experiment.lazyBindingVariables` defaults to **false**. No develop merge or default enable is authorized by this checkpoint.

## Scope and preserved behavior

The only production changes are ArgumentsList and Domain. Exact built-in ArgumentsList can return an internal null sentinel when no variables were collected. Allocation starts only when the first element is added. Public getTVariables is unchanged and still returns a fresh owned mutable list. Domain binds variables only after complete enumeration. Custom ArgumentsList subclasses use the original virtual getTVariables and its return/exception contract, including null-result failure.

The internal enumerator retains both deletion calls, both getObject evaluations, duplicate equality checks and the duplicate-path second getType call. The first getObject is evaluated even when the accumulated list is empty. Nested function enumeration retains its public virtual getTVariables call and iterator callbacks. Null variable entries still become a nonempty list and cause the original binding failure. There is no cache across calls, no skipped Rule read and no changed persistence schema.

## Qualification

Local Java 17 builds target Java 8. OFF and ON passed 22 boundary scenarios / 58 checks each, comparing independent fresh fixtures against the unchanged public enumeration oracle: full callback traces, results and original exception identity. Coverage includes deletion changes on the second check, changing first/second object reads, duplicate equality failure, binding failure after enumeration, nested exact/custom lists and iteration callbacks, public snapshot ownership, null/custom results, invalid object cast and mutation during enumeration. Actual built-in shared-variable reselection and deletion visibility are also checked.

Both modes passed existing compact snapshots (23 checks), resident base comparison (48 checks), single TValue lookup (15 checks), reopening persistence, regression corpus, transaction operations (20) and concurrent candidates (three iterations). Serialized transaction states are byte-identical OFF/ON.

A clean baseline is compiled separately from the two changed production source files. The SonProfileRunner bytecode is verified identical between baseline and prototype. Benchmark order is clean → OFF → ON → ON → OFF → clean, six samples per JVM, excluding the first two from warm summaries. All eight integrated optimizations are explicitly ON. All six logs are complete with empty stderr. All 36 raw/final hypothesis texts and last Linker statistics match across modes.

## Performance result and decision

Warm medians of the last four samples in each fresh JVM:

| Run | Wall seconds | CPU seconds | Allocated MB |
|---|---:|---:|---:|
| Clean, forward | 4.568 | 4.476 | 4,716.0 |
| OFF, forward | 4.642 | 4.526 | 4,800.7 |
| ON, forward | 4.688 | 4.602 | 4,806.7 |
| ON, reverse | 4.620 | 4.535 | 4,856.0 |
| OFF, reverse | 4.620 | 4.519 | 4,872.5 |
| Clean, reverse | 4.649 | 4.559 | 4,802.7 |

ON versus clean: wall time is 2.63% slower forward and 0.62% faster reverse; allocated bytes rise 1.92% and 1.11%. ON versus OFF: wall time is 1.01% slower forward and essentially unchanged reverse (0.02% slower); allocated bytes rise 0.12% forward and fall 0.34% reverse. CPU time does not show a consistent benefit either.

Control variation is material, including the OFF path's allocation difference from clean. The code-shape/JIT/escape-analysis explanation has not been measured and is not established by these figures. Removing a source-level empty-list allocation did not produce a reliable end-to-end allocation saving in this implementation. No extra timing series is justified by this negative result.

**Do not integrate this prototype or enable its flag by default.** Keep it as a qualified negative experiment. The binding effects and full callback traversal remain necessary; this result provides no basis for skipping them.

All five GitHub workflows passed: general CI, server, distribution, qualification isolation and the dedicated Java 8/21/26 matrix with prior eight optimizations OFF/ON and the new path OFF/ON. CI runs execute code commit `e3f287ede67b28b8941a769381eaf3d84ec9acfb`; the final evidence commit changes only documentation/logs. Final CI outcomes and run URLs are recorded in `lazy-binding-variables/ci-status.json`.

## Reproduce

```sh
bash docs/lazy-binding-variables/build-local.sh
python docs/lazy-binding-variables/qualify.py
python docs/lazy-binding-variables/build-reference.py
python docs/lazy-binding-variables/run-benchmark.py
python docs/lazy-binding-variables/analyze.py
```

Local builds require Java and external ECJ at `../tooling/ecj.jar`. Classes are written under `../build/lazy-binding-classes` and `../build/lazy-binding-reference`. Source changes, boundary runner, scripts, local qualification logs/states and benchmark evidence are kept together. The isolated experiment remains default OFF until its correctness and performance results justify further action.
