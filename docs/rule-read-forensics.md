# Rule read source forensics

Continuation of Domain binding forensics after interruption. Live `develop/3.8.0` was fetched and remains `1c943d02d39bd33ebd91fabb7c4190112ab49459`. This branch starts from `experiment/3.8.0-domain-binding-forensics @ 4226f4848d0b977a1a92d84d2d5470165769bd73`. Tracked production, qualification and workflow sources are byte-identical to live develop. All eight integrated optimizations retain ON defaults; rejected prototypes are absent.

## Measurement boundary

Two fresh sequential Java 17 JVMs, six `son(John,x)` samples each. Exclude the first two samples of each JVM from warm counters. Count only the optimization thread while `optimizeHypothesis()` executes; compilation, initial query and text rendering are outside the counter window. ECJ targets Java 8.

A separate source copy preserves the original virtual `Step.getData(Mind)`, `RuleFactory.get/find`, iterator operations, setters and deletion enumeration. Source tags distinguish point lookup from iterator next and selected higher-level callers. Every Rule binding through Step is counted before the original virtual setter. Every resulting Domain setter and variable base-setter is counted as in the preceding diagnostic. Context inspection avoids overridable public getters; identities use IdentityHashMap without user equals/hashCode.

The helper samples a stack on reads 1, 1025, 2049, etc. **Source counts below are exact counters, not extrapolated stack samples.** Stack observations supplement the tags for untagged sources and are periodic, not random; do not interpret their frequency as CPU shares or statistically unbiased source fractions. Stack count completeness is checked separately per sample and route.

Instrumentation allocates wrappers, metadata strings, maps and stacks; retains Rule/Domain references until the end of a phase; and inspects active weak references. It may alter GC, lifetime and scheduling. Diagnostic elapsed/CPU/allocation figures are excluded from performance conclusions. This is not an optimization, custom-callback equivalence qualification or concurrency test.

All 12 complete raw and optimized hypothesis texts match the earlier clean-base reference `e5a6f6608df967491dfb86295e6542cc84c7e8bf:docs/active-mind-reuse-eight/reuse-1-reference.log`. Each sample has 18 raw hypotheses, six optimized, no solutions or values. All stderr files are empty. Last Linker statistics are identical across the 12 samples: passes=6, rules=1292, rotations=6824, pairs=13281, unifications=3080. Variable/domain same+changed totals reconcile in every route. Total Domain selections reproduce the previous report's 4,888,581–4,893,872 range.

## Warm source counts per optimization

| Rule read source | Rule reads | Distinct Rule objects | Propagated Domain.setMind calls |
|---|---:|---:|---:|
| Domain.isStored(Mind) → RuleFactory.find(Solve) | 1,306,463–1,311,754 | 2,164–2,165 | 1,306,463–1,311,754 |
| GeneratedCVarMaterializer.findAlphaEquivalent iterator | 829,545 | 2,226 | 900,525 |
| RuleFactory.findByResolvedDomain candidate hydration | 328,854 | 367 | 1,633,243 |
| RuleFactory.findByDomain(predicateId, antc) candidate hydration | 272,360 | 2,203 | 593,602 |
| Linker.link rule collection (line 516) | 111,062 | 2,226 | 140,312 |
| RuleCandidateIndex.computeBatchSummary | 36,402 | 2,012 | 58,204 |
| Analyzer.checkDatabase iterator (line 308) | 33,540 | 2,226 | 42,081 |
| PredicateFactory.pack iterator | 33,360 | 115 | 33,936 |
| Analyzer.analyze iterator (line 129) | 31,860 | 1,940 | 40,011 |

Full source table and smaller routes are in `rule-read-forensics/summary.txt`; base source line numbers are in `callsites.json`. Distinct identities overlap across sources and must not be summed.

By read mechanism:

| Mechanism | Warm Rule reads |
|---|---:|
| Escalera.get point lookup | 1,961,300–1,966,591 |
| Escalera.WalkIterator.next | 1,108,112 |
| RuleFactory.getTop | 133 |

Together these produce 4,857,608–4,862,899 Domain selections through the Rule cache cascade, matching the preceding diagnostic. Rule read counts and Domain binding counts are different units: a multi-domain Rule propagates several setters per read. In particular the resolved-domain path, although only 328,854 reads, is the largest single tagged binding source (1,633,243 Domain selections).

Domain.isStored accounts for 26.72–26.80% of all Domain setters. Alpha-equivalent scanning accounts for 18.40–18.42%. These are count shares, not potential speedups. The previous small variation persists in the isStored route; the measurements do not establish its cause.

## What the code establishes

`Domain.isStored(Mind)` performs `RuleFactory.find(this)` and a final Rule deletion check. `find(Solve)` hydrates each hash candidate before `equalsTo(domain)`, so checking whether a statement is already stored can initiate many Rule/Domain rebindings.

`RuleFactory.add(Domain)` first attempts the normal exact lookup. If no Rule is found, `GeneratedCVarMaterializer.findAlphaEquivalent` rejects query sources or those without C-variables, then iterates the factory. Every visited Rule is hydrated before stored/query flags are checked and before the alpha comparison. `alphaEquivalent` checks polarity, predicate ID and arity before comparing argument values with a C-variable bijection. The measured 829,545 reads count visits to this loop, not distinct scans or successful matches. We have not counted eligibility, rejects or hits here.

The resolved-domain candidate path also remains a priority: the existing index selects IDs, then the factory hydrates each selected Rule and performs the ordinary deletion check. Repeated reads of the same 367 objects do not establish that their arguments, promotions, deletion state or variable selection remain stable.

## Decision and next bounded investigation

No production change, flag or default is introduced. The earlier built-in counterexamples against same-Mind skips still apply. These counts do not authorize unbound reads, caching selected Rules or omitting hydration merely because a candidate appears unrelated.

Next investigate alpha-equivalent scanning: count eligible calls, rejection stages and successful matches, then assess whether existing predicate/polarity/arity metadata could bound the search while preserving order, promotion semantics, mutable metadata and the observable effects of hydration. The loop is a concrete broad scan outside ordinary hash/candidate selection. A narrowed candidate set is only a hypothesis; preserving argument/C-variable semantics and unrelated binding effects needs proof before any prototype.

Keep Domain.isStored and the resolved-domain hydration path as separate candidates. No evidence here proves an immediate redundant double lookup that can safely be removed at its caller.

## Reproduce

From repository root, with Java 17 and external ECJ at `../tooling/ecj.jar`:

```sh
python docs/rule-read-forensics/instrument.py
python docs/rule-read-forensics/run.py
python docs/rule-read-forensics/analyze.py
git diff --exit-code origin/develop/3.8.0 -- kanger kanger-qualification .github
```

Modified copies/classes are under `../build/rule-read-source` and `../build/rule-read-classes`. The tracked helper, runner, scripts, source manifest, complete patch, raw logs and summary are alongside this report. General regression CI is not requested for this diagnostic-only checkpoint with base-identical production and workflows.
