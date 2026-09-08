import org.kanger.Mind;
import org.kanger.User;
import org.kanger.ValuesOrder;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;

import java.util.List;
import java.util.Map;

public final class ValuesExample {

    private ValuesExample() {
    }

    public static void main(String[] args) throws Exception {
        IUser user = new User();
        IMind mind = new Mind(user);

        require(mind.compile(
                "!score(Alice, 3);" +
                "!score(Bob, 1);" +
                "!score(Carol, 2);"),
                "score program was rejected");

        Boolean result = mind.query("?$name $score score(name, score);");
        require(Boolean.TRUE.equals(result), "expected TRUE, got " + result);

        List<Map<String, ITerm>> ascending = mind.getValues(ValuesOrder.asc("score"));
        require(ascending.size() == 3,
                "expected three Values rows, got " + ascending.size());
        require(number(ascending.get(0), "score") == 1.0,
                "ascending row 0 is not score=1");
        require(number(ascending.get(1), "score") == 2.0,
                "ascending row 1 is not score=2");
        require(number(ascending.get(2), "score") == 3.0,
                "ascending row 2 is not score=3");

        List<Map<String, ITerm>> descending = mind.getValues(ValuesOrder.desc("score"));
        require(number(descending.get(0), "score") == 3.0,
                "descending row 0 is not score=3");
        require(number(descending.get(2), "score") == 1.0,
                "descending row 2 is not score=1");

        System.out.println("VALUES_PASS rows=" + ascending.size());
    }

    private static double number(Map<String, ITerm> row, String field) {
        ITerm term = row.get(field);
        require(term != null, "Values field is missing: " + field);
        Object value = term.getValue();
        require(value instanceof Number,
                "Values field is not numeric: " + field + "=" + value);
        return ((Number) value).doubleValue();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
