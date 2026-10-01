# Small TVariableSet hash experiment

Base develop/3.8.0 verified at 1828d7164159cb04df00dedd5c27e18d556af4ea. Isolated experiment/3.8.0-variable-set-hash; code checkpoint 7a8f7e6e370f44f4973d9498d6cfe1209142f948. Flag kanger.experiment.smallVariableSetHash defaults OFF. Do not integrate on current evidence.

Original hash visits sorted variables, starting at 3 and folding with 47, reading each current ID. IDs are mutable, so caching completed hash would alter behavior. Prototype removes iterator only for sizes 0/1/2, reading first/last in existing sorted order; larger sets retain original loop. It does not change membership, representation, constructors, equals, compareTo, persistence or Linker behavior.

18 boundary checks passed OFF/ON: sizes0/1/2/3/7/30, duplicate inputs, large/negative/mutable IDs, custom getId call order, first-ID callback mutating the second ID and exception identity/short-circuit order. Regression corpus and 20-operation transaction qualification passed both modes; state files byte-identical.

Four sequential fresh JVMs ON/OFF/OFF/ON, Java17 Xmx512m, six prior integrated flags ON, direct-name and compact-snapshot paths absent. Six samples each; first two discarded; median final four. Main-thread allocated bytes during optimizeHypothesis are cumulative, not retained heap; CPU is main-thread only.

| Pair | OFF wall s | ON wall s | OFF CPU s | ON CPU s | OFF MB | ON MB | Time reduction | Allocation reduction |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ON/OFF | 5.189 | 5.293 | 5.090 | 5.176 | 5386.3 | 5256.9 | -1.99% | 2.40% |
| OFF/ON | 4.990 | 4.945 | 4.884 | 4.844 | 5384.5 | 5203.7 | 0.90% | 3.36% |

All 24 raw/final hypothesis snapshots equal; 18 raw/six final hypotheses, zero solutions/values; no benchmark stderr. No stable elapsed or CPU gain. Allocation saving is small and does not justify adding this to develop now. Preserve default-OFF prototype as archaeology; no merge/default-enable performed. Cross-JDK CI run36938504284 configured Java8/21/26 × prior OFF/ON; final statuses checked separately. Avoid another timing batch without a new hypothesis.
