# TValue observer: минимальный dispatch и JIT

Ветка `experiment/3.8.0-tvalue-observer-dispatch-jit`, родитель `ed648650ee0fc8620244ee5a07e9433b2ba175a8`. Native-база остается `2422f7f6d9e1af7608203b1566df64b5f29fe344`. Меняется только исследовательский fixture и материалы эксперимента; существующий bridge, diagnostic consumer, production/defaults и develop не меняются.

## Вывод

В данном минимальном fixture lambda не является источником steady-state allocation: во всех 9216 timing batches allocation равна 0. В отдельных JIT-прогонах slow и fast успешно inline metadata, synchronized dispatch, lambda constructor/call, lambda body и Counter.metadata. Direct также inline metadata и Counter.metadata. Гипотеза о том, что fast guard обязательно ломает inlining этой цепочки, здесь не подтверждается. Это не характеристика JIT полного consumer: его сложный callback и журналы в этом эксперименте отсутствуют.

Direct не дает общего преимущества при attached observer. Он остается отдельным исследовательским вариантом. Следующий кандидат — стоимость записей и снимков двух журналов; полные authority checks должны оставаться обязательными.

## Методика

Минимальный Counter реализует прежний TValueObserver. Callback metadata обновляет счетчик и контрольную сумму id, variable, old term и reason.length. Проверяются точное количество callbacks, сумма payload, восстановленное native-состояние и отсутствие других типов callbacks. Attach, failed и close проверяются вне таймера; diagnostic consumer не открывается и журналы не подключаются.

Две нагрузки выполняются последовательно в каждой JVM: явный вызов metadata и реальные чередующиеся TValue.setValue на восьми resident значениях. В обеих нагрузках читаются действительные value IDs, сумма публикуется в volatile sink после таймера. Native payload и оба полных reader представления проверяются вне таймера после каждой порции. Bridge нагрузка передает чередующиеся old term ID без изменения native; setter нагрузка действительно меняет payload и возвращает его к baseline. Это намеренно простой monomorphic observer, не полный inference corpus.

Шесть вариантов: slow/fast/direct, каждый disabled/attached. Все на одной native-базе и с одинаковым bytecode fixture. Шесть циклических порядков и их обратные порядки дают 12 JVM на вариант, 72 timing JVM. В каждой нагрузке 64 warmup и 64 measured batches по 65536 вызовов; всего 9216 измеренных batches. Внутри batch не проверяется диагностический экспорт. Дополнительно три attached JVM собирают LogCompilation XML и PrintInlining; эти времена исключены из агрегатов. Все команды, environment, CSV и сжатые JIT-логи сохранены рядом с отчетом.

Optimization flags не выставляются: используются defaults закрепленной native-базы. Результаты не сравниваются напрямую с full-journal on-series прошлого этапа. Runtime и module class hashes сверяются с frozen manifests; дополнительные JIT options применяются только к трем profiler JVM.

## Числа

Медиана per-JVM medians, нс на вызов:

| Нагрузка | Slow disabled | Fast disabled | Direct disabled | Slow attached | Fast attached | Direct attached |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Metadata bridge | 35.46 | 6.42 | 4.95 | 33.90 | 39.31 | 38.86 |
| Native setter | 37.48 | 8.83 | 9.54 | 32.49 | 38.48 | 41.07 |

Парная медиана fast/slow attached: 1.067 для bridge, 1.121 для setter; direct/slow: 1.165 и 1.255. Диапазон fast/slow setter — 0.718–1.786, direct/slow — 0.397–2.207. Shared host без pinning и эксклюзивности; фиксированная цена guard, статистическая значимость и causal speedup не установлены. Преимущество disabled fast сохраняется в этом микротесте; прямой вызов observer не квалифицирован как общее улучшение.

Во всех measured timing batches allocation равна 0 B/call; счетчик ThreadMXBean измеряется отдельно от elapsed time. JIT XML содержит успешный inlining lambda constructor и вызовов; вместе с нулевыми аллокациями это согласуется с устранением временного callback-объекта JIT. Конкретный механизм allocation elimination без assembly/EA доказательства не утверждается.

75 JVM успешно прошли свои проверки; все 75 native control строки идентичны. Квалификация полноценного диагностического экспорта остается результатом предыдущего этапа и не расширяется этой серией. SMART, storage reclamation, concurrent sessions, full corpus и root-clear ID repair остаются открытыми.

## Воспроизведение

Java 17 и прежний ECJ 3.33; чистые pinned checkout и generated runtimes предыдущего этапа. При новом окружении сначала выполните build команды двух предыдущих этапов (см. их отчеты), сохраняя frozen manifests. Затем из корня репозитория:

```bash
python docs/tvalue-observer-dispatch-jit/build.py
python docs/tvalue-observer-dispatch-jit/run.py
python docs/tvalue-observer-dispatch-jit/analyze.py
python docs/tvalue-observer-dispatch-jit/manifest.py
```

XML analyzer сохраняет все найденные решения inlining для bridge/Counter, включая отказы для редко вызываемых session lifecycle методов. Число inline_success означает число решений компилятора в логе, а не число runtime callbacks. Сырые XML/PrintInlining остаются первичным evidence.
