# TValue journals: три routes без временного TreeSet

Ветка `experiment/3.8.0-tvalue-journal-three-routes`, родитель `5f1ab2615e57292bdce831cf551f038e1bc0896b`. Native-база остается `2422f7f6d9e1af7608203b1566df64b5f29fe344`.

## Изменение

Metadata callback каждого из двух журналов прежде создавал Arrays.asList и TreeSet для трех long: ранее наблюдавшегося route, oldVariable и текущего TVar ID. Теперь три сравнения/обмена упорядочивают три примитивных значения, а проверки равенства исключают дубликаты. Порядок сохраняется как natural signed-long ordering, без вычитания и риска overflow. Частный metadataTouch записывает те же dirty routes, счетчик и TOUCH строки.

Изменены только два диагностических journal source файла. Consumer, bridge, hooks, обе независимые authority reads, snapshots, full comparison, counters и typed refusals сохраняются. Snapshot/state sorting не менялся. Production, defaults, Maven reactor и develop не меняются. Диагностический jar действительно изменился; новая реализация используется только в этом экспериментальном source set.

## Проверка

49 JVM: 9 consumer (три повтора on/off/verify), 3 owner/failure/recovery, 2 route-order, 28 cost, 4 wiring-refusal и 3 disagreement-gate.

63 accepted consumer traces, native rows, diagnostics и console output совпали побайтно с прежним stage. Recycled-ID, missing metadata, adapter failure и no-session refusals сохраняются; root-clear ID reuse не исправлен в native. Native controls совпали во всех cost JVM, 56 representative cost traces совпали с прежними и прошли replay. Оба полных журнала и full authority checks всегда активны в consumer/cost tests.

RouteOrderRunner сравнивает baseline/candidate и проверяет по 50 cases: пять route-ID × пять oldVariable для обоих журналов, с current route=0. Набор включает Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE, повторы и все случаи положения current route. Диагностическое observed mapping намеренно меняется через reflection; native state не меняется. Проверяются signed ordering, deduplication, TOUCH count и ошибка unsupported identity mutation. Это fault-oriented probe сортировки, а не разрешение произвольных route-ID в consumer. Его 50 строк в обоих JVM идентичны.

Ранее не fault-injected defensive JOURNAL_DISAGREEMENT теперь проверен отдельно: GateDisagreementRunner добавляет строку только в один журнал при корректных native views. Consumer отказывает с JOURNAL_DISAGREEMENT, не выдает трассу, освобождает обе сессии/observer, а следующая сессия успешно экспортируется. Проверка проходит on/off/verify. Vanilla без bridge и каждый отсутствующий protocol marker по-прежнему дают NO_INSTRUMENTATION.

Из диагностических module class hashes изменились только BeforeAuthorityJournal.class и StreamAuthorityJournal.class; остальные module classes совпадают. Native/runtime и fixtures прежних consumer/cost проверок используются без изменения.

## Измерение

Одинаковые current fast bridge, native classes и неизмененный ObserverCostRunner; отличается только diagnostic jar. Шесть пар baseline/candidate на размер 32/128 с чередованием порядка вариантов и размеров, еще четыре candidate off/verify control JVM. 16 warmup, 32 measured batches по прежней методике, всего 1792 измеренных batches. Phase timers выключены; native payload и полные checked traces проверяются. Raw CSV, команды и environment находятся рядом с отчетом.

Медиана per-JVM medians:

| Размер | Нагрузка | Baseline | Candidate | Парное candidate/baseline |
| ---: | --- | ---: | ---: | ---: |
| 32 | Setter, нс | 689.70 | 536.01 | 0.778 |
| 128 | Setter, нс | 832.26 | 715.61 | 0.883 |
| 32 | Checked export, мкс | 504.01 | 511.55 | 1.034 |
| 128 | Checked export, мкс | 1028.30 | 974.90 | 0.975 |

Setter allocation: 1739.34 → 1339.34 B/op в обоих размерах, парная экономия ровно 400 B/op (около 23%) по медианам каждой JVM. Экономия относится к обоим активным журналам вместе. Расходы на строки, коллекции состояния и full snapshots остаются.

Времена setter в медиане серии улучшились; диапазоны парных отношений 0.644–1.025 (32), 0.523–1.260 (128). Checked export не показывает общего ускорения. Размер экономии allocation export меняется между JVM, и фиксированная экономия export не заявляется. Shared host без pinning/эксклюзивности: статистическая значимость, гарантированные проценты ускорения и end-to-end inference speedup не заявляются. Основной результат этапа — сокращение setter allocations с сохранением квалифицированных трасс и отказов.

SMART persistence, storage reclamation lifecycle, concurrent sessions, arbitrary extensions и полный inference corpus остаются неквалифицированными.

## Воспроизведение

Нужны Java 17, ECJ 3.33 в `../tooling/ecj.jar`, clean native checkout `../K3-observer-native` на `2422f7f6` и `../K3-smart-native` на `5d5f6aff`. Baseline jar и прежние fixtures собираются из frozen parent checkout, поскольку текущие journal sources намеренно изменились:

```bash
git worktree add --detach ../K3-three-routes-control 5f1ab2615e57292bdce831cf551f038e1bc0896b
(cd ../K3-three-routes-control && python docs/tvalue-observer-fast-disabled/build.py)
python docs/tvalue-journal-three-routes/build.py
python docs/tvalue-journal-three-routes/run.py
python docs/tvalue-journal-three-routes/gate.py
python docs/tvalue-journal-three-routes/wiring.py
python docs/tvalue-journal-three-routes/analyze.py
python docs/tvalue-journal-three-routes/manifest.py
```

Рабочие checkout должны быть соседями, чтобы shared `../build`, tooling и native paths совпадали. Сохраненные `*.before.java` — frozen source evidence и не компилируются. Analyzer проверяет candidate hashes, native controls, accepted traces, replay, route evidence и negative gates; manifest также проверяет unchanged production/runtime/fixtures и совпадение всех остальных diagnostic sources с baseline.

Следующий отдельный кандидат — создание и сортировка списка State на каждый callback. Его snapshot semantics нужно сохранить при регистрации и retirement контекстов; изменять lifecycle этого списка без отдельной квалификации нельзя.
