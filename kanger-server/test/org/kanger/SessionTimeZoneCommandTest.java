package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kanger.account.RegistrationPolicy;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.time.DateTimeException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionTimeZoneCommandTest {

    private IUser user;

    @AfterEach
    void tearDown() throws Exception {
        if (user != null) {
            UserFactory.dropUser(user);
        }
    }

    @Test
    void changesTimeZoneInsideExistingActiveSession() throws Exception {
        user = new User();
        user.setId(370091L);
        user.setTimeZone("Europe/Brussels");
        String token = UserFactory.addUser(user);
        AtomicBoolean delegated = new AtomicBoolean(false);
        SessionSerializingReactor reactor = reactor(delegated);

        JSONObject result = (JSONObject) reactor.run(packet(token, "Asia/Tokyo"));

        assertEquals("OK", result.getString("result"));
        assertEquals("Asia/Tokyo", result.getString("timezone"));
        assertEquals("Asia/Tokyo", user.getTimeZone());
        assertSame(user, UserFactory.getUser(token));
        assertFalse(delegated.get());
    }

    @Test
    void invalidTimeZoneLeavesActiveSessionUnchanged() throws Exception {
        user = new User();
        user.setId(370092L);
        user.setTimeZone("Europe/Brussels");
        String token = UserFactory.addUser(user);
        AtomicBoolean delegated = new AtomicBoolean(false);
        SessionSerializingReactor reactor = reactor(delegated);

        assertThrows(DateTimeException.class,
                () -> reactor.run(packet(token, "Not/A_Time_Zone")));

        assertEquals("Europe/Brussels", user.getTimeZone());
        assertSame(user, UserFactory.getUser(token));
        assertFalse(delegated.get());
    }

    private static SessionSerializingReactor reactor(AtomicBoolean delegated) {
        return new SessionSerializingReactor(
                RegistrationPolicy.TRUSTED,
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject packet) {
                        delegated.set(true);
                        return new JSONObject().put("result", "delegated");
                    }
                });
    }

    private static JSONObject packet(String token, String timeZone) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("timezone", timeZone)));
    }
}
