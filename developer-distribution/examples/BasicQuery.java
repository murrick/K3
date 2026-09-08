import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;

/** Minimal standalone KANGER 3.7.0 SDK entry and query example. */
public final class BasicQuery {

    private BasicQuery() {
    }

    public static void main(String[] args) throws Exception {
        IUser user = new User();
        IMind mind = new Mind(user);

        require(mind.compile(
                "!color(apple, Red);"
                        + "!color(cucumber, Green);"
                        + "!color(lemon, Yellow);"),
                "Unable to compile example facts");

        Boolean result = mind.query("?color(apple, Red);");
        require(Boolean.TRUE.equals(result),
                "Expected color(apple, Red) to be true, got " + result);

        System.out.println("BASIC_QUERY_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
