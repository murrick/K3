# Guarded single TValue lookup

Default OFF: `-Dkanger.experiment.singleTValueLookup=true` enables the experiment.
Restart the JVM when changing this property; it is read at class initialization.

TVariable.getValue() and getCurrent() normally look up a bound projection twice.
The fast path reuses one lookup only for exact built-in TVariable, Mind and
TValueFactory classes. Subclasses retain the historical path. Each call still
selects its thread-local active Mind and reads the current projection anew.
There is no cache across calls, storage format change, or altered binding lifetime.

## Evidence

Source experiment: `experiment/3.8.0-son-profile @ 6912919`.
Full report and raw evidence remain in that branch under docs/son-profile.md.
With the five earlier optimizations enabled, Java 17 / 512 MiB exploratory paired
runs of natives.k / son(John,x) reduced hypothesis optimization time by 8.3% and
10.8% in opposite mode orders. Three runs per JVM, median of two post-first runs;
absolute runtimes drifted. This is not a universal or cold-start performance claim.
All twelve scenario runs matched full sorted raw/final hypothesis text, 18 -> 6.

The source experiment passed Java 8/21 OFF/ON corpora, 15 boundaries per mode,
20-operation transaction state comparison, and three persistent DUMB concurrent
iterations: https://github.com/murrick/K3/actions/runs/36678482715
One earlier local run lacked a completion marker and remains invalid as full-pass
evidence; repeat and remote CI completed. Qualification requires explicit success
markers, not exit code alone.

This integration candidate applies only the isolated change over develop/3.8.0
at a3ec16f, retaining its new persistent TVariable reference setter. Candidate CI
must run against this new base before merge. Default enablement is separate.
