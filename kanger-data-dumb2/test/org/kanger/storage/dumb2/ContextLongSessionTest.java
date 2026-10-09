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

    @Test
    void squashRebaseAndRejectedPublicationsPreserveRollbackAndReclaimLayers() throws Exception {
        ContextStackSessionProbe.main(new String[] { "10" });
    }

    @Test
    void topologyOnlyCyclesReclaimLayersWithoutInvalidatingLiveCheckpoints() throws Exception {
        ContextTopologySessionProbe.main(new String[] { "40" });
    }
}
