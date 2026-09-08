import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;

/** Explicit child-Mind transaction example for KANGER 3.7.0. */
public final class TransactionExample {

    private TransactionExample() {
    }

    public static void main(String[] args) throws Exception {
        IUser user = new User();
        IMind root = new Mind(user);

        require(root.compile("!color(apple, Red);"),
                "Unable to compile root fact");

        IMind transaction = new Mind(root);
        require(transaction.compile("!color(cucumber, Green);"),
                "Unable to compile transaction fact");
        require(root.commit(transaction),
                "Transaction commit was rejected");

        Boolean result = root.query("?color(cucumber, Green);");
        require(Boolean.TRUE.equals(result),
                "Committed fact is not visible in the parent Mind: " + result);

        System.out.println("TRANSACTION_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
