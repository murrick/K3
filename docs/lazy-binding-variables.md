# Lazy internal variable list for Domain binding

Prototype baseline `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, fetched 2026-10-05. Branch `experiment/3.8.0-lazy-binding-variables`. Flag `kanger.experiment.lazyBindingVariables` defaults to **false**. No develop merge or default enable is authorized by this checkpoint.

## Scope and preserved behavior

The only production changes are ArgumentsList and Domain. Exact built-in ArgumentsList can return an internal null sentinel when no variables were collected. Allocation starts only when the first element is added. Public getTVariables is unchanged and still returns a fresh owned mutable list. Domain binds variables only after complete enumeration. Custom ArgumentsList subclasses use the original virtual getTVariables and its return/exception contract, including null-result failure.

The internal enumerator retains both deletion calls, both getObject evaluations, duplicate equality checks and the duplicate-path second getType call. The first getObject is evaluated even when the accumulated list is empty. Nested function enumeration retains its public virtual getTVariables call and iterator callbacks. Null variable entries still become a nonempty list and cause the original binding failure. There is no cache across calls, no skipped Rule read and no changed persistence schema.

## Qualification

Local Java 17 builds target Java 8. OFF and ON passed 22 boundary scenarios / 58 checks each, comparing independent fresh fixtures against the unchanged public enumeration oracle: full callback traces, results and original exception identity. Coverage includes deletion changes on the second check, changing first/second object reads, duplicate equality failure, binding failure after enumeration, nested exact/custom lists and iteration callbacks, public snapshot ownership, null/custom results, invalid object cast and mutation during enumeration. Actual built-in shared-variable reselection and deletion visibility are also checked.

Both modes passed existing compact snapshots (23 checks), resident base comparison (48 checks), single TValue lookup (15 checks), reopening persistence, regression corpus, transaction operations (20) and concurrent candidates (three iterations). Serialized transaction states are byte-identical OFF/ON.

A clean baseline is compiled separately from the two changed production source files. The SonProfileRunner bytecode is verified identical between baseline and prototype. Benchmark order is clean → OFF → ON → ON → OFF → clean, six samples per JVM, excluding the first two from warm summaries. All eight integrated optimizations are explicitly ON. Every log must be complete, have empty stderr and preserve all raw/final texts and last Linker statistics. Performance results are pending.

The dedicated GitHub matrix covers Java 8/21/26, prior eight optimizations OFF/ON and the new path OFF/ON. CI results are pending.

## Reproduce

```sh
bash docs/lazy-binding-variables/build-local.sh
python docs/lazy-binding-variables/qualify.py
python docs/lazy-binding-variables/build-reference.py
python docs/lazy-binding-variables/run-benchmark.py
python docs/lazy-binding-variables/analyze.py
```

Local builds require Java and external ECJ at `../tooling/ecj.jar`. Classes are written under `../build/lazy-binding-classes` and `../build/lazy-binding-reference`. Source changes, boundary runner, scripts, local qualification logs/states and benchmark evidence are kept together. The isolated experiment remains default OFF until its correctness and performance results justify further action.
