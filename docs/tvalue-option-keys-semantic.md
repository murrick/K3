# Семантическая квалификация предвычисленных option keys

2026-10-22. Родитель `8d7a41ef0e8511ce964969cdcfe0bbab15cb9b37`.
Native pin `60fdc34736c51fa4e5659982630347fb1d1b59fc`.
Кандидат прежний: `docs/tvalue-option-keys/candidate.patch`.
Product-код, develop и defaults не изменялись; данных о speedup этот этап не даёт.

## Результат

Все 56 JVM завершились с exit code 0; 28 пар baseline/keys совпали по
assertion projections, ошибок тестов и несовпадений не обнаружено.
2 108 — число выполнений тестовых методов с повторами, а не уникальных тестов
и не число Mind.query. Дополнительные micro-gate assertions сюда не прибавлялись.

| Серия | JVM | Вызовов методов корпуса |
|---|---:|---:|
| Пять корпусов × defaults/OFF/ON/VERIFY/mixed × baseline/keys | 10 | 1 260 |
| Session options offline × baseline/keys | 2 | 424 |
| Session options с аргументом smart, локальный DUMB2 × baseline/keys | 2 | 424 |
| Целевые gates | 40 | отдельно не подсчитывались |
| Smoke | 2 | отдельно не подсчитывались |

Три transaction пары OFF/ON/VERIFY совпали побайтно по обоим файлам states/work:
по 20 операций на JVM, 120 наблюдений операций в шести процессах. В каждом
states файле 140 строк. Включены точные values переменных, результаты,
solutions/hypotheses/rules и счётчики; исходные внутренние rollback/commit
assertions также прошли.

Есть **четыре диагностических предупреждения**: baseline и keys каждый дважды
достигли штатного watchdog 10 000 мс в `set_06_0F` в DUMB2 options runner.
Оба процесса затем завершили все 212 методов с нулевыми failures и exit code 0.
Полные stderr snapshots/thread dumps сохранены, watchdog не отключался.
Остальные 54 процесса завершились без stderr. Это результат с диагностическими
предупреждениями, не утверждение об отсутствии всех проблем времени исполнения.

Для данного минимального кандидата расширенная семантическая проверка пройдена.
Следующий шаг может быть отдельным интеграционным патчем, с проверкой актуальной
integration базы; эта ветка по-прежнему содержит только эксперимент и evidence.
SMART ownership/ACL, полный reactor/backend fault/recovery inventory, production
интеграция и устойчивый inference speedup **не квалифицированы**. Результаты
предыдущих allocation/time экспериментов этим этапом не усиливаются.

## Область проверки

Использованы неизменённые qualification источники exact native pin.
`LatentSubstitutionCorpusRunner` запускает пять корпусов: completed overlay
исторического KangerTest (103 метода), stabilization (9), C1 promotion (5),
C4 intervals (3), C3 function bindings (6). Всего 126 методов на JVM.
Проверяются defaults (properties отсутствуют), все OFF, все ON, VERIFY
и смешанное начальное состояние четырёх ON/четырёх OFF. Это проверяет
JVM-default fallback, на который направлен кандидат.

`KangerOptionsRunner` запускается отдельно offline и с аргументом smart на
одноразовой базе локального DUMB2. Каждый запуск выполняет штатную historical/completed проверку
дважды с session options OFF/ON и три structured regressions с префиксом set_s5a_03 — 212
вызовов методов. Runner также проверяет команды переключения всех/одной опции,
наследование дочерней транзакцией, изоляцию другого User, timezone validation
и mixed report. Session overrides обходят JVM-default fallback, поэтому
эти результаты дополняют property corpus, а не заменяют его.

Дополнительные исходные gates:

- SolveSync aliases и custom-map fallback; вложенный commit/rollback, отказ
  конфликтующей вставки, удаление и повторный запрос в transaction projection.
- CauseWeight shadow boundaries и fallback при ожидаемых custom/missing-term
  ошибках; исходный runner проверяет единственность пойманной диагностики.
- Single TValue lookup, resident base comparison, compact find snapshot ownership
  и custom-cache fallback.
- TValue index preservation, boundary и legacy DUMB reopen/rollback/commit.

Всего 20 конфигураций gates на каждую реализацию: девять defaults, пять OFF,
три VERIFY и transaction projection OFF/ON/VERIFY. В VERIFY включены
verifyTValueIndex, verifySolveSync и shadowCauseWeights. Некоторые штатные gates
сами меняют отдельные properties — это сохранённое поведение их исходников,
а не обещание, что вся JVM оставалась в исходной конфигурации.

## Сборка и контроль сравнения

Существующие clean baseline/keys сборки той же облачной среды проверены по всем
source/class hashes, JAR contents и версии Java. Preflight также проверяет
оба предыдущих manifests. Кандидат не переписан и не пересобран с иными изменениями.

Все 87 новых source files дополнительно сверены побайтно с `git show` exact pin.
Новый вспомогательный build компилирует 87 pinned источников: локальный DUMB2 backend,
корпуса и выбранные gates. Получены 162 класса; 18 пересекающихся с прежним
runtime corpus классов побайтно совпадают с frozen build. Они одинаковы для
baseline и candidate. Отдельный SemanticLaunch выводит начальный snapshot
восьми опций, отражённо вызывает неизменённый gate и не меняет его утверждения.
Успешное возвращение отмечено отдельным marker; исключение исходного gate
пробрасывается с исходной причиной. Источники/классы/команды/hashes — build-validation.json.

Перед длинной серией выполнены два SingleTValueLookup OFF smoke запуска.
Каждая JVM имеет собственные user.home и java.io.tmpdir; все создаваемые runner
временные User/DB directories находятся под этим каталогом и удаляются после
завершения процесса. DUMB2-база одноразовая, общие или production базы не открываются.

Все stdout/stderr сохранены в gzip. `analyze.py` проверяет exit status,
pass/return markers, отсутствие нераспознанного stderr, нулевые suite failures, точный inventory
126/212 методов и ожидаемый начальный snapshot для каждой конфигурации.
Штатный 10-секундный watchdog является диагностикой, а не deadline процесса:
Diagnostics.Watchdog печатает snapshot/thread dump и не отменяет тест.
Его полные блоки распознаются отдельно только для известных методов DUMB2 options
runner; любое другое stderr, exception/error header, ненулевой exit или timeout
процесса остаётся ошибкой. Watchdog не отключался, stderr не удалялся. Полный
процесс имеет отдельный предел 300 секунд. Число предупреждений записано в summary.

Сравниваются детерминированные assertion projections: названия тестов, исходные
PASS/OK markers, числа success/fail и целевые boundary records. Полный stdout
с timing/path сведениями побайтно не сравнивается. Это parity исходных тестовых
утверждений, а не запись всех Mind.query результатов всего корпуса.

Transaction runner дополнительно сохраняет полные текстовые states и work:
ответы, точные values переменных, solutions, hypotheses, rules и счётчики
вложенных сценариев. Эти файлы сравниваются побайтно baseline/keys для каждой
из трёх конфигураций. Observer hooks и оба диагностических журнала к широкому
корпусу здесь не подключаются; их прежняя квалификация остаётся ограниченным
resident сценарием. Root-clear ID repair этим этапом не квалифицируется.

## Воспроизведение

Из корня K3 после восстановления exact sibling trees и прежних проверенных
`../build/option-keys` артефактов по предыдущему отчёту:

```sh
python3 docs/tvalue-option-keys-semantic/preflight.py
python3 docs/tvalue-option-keys-semantic/build.py
python3 docs/tvalue-option-keys-semantic/run.py --smoke
python3 docs/tvalue-option-keys-semantic/run.py
python3 docs/tvalue-option-keys-semantic/analyze.py
python3 docs/tvalue-option-keys-semantic/manifest.py
```

Для повторного запуска нужен отдельный каталог без прежних result файлов:
скрипт не перезаписывает опубликованную серию. Java OpenJDK 21.0.12.1 Debian amd64,
ECJ 3.33.0 с прежним проверенным SHA256. В этом этапе JFR и timing ratios нет.
Source pin и полный план сохранены до первого длинного запуска в environment.json.
Полный reactor/test inventory, backend fault/recovery corpus и production
интеграция этим набором не покрыты. Аргумент `smart` исходного options runner фактически выбирает локальный
`org.kanger.storage.dumb2.DB`; README pin называет его SMART. Здесь не проверяются
SMART ownership/ACL и не заявляется полная квалификация SMART.
