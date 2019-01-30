package kanger;

import kanger.calculator.Calculator;
import kanger.compiler.Compiler;
import kanger.compiler.PTree;
import kanger.compiler.Parser;
import kanger.compiler.SysOp;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.enums.QueryPass;
import kanger.enums.Tools;
import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.factory.*;
import kanger.primitives.ArgList;
import kanger.primitives.Hypotese;
import kanger.stores.*;
import kanger.units.*;

import java.io.IOException;
import java.util.*;

//import javax.script.ScriptEngine;
//import javax.script.ScriptEngineManager;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Mind {
    private static final boolean DEBUG_DISABLE_FALSE_CHECK = false;

    private int id = 0;
    private Mind next = null;
    private User user = null;

    private DictionaryFactory terms = null;                    // Словарь констант
    private PredicateFactory predicates = null;                 // Предикаты
    private DomainFactory domains = null;                          // Список доменов
    private RightFactory rights = null;                             // Список правил
    private TVariableFactory tVars = null;                      // t-переменные
    private TValueFactory tValues = null;                          // Подставленные значения
    private FunctionFactory functions = null;                    // Функции
    private FValueFactory fValues = null;                          // Решения функций

    private HypotesisStore hypotesis = null;                                // Список гипотез
    private SolutionsStore solves = null;                         // Список решений
    private ValuesStore values = null;                               // Список значений
    private LogStore log = null;                                        // Протокол вывода

    private Calculator calculator = null;                             // Калькулятор
    private Analiser analiser = null;                                   // Анализатор
    private Compiler compiler = null;                                   // Компилятор
    private Linker linker = null;                                         // Линкер

    private LibraryStore library = null;                            // Системная библиотека функций и предикатов
    private HypotesisStore excluded = null;                                // Список исключенных гипотез

//    private final Set<Long> usedTrees = new HashSet<>();
//    private final Set<Long> closedTrees = new HashSet<>();

    private final Map<Domain, Set<ArgList>> usedDomains = new HashMap<>();
    private final Map<Domain, Set<ArgList>> producedDomains = new HashMap<>();
    private final Map<Domain, Set<ArgList>> calculatedDomains = new HashMap<>();
    private final Map<Domain, Set<ArgList>> excludedDomains = new HashMap<>();

    private final Map<TVariable, Set<TValue>> queryValues = new HashMap<>();

    private boolean changed = false;
    private Boolean queryResult = null;
    private String querySource = "";
    private QueryPass queryPass = QueryPass.SILENCE;
    private String sourceFileName = "mind.k";
    private String compiledFileName = "mind.e";

    private int debugLevel = Enums.DEBUG_LEVEL_DEBUG | (Enums.DEBUG_OPTION_STATUS | Enums.DEBUG_OPTION_VALUES | Enums.DEBUG_OPTION_RIGHTS /*| Enums.DEBUG_OPTION_RTLOGS*/);
    private Stack<Integer> debugLevelStack = new Stack<>();

    public Mind(User user) throws RuntimeErrorException {
        this.user = user;
        user.setMind(this);
        init();
        clear();
    }

    public Mind(Mind root) {
        id = root.getId() + 1;
        next = root;
        user = root.getUser();
        user.setMind(this);
        init();

        terms.transaction(root.getTerms());
        predicates.transaction(root.getPredicates());
        domains.transaction(root.getDomains());
        rights.transaction(root.getRights());
        tVars.transaction(root.getTVars());
        tValues.transaction(root.getTValues());
        functions.transaction(root.getFunctions());
        fValues.transaction(root.getFValues());

        debugLevel = root.getDebugLevel();

//        private final LibraryStore library = new LibraryStore(this);                            // Системная библиотека функций и предикатов
    }

    private void init() {
        terms = new DictionaryFactory(user);                    // Словарь констант
        predicates = new PredicateFactory(user);                 // Предикаты
        domains = new DomainFactory(user);                          // Список доменов
        rights = new RightFactory(user);                             // Список правил
//        trees = new TreeFactory(user);                                // Список секвенций

        tVars = new TVariableFactory(user);                      // t-переменные
        tValues = new TValueFactory(user);                          // Подставленные значения

        functions = new FunctionFactory(user);                    // Функции
        fValues = new FValueFactory(user);                          // Решения функций

        library = new LibraryStore(user);                            // Системная библиотека функций и предикатов

        hypotesis = new HypotesisStore(user);                                // Список гипотез
        excluded = new HypotesisStore(user);                                // Список исключенных гипотез
        solves = new SolutionsStore(user);                         // Список решений
        values = new ValuesStore(user);                               // Список значений

        log = new LogStore(this);                                        // Протокол вывода

        calculator = new Calculator(user);                             // Калькулятор
        analiser = new Analiser(user);                                   // Анализатор
        compiler = new Compiler(user);                                   // Компилятор
        linker = new Linker(user);                                         // Линкер
    }

    public void commit(Mind m) throws RuntimeErrorException {
        SortedSet vars = new TreeSet<>();

        user.setMind(this);

        terms.commit(m.getTerms(), vars);
        tVars.commit(m.getTVars(), vars);
        tValues.commit(m.getTValues());
        fValues.commit(m.getFValues());
        predicates.commit(m.getPredicates());
        domains.commit(m.getDomains());
        rights.commit(m.getRights());
        functions.commit(m.getFunctions());

//        log.commit(m.getLog());
//        solves.commit(m.getSolutions());
//        values.commit(m.getValues());

        for (Object o : vars) {
            int i = terms.nextVarIndex();
            if (o instanceof Term) {
                String temp = String.format("%c%d", Enums.CVC, i);
                ((Term) o).setIndex(i);
                ((Term) o).setValue(temp);
            } else {
                ((TVariable) o).setIndex(i);
            }
        }

        update();


        log.commit(m.getLog());
        queryResult = (Boolean) m.getQueryResult();

    }

    public void update() throws RuntimeErrorException {

        if(!user.isClosed()) {
            try {
                terms.update();
                tVars.update();
                tValues.update();
                fValues.update();
                predicates.update();
                domains.update();
                rights.update();
                functions.update();

                user.flush();

            } catch (IOException e) {
                e.printStackTrace(System.err);
                throw new RuntimeErrorException(e.toString());
            }
        }

    }
    public void release(Mind m) {

        user.setMind(this);
        log.commit(m.getLog());

        solves.commit(m.getSolutions());
        values.commit(m.getValues());

        queryResult = (Boolean) m.getQueryResult();
//        querySource = m.getQuerySource();
    }

    public void clear() throws RuntimeErrorException {
        terms.clear();
        predicates.clear();
        domains.clear();
        tVars.clear();
        tValues.clear();
        rights.clear();
        functions.clear();
        fValues.clear();

        solves.clear();
        values.clear();
        hypotesis.clear();
        excluded.clear();

        if(!user.isClosed()) {
            try {
                user.clear();
            } catch (IOException e) {
                e.printStackTrace(System.err);
                throw new RuntimeErrorException(e.toString());
            }
        }

//        log.clear();

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

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setNext(Mind next) {
        this.next = next;
    }

    public Mind getNext() {
        return next;
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

    public RightFactory getRights() {
        return rights;
    }

//    public TreeFactory getTrees() {
//        return trees;
//    }

    public TVariableFactory getTVars() {
        return tVars;
    }

    public FunctionFactory getFunctions() {
        return functions;
    }

    public LibraryStore getLibrary() {
        return library;
    }

    public HypotesisStore getHypotesisStore() {
        return hypotesis;
    }

    public HypotesisStore getExcludedHypotesis() {
        return excluded;
    }

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

    public void link(Right r, boolean logging) throws RuntimeErrorException {
        linker.link(r, logging);
    }

    public Boolean analise(boolean logging) throws RuntimeErrorException {
        return analiser.analise(logging);
    }

    public boolean compile(String src) throws ParseErrorException, RuntimeErrorException {

        int pos = 0;
        Object[] t = null;
        Mind m = new Mind(this);
        m.setQueryPass(QueryPass.ACCEPT);
        while ((t = Tools.extractLine(src, pos)) != null) {
            pos = (int) t[1];
            String line = (String) t[0];
            m.compileLine(line);
        }

        m.link(null, true);
        Boolean ar = m.analise(true);

        if (ar) {
            m.getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
            release(m);
            return false;
        } else {
            m.getLog().add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
            commit(m);
            excluded.clear();
            excluded.commit(m.getHypotesisStore());
            return true;
        }
    }

    public Object compileLine(String line) throws ParseErrorException, RuntimeErrorException {
        String orig = line.trim();
        Object r = null;
        Boolean suc = null;

        switch (line.charAt(0)) {
            case Enums.FOO:
                r = Parser.implement(line, this);
                library.add((SysOp) r);
                break;
            case Enums.INS:
            case Enums.ANT:
                suc = true;
                break;
            case Enums.DEL:
            case Enums.WIPE:
            case Enums.SUC:
                suc = false;
                break;
        }
        if (suc != null) {
            PTree p = Parser.parser(line.substring(1));
            r = new Compiler(user).compileLine(p, suc, orig);
        }

        return r;
    }


    /**
     * Удаление правила из дерева вывода
     * <p>
     *
//     * @param r
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
//        clear();
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


    public Boolean query(String line) throws ParseErrorException, RuntimeErrorException {
        querySource = line;
        queryPass = QueryPass.SILENCE;
        queryResult = query(line, false);
        return queryResult;
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

    public Map<Domain, Set<ArgList>> getProducedDomains() {
        return producedDomains;
    }

    public Map<Domain, Set<ArgList>> getCalculatedDomains() {
        return calculatedDomains;
    }

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
//        markAcceptor.clear();
//        markAcceptor.addAll(acceptorDomains);
//    }
//
//    public void releaseAcceptors() {
//        acceptorDomains.clear();
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

    public boolean isSystem(Predicate p) {
        return calculator.exists(p);
    }

    public boolean isSystem(Function f) {
        return calculator.exists(f);
    }

    public int executeSystem(Domain d) throws RuntimeErrorException {
        return calculator.execute(d);
    }

    public int executeSystem(Function f) throws RuntimeErrorException {
        return calculator.execute(f);
    }

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
        if (line.charAt(0) == Enums.ANT) {
            return String.format("%c%s", Enums.SUC, line.substring(1));
        } else {
            return String.format("%c%s", Enums.ANT, line.substring(1));
        }
    }

    private String resign(int sign, String line) {
        return String.format("%c%s", sign, line.substring(1));
    }

    public Boolean query(String line, boolean testMode) throws ParseErrorException, RuntimeErrorException {
        Boolean res = null;

        boolean storeH = getHypotesisStore().isEnabled();
        boolean storeV = getValues().isEnabled();
        boolean storeS = getSolutions().isEnabled();
        boolean storeL = getLog().isEnabled();

        getHypotesisStore().enable(!testMode);
        getValues().enable(!testMode);
        getSolutions().enable(!testMode);
        getLog().enable(!testMode);

        getLog().clear();
        getSolutions().clear();
        getValues().clear();
        getHypotesisStore().clear();

        long queryStart = System.currentTimeMillis();

        getLog().add(LogMode.ANALIZER, "============= CHECKING ===================");

//        Mind m = new Mind(this);
//        excluded.clear();
//        m.link(true);
//        Boolean ar = m.analise(true);
//        release();
//        excluded.commit(m.getHypotesisStore());

//        if (ar) {
//            getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
//            res = null;
//        } else {


        if (!excluded.isEmpty()) {
            for (Hypotese h : excluded.getRoot()) {
                getLog().add(LogMode.ANALIZER, "Hypotesis excluded: " + h.toString());
            }
            getLog().add(LogMode.ANALIZER, "------------------------------------------");
        }
        int key = line.charAt(0);
        switch (key) {

            case Enums.ANT: {
                getLog().add(LogMode.ANALIZER, "============= ACCEPTING ===================");

                Mind m = new Mind(this);
                m.setQueryPass(QueryPass.ACCEPT);
                Right r = (Right) m.compileLine(line);
//                    r.setQuery(true);

                if (r != null) {
                    m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                    m.getLog().add(LogMode.ANALIZER, r);
//                    m.getLog().add(LogMode.ANALIZER, "-------------------------------------------");

                    m.link(r, true);
                    boolean ar = m.analise(true);
                    if (ar) {
                        m.getLog().add(LogMode.ANALIZER, "ERROR: Conflict in new Right");
                        release(m);
                        res = null;
                    } else {
                        m.getLog().add(LogMode.SOLVES, String.format("\tSolution 000:\t%s", line));
                        m.getLog().add(LogMode.ANALIZER, "SUCCESS: New Right Accepted");
                        commit(m);
                        excluded.commit(m.getHypotesisStore());
                        setChanged(true);
                        res = true;
                    }
                } else {
                    release(m);
                }
            }
            break;

            case Enums.DEL:
            case Enums.WIPE:
                SysOp op = calculator.find(line);
                if (op != null) {
                    if (getLibrary().remove(op.toString())) {
                        getLog().add(LogMode.ANALIZER, "SUCCESS: Function removed: " + op.toString());
                    } else {
                        getLog().add(LogMode.ANALIZER, "WARNING: Unable to remove function: " + op.toString());
                    }
                }
                break;

            case Enums.SUC: {

                hypotesis.clear();

                if (line.length() == 1) {
                    Mind m = new Mind(this);
                    m.setQueryPass(QueryPass.CHECK);

                    m.link(null, true);
                    Boolean ar = m.analise(true);

                    if (ar) {
                        m.getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
                        release(m);
                        res = false;
                    } else {
                        m.getLog().add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
                        commit(m);
                        excluded.clear();
                        excluded.commit(m.getHypotesisStore());
                        res = true;
                    }

                } else {

//                    if (!DEBUG_DISABLE_FALSE_CHECK) {
//
//                        Mind m = new Mind(this);
//                        m.setQueryPass(QueryPass.CHECKFALSE);
//                        m.getLog().add(LogMode.ANALIZER, "============= FALSE CHECKING (QUICK) ======");
//
//                        Right r = (Right) m.compileLine(invert(line));
//                        if (r != null) {
//                            r.setQuery(true);
//
//                            m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
//                            m.getLog().add(LogMode.ANALIZER, r);
//
//                            m.link(r, true);
//                            boolean ar = m.analise(true);
//                            if (ar) {
//                                m.getLog().add(LogMode.ANALIZER, "Result: FALSE");
//                                logResult(m);
//                                res = false;
//                            } else {
////                                hypotesis.commit(m.getHypotesisStore());
//                            }
//                        }
//                        release(m);
//
//                    }
//
//                    if (res == null) {
//
//                        Mind m = new Mind(this);
//                        m.setQueryPass(QueryPass.CHECKTRUE);
//                        m.getLog().add(LogMode.ANALIZER, "============= TRUE CHECKING (QUICK) =======");
//
//                        Right r = (Right) m.compileLine(line);
//                        if (r != null) {
//
//                            r.setQuery(true);
//                            m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
//                            m.getLog().add(LogMode.ANALIZER, r);
//                            m.link(r, true);
//                            boolean ar = m.analise(true);
//                            if (ar) {
//                                m.getLog().add(LogMode.ANALIZER, "Result: TRUE");
//                                logResult(m);
//                                res = true;
//                            } else {
////                                hypotesis.commit(m.getHypotesisStore());
//                            }
//                        }
//                        release(m);
//                    }
//
//
//                    if (res == null) {
                        if (!DEBUG_DISABLE_FALSE_CHECK) {

                            Mind m = new Mind(this);
                            m.setQueryPass(QueryPass.CHECKFALSE);
                            m.getLog().add(LogMode.ANALIZER, "============= FALSE CHECKING ==============");

                            Right r = (Right) m.compileLine(invert(line));
                            if (r != null) {
                                r.setQuery(true);

                                m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                                m.getLog().add(LogMode.ANALIZER, r);
                                m.link(r, true);
                                boolean ar = m.analise(true);
                                if (ar) {
                                    m.getLog().add(LogMode.ANALIZER, "Result: FALSE");
                                    logResult(m);
                                    res = false;
                                } else {
                                    hypotesis.commit(m.getHypotesisStore());
                                }
                            }
                            release(m);
                        }

                        if (res == null) {

                            Mind m = new Mind(this);
                            m.setQueryPass(QueryPass.CHECKTRUE);
                            m.getLog().add(LogMode.ANALIZER, "============= TRUE CHECKING ===============");

                            Right r = (Right) m.compileLine(line);
                            if (r != null) {

                                r.setQuery(true);
                                m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                                m.getLog().add(LogMode.ANALIZER, r);
                                m.link(r, true);
                                boolean ar = m.analise(true);
                                if (ar) {
                                    m.getLog().add(LogMode.ANALIZER, "Result: TRUE");
                                    logResult(m);
                                    res = true;
                                } else {

                                    hypotesis.commit(m.getHypotesisStore());
                                    hypotesis.exclude(excluded);

                                    if (hypotesis.getRoot() != null && hypotesis.size() > 0) {
                                        m.getLog().add(LogMode.ANALIZER, String.format("Result: WHO KNOWS? %d Hypothesis", hypotesis.size()));
                                    } else {
                                        m.getLog().add(LogMode.ANALIZER, "Result: WHO KNOWS? No Hypothesis.");
                                    }
                                }
                            }
                            release(m);
                        }
//                    }
                }

                break;
            }
        }

        getHypotesisStore().enable(storeH);
        getValues().enable(storeV);
        getSolutions().enable(storeS);
        getLog().enable(storeL);

        getLog().add(LogMode.TIMING, "* QUERY Processing time \t" + ((System.currentTimeMillis() - queryStart) / 1000.0));

        return res;
    }

    private void logResult(Mind mind) {
        boolean status = (debugLevel & Enums.DEBUG_OPTION_STATUS) != 0;
        if (mind.getSolutions().size() > 0) {
            mind.getLog().add(LogMode.SOLVES, "Solves (" + mind.getSolutions().size() + "):");
            int i = 0;
            for (Right log : mind.getSolutions().getRoot()) {
                mind.getLog().add(LogMode.SOLVES, String.format("\tSolution %03d: %s", ++i,
                        log.toString()));
            }
        }
        if (mind.getValues().size() > 0) {
            mind.getLog().add(LogMode.VALUES, "Values (" + mind.getValues().size() + "):");
            int i = 0;
            for (List<TValue> row : mind.getValues().getRoot()) {
                String s = String.format("\tRow %03d: ", ++i);
                for (TValue log : row) {
                    if(!s.endsWith(" ")) {
                        s += " ";
                    }
                    s += log.toString();
                }
                mind.getLog().add(LogMode.VALUES, s);
            }
        }
    }


//    private List<Right> killInsertion(Mind mind, Right target, boolean withRelatedRights) {
//        int flag = 0;
//        mind.clear();
//
//        mind.getUsedTrees().clear();
//        mind.getClosedTrees().clear();
//
//        mind.getUsedDomains().clear();
//        mind.getQueryValues().clear();
//
////        mind.clearQueryStatus();
//
//        List<Right> rr = new ArrayList<>();
//
//        if (mind.getHypotesisStore().size() > 0) {
//            for (Hypotese h : (List<Hypotese>) mind.getHypotesisStore().getRoot()) {
////                h.getPredicate().deleteSolve(h.getSolve());
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

}


//TODO: При use: 1) Путает имена переменных, 2) Добавляет в результат %%, 3) Выводит море кривых гипотез

