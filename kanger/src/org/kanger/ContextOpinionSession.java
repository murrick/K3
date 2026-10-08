/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.CommandErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IContextFederation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/** Session-local, detached query input and explicitly collected source opinions. */
public final class ContextOpinionSession {
    private String query;
    private List<SemanticTermSnapshot> parameters;
    private String state;
    private IMind owner;
    private Map<String, IContextFederation.Opinion> opinions;
    private String collectedLocator;
    private boolean collecting;

    public void invalidate() {
        if (collecting) return;
        query = null; parameters = null; state = null; owner = null; opinions = null;
        collectedLocator = null;
    }

    public void remember(Mind mind, String source, Queue<ITerm> externals) throws Exception {
        if (collecting || !(((User) mind.getUser()).getData() instanceof IContextFederation)) return;
        List<SemanticTermSnapshot> copy = new ArrayList<>();
        for (ITerm term : externals) copy.add(SemanticTermSnapshot.capture(term));
        query = source;
        parameters = Collections.unmodifiableList(copy);
        owner = mind.getUser().getCurrentMind();
        state = state(mind);
        opinions = null;
        collectedLocator = null;
    }

    private String state(Mind mind) throws Exception {
        IContextFederation.Snapshot snapshot = ((IContextFederation) ((User) mind.getUser()).getData()).federationSnapshot();
        StringBuilder key = new StringBuilder().append(snapshot.getSourceContextId()).append('@')
                .append(snapshot.getSourceRevision());
        List<String> pins = new ArrayList<>();
        for (IContextFederation.Connection pin : snapshot.getConnections())
            pins.add(pin.getTargetContextId() + "@" + pin.getPinnedRevision());
        Collections.sort(pins);
        return key.append(pins).toString();
    }

    private void requireCurrent(Mind mind) throws Exception {
        if (query == null || owner != mind || owner != mind.getUser().getCurrentMind()
                || !(((User) mind.getUser()).getData() instanceof IContextFederation)
                || !mind.isStorageUsed() || !state.equals(state(mind))) {
            invalidate();
            throw new CommandErrorException("Run a query in the current Context before collecting opinions");
        }
    }

    public Map<String, IContextFederation.Opinion> collect(Mind mind, String locator) throws Exception {
        requireCurrent(mind);
        if (opinions != null && java.util.Objects.equals(locator, collectedLocator)) return opinions;
        collecting = true;
        try {
            Map<String, IContextFederation.Opinion> result = ((IContextFederation) ((User) mind.getUser()).getData())
                    .executeOpinions(mind, locator, query, parameters);
            opinions = Collections.unmodifiableMap(new LinkedHashMap<>(result));
            collectedLocator = locator;
            return opinions;
        } finally { collecting = false; }
    }

    public Map<String, IContextFederation.Opinion> saved(Mind mind, String locator) throws Exception {
        requireCurrent(mind);
        if (opinions == null) throw new CommandErrorException("Run ctx opinions before viewing source results");
        if (locator == null) return opinions;
        IContextFederation.Opinion selected = opinions.get(locator);
        if (selected == null) throw new CommandErrorException("No saved opinion for " + locator);
        return Collections.singletonMap(locator, selected);
    }
}
