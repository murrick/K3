# Предвычисление имён JVM option keys: отдельный облачный эксперимент

2026-10-22. Родитель `dcc8c853a5ea6e7ce7f3963a7558890f25ce0420`.
Native pin `60fdc34736c51fa4e5659982630347fb1d1b59fc`, прежний pin
`2422f7f6d9e1af7608203b1566df64b5f29fe344`.

## Результат и границы

Минимальный кандидат квалифицирован в ограниченном resident сценарии.
43 inference JVM, 20 426 запросов, 5 836 точных строк `[{x=Tom}]`,
1 606 authority replay traces и 948 сравнений trace/event файлов с отдельными
контролями прошли. Ещё две JVM проверили контракт OptimizationOptions.
Native строки совпали, оба диагностических журнала и callback counts сохранены.
Полный inference corpus и SMART не квалифицированы. Inference speedup не установлен.
Develop, product core и production/defaults не менялись.

Кандидат хранится в `tvalue-option-keys/OptimizationOptions.java`, применимый
минимальный diff — `candidate.patch`. Только имена `kanger.experiment.*`
вычисляются при инициализации класса; каждое fallback чтение вызывает прежний
`System.getProperty` и `Boolean.parseBoolean`. Индекс ключа получен из уже
разрешённого бита. Значения опций не кэшируются, массив приватный.
Session, volatile publication, set, setAll, snapshot и валидация имён прежние.
В clean и hooked сборках меняется ровно `OptimizationOptions.class`;
`OptimizationOptions$Session.class` побайтно прежний.

## Контракт и корректность

`OptionsContract.java` выполняется отдельно на baseline и candidate. Для всех
восьми имён проверены отсутствие property/default true, изменение после первого
чтения false → TrUe → невалидная строка → удаление, live individual overrides,
общие overrides двух Mind одного User, изоляция другого User и его JVM fallback.
Проверены setAll(false/true/false), ошибки неизвестных/null имён, отсутствие User,
snapshot и защитная копия names. Конкурентный writer переключает setAll 200 000 раз;
reader видит только полные опубликованные слова 0xFF/0xFFFF. Это проверка
атомарной публикации; отдельные вызовы enabled/snapshot не обещают транзакционный
снимок нескольких опций при конкурентном writer. Полный concurrency corpus
не квалифицирован. Логи содержат число наблюдений каждой JVM.

Smoke: old/current/keys, OFF, attached TOUCH journal, два цикла по семь запросов.
Native строки и trace/event файлы всех трёх совпали до длинных запусков.
Матрица: current/keys × OFF/ON/VERIFY × clean/журнал context-snapshots/журнал
TOUCH-strings × два повтора, 24 цикла. Во втором повторе порядок реализаций и
модулей обращён. 36 JVM и 6 048 запросов. Затем четыре OFF JFR JVM:
current/keys × clean/TOUCH-strings, 512 циклов, 14 336 запросов.
Вместе со smoke: 43 JVM, 20 426 запросов. Session overrides в измерительной серии
не используются. Проверки ответов, точных substitutions, root reservations,
resident storage и освобождения observer/context references остаются в fixture.

Все native строки сравниваются с контролями; для короткой матрицы сравниваются
оба варианта журнала и обе реализации, профильные attached traces — с таким же
baseline журналом. `analyze.py` выполняет replay и проверяет CSV accounting:
суммы семи отдельных query timers/allocations равны query cycle для каждого цикла.

## Allocations отдельно от времени

Медианы суммы семи Mind.query, циклы 128–511, байты ThreadMXBean:

| Режим | Baseline | Keys | Keys / baseline: bytes | Keys / baseline: time |
|---|---:|---:|---:|---:|
| clean | 4 218 976 | 3 756 776 | 0.8904 | 1.0027 |
| attached TOUCH journal | 9 113 616 | 7 007 680 | 0.7689 | 0.9512 |

Это облачные Linux измерения одной среды. Прежние macOS времена и CSV не
объединялись с ними. Одна профильная JVM на конфигурацию, активный JFR, общий
хост, без CPU pinning; поздний интервал не объявляется steady state.
Allocation difference поддерживает адресный кандидат, но не доказывает, что
каждый сэкономленный байт вызван только конкатенацией: JIT и ветвления также
могут менять allocations. Временные отношения диагностические, статистическая
значимость и устойчивый inference speedup не заявляются.

14 336 пользовательских query events точно соответствуют query CSV. CPU/alloc
samples привязаны по thread ID, времени внутри query interval и наличию
Mind.query в стеке. Ранние 0–127 и поздние 128–511 интервалы раздельны.
Выборочные ObjectAllocationSample weights не заменяют точные allocated bytes.

## Среда, сборка и воспроизведение

Git 2.52.0, Python 3.12.14, ECJ 3.33.0; сборка и запуск OpenJDK
21.0.12.1 Debian amd64. Полный Oracle JDK 21.0.2 предоставляет jfr 21.0.2 для
configure/export; этот инструмент успешно прочитал все четыре recording.
Версии, URL загрузок и hashes — `toolchain.json`. ECJ SHA256
`f7686c4960cf70c2ebc5c500a73a8cfc04541b730c18f1c5c21329889b137f45`
совпадает с прежним manifest. Обе exact trees заново восстановлены через
`git archive` в sibling `../K3-observer-native` и `../K3-current-native`.
Случайный HEAD и прежние внешние build directories не использовались.
Доступных AGENTS.md в checkout и обеих exact trees не найдено; lifecycle прочитан.

Запуск из корня K3, в новой папке evidence без существующих result файлов:

```sh
git fetch origin 60fdc34736c51fa4e5659982630347fb1d1b59fc 2422f7f6d9e1af7608203b1566df64b5f29fe344
# Скачать проверенный ECJ в ../tooling/ecj.jar; JAVA_HOME — полный JDK 21.
python3 docs/tvalue-option-keys/prepare.py
python3 docs/tvalue-option-keys/build.py
python3 docs/tvalue-option-keys/run.py --smoke
"$JAVA_HOME/bin/jfr" configure --input profile --output docs/tvalue-option-keys/profile.jfc 'jdk.ExecutionSample#period=5 ms' 'jdk.ObjectAllocationSample#enabled=true'
python3 docs/tvalue-option-keys/run.py
python3 docs/tvalue-option-keys/export.py
python3 docs/tvalue-option-keys/analyze.py
python3 docs/tvalue-option-keys/profile.py
python3 docs/tvalue-option-keys/manifest.py
```

`prepare.py` отказывается перезаписывать существующие native trees. Сборка пишет
в новый `../build/option-keys`, сверяет frozen class hashes диагностических модулей,
обратимость hooks и область изменённых class files. Исходники модулей сохранены.
Для повторения опубликованные данные следует оставить неизменными: запускать
в отдельном checkout с новой копией каталога скриптов без прежних result файлов.

В новых копиях export.py и profile.py macOS jfr path заменён на
JAVA_HOME/bin/jfr либо PATH; прежние скрипты и evidence не редактировались.
Публикуются только JSON.gz exports org.kanger.ResidentQuery,
jdk.ExecutionSample, jdk.ObjectAllocationSample с полными выбранными стеками.
Raw JFR перенесены в непубликуемый `../build/option-keys/private-recordings`;
их SHA256 записаны в result manifests. Контейнеры и остальная metadata JFR
в коммит не входят. CSV, native строки, stdout/stderr, журналы, hashes сборок,
исходники и проверяемый manifest остаются в репозитории.
