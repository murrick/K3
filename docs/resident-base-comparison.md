# Resident base comparison experiment

Base: live develop/3.8.0 verified at 1828d7164159cb04df00dedd5c27e18d556af4ea.
Branch: experiment/3.8.0-resident-base-comparison.
Default OFF: `-Dkanger.experiment.residentBaseComparison=true` (restart JVM).
No unmerged predicate-name, snapshots, active-Mind-reuse or current-lookup slice
is included. No change to develop, persistence, representation or inference rules.

## Minimal safe scope

ArgumentsList.equalsBase repeatedly calls isEmpty/getValue on the same operands.
The old archived baseValueLocals prototype added expensive preflight reads and
traversed Mind/parent chains; it is not copied as a semantic oracle here.

The new path applies only to exact ArgumentsList and Argument objects and
resident exact built-in Term/TValue/TVariable objects. For a TVariable, its
thread-local view must equal the supplied exact Mind, and the TValueFactory
must be exact. The resolver reads the existing current binding once and does
not hydrate or call custom value methods. No value survives the current loop
position; it is resolved again on every comparison.

The comparison decides only pure cases: equal IDs, or unequal IDs with at least
one ordinary (non-CVar) Term. Different CVar IDs always use the original full
expression, including all parent lookups and intervening value reads. Null,
unbound, unloaded, custom and mismatched-context cases use the original path.
No parent graph scan or speculative parent lookup is added.

The three production files gain a package-local resident resolver, the small
comparison prefilter, and TValue.peekBuiltinTerm, an internal resident-only
probe. Every optimized caller checks the exact donor class before invoking the probe;
even an overridden probe on a custom TValue is not called. The built-in probe
returns null for custom/unloaded donors and
never invokes getId/isLoaded/getValue or a factory. It does not validate stored
valueId: the original getValue returns an already resident donor even if its ID
was subsequently changed. There is no knowledge/index cache or new manager.

## Critical callback boundary

Term.equals invokes getId on the other Term. A built-in Term used as a HashMap
probe can therefore invoke a custom *stored* parent's key callback. Exact Mind
and exact probe classes alone do not prove a parent lookup callback-free.

The boundary fixture stores a custom parent-map key whose getId changes the
current TVariable binding. The original expression sees that new binding in its
final ID comparison. A whole-expression operand cache would return a different
result. This prototype retains the original path: result true, two callback
invocations, changed binding. A throwing callback still executes once, produces
the historical caught-error diagnostic and returns false. No speculative parent
lookup is performed by the prefilter.

## Diagnostic census (not performance timing)

A temporary entry-counter patch runs two son optimization iterations OFF and two
ON, six integrated flags ON. It is compiled into separate diagnostic classes;
production sources and the uninstrumented runner are restored in finally.
Counters are plain long entries for the single-thread workload, not concurrency
telemetry. Their timings must not be used as performance evidence.

| First optimization counts | OFF | ON |
| --- | ---: | ---: |
| equalsBase calls | 14,437,010 | 14,437,010 |
| Operand positions | 18,160,054 | 18,160,054 |
| Historical Argument.getValue entries inside equalsBase | 100,178,508 | 10,987,379 |
| Historical TVariable.getValue entries inside equalsBase | 43,648,177 | 5,394,400 |
| Current TValueFactory.get(TVariable) calls inside equalsBase | 43,648,177 | 19,562,972 |
| Current TValueFactory.get(TVariable), entire optimization | 86,354,640 | 62,269,435 |
| TVariable.activeMind, entire optimization | 87,555,216 | 63,470,011 |

The resident resolver itself replaces public getValue entries, so the first two
getter reductions are not reductions of *all* value resolution work. Factory
lookup counts include the new preflight and original fallback: their 55.18%
reduction inside equalsBase measures actual removed current-map searches.
The exact path handles about 92.08% of operand positions; parent-CVar fallback
about 4.73%, unknown/unbound/custom/context fallback about 3.19% in this fixture.
Both iterations keep equalsBase/position/domain-selection counts identical.
All four raw/final hypothesis texts match, stderr empty. This census is a lead,
not a wall/CPU speedup claim. See analyze-counts.py and count-summary.txt.

## Qualification at code checkpoint

Local Java 17.0.20 OFF/ON with all six integrated flags ON:
- 48 boundaries: empty/unequal lists, positional IDs, parent/child/siblings,
  missing parent relation, lazy/resident values, changing bindings, custom
  arguments/terms/donors/lists/factories/Minds and exact callback counts,
  caller/active context mismatch, cleared weak view, mutable donor IDs, null
  context, legacy non-ArgumentsList cast, parent callback mutation/failure.
- Existing 15 single-TValue boundaries, latent corpus, TValue DB reopen/hydration,
  rollback/commit/collisions, 20 transaction operations with equal saved states.
- Candidate concurrency in OFF/ON, three iterations each.

CI will run Java 8/21/26 x prior six flags OFF/ON, new mode OFF/ON per job,
including projection, storage and concurrency checks. Pending at first push.

## Clean measurement protocol

Four fresh JVMs ON/OFF/OFF/ON, six son samples each, discard first two and report
last-four medians. Six integrated flags ON, no other branch experiments. Compare
complete raw/final hypotheses and result/solution/value counts. Java 17.0.20,
512 MB heap, wall/main-thread CPU/main-thread allocated bytes, no counters,
profiler or verify flags. Single local workload, no retained-heap claim.

No merge, release, deploy or default enable is authorized by this checkpoint.
