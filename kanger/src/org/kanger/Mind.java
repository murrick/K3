package org.kanger;

import org.kanger.calculator.Calculator;
import org.kanger.compiler.Compiler;
import org.kanger.compiler.PTree;
import org.kanger.compiler.Parser;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.enums.QueryPass;
import org.kanger.enums.Tools;
import org.kanger.factory.*;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Cause;
import org.kanger.primitives.TVariableSet;
import org.kanger.stores.HypothesisStore;
import org.kanger.stores.LogStore;
import org.kanger.stores.SolutionsStore;
import org.kanger.stores.ValuesStore;
import org.kanger.units.*;

import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Mind {

    private static final boolean DEBUG_DISABLE_FALSE_CHECK = false;
    private static final int FLOOD_CONTROL_LIMIT = 1000;

    //    private volatile boolean blockCommit = false;
    private final Object locker = new Object();

    private final Map<Long, Set<Rule>> usedRules = new HashMap<>();
    private final Map<Domain, Set<ArgList>> usedDomains = new HashMap<>();
    private final Map<Domain, Set<ArgList>> excludedDomains = new HashMap<>();
    private final Map<Domain, List<List<Term>>> calculatedDomains = new HashMap<>();
    private final Map<Domain, List<List<Term>>> producedDomains = new HashMap<>();
    private final Map<Domain, Map<ArgList, Set<Cause>>> domainCauses = new HashMap<>();
    private final Map<Domain, Map<ArgList, SortedSet<TValue>>> domainSolves = new HashMap<>();
    private final Map<TVariable, Set<TValue>> queryValues = new HashMap<>();
    private final Map<TVariableSet, List<TSolve>> ruleSolves = new LinkedHashMap<>();
    private final Map<TVariable, long[]> floodControl = new HashMap<>();

    private long id = 0;
    private Mind next = null;

    private DictionaryFactory terms = null;                    // Словарь констант
    private PredicateFactory predicates = null;                 // Предикаты
    private DomainFactory domains = null;                          // Список доменов
    private RuleFactory rules = null;                             // Список правил
    private TVariableFactory tVars = null;                      // t-переменные
    private TValueFactory tValues = null;                          // Подставленные значения
    private FunctionFactory functions = null;                    // Функции
    private FValueFactory fValues = null;                          // Решения функций
    private CommentFactory comments = null;


    private IUser user = null;

    private SolutionsStore solves = null;                         // Список решений
    private ValuesStore values = null;                               // Список значений
    private LogStore log = null;                                        // Протокол вывода
    private LibraryFactory library = null;                            // Системная библиотека функций и предикатов
    private HypothesisStore hypothesis = null;                                // Список гипотез

    private Calculator calculator = null;                             // Калькулятор
    private Analyzer analyzer = null;                                   // Анализатор
    private Compiler compiler = null;                                   // Компилятор
    private Linker linker = null;                                         // Линкер

    private boolean changed = false;
    private Boolean queryResult = null;
    private String querySource = "";
    //    private Mind queryContext = null;
    private QueryPass queryPass = QueryPass.SILENCE;
    private String sourceFileName = "mind.k";
    private String compiledFileName = "mind.e";
    private boolean logging = true;

    private int debugLevel = Enums.DEBUG_LEVEL_DEBUG | (Enums.DEBUG_OPTION_VALUES);
    private Stack<Integer> debugLevelStack = new Stack<>();

    private String compliedLine = "";
    private Rule acceptedRule = null;
    private int floodControlLimit = FLOOD_CONTROL_LIMIT;
//    private HypothesisStore excluded = null;                                // Список исключенных гипотез

//    private volatile boolean busyCommit = false;

    public Mind(IUser user) throws Exception {
        this.user = user;
//        user.setMind(this);
        init();
        clear();
    }

    public Mind(Mind root) throws Exception {
        next = root;
        user = root.getUser();
//        user.setMind(this);
        id = user.nextId(); //root.getId() + 1;
        init();

//        terms.transaction(root.getTerms());
//        predicates.transaction(root.getPredicates());
//        library.transaction(root.getLibrary());

//        synchronized (locker) {
        terms = root.getTerms();
        predicates = root.getPredicates();
        library = root.getLibrary();

//            rightSolves.putAll(root.getRightSolves());


//        functions = root.getFunctions();

        domains.transaction(root.getDomains());
        rules.transaction(root.getRules());
        comments.transaction(root.getComments());
        tVars.transaction(root.getTVars());
        tValues.transaction(root.getTValues());
        functions.transaction(root.getFunctions());
        fValues.transaction(root.getFValues());

        debugLevel = root.getDebugLevel();
//        domainCauses.putAll(root.getDomainCauses());

//        private final LibraryStore library = new LibraryStore(this);                            // Системная библиотека функций и предикатов
//        }
    }

    private void init() throws Exception {
//        synchronized (user.getLocker()) {
        terms = new DictionaryFactory(this);                    // Словарь констант
        predicates = new PredicateFactory(this);                 // Предикаты
        functions = new FunctionFactory(this);                    // Функции
        library = new LibraryFactory(this);                            // Пользовательсткая библиотека функций и предикатов

        domains = new DomainFactory(this);                          // Список доменов
        rules = new RuleFactory(this);                             // Список правил
        comments = new CommentFactory(this);
//        trees = new TreeFactory(user);                                // Список секвенций

        tVars = new TVariableFactory(this);                      // t-переменные
        tValues = new TValueFactory(this);                          // Подставленные значения

        fValues = new FValueFactory(this);                          // Решения функций


        hypothesis = new HypothesisStore(this);                                // Список гипотез
//        excluded = new HypothesisStore(this);                                // Список исключенных гипотез
        solves = new SolutionsStore(this);                         // Список решений
        values = new ValuesStore(this);                               // Список значений
//        results = new ResultsStore(user);                               // Список значений

        log = new LogStore(this);                                        // Протокол вывода

        calculator = new Calculator(this);                             // Калькулятор
        compiler = new Compiler(this);                                   // Компилятор
        analyzer = new Analyzer(this);                                   // Анализатор
        linker = new Linker(this);                                         // Линкер

        floodControlLimit = Integer.parseInt(user.getProperty("flood.limit", FLOOD_CONTROL_LIMIT + ""));
//        }
    }


    public boolean commit(Mind m) throws Exception {
        boolean result = true;
//        pack();
        synchronized (locker) {

            boolean sequencedBy = rules.isSequencedBy(m.getRules());
            if (!sequencedBy) {
                functions.mark();
                fValues.mark();
                tVars.mark();
                tValues.mark();

                domains.mark();
                rules.mark();
                comments.mark();
            }

            functions.commit(m.getFunctions());
            fValues.commit(m.getFValues());
            tVars.commit(m.getTVars());
            tValues.commit(m.getTValues());

            domains.commit(m.getDomains());
            Set<Long> list = rules.commit(m.getRules());
            comments.commit(m.getComments());

            if (!sequencedBy) {
                Boolean res = analyzer.checkDatabase(list, false);
                if (res != null && res) {
                    functions.release();
                    fValues.release();
                    tVars.release();
                    tValues.release();

                    domains.release();
                    rules.release();
                    comments.release();
                    result = false;
                } else {

//                    terms.update();
//                    predicates.update();
//                    library.update();

                    functions.commit();
                    fValues.commit();
                    tVars.commit();
                    tValues.commit();

                    domains.commit();
                    rules.commit();
                    comments.commit();

//                    functions.update();
//                    fValues.update();
//                    tVars.update();
//                    tValues.update();
//
//                    domains.update();
//                    rights.update();
//
//                    if(next == null) {
//                        user.flush();
//                    }

                }
            }

//                for (Map.Entry<TVariableSet, List<TSolve>> e : m.getRightSolves().entrySet()) {
//                    for (TSolve t : e.getValue()) {
//                        addTSolve(t.getSolve());
//                    }
//                }
//        }
            pack();
            update();

//            terms.pack();
//            predicates.pack();
//            library.pack();
//
//            tValues.pack();
//            tVars.pack();
//            domains.pack();
//            rights.pack();
//            fValues.pack();
//            functions.pack();
//
//
//            if (next == null) {
//                terms.update();
//                predicates.update();
//                library.update();
//
//                functions.update();
//                fValues.update();
//                tVars.update();
//                tValues.update();
//                domains.update();
//                rights.update();
//
//                user.flush();
//            }

            log.commit(m.getLog());
            queryResult = (Boolean) m.getQueryResult();

//            m.closeConnection();

        }

//        pack();
//        update();

        return result;

//        }
    }

    public void closeConnection() throws Exception {
        if (next != null) {
            terms.closeConnection();
            predicates.closeConnection();
            library.closeConnection();
            functions.closeConnection();
            fValues.closeConnection();
            tVars.closeConnection();
            tValues.closeConnection();
            domains.closeConnection();
            rules.closeConnection();
            comments.closeConnection();
        }
    }

    public void update() throws Exception {
//        synchronized (locker) {

        if (next == null) {
            terms.update();
            predicates.update();
            library.update();

            functions.update();
            fValues.update();
            tVars.update();
            tValues.update();
            domains.update();
            rules.update();
            comments.update();

            user.flush();
        }
//        }
    }

    public void release(Mind m) throws Exception {
        synchronized (locker) {

            log.commit(m.getLog());

            solves.commit(m.getSolutions());
            values.commit(m.getValues());
//        results.commit(m.getResults());

            // Сброс индексов связи предикаторв
//        terms.unlink();
//        tVars.unlink();
//        tValues.unlink();
//        fValues.unlink();
//        predicates.unlink();
//        domains.unlink();
//        rights.unlink();
//        functions.unlink();
//        library.unlink();

            queryResult = (Boolean) m.getQueryResult();

//            m.closeConnection();

        }
//        querySource = m.getQuerySource();
    }

    public void clear() throws Exception {
        synchronized (locker) {
            terms.clear();
            predicates.clear();
            library.clear();

            domains.clear();
            tVars.clear();
            tValues.clear();
            rules.clear();
            comments.clear();
            functions.clear();
            fValues.clear();

            solves.clear();
            values.clear();
            hypothesis.clear();
//            excluded.clear();

            ruleSolves.clear();
        }
//        update();
    }

    public void pack() throws Exception {
        synchronized (locker) {
            terms.pack();
            predicates.pack();
            library.pack();

            tValues.pack();
            tVars.pack();
            domains.pack();
            rules.pack();
            comments.pack();
            fValues.pack();
            functions.pack();
        }
//        tValues.update();

//        update();
//        user.getMind().getTValues().update();
    }


    public QueryPass getQueryPass() {
        return queryPass;
    }

    public void setQueryPass(QueryPass queryPass) {
        this.queryPass = queryPass;
    }

    public void pushDebugLevel() {
        debugLevelStack.push(debugLevel);
    }

    public void popDebugLevel() {
        debugLevel = debugLevelStack.pop();
    }

    public IUser getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public long getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Mind getNext() {
        return next;
    }

    public void setNext(Mind next) {
        this.next = next;
    }

    public int getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(int debugLevel) {
        this.debugLevel = debugLevel;
    }

    public DictionaryFactory getTerms() {
        return terms;
    }

    public PredicateFactory getPredicates() {
        return predicates;
    }

    public DomainFactory getDomains() {
        return domains;
    }

    public ValuesStore getValues() {
        return values;
    }

    public RuleFactory getRules() {
        return rules;
    }

    public CommentFactory getComments() {
        return comments;
    }

    //    public ResultsStore getResults() {
//        return results;
//    }

//    public TreeFactory getTrees() {
//        return trees;
//    }

    public TVariableFactory getTVars() {
        return tVars;
    }

    public FunctionFactory getFunctions() {
        return functions;
    }

    public LibraryFactory getLibrary() {
        return library;
    }

    public HypothesisStore getHypothesisStore() {
        return hypothesis;
    }

//    public HypothesisStore getExcludedHypothesis() {
//        return excluded;
//    }

    public LogStore getLog() {
        return log;
    }

    public SolutionsStore getSolutions() {
        return solves;
    }

    public TValueFactory getTValues() {
        return tValues;
    }

    public FValueFactory getFValues() {
        return fValues;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean b) {
        changed = b;
    }

    public void link(Rule r, boolean logging) throws Exception {
        linker.link(r, logging);
    }

    public Boolean analyze(Rule rule, boolean logging) throws Exception {
        return analyzer.analyze(rule, logging);
    }

    public boolean compile(String src) throws Exception {
        return compile(src, true);
    }

    public boolean compile(String src, boolean logging) throws Exception {
        this.logging = logging;

        int pos = 0;
        Object[] t = null;
        Mind m = new Mind(this);
        m.setQueryPass(QueryPass.ACCEPT);
        int previousPos = 0;
        while ((t = Tools.extractLine(src, pos)) != null) {
            pos = (int) t[1];
            String line = (String) t[0];
            String comment = src.substring(previousPos, pos - ((String) t[0]).length()).trim();
            if (previousPos == 0) {
                String[] cc = Parser.extractComments(comment);
                if (cc.length > 1 && !cc[0].isEmpty()) {
                    m.getComments().add(CommentFactory.HEADER_ID, cc[0].trim());
                    comment = comment.substring(cc[0].length()).trim();
                }
            }
            previousPos = pos;

            Object r = m.compileLine(line, false, null);
            if (!comment.isEmpty() && r instanceof Rule) {
                m.getComments().add(((Rule) r).getId(), comment);
            }

//            Mind x = new Mind(m);
//            setCompliedLine(line);
//            Object r = x.compileLine(line, false, null);
//            if (r instanceof Right && ((Right) r).isDeleted()) {
//                m.release(x);
//                m.getLog().add(LogMode.ANALIZER, "WARNING: Right is duplicated: " + r);
//            } else if (r instanceof Right) {
//                m.commit(x);
//                m.getLog().add(LogMode.ANALIZER, "Compiled: " + ((Right) r).getOrig());
//                m.getLog().add(LogMode.ANALIZER, (Right) r);
//                for (Right rx : m.rights) {
//                    if (rx.getId() > ((Right) r).getId() && rx.isGenerated()) {
//                        m.getLog().add(LogMode.ANALIZER, "Extracted: " + rx.getOrig());
//                    }
//                }
//
//            }
        }

        if (src.length() > pos) {
            String comment = src.substring(pos).trim();
            if (!comment.isEmpty()) {
                m.getComments().add(CommentFactory.FOOTER_ID, comment);
            }
        }

        m.link(null, logging);
        Boolean ar = m.analyze(null, logging);

        if (ar) {
            if (logging) {
                m.getLog().add(LogMode.ANALYZER, "ERROR: Collisions in Program");
            }
            release(m);
            return false;
        } else {
            if (logging) {
                m.getLog().add(LogMode.ANALYZER, "SUCCESS: No Collisions in Program");
            }

            commit(m);
//            excluded.clear();
//            excluded.commit(m.getHypothesisStore());

            return true;
        }
    }

    public Object compileLine(String line, boolean query, Object[] ext) throws Exception {
        String orig = line.trim();
        compliedLine = orig;
        Object r = null;
        Boolean suc = null;

        switch (line.charAt(0)) {
            case Enums.FOO:
                r = Parser.implement(line, this);
                if (r != null) {
                    if (((SysOp) r).getScripts().isEmpty()) {
                        library.delete((SysOp) r);
                    }
                    library.add((SysOp) r);
                }
                break;
            case Enums.INS:
            case Enums.ANT:
                suc = true;
                break;
            case Enums.DEL:
//            case Enums.WIPE:
            case Enums.SUC:
                suc = false;
                break;
        }
        if (suc != null) {
            Mind x = new Mind(this);

            PTree p = Parser.parser(line.substring(1));
            r = x.compiler.compileLine(p, suc, orig, query, ext);
            x.setCompliedLine(line);
            if (r instanceof Rule && ((Rule) r).isDeleted()) {
                release(x);
                getLog().add(LogMode.ANALYZER, "WARNING: Rule is duplicated: " + r);
                r = null;
            } else if (r instanceof Rule) {
                commit(x);
                getLog().add(LogMode.ANALYZER, "Compiled: " + ((Rule) r).getOrig());
                getLog().add(LogMode.ANALYZER, (Rule) r);
                for (Rule rx : rules) {
                    if (rx.getId() > ((Rule) r).getId() /*&& rx.isGenerated()*/) {
                        getLog().add(LogMode.ANALYZER, "Extracted: " + rx.getOrig());
                    }
                }
            }
        }
        return r;
    }


    /**
     * Удаление правила из дерева вывода
     * <p>
     * <p>
     * //     * @param r
     */
//    private void removeRightRecord(Right r) {
//        if (rights.getRoot() == r) {
//            rights.setRoot(r.getNext());
//        } else {
//            for (Right p = rights.getRoot(); p != null; p = p.getNext()) {
//                if (p.getNext() == r) {
//                    p.setNext(r.getNext());
//                    break;
//                }
//            }
//        }
//    }
//
//    private void removeTreeRecords(Right r) {
//        Tree o = null;
//        for (Tree t = trees.getRoot(); t != null; t = t.getNext()) {
//
//            if (t.getRight() == r) {
//                if (o == null) {
//                    trees.setRoot(t.getNext());
//                } else {
//                    o.setNext(t.getNext());
//                }
//            } else {
//                o = t;
//            }
//        }
//    }
//
//    private void removeDomainRecords(Right r) {
//        Domain o = null;
//        for (Domain t = domains.getRoot(); t != null; t = t.getNext()) {
//
//            if (t.getRight() == r) {
//                if (o == null) {
//                    domains.setRoot(t.getNext());
//                } else {
//                    o.setNext(t.getNext());
//                }
//            } else {
//                o = t;
//            }
//        }
//    }
//
//    private void removeTVarRecords(Right r) {
//        TVariable o = null;
//        for (TVariable t = tVars.getRoot(); t != null; t = t.getNext()) {
//
//            if (t.getRight() == r) {
//                if (o == null) {
//                    tVars.setRoot(t.getNext());
//                } else {
//                    o.setNext(t.getNext());
//                }
//            } else {
//                o = t;
//            }
//        }
//    }
//
//    private void removeCVarRecords(Right r) {
//        Term o = null;
//        for (Term t = terms.getRoot(); t != null; t = t.getNext()) {
//            if (t.getRight() == r && t.isCVariable()) {
//                if (o == null) {
//                    terms.setRoot(t.getNext());
//                } else {
//                    o.setNext(t.getNext());
//                }
//            } else {
//                o = t;
//            }
//        }
//    }
//
//    public void removeInsertionRight(Right r) {
//        reset();
//
//        removeTVarRecords(r);
//        removeCVarRecords(r);
//        removeDomainRecords(r);
//        removeTreeRecords(r);
//        removeRightRecord(r);
//
////        mark();
//    }
    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String name) {
        sourceFileName = name;
    }

    public String getCompiledFileName() {
        return compiledFileName;
    }

    public void setCompiledFileName(String compiledFileName) {
        this.compiledFileName = compiledFileName;
    }

    public Boolean query(String line) throws Exception {
        return query(line, null);
    }

    public Boolean query(String line, Object[] ext) throws Exception {
        querySource = line;
        queryPass = QueryPass.SILENCE;
//        queryContext = null;
        queryResult = query(line, ext, true);
        return queryResult;
    }

    public String getCompliedLine() {
        return compliedLine;
    }

    public void setCompliedLine(String compliedLine) {
        this.compliedLine = compliedLine;
    }

    public String getVersion() {
        return Version.VERSION_S;
    }

    public Map<Domain, Set<ArgList>> getUsedDomains() {
        return usedDomains;
    }

    public Map<Domain, Set<ArgList>> getExcludedDomains() {
        return excludedDomains;
    }

    public Map<Domain, List<List<Term>>> getProducedDomains() {
        return producedDomains;
    }

    public Map<Domain, List<List<Term>>> getCalculatedDomains() {
        return calculatedDomains;
    }

    public Map<Domain, Map<ArgList, Set<Cause>>> getDomainCauses() {
        return domainCauses;
    }

    public Map<Domain, Map<ArgList, SortedSet<TValue>>> getDomainSolves() {
        return domainSolves;
    }

    public Map<Long, Set<Rule>> getUsedRules() {
        return usedRules;
    }

    public Map<TVariableSet, List<TSolve>> getRuleSolves() {
        return ruleSolves;
    }

    public Map<TVariable, long[]> getFloodControl() {
        return floodControl;
    }

    //    public Map<Domain, Map<ArgList, Set<Long>>> getDomainTags() {
//        return domainTags;
//    }

//    public Map<Long, Set<List<Long>>> getStoredDomains() {
//        return storedDomains;
//    }

//    public Set<Long> getUsedTrees() {
//        return usedTrees;
//    }

//    public Set<Long> getClosedTrees() {
//        return closedTrees;
//    }

//    public Set<Function> getDefined() {
//        return defined;
//    }

//    public Set<Long> getAcceptorDomains() {
//        return acceptorDomains;
//    }
//
//    public void markAcceptors() {
//        markAcceptor.reset();
//        markAcceptor.addAll(acceptorDomains);
//    }
//
//    public void releaseAcceptors() {
//        acceptorDomains.reset();
//        acceptorDomains.addAll(markAcceptor);
//    }

//    public Set<Long> getQueuedDomains() {
//        return queuedDomains;
//    }


    public Map<TVariable, Set<TValue>> getQueryValues() {
        return queryValues;
    }

    //TODO: Для отладки все закоментил
//    public Set<Right> getActualRights() {
//        Set<Right> set = new HashSet<>();
////        if (substituted.isEmpty() && calculated.isEmpty()) {
//        for (Right r = rights.getRoot(); r != null; r = r.getNext()) {
//            set.add(r);
//        }
////        } else {
////            if (tVars.size() > 0) {
////                for (TVariable t : substituted) {
////
////                    for (Domain d : t.getUsage()) {
////                        set.addAll(d.getPredicate().getRights());
////                    }
////
////                }
////            }
//////            for (Function f : calculated) {
//////                set.addAll(f.getOwner().getPredicate().getRights());
//////            }
////        }
//        return set;
//    }

    public String getQuerySource() {
        return querySource;
    }

    public Object getQueryResult() {
        return queryResult;
    }

//    public Right getQuery() {
//        for (Right r = rights.getRoot(); r != null; r = r.getNext()) {
//            if (r.isQuery()) {
//                return r;
//            }
//        }
//        return null;
//    }

    public boolean isSystem(Predicate p) throws Exception {
        return calculator.exists(p);
    }

//    public boolean isSystem(Function f) {
//        return calculator.exists(f);
//    }

    public int executeSystem(Domain d) throws Exception {
        for (int i = 0; i < d.getRange(); ++i) {
            if (d.getArguments().get(i).isFSet() && d.getArguments().get(i).getF(this).isCalculable() && d.getArguments().get(i).getF(this).isEmpty()) {
//                d.getArguments().get(i).getF().clear();
                calculator.calculate(d.getArguments().get(i).getF(this), logging);
            }
        }

        return calculator.execute(d);
    }

//    public int executeSystem(Function f) throws Exception {
//        return calculator.execute(f);
//    }

//    public Queue<Tree> getActualTrees() {
//        Queue<Tree> set = new LinkedList<>();
//        for (Right r : getActualRights()) {
//            set.addAll(r.getTree());
//        }
//        return set;
//    }


    //TODO: Ограничить область опредедения: !num(0); !@x num(x) && x < 10 -> num(++x);      ?$x num(x);      --- ВКЛЮЧАЕТ 10
    //TODO: Вывод гипотез группами. При нескольких вариантах это возможно
    //TODO: Вывод результатов по группам, группа - один проход, варианты решений - группами в Storage
    //TODO: Mind - наследование вместо всяких mark/release
    //TODO: Оптимизация!!!!


    /////////////////////////////////////
    private String invert(String line) {
        if (line.charAt(0) == Enums.SUC) {
            return String.format("%c%s", Enums.ANT, line.substring(1));
        } else {
            return String.format("%c%s", Enums.SUC, line.substring(1));
        }
    }

    private String antc(String line) {
        return String.format("%c%s", Enums.ANT, line);
    }

    private String succ(String line) {
        return String.format("%c%s", Enums.SUC, line);
    }

    private String resign(int sign, String line) {
        return String.format("%c%s", sign, line.substring(1));
    }

    public List<Rule> getProductions(Rule r) {
        List<Rule> productions = new ArrayList<>();
        for (Rule pr : getRules()) {
            if (pr.getId() > r.getId()) {
                if (pr.isGenerated()) {
                    productions.add(pr);
                }
            } else {
                break;
            }
        }
        return productions;
    }

    public Boolean queryInsert(String line, Object[] ext, boolean logging) throws Exception {
        Boolean res = null;
        Mind m = new Mind(this);
        m.setQueryPass(QueryPass.INSERT);
        if (logging) {
            getLog().add(LogMode.ANALYZER, "============= INSERT ======================");
        }

        line = invert(line);
        line = invert(line);

        setCompliedLine(line);
        Rule r = (Rule) m.compileLine(line, true, ext);
        if (r != null) {

            m.link(r, logging);
            boolean ar = m.analyze(r, logging);
            if (ar) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "ERROR: Conflict in new rule");
                }
                release(m);
                res = null;
            } else {

                List<Rule> productions = m.getProductions(r);
                if (!productions.isEmpty()) {
                    if (logging) {
                        m.getLog().add(LogMode.ANALYZER, "SUCCESS: Solves to append (" + productions.size() + "):");
                    }
                    for (Rule pr : productions) {
                        pr.setQuery(false);
                        pr.setGenerated(false);
                        pr.primitivize();
                        if (logging) {
                            m.getLog().add(LogMode.SOLVES, String.format("\tProduced %03d:\t%s", pr.getId(), pr.toString()));
                        }
                    }
                } else if (logging) {
                    m.getLog().add(LogMode.ANALYZER, String.format("WARNING: No candidates to append"));
                }

                m.getRules().delete(r);
                m.getComments().delete(r.getId());

                commit(m);
                setChanged(true);
                res = true;
            }
        } else {
            if (logging && r != null && r.isDeleted()) {
                m.getLog().add(LogMode.ANALYZER, "WARNING: Right is duplicated: " + r);
            }
            release(m);
        }

        hypothesis.clear();
        return res;
    }

    public Boolean queryAccept(String line, Object[] ext, boolean logging) throws Exception {
        Boolean res = null;
        Mind m = new Mind(this);
        m.setQueryPass(QueryPass.ACCEPT);
        if (logging) {
            getLog().add(LogMode.ANALYZER, "============= ACCEPTING ===================");
        }

        setCompliedLine(line);
        Rule r = (Rule) m.compileLine(line, false, ext);
        if (r != null) {
            boolean ar = m.analyze(r, logging);
            if (ar) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "ERROR: Conflict in new rule");
                }
                release(m);
                res = null;
            } else {
                release(m);
                m.link(r, logging);
                ar = m.analyze(r, logging);
                if (ar) {
                    if (logging) {
                        m.getLog().add(LogMode.ANALYZER, "ERROR: Conflict in new rule");
                    }
                    release(m);
                    res = null;
                } else {
                    if (logging) {
                        m.getLog().add(LogMode.SOLVES, String.format("\tSolution %03d:\t%s", r.getId(), r.toString()));
                        List<Rule> productions = m.getProductions(r);
                        if (!productions.isEmpty()) {
                            for (Rule pr : productions) {
                                m.getLog().add(LogMode.SOLVES, String.format("\tProduced %03d:\t%s", pr.getId(), pr.toString()));
                            }
                        }
                        m.getLog().add(LogMode.ANALYZER, "SUCCESS: New rule accepted");
                    }
                    commit(m);
                    setChanged(true);
                    acceptedRule = r;
                    res = true;
                }
            }
        } else {
            if (logging && r != null && r.isDeleted()) {
                m.getLog().add(LogMode.ANALYZER, "WARNING: Right is duplicated: " + r);
            }
            release(m);
        }

        hypothesis.clear();
        return res;
    }

    public Boolean queryDelete(String line, Object[] ext, boolean logging) throws Exception {
        Boolean res = null;
        Mind m = new Mind(this);
        m.setQueryPass(QueryPass.DELETE);
        if (logging) {
            getLog().add(LogMode.ANALYZER, "============= DELETE ======================");
        }

        line = invert(line);
        setCompliedLine(line);
        Rule r = (Rule) m.compileLine(line, true, ext);
        if (r != null) {
            m.link(r, logging);
            boolean ar = m.analyze(r, logging);
            if (ar) {
                removeResult(m, logging);
                res = true;
            } else {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "WARNING: No candidates to delete");
                }
            }
        } else if (logging && r != null && r.isDeleted()) {
            m.getLog().add(LogMode.ANALYZER, "WARNING: Right is duplicated: " + r);
        }
        release(m);
        hypothesis.clear();
        return res;
    }

    public Boolean queryCheck(boolean logging) throws Exception {
        Boolean res = null;

        setQueryPass(QueryPass.CHECK);

        boolean found = false;
        for (Rule rx : getRules()) {
            if (!rx.isDeleted() && rx.isGenerated()) {
                if (logging) {
                    getLog().add(LogMode.STORAGE, "Delete produced rule: " + String.format("%03d: %s", rx.getId(), rx));
                }
                getRules().delete(rx);
                getComments().delete(rx.getId());
                found = true;
            }
        }
        if (found) {
            pack();
            if (logging) {
                getLog().add(LogMode.STORAGE, "-------------------------------------------");
            }
        }

        link(null, logging);
        Boolean ar = analyze(null, logging);

        if (ar) {
            if (logging) {
                getLog().add(LogMode.ANALYZER, "ERROR: Collisions in Program");
            }
            res = false;
        } else {
            if (logging) {
                getLog().add(LogMode.ANALYZER, "SUCCESS: No Collisions in Program");
            }
            res = true;
        }
        hypothesis.clear();
        return res;
    }

    public Boolean queryCheckFalse(String line, Object[] ext, boolean logging) throws Exception {
        Boolean res = null;
        Mind m = new Mind(this);
        m.setQueryPass(QueryPass.CHECKFALSE);
        if (logging) {
            m.getLog().add(LogMode.ANALYZER, "============= FALSE CHECKING ==============");
        }

        setCompliedLine(line);
        Rule r = (Rule) m.compileLine(invert(line), true, ext);
        if (r != null) {
            boolean ar = m.analyze(r, logging);
            if (ar) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "Result: FALSE");
                    logResult(m);
                }
                res = false;
                hypothesis.clear();
            } else {
                m.link(r, logging);
                ar = m.analyze(r, logging);
                if (ar) {
                    if (logging) {
                        m.getLog().add(LogMode.ANALYZER, "Result: FALSE");
                        logResult(m);
                    }
                    res = false;
                    hypothesis.clear();
                } else {
                    hypothesis.commit(m.getHypothesisStore());
                }
            }
        } else if (logging && r != null && r.isDeleted()) {
            m.getLog().add(LogMode.ANALYZER, "WARNING: Right is duplicated: " + r);
        }
        release(m);
        return res;
    }

    public Boolean queryCheckTrue(String line, Object[] ext, boolean logging) throws Exception {
        Boolean res = null;
        Mind m = new Mind(this);
        m.setQueryPass(QueryPass.CHECKTRUE);
        if (logging) {
            m.getLog().add(LogMode.ANALYZER, "============= TRUE CHECKING ===============");
        }

        setCompliedLine(line);
        Rule r = (Rule) m.compileLine(line, true, ext);
        if (r != null) {
            boolean ar = m.analyze(r, logging);
            if (ar) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "Result: TRUE");
                    logResult(m);
                }
                res = true;
                hypothesis.clear();
            } else {
                m.link(r, logging);
                ar = m.analyze(r, logging);
                if (ar) {
                    if (logging) {
                        m.getLog().add(LogMode.ANALYZER, "Result: TRUE");
                        logResult(m);
                    }
                    res = true;
                    hypothesis.clear();
                } else {
                    hypothesis.commit(m.getHypothesisStore());
                    if (logging) {
                        if (hypothesis.getRoot() != null && hypothesis.size() > 0) {
                            m.getLog().add(LogMode.ANALYZER, String.format("Result: WHO KNOWS? %d Hypothesis", hypothesis.size()));
                        } else {
                            m.getLog().add(LogMode.ANALYZER, "Result: WHO KNOWS? No Hypothesis.");
                        }
                    }
                }
            }
        } else if (logging && r != null && r.isDeleted()) {
            m.getLog().add(LogMode.ANALYZER, "WARNING: Right is duplicated: " + r);
        }
        release(m);
        return res;
    }

    public Boolean query(String line, Object[] ext, boolean logging) throws Exception {
        this.logging = logging;

        Boolean res = null;
        acceptedRule = null;

        getLog().clear();
        getSolutions().clear();
        getValues().clear();
        getHypothesisStore().clear();

        long queryStart = System.currentTimeMillis();

        int key = line.charAt(0);
        switch (key) {

            case Enums.INS:
                res = queryInsert(line, ext, logging);
                break;

            case Enums.ANT:
                res = queryAccept(line, ext, logging);
                break;

            case Enums.DEL:
                res = queryDelete(line, ext, logging);
                break;

            case Enums.SUC:
                hypothesis.clear();
                if (line.length() == 1) {
                    res = queryCheck(logging);
                } else {
                    if (!DEBUG_DISABLE_FALSE_CHECK) {
                        res = queryCheckFalse(line, ext, logging);
                    }
                    if (res == null) {
                        res = queryCheckTrue(line, ext, logging);
                    }
                }
                break;
        }

        if (logging) {
            getLog().add(LogMode.TIMING, "* QUERY Processing time \t" + ((System.currentTimeMillis() - queryStart) / 1000.0));
        }

        return res;
    }

//    private void appendResult(Right right, boolean logging) throws Exception {
//
//        boolean needPack = false;
////        for (Right rx : mind.getRights()) {
////            if (rx.getMindId() == mind.getId()) {
////                if (!rx.isDeleted() /*&& !rx.isQuery()*/ && rx.getDomain().getArguments().getCVariables(this).isEmpty()) {
////                    mind.getSolutions().add(rx);
////                } else {
////                    getRights().delete(rx);
////                    getComments().delete(rx.getId());
////                    needPack = true;
////                }
////            } else {
////                break;
////            }
////        }
//
//        if (logging) {
//            List<Right> productions = getProductions(right);
//            if (!productions.isEmpty()) {
//                getLog().add(LogMode.ANALIZER, "Result: Solves to append (" + productions.size() + "):");
//                int counter = 0;
//                for (Right pr : productions) {
//                    getLog().add(LogMode.SOLVES, String.format("\tProduced %03d:\t%s", ++counter, pr.toString()));
//                }
//            } else {
//                getLog().add(LogMode.ANALIZER, String.format("Result: No candidates to append"));
//            }
//        }
////            int i = 0;
////            for (Right r : mind.getSolutions().getRoot()) {
////                if (r.isGenerated() && !r.isDeleted()) {
////                    ArgList arg = r.getDomain().getArguments().convertBase(this);
////                    r.getDomain().getArguments().clear();
////                    r.getDomain().getArguments().addAll(arg);
////                    r.setGenerated(false);
////                    r.setQuery(false);
////                    if (logging) {
////                        mind.getLog().add(LogMode.SOLVES, String.format("\tAppended %03d: %s", ++i, r.toString()));
////                    }
////                } else if (!r.isDeleted()) {
////                    if (logging) {
////                        mind.getLog().add(LogMode.SOLVES, String.format("\t Skiped %03d: %s", ++i, r.toString()));
////                    }
////                }
////            }
////        if (needPack) {
//
//        getRights().delete(right);
//        getComments().delete(right.getId());
//        needPack = true;
//
//        pack();
////        }
//    }

//    private int resurseCount(Right r) throws Exception {
//        int i = 1;
//        for (Right rx : rights) {
//            if (rx.isGenerated()) {
//                for (Cause c : rx.getCauses()) {
//                    if (c.getSrc().getRightId() == r.getId() || c.getDst().getRightId() == r.getId()) {
//                        i += resurseCount(rx);
//                        break;
//                    }
//                }
//            }
//        }
//        return i;
//    }

//    private boolean isInherited(Set<Cause> rx, Right r) throws Exception {
//        if (r.isStored()) {
//            for (Cause c : rx) {
//                if (c.getDonor().equals(r.getDomain())) {
//                    return true;
//                }
//            }
//        }
//        for (Cause c : rx) {
//            if (!c.getDonor().equals(r.getDomain())) {
//                Right x = getRights().find(c.getDonor());
//                if (x != null) {
//                    if (isInherited(x.getCauses(), r)) {
//                        return true;
//                    }
//                }
//            }
//        }
//        return false;
//    }

//    private Set<Right> getDeleteCandidates(Right r) throws Exception {
//        Set<Right> set = new HashSet<>();
//        set.add(r);
//        for (Right rx : rights) {
//            if (rx.getId() != r.getId() && rx.isGenerated() && isInherited(rx.getCauses(), r)) {
//                set.add(rx);
//            }
//        }
//        return set;
//    }

    public Boolean delete(Rule r, boolean logging) throws Exception {
        this.logging = logging;

        solves.clear();
        values.clear();
        getLog().clear();

        Set<Rule> set = new HashSet<>();
        set.add(r);
        for (Rule rg : getRules()) {
            if (rg.isGenerated()) {
                set.add(rg);
            }
        }

        for (Rule rx : set) {
            rules.delete(rx);
            comments.delete(rx.getId());
        }

        pack();
        tValues.clear();
        link(r, logging);
        Boolean ar = analyze(r, logging);

        Set<Rule> success = new HashSet<>();
        for (Rule rx : set) {
            if (getRules().find(rx) == null) {
                success.add(rx);
            }
        }

        if (logging) {
            if (ar) {
                getLog().add(LogMode.ANALYZER, "ERROR: Collisions in Program");
            } else if (success.isEmpty()) {
                getLog().add(LogMode.ANALYZER, "WARNING: No rules have been deleted");
            } else {
                getLog().add(LogMode.ANALYZER, "SUCCESS: Deleted " + success.size() + " rules");
                for (Rule rx : success) {
                    getLog().add(LogMode.SOLVES, String.format("\tDeleted %03d: %s", rx.getId(), rx.toString()));
                }
            }
        }
        return ar;
    }


    private void removeResult(Mind m, boolean logging) throws Exception {
        if (m.getSolutions().size() > 0) {
            Set<Rule> set = new HashSet<>();
            for (Rule r : m.getSolutions().getRoot()) {
                set.add(r);
            }

            if (!set.isEmpty()) {

                for (Rule r : getRules()) {
                    if (r.isGenerated()) {
                        set.add(r);
                    }
                }

                for (Rule r : set) {
                    getRules().delete(r);
                    getComments().delete(r.getId());
                }

                pack();
                getTValues().clear();
                link(null, logging);
                Boolean ar = analyze(null, logging);

                Set<Rule> success = new HashSet<>();
                for (Rule r : set) {
                    if (getRules().find(r) == null) {
                        success.add(r);
                    }
                }

                if (logging) {
                    if (ar) {
                        m.getLog().add(LogMode.ANALYZER, "ERROR: Collisions in Program");
                    } else if (success.isEmpty()) {
                        m.getLog().add(LogMode.ANALYZER, "WARNING: No rules have been deleted");
                    } else {
                        m.getLog().add(LogMode.ANALYZER, "SUCCESS: Deleted " + success.size() + " rules");
                        for (Rule r : success) {
                            m.getLog().add(LogMode.SOLVES, String.format("\tDeleted %03d: %s", r.getId(), r.toString()));
                        }
                    }
                }
            }
        }
    }

    private void logResult(Mind mind) {
        if (mind.getSolutions().size() > 0) {
            mind.getLog().add(LogMode.SOLVES, "Solutions (" + mind.getSolutions().size() + "):");
            int i = 0;
            for (Rule log : mind.getSolutions().getRoot()) {
                mind.getLog().add(LogMode.SOLVES, String.format("\tSolution %03d: %s", log.getId(), log.toString()));
            }
        }
        if (mind.getValues().size() > 0) {
            mind.getLog().add(LogMode.VALUES, "Values (" + mind.getValues().size() + "):");
            int i = 0;
            for (Map<String, Object> map : mind.getValues()) {
                String s = String.format("\tRow %03d: ", ++i);
                for (Map.Entry<String, Object> row : map.entrySet()) {
                    if (!s.endsWith(" ")) {
                        s += " ";
                    }
                    s += row.getKey() + "=" + formatValue(row.getValue());
                }
                mind.getLog().add(LogMode.VALUES, s);
            }
        }
    }

    private String formatValue(Object o) {
        if (o instanceof byte[]) {
            String s = "#";
            for (byte x : ((byte[]) o)) {
                s += String.format("%02X", x & 0xFF);
            }
            return s;
        } else {
            return o.toString();
        }
    }


    public boolean isLogging() {
        return logging;
    }

    public Calculator getCalculator() {
        return calculator;
    }

//    private List<Right> killInsertion(Mind mind, Right target, boolean withRelatedRights) {
//        int flag = 0;
//        mind.reset();
//
//        mind.getUsedTrees().reset();
//        mind.getClosedTrees().reset();
//
//        mind.getUsedDomains().reset();
//        mind.getQueryValues().reset();
//
////        mind.clearQueryStatus();
//
//        List<Right> rr = new ArrayList<>();
//
//        if (mind.getHypotesisStore().size() > 0) {
//            for (Hypotese h : (List<Hypotese>) mind.getHypotesisStore().getRoot()) {
////                h.getPredicate().deleteSolve(h.getSolves());
//                if (withRelatedRights) {
//
//                    for (Right r : h.getRights()) {
//                        rr.add(r);
//                        mind.removeInsertionRight(r);
//                    }
//                }
//            }
//        }
////        else if (target.getWidth() == 1 && target.getHeight() == 1) {
////            Solution s = target.getTVariable().getD().getPredicate().deleteSolve(target.getTVariable().getD().getArguments());
////            if (withRelatedRights && s != null) {
////                if (s.getRight() != null) {
////                    rr.createTVar(s.getRight());
////                    mind.removeInsertionRight(s.getRight());
////                }
////            }
////        }
//
////        mind.mark();
//        return rr;
//
////        List<Right> todoo = new ArrayList<>();
////        for (Right r = mind.getRights().getRoot(); r != null; r = r.getNext()) {
////            if (r.equals(target)) {
////                todoo.createTVar(r);
////            }
////        }
////        for (Right r : todoo) {
////            mind.removeInsertionRight(r);
////        }
//    }

//    public boolean isSequencedBy(Mind m) {
//        return rights.isSequencedBy(m.rights);
//    }

    public TSolve findTSolve(List<TValue> list) throws Exception {
        TVariableSet ts = new TVariableSet(list);
        if (getRuleSolves().containsKey(ts)) {
            TSolve tmp = new TSolve(list, this);
            for (TSolve t : getRuleSolves().get(ts)) {
                if (tmp.equalsTo(t)) {
                    return t;
                }
            }
        }
        return null;
    }

    public TSolve addTSolve(List<TValue> list) throws Exception {
        TSolve tmp = findTSolve(list);
        if (tmp != null) {
            return tmp;
        } else {
            tmp = new TSolve(list, this);
            TVariableSet ts = new TVariableSet(tmp);
            if (!getRuleSolves().containsKey(ts)) {
                getRuleSolves().put(ts, new ArrayList<>());
            }
            getRuleSolves().get(ts).add(tmp);
            return tmp;
        }
    }

    public String getSourceCode() throws Exception {
        String str = "";
        SortedMap<Long, Rule> map = new TreeMap<>();
        for (Rule r : getRules()) {
            if (!r.isGenerated()) {
                map.put(r.getId(), r);
            }
        }
        Comment c = getComments().get(CommentFactory.HEADER_ID);
        if (c != null) {
            for (String s : c.getComment().split("\\R")) {
                str += s + Enums.LINE_SEPARATOR;
            }
        }
        for (Rule r : map.values()) {
            c = getComments().get(r.getId());
            if (c != null) {
                str += Enums.LINE_SEPARATOR;
                for (String s : c.getComment().split("\\R")) {
                    str += s + Enums.LINE_SEPARATOR;
                }
            }
//            str += "// Right ID " + r.getId() + Enums.LINE_SEPARATOR;
            for (String s : r.getOrig().toString().split("\\R")) {
                str += s + Enums.LINE_SEPARATOR;
            }
        }
        c = getComments().get(CommentFactory.FOOTER_ID);
        if (c != null) {
            str += Enums.LINE_SEPARATOR;
            for (String s : c.getComment().split("\\R")) {
                str += s + Enums.LINE_SEPARATOR;
            }
        }
        return str;
    }

//    public TSolve addTSolve(TValue vv) throws Exception {
//        List<TValue> list = new ArrayList<>();
//        list.add(vv);
//        TSolve tmp = findTSolve(list);
//        if (tmp != null) {
//            return tmp;
//        } else {
//            tmp = new TSolve(list, this);
//            TVariableSet ts = new TVariableSet(tmp);
//            if (!getRightSolves().containsKey(ts)) {
//                getRightSolves().put(ts, new ArrayList<>());
//            }
//            getRightSolves().get(ts).add(tmp);
//            return tmp;
//        }
//    }

//    public List<Right> getResults() throws Exception {
//        return rights.getResults();
//    }


    public Rule getAcceptedRule() {
        return acceptedRule;
    }

    public int getFloodControlLimit() {
        return floodControlLimit;
    }

    public void setFloodControlLimit(int floodControlLimit) {
        this.floodControlLimit = floodControlLimit;
    }
}


//TODO: При use: 1) Путает имена переменных, 2) Добавляет в результат %%, 3) Выводит море кривых гипотез
//TODO: +@x @y ($z parent(z,x), parent(z,y), x != y) -> native(x,y);
//TODO: -$x $y native(x,y);
