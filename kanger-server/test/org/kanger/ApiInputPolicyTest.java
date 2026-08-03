package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiInputPolicyTest {

    @Test
    void acceptsOrdinarySourceAndNamespaceNames() {
        assertTrue(ApiInputPolicy.isSafeLeafName("mind.k"));
        assertTrue(ApiInputPolicy.isSafeLeafName("Пример 1.k"));
        assertTrue(ApiInputPolicy.isSafeStorageName("main.data.store"));
        assertTrue(ApiInputPolicy.isSafeStorageName("diagnostic-2026"));

        JSONObject parameters = new JSONObject()
                .put("get", "mind.k")
                .put("use", "main.data.store");
        assertNull(ApiInputPolicy.violation(parameters));
    }

    @Test
    void rejectsUnixAndWindowsTraversalForms() {
        assertFalse(ApiInputPolicy.isSafeLeafName("../users.conf"));
        assertFalse(ApiInputPolicy.isSafeLeafName("/etc/passwd"));
        assertFalse(ApiInputPolicy.isSafeLeafName("..\\users.conf"));
        assertFalse(ApiInputPolicy.isSafeLeafName("C:\\temp\\mind.k"));
        assertFalse(ApiInputPolicy.isSafeLeafName("safe..hidden.k"));
    }

    @Test
    void rejectsUnsafeStorageNamespaces() {
        assertFalse(ApiInputPolicy.isSafeStorageName("../main"));
        assertFalse(ApiInputPolicy.isSafeStorageName("main/data"));
        assertFalse(ApiInputPolicy.isSafeStorageName("main\\data"));
        assertFalse(ApiInputPolicy.isSafeStorageName(".main"));
        assertFalse(ApiInputPolicy.isSafeStorageName("main."));
        assertFalse(ApiInputPolicy.isSafeStorageName("main..data"));
        assertFalse(ApiInputPolicy.isSafeStorageName("C:main"));
    }

    @Test
    void violationNamesParameterWithoutEchoingAttackerValue() {
        String attackerValue = "../../secret-users.conf";
        JSONObject violation = ApiInputPolicy.violation(
                new JSONObject().put("put", attackerValue));

        assertEquals("error", violation.getString("result"));
        assertTrue(violation.getString("description").contains("put"));
        assertFalse(violation.toString().contains(attackerValue));
    }

    @Test
    void emptyValuesRemainAvailableForListAndCurrentSelectionOperations() {
        JSONObject parameters = new JSONObject()
                .put("get", "")
                .put("use", "")
                .put("drop", "");

        assertNull(ApiInputPolicy.violation(parameters));
    }
}
