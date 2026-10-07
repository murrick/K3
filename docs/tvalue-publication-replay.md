# Native publication-order capture and clean replay

All 144 prepared-child threaded cases reproduce their captured native results exactly in fresh clean JVMs when replayed in the measured publication order. The six forced orders and 96 unforced races cover both native branches: six retained Rules when child 1 wins the conflict, seven when child 2 wins, with six positive solutions in either branch. A deliberately wrong-order replay is rejected despite preserving that solution count.

Base: `experiment/3.8.0-tvalue-dirty-buckets` at `6c45ebe16fbdc3a05fd5ff573bebc2f33ffb601d`. Production sources, qualification-module sources and CI workflows remain unchanged. Only standalone diagnostic sources and evidence under `docs/` are added. There is no scheduler consumer, new default flag or speedup claim.

This qualifies publication of ready native child operands under their recorded monitor order. It **does not retroactively accept the two preceding randomized-corpus attempts**: the original test also overlaps operand preparation with publication, and its frozen complete-corpus gate remains unqualified.

## Capture boundary

`PublicationFixture` constructs a native root and three children. It prepares the native `set_08_03` operands with the same object-parameter query form: three positive values per child, the duplicate positive operand in child 1, and the conflicting negative operand in child 2. Preparation finishes on the main thread before workers start. All three reservations are still live and the parent has no published Rules. The complete prepared Rule inputs, including identities and native visibility/status flags, are recorded and hashed.

`CommitOrderJournal` is explicitly registered for one parent and the three child objects. Its registry uses object identity and is independent of ThreadLocal state. Temporary Mind instrumentation records entry and outcome inside the parent's existing monitor. The ordinal therefore measures entry to the native critical section, not worker start, a pre-lock attempt or completion-message delivery. Unregistered internal/query commits are ignored.

The added wrapper takes the same reentrant native monitor around an unchanged private commit body, records the Boolean return or thrown failure class, and preserves propagation. Event pairs are rendered after workers finish from their recorded ordinals. Recorder errors are collected and checked at finish; event data never supplies a native commit decision. Registration is removed before the final query. All measured entries/exits are complete and all recorder scopes close.

The recorder extends monitor occupancy and can influence which racing worker enters first. This diagnostic tests the order that actually occurred; it does not prove unchanged timing, fairness or scheduling distribution. The registry also retains its explicit scopes until finish. The protocol has no reverse registry-to-Mind lock acquisition.

## Native replay and independent oracle

Eight capture JVMs cover order-only and prior TValue-shadow instrumentation, each in ON/OFF/VERIFY and an ON repeat. Each contains six forced native orders and twelve simultaneous-start races, using three worker threads per case. Every case settles the three actual native commits and closes all reservations. Eight separate clean JVMs reconstruct the same native prepared inputs and replay each measured order on the original, uninstrumented Mind classes.

The replay compares complete captured result fields, rather than only a Boolean or a count:

- Child acceptance vector and retained Rule count.
- Full visible Rule IDs, text, deletion/stored/query flags before and after the final query.
- Query answer, all solution IDs/text and complete Values rows with Term identities.
- Hypothesis and temporary-hypothesis buffers and final reservation count.

An independent Python oracle verifies the expected acceptance vector, complete fact/Values payload and negative retained operand for each order. The native stored negative operand is rendered as `?value(...)` with `query=false`; the input is the native `!~value(...)` assertion. Its identity/status is preserved rather than mistaken for an ordinary query. Inputs are identical across all captures, and every captured result for a given order agrees across builds, flags and repeats, including native IDs.

| Measured native order | Accepted children `[1,2,3]` | Retained Rules | Positive solution families |
| --- | --- | ---: | --- |
| 123 | `[true,false,true]` | 6 | 1 and 3 |
| 132 | `[true,false,true]` | 6 | 1 and 3 |
| 213 | `[false,true,true]` | 7 | 2 and 3 |
| 231 | `[false,true,true]` | 7 | 2 and 3 |
| 312 | `[true,false,true]` | 6 | 1 and 3 |
| 321 | `[false,true,true]` | 7 | 2 and 3 |

All six orders also occur in the unforced races. Their observed frequencies are recorded, without a distribution or scheduling-transparency claim.

## Qualification and negative control

There are 26 successful positive JVMs with empty stderr: eight captures, eight clean replays, three 83-check TValue native gates, three native commit-exception atomicity gates, two exact candidate replays and two 20-operation transaction runs. The frozen map for all 47 candidate sources, complete small/external answer rows, full transaction state/work vectors and the inherited native gate traces retain their oracles.

One further clean JVM is an expected negative control. Only the first captured order changes from `123` to `213`; its prepared inputs and captured result remain intact. Native replay then produces the other acceptance vector, seven Rules and the other six-fact/Values family. The runner exits nonzero and rejects the complete outcome. Its stderr and exact mutation are retained. Matching `true` plus six solutions is therefore insufficient to pass this replay oracle.

The nine TValue-shadow traces replay independently against 12,996 full authority observations. The three 83-check gate traces and the candidate/transaction traces match their preceding shadow evidence byte-for-byte. Capture-case traces also verify every projection delta and inspection counter; this is a boundary consistency check, not worker mutation-channel coverage.

All 677 classes from the preceding clean diagnostic rebuild remain byte-for-byte identical. Order-only instrumentation changes Mind and its anonymous class; the shadow build changes the same eight shared classes as the preceding TValue observer. All prior shadow classes except Mind remain identical. Removing the exact wrappers/hooks recovers all original Mind, Linker and TValueFactory files byte-for-byte. Original settlement bodies, inference kernels, typed factories and donor universes remain intact.

Local qualification uses Java 17.0.20, ECJ 3.33.0 targeting Java 8 and explicit values for all eight integrated flags. VERIFY adds TValue-index and solve-sync verification. No new Java 8/21 CI result is claimed for this docs-only branch.

## Remaining boundary

This fixture separates operand preparation from concurrent commit. It does not cover the original corpus's overlapping queries, arbitrary worker-side TValue/current/FValue writes, shared callback state, storage, custom Mind implementations or general concurrent inference. The prior ThreadLocal TValue observer's worker limitations remain in force. The new recorder is a separate object-scoped diagnostic, not an implicit propagation of that observer to workers.

The preceding two complete-corpus rejections are retained unchanged. These results support an order-aware publication oracle and show why a fixed-count comparison can reject another native branch. They do not replace or weaken the old frozen oracle, prove the exact causes of every older race, or make the original corpus an accepted full-equivalence result.

The next refinement can cover the currently untracked canonical TValue metadata writes while retaining full snapshot checks. Before any scheduling consumer is introduced, other dependency channels and any unsupported concurrent/callback path still require conservative full traversal.

## Reproduction

From the checkout root, with ECJ at `../tooling/ecj.jar`:

```sh
python docs/tvalue-publication-replay/build.py
python docs/tvalue-publication-replay/run.py
python docs/tvalue-publication-replay/analyze.py
```

Temporary clean/order/shadow sources and classes are generated outside repository source directories at `../build/tvalue-publication-replay-*`. Immutable `.cases` files contain complete base64 inputs/results, and `.events` files contain ordinal actor/outcome pairs. Compressed complete logs/traces, expected negative stderr, native state/work vectors, source/class hashes and `summary.json` are committed beside the report. Two preliminary JVMs are archived separately and excluded from the 27-JVM qualification count. `manifest-sha256.json` covers report and evidence, excluding itself.
