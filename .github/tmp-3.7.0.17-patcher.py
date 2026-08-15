from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, got {count}")
    p.write_text(text.replace(old, new, 1))


# A canonical Rule restored from a lower-level deletion is a surviving semantic
# change of this technical transaction, not an ordinary duplicate. RuleFactory
# has already published the restoration marker in the current Mind; preserve it
# by classifying the returned canonical Rule like a primary promotion.
replace_once(
    "kanger/src/org/kanger/compiler/Compiller.java",
    '''        } else if (mind.getRules().isPromotedHere(r)) {
            ((Rule) r).setSecond(false);
        } else {
            ((Rule) r).setSecond(true);
        }
''',
    '''        } else if (mind.getRules().isPromotedHere(r) || r.isRestored(mind)) {
            ((Rule) r).setSecond(false);
        } else {
            ((Rule) r).setSecond(true);
        }
''')
