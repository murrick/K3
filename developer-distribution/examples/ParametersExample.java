import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;

public final class ParametersExample {

    private ParametersExample() {
    }

    public static void main(String[] args) throws Exception {
        IUser user = new User();
        IMind mind = new Mind(user);

        require(mind.compile(
                "!reading(?, ?);!reading(?, ?);",
                new Object[]{1, 42, 2, 84}),
                "parameterized program was rejected");

        Boolean first = mind.query(
                "?reading(?, ?);",
                new Object[]{1, 42});
        require(Boolean.TRUE.equals(first),
                "first parameterized query did not return TRUE: " + first);

        Boolean second = mind.query(
                "?reading(?, ?);",
                new Object[]{2, 84});
        require(Boolean.TRUE.equals(second),
                "second parameterized query did not return TRUE: " + second);

        System.out.println("PARAMETERS_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
