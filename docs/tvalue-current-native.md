# Resident inference на актуальной native-базе: совместимость и профиль настроек

2026-10-22. Родитель: `95f1c4ef93bd228ce04c35e03fc5fc8380f5919a`.
Исследуемый develop/3.8.0: `60fdc34736c51fa4e5659982630347fb1d1b59fc`.
Прежняя native-база: `2422f7f6d9e1af7608203b1566df64b5f29fe344`.
Изменены только новые qualification fixtures, отчёт и evidence.

## Результат

**57 JVM, 40 320 запросов**, все проверки прошли. Текущий native сохраняет
ответы, точные подстановки и диагностические трассы прежнего ограниченного
resident сценария. Это позволяет продолжать данный эксперимент на новом pin;
полная совместимость ядра, SMART и всего inference corpus этим не заявляется.

Профиль выделил `OptimizationOptions.enabled` как кандидат для отдельной
оптимизации. Публичные session overrides с теми же значениями OFF обходят
JVM-default fallback. В диагностической пробе это уменьшает медиану thread
allocations семи запросов с примерно 9.14 МБ до 7.04 МБ в attached candidate,
без изменения ответов/журналов. Это **не новый патч ядра и не доказанный speedup**.

## Изменения develop

Прежний pin является предком актуального develop. В `kanger/src`:
39 добавленных и десять изменённых файлов, всего 49. Изменения затрагивают
Mind.compileLine, Linker, technical transactions, восстановление удалённых
правил/переменных, аргументы компилятора и DMZ capture/restriction paths.
TValue и TValueFactory не изменились. OptimizationOptions также побайтно прежний:
обнаруженная стоимость настроек не введена последними DMZ изменениями.

Собраны 712 классов старого и 930 текущего runtime; состав сборки включает core,
UDF, command, bootstrap, legacy DUMB, console и прежние вспомогательные fixtures.
37 общих классов различаются; добавлено 218, удалений нет. Это число классов всей
сборки, а не только 49 изменённых файлов ядра. Полный patch и перечни сохранены
в native-core-diff.patch.gz и develop-drift.json. Новый runtime не подменяет старый
в прежних evidence или manifests.

Прежние hooks применены к Mind/TValueFactory/TValue на обеих native-базах.
Все substitutions однозначны, обратное удаление hooks восстанавливает исходники
побайтно. Clean/hooked различаются только квалифицированными hook-классами.
Оба диагностических модуля пересобраны и совпадают по всем class hashes с frozen
context-snapshots baseline и TOUCH-strings candidate. Старый runtime совпадает
с frozen Kanger classes; сохранилось прежнее отличие Rhino NativeArray при
сборке Java 21 вместо Java 17.

## Матрица и корректность

48 контрольных JVM: old/current × on/off/verify × два повтора ×
clean/disabled/baseline/candidate; по 24 цикла семи запросов. Порядок баз и модулей
обращается во втором повторе. Всего 8 064 запроса в этой части.

Пять отдельных JFR JVM: old-clean и current-clean/disabled/baseline/candidate,
только OFF, по 512 циклов. Ещё четыре JFR JVM повторяют current-clean/candidate
через JVM properties, затем через public `OptimizationOptions.setAll(m,false)`.
Все восемь эффективных оптимизаций остаются OFF; это настройка fixture, а не
изменение defaults. Запросы и expected results прежние. Эти девять JVM дают
32 256 запросов. Timing ratios между JVM не квалифицируются.

Все 40 320 native строк совпали с clean контролями, а current также с old.
Проверены **11 520** точных variable rows `[{x=Tom}]`. Все **2624** attached
trace прошли authority replay; **1824** сравнения candidate с baseline совпали
побайтно, как и callback counts. Это включает сравнение обоих override probes
с основным baseline. Все **288** межверсионных attached трасс короткой матрицы
также совпали побайтно. Сессии освобождают observer/context references, root
reservations остаются нулевыми, storage не используется.

## Профиль запросов

JFR: ExecutionSample period 5 мс, stack depth 128 и ObjectAllocationSample.
Пользовательские события отмечают ровно каждый Mind.query: 3584 события на JVM,
32 256 в девяти запусках. Временные интервалы не пересекаются. Для атрибуции
требуется совпадение thread ID, timestamp внутри query event и стек с Mind.query.
Startup, seed, записи trace, явные observes и finish в query profile не входят.
Подсчёт не основан на имени потока или на предположении о длительности прогрева.

Часть 0–127 и часть 128–511 показаны отдельно; последняя называется late,
а не steady state. В current-clean late variable части 62 CPU samples,
из них в 50 есть Mind.link/Linker.link, в 44 — rotateVariables.
В 12 ближайшим Kanger frame является OptimizationOptions.enabled. В отдельной
повторной property JVM это 5 из 44 samples, в session-override JVM — 0 из 54.
Малая выборка и inlining не позволяют толковать ноль как отсутствие вызовов или
назначать точные проценты CPU. Поддерживается гипотеза для адресного эксперимента,
а не точное ранжирование всех узких мест.

В query timers сохраняется точный ThreadMXBean allocation accounting.
Сумма семи per-query измерений сверяется с циклом для каждого запуска.
AllocationSample weights — отдельные выборочные оценки; они не заменяют
точные счётчики и не используются для приведённой ниже таблицы.

## Проба JVM defaults против session overrides

Медиана суммы allocated bytes семи Mind.query в циклах 128–511:

| Runtime/модуль | JVM properties: основная проба | JVM properties: повтор | Session OFF |
|---|---:|---:|---:|
| current clean | 4 257 648 | 4 127 312 | 3 777 416 |
| current attached candidate | 9 142 480 | 9 141 728 | 7 035 952 |

Одинаковые flags и ответы не означают одинаковый JIT-код. Здесь одна session JVM
на режим и две property JVM; профилирование активно, хост общий, pinning нет.
Вся разница не приписывается только конкатенации строк: изменение ветвления и
JIT-оптимизаций может менять другие allocations. В чистом режиме экономия
относительно двух property контролей 0.35–0.48 МБ; в attached — около 2.11 МБ.
Предыдущие утверждения об allocation выигрыше TOUCH автоматически не переносятся
на эту новую настройку. Измерение скорости и статистическая значимость не заявлены.

По исходнику enabled каждый раз разрешает имя опции, читает volatile overrides,
а при отсутствии override собирает `"kanger.experiment." + name` и вызывает
System.getProperty. Public setAll(false) меняет только путь разрешения одинаковых
OFF значений. Следующий минимальный кандидат — предвычислить **имена ключей**,
сохранив динамическое чтение значений JVM и live User overrides. Кэшировать сами
значения опций этим исследованием не предлагается.

## Evidence, воспроизведение и восстановление

Java Temurin 21.0.6, ECJ 3.33 с проверенным SHA256, macOS aarch64.
Native trees располагаются в `../K3-observer-native` и `../K3-current-native`
на указанных pin; compiler — `../tooling/ecj.jar`. Сначала создать profile.jfc:

```sh
jfr configure --input profile --output docs/tvalue-current-native/profile.jfc \
 'jdk.ExecutionSample#period=5 ms' 'jdk.ObjectAllocationSample#enabled=true'
python3 docs/tvalue-current-native/build.py
python3 docs/tvalue-current-native/run.py
python3 docs/tvalue-current-native/export.py
python3 docs/tvalue-current-native/analyze.py
python3 docs/tvalue-current-native/profile.py
python3 docs/tvalue-current-native/manifest.py
```

Экспорт сохраняет полный набор только трёх используемых event types в JSON.gz,
включая полные выбранные стеки. Остальные JFR metadata не публикуются; исходные
контейнеры остаются в локальной scratch-папке, их hashes записаны. Анализ повторяется
непосредственно по сохранённым event exports. Native rows, CSV, stdout/stderr,
полные trace/event архивы, сборки, источники hooks и manifests сохранены.

Предыдущая временная рабочая копия была утрачена после прерывания чата.
Её завершённые 53 запуска и незавершённая override проба **не входят** в эти
итоги. Все 57 окончательных JVM повторены с сохранением evidence в постоянной
папке проекта. Старые опубликованные этапы не изменены.

## Решение

Ограниченный public API сценарий и прежние журналы совместимы с новым native pin.
Полный corpus, SMART persistence, live multi-user switching, concurrency и root-clear
ID repair не квалифицированы. Старые failure/wiring серии не повторялись:
диагностические модули совпали с frozen bytecode, проверка native hooks здесь
позитивная и структурная. Не выдавать её за полную отрицательную квалификацию.

Ядро и production/defaults не менялись. Продолжить отдельным небольшим кандидатом
на имена option keys, с проверками изменения JVM properties после первого чтения,
индивидуальных overrides, User isolation, атомарного setAll, ошибок неизвестных имён
и сравнением native результатов/обоих журналов. Это содержательнее очередного
увеличения фиксированного прогрева.
