# Active Mind weak-reference reuse on the eight-optimization base

Base: `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459` (live checked 2026-10-04).

Requalifies only the minimal TVariable.setMind delta from the older experiment. `kanger.experiment.reuseActiveMind` remains default OFF. All eight integrated optimizations retain their current defaults.

For an exact TVariable, reuse the selected thread-local WeakReference when its live referent is the requested Mind. Changed or cleared references are replaced; subclasses retain reference behavior. Owner qualification still executes on every call, in the original order, including custom getNext/getId callbacks and reentrant changes. No strong Mind retention, persistence change, or shared cache.

ActiveMindReuseRunner checks bindings, root/child owner selection, weak-reference clearing, null selection, sibling-thread isolation, custom variable callbacks, exception identity, callback counts and reentrant selection (23 checks). CI covers Java 8/21/26, prior eight OFF/ON, reuse OFF/ON, corpus/reopen/projection/transactions/concurrency and existing boundary runners.

Local qualification and fresh clean/OFF/ON/ON/OFF/clean measurements are pending. Historical six-optimization timing does not qualify this base. No merge or default enable is requested at this checkpoint.
