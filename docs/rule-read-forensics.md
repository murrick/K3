# Rule read route attribution (diagnostic only)

Baseline: `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, live rechecked 2026-10-04. Branch: `experiment/3.8.0-rule-read-forensics`. Production, qualification and CI files are unchanged. No new default, optimization, or merge is proposed by this checkpoint.

## Findings

Each warm sample of the existing `son(John, x)` workload produced these bindings:

| Route | Domain.setMind calls | TVariable.setMind calls inside those bindings |
| --- | ---: | ---: |
| Resolved candidate selection, RuleFactory.findByResolvedDomain (503) | 1,633,243 | 2,779,801 |
| Cache walk, Escalera.WalkIterator.next (655), through Rule.setMind | 1,244,915 | 325,475 |
| Canonical rule lookup, RuleFactory.find(Solve) (911) | 1,310,098–1,315,389 | 0 |
| Predicate-id candidate selection, RuleFactory.findByDomain (446) | 593,602 | 689,222 |
| Remaining routes | 106,723 | 69,301 |
| Total | 4,888,581–4,893,872 | 3,863,799 |

These are **Domain binding counts attributed to a read route**, not total Rule reads, candidate cardinalities, unique rules, or redundant accesses. One Rule read may bind multiple Domains. The remaining group includes Domain-only cache walks and direct factory publication/commit bindings.

Resolved candidate selection accounts for 71.944762% of variable selections inside the measured bindings. All 3,593 actual changes to a variable's active context occurred under the Rule cache walk. No same-Domain-context binding changed a variable context in this workload; this does not make that binding safe to skip.

Canonical lookup bound roughly 1.31 million Domains without selecting any variables. Since every base Domain.setMind obtains getTVariables and the base implementation creates a fresh ArrayList, this identifies a candidate for avoiding empty-list allocations. It does **not** prove that those Domains contain no variable arguments: deletion checks, nested functions and callbacks still run, and may legitimately produce an empty result. There is no per-call list-length histogram in this checkpoint.

## Boundary and next experiment

Retain Rule/cache reads, hydration, effective views, promotion, and Domain/variable binding. The previous three counterexamples still demonstrate that an unchanged Rule/Domain context cannot justify skipping propagation: a shared variable may have been moved to another context independently.

A bounded next prototype can investigate lazy allocation inside an internal variable enumeration used by Domain binding. Public getTVariables must retain its fresh, owned, mutable result. Custom ArgumentsList implementations must retain virtual dispatch. Both deletion checks, all getType/getObject callbacks, nested function enumeration, duplicate checks, callback order, and exception identity must remain intact. In particular, avoiding allocation must not short-circuit the first getObject evaluation when the accumulating list is empty. No cross-call cache or persistence change is needed.

No performance benefit has yet been measured for this proposed path. The large resolved-candidate route remains a separate topic; these counts do not justify returning unbound cached rules or eliminating reads.

## Method and reproducibility

From repository root:

```sh
python docs/rule-read-forensics/instrument.py
python docs/rule-read-forensics/run.py
python docs/rule-read-forensics/analyze.py
```

Requires Java, `../tooling/ecj.jar`, the repository runtime resources and previously recorded clean reference commit `e5a6f6608df967491dfb86295e6542cc84c7e8bf`. ECJ targets Java 8; runs used Java 17.0.20 and `-Xmx512m`. All eight integrated optimization flags used their baseline default ON. Temporary patched source/classes live under `../build/rule-read-source` and `../build/rule-read-classes`.

Two fresh JVMs ran sequentially, six samples each; first two samples in each were excluded from count ranges. All twelve RAW and OPTIMIZED hypothesis texts matched the clean baseline reference. All stderr files are empty. The existing three identity-guard witnesses passed 21 checks. This checks observed texts and diagnostic invariants, not full regression equivalence or concurrency qualification of a new implementation.

Read wrappers tag `getData(mind)` in Escalera/RuleFactory, RuleFactory's cache.get(id), and internal get(id) callsites; setter wrappers preserve the earlier attribution. Every wrapper delegates to the original virtual operation and restores its diagnostic caller context in finally. No extra getId/getType/deletion/owner callbacks are introduced. Instrumentation inspects identity of the private active variable context, not the public context lookup. Instrumentation.patch records exact modifications; callsites.json records setter wrappers only. All line numbers refer to the baseline source. An untagged higher-level external point read retains the RuleFactory:664 route.

The profiler observes only the target main thread and retains Domain identities until phase end. Extra wrappers, counters, maps and retained references change allocation, GC and scheduling. **Profile timings are excluded; no speed or retained-heap claim follows from these logs.** Minor canonical lookup count variation is reported as a range, without attributing its cause. No literal duplicate Rule read has been established.

[Full counts](rule-read-forensics/summary.txt), [instrumentation](rule-read-forensics/instrumentation.patch), [manifest](rule-read-forensics/manifest.json), and raw logs/runners are saved together in `rule-read-forensics/`.
