# TValue journals: снимок состава контекстов

Ветка `experiment/3.8.0-tvalue-journal-context-snapshots`, родитель `f19545095e1cbc27812033e99ee710b8d9713017`. Native-база остается `2422f7f6d9e1af7608203b1566df64b5f29fe344`.

## Изменение и границы

Оба журнала прежде создавали и сортировали новый ArrayList из IdentityHashMap.values на каждый touch, metadata, materialized и finish. Теперь Session хранит упорядоченный State[]; регистрация создает новый массив с прежними State references, добавляет новый State, сортирует по context и лишь затем публикует новую ссылку. Опубликованные массивы не меняются. Каждый из четырех обходов захватывает ссылку в локальную переменную.

Это снимок membership, а не копия State: retired и прочие флаги по-прежнему читаются непосредственно из State. Регистрация внутри текущего обхода не добавляет новый контекст в его состав; следующий обход его видит. Retirement внутри обхода сразу действует на оставшиеся State. IdentityHashMap остается основным реестром; единственная точка его пополнения по-прежнему safely. Reset/checkpoints/settlements не меняют membership. Session finish/discard снимают ThreadLocal и освобождают массив вместе с остальным journal state; retired State уже удерживались прежним реестром до завершения сессии.

Массив принадлежит конкретной Session и не шарится между журналами/сессиями. Другие native snapshots, authority reads и dirty routes не кэшируются этим изменением. Оба полных журнала, full export gates и прошлое улучшение three-route остаются активны. Core, bridge, hooks, defaults, Maven reactor и develop не меняются.

У регистрации появился дополнительный расход: копирование и сортировка массива при каждом новом контексте. Setter microbenchmark измеряет callbacks при одном уже зарегистрированном контексте, с регистрацией за пределами setter timer. Выигрыш не переносится на массовую регистрацию контекстов; ее стоимость и большие registry sizes пока отдельно не измерены.

## Квалификация

55 финальных JVM: 9 consumer, 3 owner/failure/recovery, 2 route-order, 28 cost, 6 context-membership, 3 disagreement-gate и 4 wiring-refusal. Все 63 прежние accepted traces, native rows, diagnostics и console output совпали побайтно. 56 representative cost traces совпали с прежними и прошли replay. Native controls совпали во всех cost JVM. Typed refusals, отсутствие инструментирования и observer/session ownership сохранились; JOURNAL_DISAGREEMENT снова fault-injected с запретом экспорта и успешной следующей сессией.

ContextSnapshotRunner сравнивает baseline/candidate при on/off/verify: 24 cases, четыре на JVM (touch/metadata × два журнала). Синтетический TValue wrapper с одноразовым getter callback регистрирует поздний контекст и retiring другой контекст посреди настоящего journal обхода. Wrapper никогда не помещается в native factory; в metadata probe через reflection добавляется только диагностическая identity registration. Native state до/после остается тем же. Первый callback посещает contexts 1 и 3, исключая новый 4 и уже retired 2; следующий посещает 1, 3, 4. В candidate дополнительно проверяются новый array identity, неизменность длины старого массива и тех же State references. Finish снимает Session.

Все шесть context probe evidence файлов baseline/candidate попарно идентичны, включая завершенные journal traces. Это намеренно fault-oriented reentrancy test, не квалификация arbitrary TValue extensions, storage lifecycle или concurrent native sessions. Начальный preflight исправлял повторяющееся имя тестового пользователя; финальная серия контекстов целиком использует исправленный fixture. В аналитические результаты preflight не включен.

Изменения class hashes ограничены двумя journal families: outer class, Session, State и UpdateFrame. Session получает массив; изменения hashes State/UpdateFrame связаны с генерируемыми ECJ synthetic accessors окружающего класса. Все прочие module classes, native/runtime и прежние consumer/cost/route/gate fixtures сохраняются.

## Замер

Одинаковые fast bridge, native classes и прежний ObserverCostRunner. Baseline jar — three-route stage, candidate jar — текущий stage. Шесть пар на размер 32/128 с чередованием очередности модулей и размеров, еще четыре candidate off/verify JVM. 16 warmup и 32 measured batches по прежней методике: 8192 setters или 16 полных checked export cycles. Всего 1792 measured batches. Phase timers выключены; raw CSV/commands/environment сохранены рядом.

Медиана per-JVM medians:

| Размер | Нагрузка | Baseline | Candidate | Парное candidate/baseline |
| ---: | --- | ---: | ---: | ---: |
| 32 | Setter, нс | 598.90 | 402.54 | 0.702 |
| 128 | Setter, нс | 711.91 | 511.54 | 0.752 |
| 32 | Checked export, мкс | 445.49 | 406.40 | 0.955 |
| 128 | Checked export, мкс | 901.93 | 920.48 | 1.083 |

Setter allocation: baseline 1339.34 B/op в обоих размерах, candidate 1159.34 / 1195.34 B/op (32/128). Парная медиана экономии 180.00 / 144.00 B/op, около 13% / 11% дополнительно к three-route stage. Во всех шести парах на 128 экономия по per-JVM medians равна примерно 144 B/op; на 32 диапазон 144–216 B/op. Этот расход относится к обоим активным журналам вместе, а не ко всей машине вывода.

Времена setter улучшились в медиане серии; на 32 все шесть пар быстрее baseline. На 128 отношение времен меняется от 0.529 до 1.258. Checked export дает смешанный результат. Shared host, без pinning/эксклюзивности: статистическая значимость, гарантированные проценты ускорения и end-to-end inference speedup не заявляются. Основной подтвержденный результат — сокращение callback allocation при сохранении bounded membership/retirement semantics и полной проверки экспорта.

SMART persistence, storage reclamation, arbitrary extensions, concurrent sessions, полный inference corpus и native root-clear ID repair остаются открытыми.

## Воспроизведение

Java 17, ECJ 3.33, прежние pinned native checkout и shared build paths. Baseline module/fixtures берутся из frozen three-route checkout; native/consumer/cost fixtures — из frozen pre-three-route checkout. Все checkout должны быть соседями. Если generated builds отсутствуют:

```bash
git worktree add --detach ../K3-observer-control 5f1ab2615e57292bdce831cf551f038e1bc0896b
(cd ../K3-observer-control && python docs/tvalue-observer-fast-disabled/build.py)
git worktree add --detach ../K3-three-routes-control f19545095e1cbc27812033e99ee710b8d9713017
(cd ../K3-three-routes-control && python docs/tvalue-journal-three-routes/build.py)
```

Затем из текущего корня:

```bash
python docs/tvalue-journal-context-snapshots/build.py
python docs/tvalue-journal-context-snapshots/run.py
python docs/tvalue-journal-context-snapshots/gate.py
python docs/tvalue-journal-context-snapshots/wiring.py
python docs/tvalue-journal-context-snapshots/analyze.py
python docs/tvalue-journal-context-snapshots/manifest.py
```

Frozen `*.before.java` служат evidence и не компилируются. Analyzer проверяет candidate source/class hashes, exact consumer and cost traces, route/context probe evidence и negative gates. Manifest сверяет baseline module/fixtures, native/runtime hashes, неизменность production/runtime и всех других diagnostic sources.

Следующий отдельный кандидат — формирование TOUCH строк и промежуточного scope string. Текст и порядок событий должны оставаться прежними; authority checks не упрощаются.
