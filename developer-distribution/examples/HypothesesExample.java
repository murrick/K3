import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;

public final class HypothesesExample {

    private HypothesesExample() {
    }

    public static void main(String[] args) throws Exception {
        IUser user = new User();
        IMind mind = new Mind(user);

        require(mind.compile("!@x premise(x) -> target(x);"),
                "hypothesis program was rejected");

        Boolean result = mind.query("?target(item);");
        require(result == null,
                "expected an undetermined result, got " + result);

        mind.optimizeHypothesis();

        int count = 0;
        boolean premiseFound = false;
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            count++;
            if ("premise".equals(hypothesis.getPredicate().getName(mind))) {
                premiseFound = true;
            }
        }

        require(count > 0, "optimized hypothesis set is empty");
        require(premiseFound,
                "optimized hypothesis set does not contain premise(item)");

        System.out.println("HYPOTHESES_PASS count=" + count);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
