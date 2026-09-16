# Factory index: full publication and storage reopen

2026-09-16. Qualification-only runner added; production code unchanged.

## Scope and method

Compare `kanger.experiment.latent=off` with `factory`; all other optimization and profiler flags omitted. Each sample creates an isolated user and storage, installs `p(x) -> q(x)` and `q(x) -> r(x)` with 10 or 30 facts, then times compilation of one additional `p` fact. This operation includes publication of the fact and acceptance of derived productions. It is followed by close and a timed reopening of the same storage.

Both timers cover full synchronous API calls. Publication includes parsing, inference and persistence; reopen includes hydration, index preparation and semantic qualification. Thus the difference is the total cost/benefit of factory mode for these operations, **not isolated index-update overhead**. Reopening immediately after close uses warm OS filesystem caches; this is not cold-start disk latency. User-current-Mind bookkeeping, fingerprints, checks and closing are outside the measured intervals.

OpenJDK 17, 512 MiB heap, ECJ Java 8-target classes. Three fresh JVMs per mode, run sequentially in OFF/ON, ON/OFF, OFF/ON order. Two warmups plus five measured samples for each size in each JVM, sizes always 10 then 30. Sixty measured samples overall. JIT/order/GC and IO variability remain material; no significance estimate is claimed.

## Results

Each cell lists three JVM medians in milliseconds; the final columns compare their medians.

| Seed facts / operation | OFF JVM medians | Factory JVM medians | OFF → factory ms | Change |
| --- | --- | --- | --- | --- |
| 10 / publish | 11.831, 8.994, 10.968 | 12.531, 11.921, 13.642 | 10.968 → 12.531 | +14.2% |
| 10 / reopen | 15.394, 10.364, 13.062 | 12.418, 15.534, 15.036 | 13.062 → 15.036 | +15.1% |
| 30 / publish | 22.184, 13.384, 16.111 | 20.134, 13.246, 14.402 | 16.111 → 14.402 | −10.6% |
| 30 / reopen | 29.777, 19.330, 20.692 | 26.565, 19.373, 20.972 | 20.692 → 20.972 | +1.4% |

On 10 facts, publication is slower in all three matched pairs, but reopen direction varies. On 30 facts, publication is faster in all three pairs, while reopen is effectively mixed at this resolution. These small fixtures do not support either a universal overhead percentage or a general speedup. They also do not isolate how much of the change comes from construction versus reduced runtime domain scans.

## Qualification

Every sample verifies that the sorted canonical visible-rule fingerprint (generated flag plus textual rule) is identical before close and after reopen. It then checks the new derived `r(size)`, original derived `r(0)`, and absence of a never-added `p(size+1)`. Across all measured samples/modes each size has one fingerprint and one resulting visible-rule count: 35 and 95. These checks are specific to publication/reopen; they do not replace the earlier transaction/collision corpus or compare all possible solutions.

A separate one-sample-per-size `factory-verify` smoke run passed before timing. Its fingerprints match the measured runs; its times are excluded. Raw direct CSV outputs are authoritative and contain complete sample ranges. Evidence is in [publication/open](latent-substitution-evidence/publication-open/).

Reproduce with the existing qualification classpath:

```sh
java -Xmx512m -cp "$CP" -Dkanger.experiment.latent=off \
  org.kanger.LatentPublicationBenchmarkRunner /absolute/path/off.csv
```

Repeat with `factory` in alternating JVM order. Defaults are `-DbenchWarmups=2 -DbenchSamples=5`; smoke uses zero warmups, one sample, and `factory-verify`. The runner creates a temporary home and closes storage in `finally`.

No default enablement or persistence change. Next investigation should focus on candidate traversal/representation and shared transaction footprint, rather than extrapolating milliseconds from these small workloads. Full canonical Maven/Java 8/21 qualification and cold/open larger-database measurements remain outstanding.
