package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;

class ContextLongSessionTest {
    @Test
    void equivalentCyclesReclaimLayersAndPreserveNestedCheckpoints() throws Exception {
        ContextLongSessionProbe.main(new String[] { "40" });
    }

    @Test
    void publicationsAndHistoricalForksRetainPinnedValuesAsCurrentAdvances() throws Exception {
        ContextPinnedSessionProbe.main(new String[] { "20" });
    }
}
