from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, got {count}")
    p.write_text(text.replace(old, new, 1))


# Canonical lookup must prefer an already-existing equivalent Rule over the
# freshly registered candidate itself, so inherited deleted rules can be
# restored in a child transaction instead of being shadowed by a duplicate.
replace_once(
    "kanger/src/org/kanger/factory/RuleFactory.java",
    '''    public IRule find(IRule rule) throws Exception {
        for (long id : cache.find(((Rule) rule).getHash())) {
            IRule one = get(id);
            if (((Rule) one).equalsTo(rule)) {
                return one;
            }
        }
        return null;
    }
''',
    '''    public IRule find(IRule rule) throws Exception {
        IRule self = null;
        for (long id : cache.find(((Rule) rule).getHash())) {
            IRule one = get(id);
            if (((Rule) one).equalsTo(rule)) {
                if (one.getId() != rule.getId()) {
                    return one;
                }
                self = one;
            }
        }
        return self;
    }
''')

# Internal reindex already closes/checkpoints the active storage. Reopening that
# just-cleared root must not enter the user-facing offline-stack classifier,
# whose semantic iterators still point at the closed physical bases.
replace_once(
    "kanger/src/org/kanger/User.java",
    '''        mind = use(mind, name);
        if (data != null && !data.isClosed()) {
            data.reindex(reactor, mind);
        }

        mind = close(mind);
        if (reopened) {
            mind = use(mind, saveName);
        }
''',
    '''        mind = reopened
                ? openClosedStorage((Mind) mind, name)
                : use(mind, name);
        if (data != null && !data.isClosed()) {
            data.reindex(reactor, mind);
        }

        mind = close(mind);
        if (reopened) {
            mind = openClosedStorage((Mind) mind, saveName);
        }
''')

# Once the test explicitly releases its child, publish the root before session
# teardown so runtime cleanup does not attempt a second release.
replace_once(
    "kanger-server/test/org/kanger/CompileSourceBoundaryContractTest.java",
    '''            assertSame(beforeReject, user.getCurrentMind());
            root.release((Mind) user.getCurrentMind());
''',
    '''            assertSame(beforeReject, user.getCurrentMind());
            root.release((Mind) user.getCurrentMind());
            user.setCurrentMind(root);
''')
