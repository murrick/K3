package org.kanger;

import org.kanger.calculator.Calculator;
import org.kanger.compiler.Compiler;
import org.kanger.compiler.PTree;
import org.kanger.compiler.Parser;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.enums.QueryPass;
import org.kanger.enums.Tools;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.factory.*;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Hypotese;
import org.kanger.stores.HypotesisStore;
import org.kanger.stores.LogStore;
import org.kanger.stores.SolutionsStore;
import org.kanger.stores.ValuesStore;
import org.kanger.units.*;

import java.io.IOException;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Mind {

    private static final boolean DEBUG_DISABLE_FALSE_CHECK = false;

    private final Map<Long, Set<Right>> usedRights = new HashMap<>();
    private final Map<Domain, Set<ArgList>> usedDomains = new HashMap<>();
    private final Map<Domain, Set<ArgList>> excludedDomains = new HashMap<>();
    private final Map<Domain, List<List<Term>>> calculatedDomains = new HashMap<>();
    private final Map<Domain, List<List<Term>>> producedDomains = new HashMap<>();
    private final Map<Domain, Map<ArgList, Set<Cause>>> domainCauses = new HashMap<>();
    private final Map<Domain, Map<ArgList, SortedSet<TValue>>> domainSolves = new HashMap<>();
    //    private final Map<Domain, Map<ArgList, Set<Long>>> domainTags = new HashMap<>();
    private final Map<TVariable, Set<TValue>> queryValues = new HashMap<>();
    private long id = 0;
    private Mind next = null;
    private IUser user = null;
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

    //    private final Set<Long> usedTrees = new HashSet<>();
//    private final Set<Long> closedTrees = new HashSet<>();
    private LogStore log = null;                                        // Протокол вывода
    private Calculator calculator = null;                             // Калькулятор
    private Analiser analiser = null;                                   // Анализатор
    private Compiler compiler = null;                                   // Компилятор
    private Linker linker = null;                                         // Линкер
    private LibraryFactory library = null;                            // Системная библиотека функций и предикатов
    private HypotesisStore excluded = null;                                // Список исключенных гипотез
    //    private ResultsStore results = null;
    private boolean changed = false;
    private Boolean queryResult = null;
    private String querySource = "";
    private Mind queryContext = null;
    private QueryPass queryPass = QueryPass.SILENCE;
    private String sourceFileName = "mind.k";
    private String compiledFileName = "mind.e";
    private boolean logging = true;

    private int debugLevel = Enums.DEBUG_LEVEL_DEBUG | (Enums.DEBUG_OPTION_STATUS | Enums.DEBUG_OPTION_VALUES | Enums.DEBUG_OPTION_RIGHTS /*| Enums.DEBUG_OPTION_RTLOGS*/);
    private Stack<Integer> debugLevelStack = new Stack<>();

    private volatile boolean blockCommit = false;

    public Mind(IUser user) throws IOException, OutOfBufferException {
        this.user = user;
//        user.setMind(this);
        init();
        clear();
    }

    public Mind(Mind root) {
        next = root;
        user = root.getUser();
//        user.setMind(this);
        id = user.nextId(); //root.getId() + 1;
        init();

//        terms.transaction(root.getTerms());
//        predicates.transaction(root.getPredicates());
//        library.transaction(root.getLibrary());

        terms = root.getTerms();
        predicates = root.getPredicates();
        library = root.getLibrary();


//        functions = root.getFunctions();

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
        terms = new DictionaryFactory(this);                    // Словарь констант
        predicates = new PredicateFactory(this);                 // Предикаты
        functions = new FunctionFactory(this);                    // Функции
        library = new LibraryFactory(this);                            // Пользовательсткая библиотека функций и предикатов

        domains = new DomainFactory(this);                          // Список доменов
        rights = new RightFactory(this);                             // Список правил
//        trees = new TreeFactory(user);                                // Список секвенций

        tVars = new TVariableFactory(this);                      // t-переменные
        tValues = new TValueFactory(this);                          // Подставленные значения

        fValues = new FValueFactory(this);                          // Решения функций


        hypotesis = new HypotesisStore(this);                                // Список гипотез
        excluded = new HypotesisStore(this);                                // Список исключенных гипотез
        solves = new SolutionsStore(this);                         // Список решений
        values = new ValuesStore(this);                               // Список значений
//        results = new ResultsStore(user);                               // Список значений

        log = new LogStore(this);                                        // Протокол вывода

        calculator = new Calculator(this);                             // Калькулятор
        compiler = new Compiler(this);                                   // Компилятор
        analiser = new Analiser(this);                                   // Анализатор
        linker = new Linker(this);                                         // Линкер

    }


    public synchronized boolean commit(Mind m) throws Exception {

        boolean result = true;

        boolean sequencedBy = isSequencedBy(m);
        if (!sequencedBy) {
            functions.mark();
            fValues.mark();
            tVars.mark();
            tValues.mark();
            domains.mark();
            rights.mark();
        }

        functions.commit(m.getFunctions());
        fValues.commit(m.getFValues());
        tVars.commit(m.getTVars());
        tValues.commit(m.getTValues());
        domains.commit(m.getDomains());
        rights.commit(m.getRights());

        if (!sequencedBy) {
            Boolean res = analiser.checkDatabase(null, false);
            if (res != null && res) {
                functions.release();
                fValues.release();
                tVars.release();
                tValues.release();
                domains.release();
                rights.release();
                result = false;
            } else {
                functions.commit();
                fValues.commit();
                tVars.commit();
                tValues.commit();
                domains.commit();
                rights.commit();
            }
        }

        pack();
        update();

        log.commit(m.getLog());
        queryResult = (Boolean) m.getQueryResult();

        return result;
    }

    public synchronized void update() throws IOException {

        terms.update();
        tVars.update();
        tValues.update();
        fValues.update();
        predicates.update();
        domains.update();
        rights.update();
        functions.update();
        library.update();

        user.flush();
    }

    public synchronized void release(Mind m) throws Exception {

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
//        querySource = m.getQuerySource();
    }

    public synchronized void clear() throws IOException, OutOfBufferException {
        terms.clear();
        predicates.clear();
        domains.clear();
        tVars.clear();
        tValues.clear();
        rights.clear();
        functions.clear();
        fValues.clear();
        library.clear();

        update();

        solves.clear();
        values.clear();
//        results.clear();
        hypotesis.clear();
        excluded.clear();

    }

    public synchronized void pack() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {

        for (TValue v : tValues) {
            Set<Cause> toDeleteC = new HashSet<>();
            for (Cause c : v.getCauses()) {
                if (c.getSrc(this).isDeleted() || c.getDst(this).isDeleted()) {
                    toDeleteC.add(c);
                }
            }
            if (!toDeleteC.isEmpty()) {
                v.getCauses().removeAll(toDeleteC);
            }
        }

        terms.pack();
        predicates.pack();
//        tValues.pack();
        tVars.pack();
        domains.pack();
        rights.pack();
        fValues.pack();
        functions.pack();
        library.pack();

        for (TValue v : tValues) {
            Set<Cause> toDeleteC = new HashSet<>();
            for (Cause c : v.getCauses()) {
                if (c.getSrc(this) == null || c.getDst(this) == null) {
                    toDeleteC.add(c);
                }
            }
            if (!toDeleteC.isEmpty()) {
                v.getCauses().removeAll(toDeleteC);
            }
            if (v.getCauses().isEmpty()) {
                tValues.delete(v);
            }
        }

        tValues.pack();
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

    public RightFactory getRights() {
        return rights;
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

    public void link(Right r, boolean logging) throws Exception {
        linker.link(r, logging);
    }

    public Boolean analise(Right right, boolean logging) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return analiser.analise(right, logging);
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
        while ((t = Tools.extractLine(src, pos)) != null) {
            pos = (int) t[1];
            String line = (String) t[0];

//            m.compileLine(line, false);

            Mind x = new Mind(m);
            Object r = x.compileLine(line, false, null);
            if (r instanceof Right && ((Right) r).isDeleted()) {
                m.release(x);
            } else {
                m.commit(x);
            }
        }

        m.link(null, logging);
        Boolean ar = m.analise(null, logging);

        if (ar) {
            if (logging) {
                m.getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
            }
            release(m);
            return false;
        } else {
            if (logging) {
                m.getLog().add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
            }
            commit(m);
            excluded.clear();
            excluded.commit(m.getHypotesisStore());
            return true;
        }
    }

    public Object compileLine(String line, boolean query, Object[] ext) throws Exception {
        String orig = line.trim();
        Object r = null;
        Boolean suc = null;

        switch (line.charAt(0)) {
            case Enums.FOO:
                r = Parser.implement(line, this);
                if (r != null) {
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
            PTree p = Parser.parser(line.substring(1));
            r = compiler.compileLine(p, suc, orig, query, ext);
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

    public synchronized Boolean query(String line, Object[] ext) throws Exception {
        querySource = line;
        queryPass = QueryPass.SILENCE;
        queryContext = null;
        queryResult = query(line, ext, true);
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

    public Map<Long, Set<Right>> getUsedRights() {
        return usedRights;
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

    public boolean isSystem(Predicate p) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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

    public synchronized Boolean query(String line, Object[] ext, boolean logging) throws Exception {
//        querySource = line;
//        queryPass = QueryPass.SILENCE;
//        queryContext = null;
        this.logging = logging;

        Boolean res = null;

        getLog().clear();
        getSolutions().clear();
        getValues().clear();
        getHypotesisStore().clear();

        long queryStart = System.currentTimeMillis();

        if (logging) {
            getLog().add(LogMode.ANALIZER, "============= CHECKING ===================");
        }

//        Mind m = new Mind(this);
//        excluded.reset();
//        m.link(true);
//        Boolean ar = m.analise(true);
//        release();
//        excluded.commit(m.getHypotesisStore());

//        if (ar) {
//            getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
//            res = null;
//        } else {


        if (logging) {
            if (!excluded.isEmpty()) {
                for (Hypotese h : excluded.getRoot()) {
                    getLog().add(LogMode.ANALIZER, "Hypotesis excluded: " + h.toString());
                }
                getLog().add(LogMode.ANALIZER, "------------------------------------------");
            }
        }

        int key = line.charAt(0);
        switch (key) {

            case Enums.INS: {
                if (logging) {
                    getLog().add(LogMode.ANALIZER, "============= INSERT ======================");
                }

                Mind m = new Mind(this);
                m.setQueryPass(QueryPass.ACCEPT);

                line = invert(line);
                line = invert(line);

                Right r = (Right) m.compileLine(line, true, ext);
                if (r != null && !r.isDeleted()) {
//                    r.setQuery(true);

                    if (logging) {
                        m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                        m.getLog().add(LogMode.ANALIZER, r);
                    }

                    m.link(r, logging);
                    boolean ar = m.analise(r, logging);
                    if (ar) {
                        if (logging) {
                            m.getLog().add(LogMode.ANALIZER, "ERROR: Conflict in new Right");
                        }
                        release(m);
                        res = null;
                    } else {
                        appendResult(m, logging);
                        m.getRights().delete(r);
                        commit(m);

//                        excluded.commit(m.getHypotesisStore());
                        setChanged(true);
                        res = true;
                    }
                    queryContext = m;
                } else {
                    if (logging && r != null && r.isDeleted()) {
                        m.getLog().add(LogMode.ANALIZER, "WARNING: Right is duplicated: " + r);
                    }
                    release(m);
                }
            }
            break;

            case Enums.ANT: {
                if (logging) {
                    getLog().add(LogMode.ANALIZER, "============= ACCEPTING ===================");
                }

                Mind m = new Mind(this);
                m.setQueryPass(QueryPass.ACCEPT);

                Right r = (Right) m.compileLine(line, false, ext);
                if (r != null && !r.isDeleted()) {
//                    r.setQuery(true);
                    if (logging) {
                        m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                        m.getLog().add(LogMode.ANALIZER, r);
                    }
                    boolean ar = m.analise(r, logging);
                    if (ar) {
                        if (logging) {
                            m.getLog().add(LogMode.ANALIZER, "ERROR: Conflict in new Right");
                        }
                        release(m);
                        res = null;
                    } else {
                        //TODO: При повторном добавлении не генерируются все продукции
                        m.link(r, logging);
                        ar = m.analise(r, logging);
//                        m.link(null, logging);
//                        ar = m.analise(null, logging);
                        if (ar) {
                            if (logging) {
                                m.getLog().add(LogMode.ANALIZER, "ERROR: Conflict in new Right");
                            }
                            release(m);
                            res = null;
                        } else {
                            if (logging) {
                                m.getLog().add(LogMode.SOLVES, String.format("\tSolution 000:\t%s", line));
                                m.getLog().add(LogMode.ANALIZER, "SUCCESS: New Right Accepted");
                            }
                            commit(m);
                            excluded.commit(m.getHypotesisStore());
                            setChanged(true);
                            res = true;
                        }
                    }
                    queryContext = m;
                } else {
                    if (logging && r != null && r.isDeleted()) {
                        m.getLog().add(LogMode.ANALIZER, "WARNING: Right is duplicated: " + r);
                    }
                    release(m);
                }
            }
            break;

//            case Enums.DEL:
//            case Enums.WIPE:
//                SysOp op = calculator.find(line);
//                if (op != null) {
//                    if (getLibrary().remove(op.toString())) {
//                        getLog().add(LogMode.ANALIZER, "SUCCESS: Function removed: " + op.toString());
//                    } else {
//                        getLog().add(LogMode.ANALIZER, "WARNING: Unable to remove function: " + op.toString());
//                    }
//                }
//                break;

            case Enums.DEL: {

                Mind m = new Mind(this);
                m.setQueryPass(QueryPass.CHECKTRUE);
                if (logging) {
                    m.getLog().add(LogMode.ANALIZER, "============= DELETE ======================");
                }
                line = invert(line);
                Right r = (Right) m.compileLine(line, true, ext);
                if (r != null && !r.isDeleted()) {
//                    r.setQuery(true);
                    if (logging) {
                        m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                        m.getLog().add(LogMode.ANALIZER, r);
                    }
                    m.link(r, logging);
                    boolean ar = m.analise(r, logging);
                    if (ar) {
                        removeResult(m, logging);
                        res = true;
                    } else {
                        if (logging) {
                            m.getLog().add(LogMode.ANALIZER, "Result: No candidates to delete");
                        }
                    }
                    queryContext = m;
                } else if (logging && r != null && r.isDeleted()) {
                    m.getLog().add(LogMode.ANALIZER, "WARNING: Right is duplicated: " + r);
                }
                release(m);
//                if (res) {
//                    pack();
//                    tValues.clear();
//                    link(null, logging);
//                    boolean ar = analise(null, logging);
//                    if (logging) {
//                        if (ar) {
//                            log.add(LogMode.ANALIZER, "ERROR: Collisions in Program");
//                        } else {
//                            log.add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
//                        }
//                    }
//                }
            }
            break;

            case Enums.SUC: {

                hypotesis.clear();

                if (line.length() == 1) {

//                    Mind m = new Mind(this);
                    setQueryPass(QueryPass.CHECK);

                    boolean found = false;
                    for (Right rx : getRights()) {
                        if (!rx.isDeleted() && rx.isGenerated()) {
                            if (logging) {
                                getLog().add(LogMode.STORAGE, "Delete produced right: " + String.format("%03d: %s", rx.getId(), rx));
                            }
                            getRights().delete(rx);
                            found = true;
                        }
                    }
                    if (found) {
                        pack();
                        getTValues().clear();
                        if (logging) {
                            getLog().add(LogMode.STORAGE, "-------------------------------------------");
                        }
                    }

                    link(null, logging);
                    Boolean ar = analise(null, logging);

                    if (ar) {
                        if (logging) {
                            getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
                        }
                        res = false;
                    } else {
                        if (logging) {
                            getLog().add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
                        }
//                        commit(m);
//                        excluded.clear();
//                        excluded.commit(m.getHypotesisStore());
                        res = true;
                    }
                    queryContext = this;

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
                        if (logging) {
                            m.getLog().add(LogMode.ANALIZER, "============= FALSE CHECKING ==============");
                        }

                        Right r = (Right) m.compileLine(invert(line), true, ext);
                        if (r != null && !r.isDeleted()) {
                            r.setQuery(true);

                            if (logging) {
                                m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                                m.getLog().add(LogMode.ANALIZER, r);
                            }

                            boolean ar = m.analise(r, logging);
                            if (ar) {
                                if (logging) {
                                    m.getLog().add(LogMode.ANALIZER, "Result: FALSE");
                                    logResult(m);
                                }
                                res = false;
                                queryContext = m;
                            } else {
                                m.link(r, logging);
                                ar = m.analise(r, logging);
                                if (ar) {
                                    if (logging) {
                                        m.getLog().add(LogMode.ANALIZER, "Result: FALSE");
                                        logResult(m);
                                    }
                                    res = false;
                                    queryContext = m;
                                } else {
                                    hypotesis.commit(m.getHypotesisStore());
                                }
                            }
                        } else if (logging && r != null && r.isDeleted()) {
                            m.getLog().add(LogMode.ANALIZER, "WARNING: Right is duplicated: " + r);
                        }
                        release(m);
                    }

                    if (res == null) {

                        Mind m = new Mind(this);
                        m.setQueryPass(QueryPass.CHECKTRUE);
                        if (logging) {
                            m.getLog().add(LogMode.ANALIZER, "============= TRUE CHECKING ===============");
                        }

                        Right r = (Right) m.compileLine(line, true, ext);
                        if (r != null && !r.isDeleted()) {
                            r.setQuery(true);
                            if (logging) {
                                m.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                                m.getLog().add(LogMode.ANALIZER, r);
                            }
                            boolean ar = m.analise(r, logging);
                            if (ar) {
                                if (logging) {
                                    m.getLog().add(LogMode.ANALIZER, "Result: TRUE");
                                    logResult(m);
                                }
                                res = true;
                            } else {
                                m.link(r, logging);
                                ar = m.analise(r, logging);
                                if (ar) {
                                    if (logging) {
                                        m.getLog().add(LogMode.ANALIZER, "Result: TRUE");
                                        logResult(m);
                                    }
                                    res = true;
                                } else {
                                    hypotesis.commit(m.getHypotesisStore());
                                    hypotesis.exclude(excluded);
                                    if (logging) {
                                        if (hypotesis.getRoot() != null && hypotesis.size() > 0) {
                                            m.getLog().add(LogMode.ANALIZER, String.format("Result: WHO KNOWS? %d Hypothesis", hypotesis.size()));
                                        } else {
                                            m.getLog().add(LogMode.ANALIZER, "Result: WHO KNOWS? No Hypothesis.");
                                        }
                                    }
                                }
                            }
                            queryContext = m;
                        } else if (logging && r != null && r.isDeleted()) {
                            m.getLog().add(LogMode.ANALIZER, "WARNING: Right is duplicated: " + r);
                        }
                        release(m);

                    }
//                    }
                }

                break;
            }
        }

        if (logging) {
            getLog().add(LogMode.TIMING, "* QUERY Processing time \t" + ((System.currentTimeMillis() - queryStart) / 1000.0));
        }


//        if(queryContext != null) {
//            for(Right solve : queryContext.getRights().getSolves()) {
//                Cause cause = null;
//                Iterator<Cause> iterator = solve.getCauses().iterator();
//                if(iterator.hasNext()) {
//                    cause = iterator.next();
//                }
//
//                System.out.println("--- " + solve + (cause == null ? "" : " - " + cause.getDst().getRight()));
//
//            }
//            for(List<TValue> row: queryContext.getRights().getValues()) {
//                String s = "";
//                for(TValue v : row) {
//                    if(!s.isEmpty()) {
//                        s += " ";
//                    }
//                    s += v;
//                }
//                System.out.println("... " + s);
//            }
//        }

        return res;
    }

    private void appendResult(Mind mind, boolean logging) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {

        boolean needPack = false;
        for (Right rx : mind.getRights()) {
            if (rx.getMindId() == mind.getId()) {
                if (!rx.isDeleted() /*&& !rx.isQuery()*/ && rx.getDomain().getArguments().getCVariables(this, true).isEmpty()) {
                    mind.getSolutions().add(rx);
                } else {
                    getRights().delete(rx);
                    needPack = true;
                }
            } else {
                break;
            }
        }

        if (mind.getSolutions().size() > 0) {
            if (logging) {
                mind.getLog().add(LogMode.SOLVES, "Solves to append (" + mind.getSolutions().size() + "):");
            }
            int i = 0;
            for (Right r : mind.getSolutions().getRoot()) {
                if (r.isGenerated() && !r.isDeleted()) {
                    ArgList arg = r.getDomain().getArguments().convertBase(this);
                    r.getDomain().getArguments().clear();
                    r.getDomain().getArguments().addAll(arg);
                    r.setGenerated(false);
                    r.setQuery(false);
                    if (logging) {
                        mind.getLog().add(LogMode.SOLVES, String.format("\tAppended %03d: %s", ++i, r.toString()));
                    }
                } else if (!r.isDeleted()) {
                    if (logging) {
                        mind.getLog().add(LogMode.SOLVES, String.format("\t Skiped %03d: %s", ++i, r.toString()));
                    }
                }
            }
        } else {
            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, String.format("Result: No candidates to append"));
            }
        }
        if (needPack) {
            pack();
        }
    }

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

    private boolean isInherited(Right rx, Right r) throws Exception {
        for (Cause c : rx.getCauses()) {
            if ((r.isStored() && c.getSrc(this).getId() == r.getDomain().getId())
                    || c.getDst(this).getRightId() == r.getId()) {
                return true;
            } else {
                c.getSrc(this).getArguments().applyArguments(this, c.getArguments());
                Right x = rights.find(c.getSrc(this));
                if (x != null && isInherited(x, r)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<Right> getDeleteCandidates(Right r) throws Exception {
        Set<Right> set = new HashSet<>();
        set.add(r);
        for (Right rx : rights) {
            if (rx.getId() != r.getId() && rx.isGenerated() && isInherited(rx, r)) {
                set.add(rx);
            }
        }
        return set;
    }

    public Boolean delete(Right r, boolean logging) throws Exception {
        this.logging = logging;

        solves.clear();
        values.clear();
        getLog().clear();

        Set<Right> set = getDeleteCandidates(r);
        if (logging) {
            getLog().add(LogMode.SOLVES, "Rights to delete (" + set.size() + "):");
        }

        for (Right rx : set) {
            rights.delete(rx);
            if (logging) {
                getLog().add(LogMode.SOLVES, String.format("\tDeleted %03d: %s", rx.getId(), rx.toString()));
            }
        }

        pack();
        tValues.clear();
        link(null, logging);
        Boolean ar = analise(null, logging);

        if (logging) {
            if (ar) {
                getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
            } else {
                getLog().add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
            }
        }
        return ar;
    }


    private void removeResult(Mind mind, boolean logging) throws Exception {
        if (mind.getSolutions().size() > 0) {
            Set<Right> set = new HashSet<>();
            for (Right r : mind.getSolutions().getRoot()) {
                set.addAll(getDeleteCandidates(r));
            }
            if (!set.isEmpty()) {
                if (logging) {
                    mind.getLog().add(LogMode.SOLVES, "Rights to delete (" + set.size() + "):");
                }
                for (Right r : set) {
                    rights.delete(r);
                    if (logging) {
                        mind.getLog().add(LogMode.SOLVES, String.format("\tDeleted %03d: %s", r.getId(), r.toString()));
                    }
                }

                pack();
                tValues.clear();
                link(null, logging);
                Boolean ar = analise(null, logging);

                if (logging) {
                    if (ar) {
                        mind.getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
                    } else {
                        mind.getLog().add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
                    }
                }
            }
        }
    }

    private void logResult(Mind mind) {
        boolean status = (debugLevel & Enums.DEBUG_OPTION_STATUS) != 0;
        if (mind.getSolutions().size() > 0) {
            mind.getLog().add(LogMode.SOLVES, "Solves (" + mind.getSolutions().size() + "):");
            int i = 0;
            for (Right log : mind.getSolutions().getRoot()) {
                mind.getLog().add(LogMode.SOLVES, String.format("\tSolution %03d: %s", ++i, log.toString()));
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

    public boolean isSequencedBy(Mind m) {
        return rights.isSequencedBy(m.rights);
    }

//    public List<Right> getResults() throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
//        return rights.getResults();
//    }
}


//TODO: При use: 1) Путает имена переменных, 2) Добавляет в результат %%, 3) Выводит море кривых гипотез
//TODO: +@x @y ($z parent(z,x), parent(z,y), x != y) -> native(x,y);
//TODO: -$x $y native(x,y);
