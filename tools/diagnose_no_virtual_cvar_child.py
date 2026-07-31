#!/usr/bin/env python3
"""Diagnostic-only source transform for the C-variable adjacency audit.

The script removes the two Linker branches that create/reuse a child C-variable
and replaces them with direct canonical TValue lookup. It is intentionally kept
outside production source and is invoked only by the diagnostic branch POM.
"""

from pathlib import Path

path = Path("kanger/src/org/kanger/Linker.java")
text = path.read_text(encoding="utf-8")

replacement = "                                                    TValue s = mind.getTValues().find(t, tm);\n"

blocks = [
    """                                                    TValue s = null;
                                                    if (tm.isCVariable() && tm.getParentId(mind) == -1 && slave.getRuleId() == tm.getRuleId() && !tm.isDomini() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {
                                                        Term tn = (Term) tm.getChild(mind);
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(master.getRule(), tm.getName(mind), tm);
                                                        }
                                                        tm = tn;
                                                    } else {
                                                        s = mind.getTValues().find(t, tm);
                                                    }
""",
    """                                                    TValue s = null;
                                                    if (tm.isCVariable() && tm.getParentId(mind) == -1 && master.getRuleId() == tm.getRuleId() && !tm.isDomini() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {
                                                        Term tn = (Term) tm.getChild(mind);
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(slave.getRule(), tm.getName(mind), tm);
                                                        }
                                                        tm = tn;
                                                    } else {
                                                        s = mind.getTValues().find(t, tm);
                                                    }
""",
]

changed = False
for block in blocks:
    count = text.count(block)
    if count == 1:
        text = text.replace(block, replacement)
        changed = True
    elif count == 0:
        # A second Maven invocation in the same checkout sees the transformed file.
        continue
    else:
        raise SystemExit(f"Unexpected virtual-child block count: {count}")

if changed:
    path.write_text(text, encoding="utf-8")

for forbidden in (
    "mind.getTerms().createCVar(master.getRule(), tm.getName(mind), tm)",
    "mind.getTerms().createCVar(slave.getRule(), tm.getName(mind), tm)",
):
    if forbidden in text:
        raise SystemExit(f"Virtual C-variable child creation remains in Linker: {forbidden}")

print("C-variable audit: Linker virtual child substitution disabled")
