import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.DataType;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;

import java.util.Arrays;
import java.util.Map;

public final class BinaryDataExample {

    private BinaryDataExample() {
    }

    public static void main(String[] args) throws Exception {
        IUser user = new User();
        IMind mind = new Mind(user);

        byte[] payload = new byte[]{0x02, 0x03, 0x04, 0x05};

        require(mind.compile(
                "!payload(item, ?);",
                new Object[]{payload}),
                "binary program was rejected");

        Boolean exact = mind.query(
                "?payload(item, ?);",
                new Object[]{payload});
        require(Boolean.TRUE.equals(exact),
                "parameterized BLOB query did not return TRUE: " + exact);

        Boolean projected = mind.query("?$value payload(item, value);");
        require(Boolean.TRUE.equals(projected),
                "BLOB projection did not return TRUE: " + projected);

        Map<String, ITerm> row = mind.getValues().iterator().next();
        ITerm term = row.get("value");
        require(term != null, "Values row does not contain value");
        require(term.getType() == DataType.BLOB,
                "projected value is not BLOB: " + term.getType());
        require(term.getValue() instanceof byte[],
                "projected BLOB is not byte[]");
        require(Arrays.equals(payload, (byte[]) term.getValue()),
                "binary round-trip changed payload");

        System.out.println("BINARY_DATA_PASS bytes=" + payload.length);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
