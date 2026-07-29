from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


path = Path("kanger/src/org/kanger/Linker.java")
source = path.read_text(encoding="utf-8")

helper = '''
    private Long currentTermId(IArgument argument) throws Exception {
        if (argument.getType() == ArgumentType.TERM) {
            return argument.getId();
        }
        if (argument.getType() == ArgumentType.TVARIABLE) {
            TVariable variable = (TVariable) argument.getObject(mind);
            TValue current = variable.getCurrent();
            return current == null ? null : current.getValueId();
        }
        return null;
    }

    /**
     * Query-local compatibility check for transient/query/generated fallback
     * candidates. It uses only already selected current TValue assignments;
     * unresolved and dynamic arguments remain wildcards. Normal unification is
     * still authoritative for every retained candidate.
     */
    private boolean mayMatchCurrent(Domain slave, IRule candidate) throws Exception {
        for (List<Domain> branch : ((Rule) candidate).getTree()) {
            for (Domain master : branch) {
                if (master.getPredicateId() != slave.getPredicateId()
                        || master.isAntc() == slave.isAntc()
                        || master.getRange() != slave.getRange()) {
                    continue;
                }

                boolean compatible = true;
                for (int position = 0; position < slave.getRange(); ++position) {
                    Long slaveTermId = currentTermId(slave.get(position));
                    Long masterTermId = currentTermId(master.get(position));
                    if (slaveTermId != null
                            && masterTermId != null
                            && !slaveTermId.equals(masterTermId)) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    return true;
                }
            }
        }
        return false;
    }

'''

source = replace_once(
    source,
    "    private void addOppositeNatives(Set<IRule> ruleSet,\n",
    helper + "    private void addOppositeNatives(Set<IRule> ruleSet,\n",
    "query-local compatibility helper",
)

source = replace_once(
    source,
    "        for (IRule candidate : candidates) {\n"
    "            if (allowedIds.contains(candidate.getId())) {\n"
    "                filtered.add(candidate);\n"
    "            }\n"
    "        }\n",
    "        for (IRule candidate : candidates) {\n"
    "            if (allowedIds.contains(candidate.getId())\n"
    "                    && mayMatchCurrent(slave, candidate)) {\n"
    "                filtered.add(candidate);\n"
    "            }\n"
    "        }\n",
    "resolved candidate post-filter",
)

path.write_text(source, encoding="utf-8")
