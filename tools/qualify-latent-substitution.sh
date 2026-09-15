#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${LATENT_CLASSPATH:?Set the absolute qualification test classpath first}"
out="${1:-target/latent-experiment}"
mkdir -p "$out"
for mode in off verify; do
    java -Xmx1g -Dkanger.experiment.latent="$mode" -cp "$LATENT_CLASSPATH" \
        org.kanger.LatentSubstitutionCorpusRunner > "$out/corpus-$mode.log" 2>&1
done
for mode in off index verify; do
    java -Xmx1g -Dkanger.experiment.latent="$mode" -cp "$LATENT_CLASSPATH" \
        org.kanger.LatentSubstitutionStateRunner > "$out/state-$mode.log" 2> "$out/state-$mode.tsv"
done
cmp "$out/state-off.log" "$out/state-index.log"
cmp "$out/state-off.log" "$out/state-verify.log"
for mode in off index; do
    java -Xmx1g -Dkanger.experiment.latent="$mode" -cp "$LATENT_CLASSPATH" \
        org.kanger.KangerLinkerProfileRunner 30,100 > "$out/profile-$mode.csv" 2> "$out/profile-$mode.err"
done
python3 - "$out" <<'PY'
import csv, pathlib, sys
root = pathlib.Path(sys.argv[1])
def read(mode):
    with (root / ('profile-' + mode + '.csv')).open() as f:
        return list(csv.DictReader(f))
old, new = read('off'), read('index')
assert old and len(old) == len(new)
for a, b in zip(old, new):
    for key in a:
        if key not in ('millis', 'domain_pairs'):
            assert a[key] == b[key], (key, a, b)
    assert int(b['domain_pairs']) <= int(a['domain_pairs'])
print('LATENT_PROFILE_EQUIVALENCE_PASS')
PY
echo LATENT_EXPERIMENT_PASS
