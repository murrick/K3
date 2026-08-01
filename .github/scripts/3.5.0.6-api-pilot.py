#!/usr/bin/env python3
"""Apply the first documentation-only public API contract package."""

from pathlib import Path
import re


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit("%s: expected one anchor, found %d" % (label, count))
    return text.replace(old, new, 1)


def regex_once(text, pattern, replacement, label):
    result, count = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
    if count != 1:
        raise SystemExit("%s: expected one regex anchor, found %d" % (label, count))
    return result


user_path = Path("kanger/src/org/kanger/interfaces/IUser.java")
user = user_path.read_text(encoding="utf-8")
user = regex_once(
    user,
    r"/\*\*\s*\n\s*\* Описатель объекта пользователя\.\s*\n\s*\*/\s*\npublic interface IUser \{",
    """/**
 * Внешний пользовательский контекст KANGER.
 *
 * <p>Интерфейс объединяет три совместимые, но независимые роли: внешний
 * идентификатор пользователя, сохраняемый набор строковых параметров и
 * caller-managed ссылку на текущий {@link IMind}. Ядро KANGER не выполняет
 * аутентификацию и не придаёт {@link #getId()} глобальной семантики: значение
 * хранится для приложения, storage-модуля или иной внешней интеграции.</p>
 *
 * <p>Параметры принадлежат объекту пользователя и могут сохраняться в
 * {@code kanger.conf}, если задан {@code user.dir}. Методы чтения с default
 * имеют побочный эффект: отсутствующее значение регистрируется и может быть
 * записано в конфигурационный файл. Поэтому этот интерфейс не является
 * неизменяемым property view.</p>
 *
 * <p>{@code currentMind} — compatibility slot, управляемый вызывающей
 * стороной. Он не является lifecycle authority, владельцем storage,
 * указателем опубликованной транзакции или заменой {@link IMind#commit(IMind)}
 * и {@link IMind#release(IMind)}. Приложение обязано само поддерживать ссылку
 * в соответствии со своим transaction workflow.</p>
 */
public interface IUser {""",
    "IUser class contract",
)
user = replace_once(
    user,
    """    IMind getCurrentMind();

    void setCurrentMind(IMind mind);""",
    """    /**
     * Возвращает сохранённую приложением ссылку на текущий Mind.
     *
     * <p>Метод не вычисляет вершину transaction chain и не проверяет, что
     * объект ещё активен. Значение может быть {@code null}; ядро KANGER не
     * использует этот slot как источник истины lifecycle.</p>
     *
     * @return caller-managed ссылка на Mind или {@code null}
     */
    IMind getCurrentMind();

    /**
     * Сохраняет caller-managed ссылку на текущий Mind.
     *
     * <p>Операция не выполняет commit, release, reservation, storage
     * publication или cleanup. Передача {@code null} только очищает slot.
     * Вызывающая сторона отвечает за согласованность ссылки со своим
     * transaction workflow.</p>
     *
     * @param mind сохраняемый Mind или {@code null}
     */
    void setCurrentMind(IMind mind);""",
    "IUser current Mind methods",
)
user_path.write_text(user, encoding="utf-8")


mind_path = Path("kanger/src/org/kanger/interfaces/IMind.java")
mind = mind_path.read_text(encoding="utf-8")
mind = regex_once(
    mind,
    r"/\*\*\s*\n\s*\* Интерфейс описателя транзакции\..*?\n\s*\*/\s*\npublic interface IMind \{",
    """/**
 * Публичный контракт активного логического контекста KANGER.
 *
 * <p><strong>Модель.</strong> Корневой Mind владеет базовым пользовательским
 * контекстом, а созданный от него дочерний Mind представляет изолированный
 * transaction overlay. Цепочка направлена от ребёнка к родителю через
 * {@link #getNext()}; {@link #getTop()} возвращает корневой уровень. Видимое
 * состояние складывается из текущего overlay и родительских уровней, но новые
 * изменения принадлежат только уровню, на котором они созданы.</p>
 *
 * <p><strong>Запрос и компиляция.</strong> {@link #query(String)} выполняет
 * один оператор языка, насыщает состояние до выбранной неподвижной точки и
 * публикует query-local Solutions, Values, Hypothesis и Log. Результат
 * трёхзначен: {@code true}, {@code false} или {@code null} для
 * неопределённости. Flood/error не является частичным логическим ответом.
 * {@link #compile(String)} атомарно принимает набор правил/утверждений либо
 * отвергает весь переданный текст при конфликте или ошибке.</p>
 *
 * <p><strong>Транзакции.</strong> Родитель применяет непосредственного
 * ребёнка через {@link #commit(IMind)} или отбрасывает его через
 * {@link #release(IMind)}. После завершения ребёнок больше не должен
 * использоваться вызывающей стороной. Commit сохраняет canonical identity,
 * выполняет conflict/deduplication checks и публикует только полностью
 * завершённую дельту; failure не разрешает наблюдать частично применённый
 * child state.</p>
 *
 * <p><strong>Storage и lifecycle.</strong> Операции use/close/clear/reindex
 * могут заменить возвращаемый активный Mind и уничтожить незавершённые
 * дочерние уровни, что прямо отмечено в их контрактах. Storage implementation
 * выбирается дистрибутивом; IMind предоставляет логические операции и не
 * обещает caller-у конкретную физическую модель хранения.</p>
 *
 * <p><strong>Concurrency.</strong> Публичные операции Mind используют
 * внутренний reservation/locking protocol, однако связанные result stores,
 * factory views и compatibility slot {@link IUser#getCurrentMind()} не
 * превращают объект в произвольно конкурентный mutable facade. Caller обязан
 * сериализовать собственный workflow и использовать объект Mind, возвращённый
 * lifecycle-операцией.</p>
 */
public interface IMind {""",
    "IMind class contract",
)
mind_path.write_text(mind, encoding="utf-8")


linker_path = Path("kanger/src/org/kanger/Linker.java")
linker = linker_path.read_text(encoding="utf-8")

linker = replace_once(
    linker,
    "    public Linker(Mind mind) {",
    """    /**
     * Создаёт per-Mind исполнитель насыщения.
     *
     * <p>Экземпляр заимствует Mind и его LogStore, но не приобретает
     * самостоятельного transaction или storage ownership. Caller не должен
     * передавать один Linker другому Mind или запускать его конкурентно.</p>
     *
     * @param mind активный runtime-контекст, чьи фабрики и query-local stores
     *             используются во всех последующих вызовах
     */
    public Linker(Mind mind) {""",
    "Linker constructor",
)

linker = replace_once(
    linker,
    "    public LinkerStatistics snapshotStatistics() {",
    """    /**
     * Возвращает отделённый снимок диагностических счётчиков последнего
     * запуска Linker.
     *
     * <p>Снимок не является частью логического ответа, не владеет runtime
     * объектами Mind и не изменяется следующими проходами. Значения пригодны
     * для profiling/qualification, но не для управления семантикой запроса.</p>
     *
     * @return независимый snapshot текущей статистики
     */
    public LinkerStatistics snapshotStatistics() {""",
    "Linker statistics snapshot",
)

linker = replace_once(
    linker,
    "    public void link(Rule rule, boolean logging) throws Exception {",
    """    /**
     * Выполняет насыщение принадлежащего экземпляру Mind до неподвижной точки.
     *
     * <p>Перед первым pass очищаются query-local used/excluded/calculated,
     * flood, TSolve-index и statistics state. При {@code rule == null}
     * обходятся все видимые неудалённые Rule. Ненулевой seed ограничивает
     * начальный набор переданным Rule и транзитивно добавляемыми
     * opposite-polarity, already-used и новыми generated кандидатами.</p>
     *
     * <p>Метод координирует Rule/TValue/FValue actions, функции, системные
     * предикаты, hypotheses и отложенную materialization. Он не открывает
     * отдельную пользовательскую транзакцию: caller обязан вызвать его внутри
     * корректно зарезервированного Mind workflow. Исключение, включая flood
     * control, не означает частичный логический результат.</p>
     *
     * @param rule seed Rule для локализованного насыщения или {@code null}
     *             для полного видимого Rule-set
     * @param logging {@code true}, если pass/timing и semantic events должны
     *                публиковаться в LogStore Mind
     * @throws Exception при ошибке unification, вычисления, materialization
     *                   или превышении flood limit
     */
    public void link(Rule rule, boolean logging) throws Exception {""",
    "Linker link contract",
)

linker = replace_once(
    linker,
    "    private boolean isValidFor(SortedSet<TVariable> tail) throws Exception {",
    """    /**
     * Проверяет, совместима ли текущая частичная подстановка с уже
     * зарегистрированными TSolve.
     *
     * <p>{@code tail} является непустым suffix вращаемых TVariable; первая
     * переменная уже имеет current TValue. Для каждого TVariableSet,
     * содержащего её, метод требует существования хотя бы одного TSolve,
     * совместимого со всеми уже назначенными переменными из suffix. Unary
     * tuple не добавляет межпеременного ограничения. Если релевантных
     * TVariableSet нет, частичная подстановка допустима.</p>
     *
     * <p>Это query-local join/filter, а не создание TValue/TSolve и не
     * доказательство Rule. Tuple index только ускоряет поиск и синхронно
     * достраивается из authoritative {@code mind.getRuleSolves()}.</p>
     *
     * @param tail непустой упорядоченный suffix переменных с установленными
     *             current bindings для уже вращаемой части
     * @return {@code true}, если ограничений нет или найден совместимый
     *         зарегистрированный tuple; иначе {@code false}
     * @throws Exception если current binding или tuple metadata не могут
     *                   быть разрешены в текущем Mind
     */
    private boolean isValidFor(SortedSet<TVariable> tail) throws Exception {""",
    "Linker partial binding compatibility",
)

linker = replace_once(
    linker,
    "    private boolean linkDatabase(List<Domain> tree, Map<IRule, Set<Cause>> causes, Set<TVariable> tvars, boolean logging) throws Exception {",
    """    /**
     * Классифицирует terminal branch и регистрирует её отложенный
     * семантический эффект.
     *
     * <p>Историческое имя не обозначает физический database I/O. После
     * {@link #checkSystem(List, boolean)} метод собирает текущий solve,
     * разделяет Domains на stored, calculated, excluded, assumed и обычные
     * candidates, после чего либо отмечает ровно выводимый Domain как
     * produced, либо добавляет альтернативные temporary hypotheses.
     * Canonical Rule создаётся позже в {@code updateDatabase()}, после
     * завершения branch traversal.</p>
     *
     * <p>Возвращаемое значение сообщает только о новом produced-domain
     * эффекте, который требует продолжения saturation. Создание одной
     * hypothesis само по себе не превращает результат в {@code true}; её
     * собственный action signal учитывается владельцем store. Метод может
     * изменять produced domains, causes, solves, calculated/used marks,
     * temporary hypotheses и diagnostic log.</p>
     *
     * @param tree terminal conjunction текущей Rule branch
     * @param causes накопленные provenance-связи по Rule
     * @param tvars переменные Rule, из current bindings которых строится
     *              solve для materialized результата
     * @param logging {@code true} для публикации classification/provenance
     *                событий в LogStore
     * @return {@code true}, если зарегистрирован новый produced Domain;
     *         иначе {@code false}
     * @throws Exception при ошибке разрешения Domain, system predicate,
     *                   provenance или hypothesis state
     */
    private boolean linkDatabase(List<Domain> tree, Map<IRule, Set<Cause>> causes, Set<TVariable> tvars, boolean logging) throws Exception {""",
    "Linker branch classifier",
)

linker = replace_once(
    linker,
    "    public boolean calcFunctions(List<Domain> master, Map<IRule, Set<Cause>> causes, boolean logging) throws Exception {",
    """    /**
     * Вычисляет пустые calculable Function occurrences в переданных Domains.
     *
     * <p>Каждое подходящее вхождение очищается и передаётся Calculator
     * текущего Mind. Canonical FValue identity, UDF binding и invalidation
     * остаются обязанностью Calculator/FunctionFactory/FValueFactory. Метод
     * не выполняет отдельный saturation pass и не использует параметр
     * {@code causes}; параметр сохранён в исторической сигнатуре.</p>
     *
     * @param master Domains текущей branch
     * @param causes исторический параметр provenance; методом не изменяется
     * @param logging {@code true} для публикации вычислительных событий
     * @return {@code true}, если хотя бы одно вычисление создало новый
     *         surviving result
     * @throws Exception при ошибке разрешения или выполнения функции
     */
    public boolean calcFunctions(List<Domain> master, Map<IRule, Set<Cause>> causes, boolean logging) throws Exception {""",
    "Linker function calculation",
)

linker = replace_once(
    linker,
    "    public boolean checkSystem(List<Domain> tree, boolean logging) throws Exception {",
    """    /**
     * Выполняет системные предикаты branch и определяет, блокируют ли они
     * дальнейшую классификацию.
     *
     * <p>Успешные complete system Domains получают calculated mark. Если
     * любой системный предикат противоречит своей polarity либо после
     * частичного успеха остаётся невычисленный system Domain, все marks
     * текущей попытки снимаются и branch блокируется. Полные наборы current
     * TValue публикуются как TSolve только после согласованного успеха всей
     * системной части.</p>
     *
     * <p>{@code true} означает отсутствие доказанного блока, а не
     * обязательную вычисленность каждого ещё неполного предиката.</p>
     *
     * @param tree terminal conjunction, содержащая обычные и/или системные
     *             Domains
     * @param logging сохранённый orchestration flag; непосредственный вывод
     *                этим методом не производится
     * @return {@code false}, если системная часть отвергает branch;
     *         иначе {@code true}
     * @throws Exception при ошибке выполнения системного предиката или
     *                   регистрации TSolve
     */
    public boolean checkSystem(List<Domain> tree, boolean logging) throws Exception {""",
    "Linker system predicate contract",
)

linker_path.write_text(linker, encoding="utf-8")
