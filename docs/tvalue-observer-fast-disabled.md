# TValue observer: быстрый disabled path

Ветка: `experiment/3.8.0-tvalue-observer-fast-disabled`.

Эксперимент продолжает `550ba13fbf4339afbcd3f266c62d4c97461c7626` на native-базе `2422f7f6d9e1af7608203b1566df64b5f29fe344`. Изменена только реализация qualification bridge `TValueObservation`; production/defaults и develop не меняются.

## Изменение и контракт

Все 12 публичных callback-методов сначала читают `volatile current`. При отсутствии attachment они возвращаются до создания callback lambda и входа в synchronized dispatch. `beforeClear` возвращает null. При наличии attachment dispatch снова проверяет текущее подключение под прежним monitor; exclusive owner, taint при callback failure/чужом потоке и очистка ссылок при close сохраняются. `attach`, `attached`, `failed` и `close` остаются синхронизированными.

Volatile обеспечивает видимость подключения для fast-path проверки. Это не расширяет контракт до concurrent native sessions: consumer и два полных журнала по-прежнему квалифицированы только для bounded single-owner fixtures. Callback, пересекающий конкурентное изменение attachment, не получает новой гарантии привязки к сессии.

Контрольная реализация сохранена в `baseline/` и собирается на той же native-базе и с теми же hooks. Все ее class hashes совпадают с предыдущим fresh-base hooked runtime. Между slow и fast runtime отличаются только `TValueObservation` и его `Attachment`; native классы, diagnostic jar и fixture bytecode совпадают. Обратное удаление hooks восстанавливает native sources побайтно.

## Квалификация

28 JVM: 18 consumer-matrix, 6 owner/failure/recovery, 4 wiring-refusal. Все 63 успешные трассы, native rows и вывод consumer-matrix совпадают с предыдущими побайтно. Recycled-ID и missing-event/adapter/no-session отказы сохраняются. Vanilla runtime без bridge API и отсутствие каждого из трех protocol markers приводят к `NO_INSTRUMENTATION` до открытия журналов. Throwing observer не меняет native успех и исходное исключение setter.

## Измерение

Пять вариантов: clean native, slow-disabled, fast-disabled, slow-attached, fast-attached. Все используют одну native-базу, неизмененный cost driver и два полных журнала. Для on выполнены пять циклических порядков и их обратные порядки, по десять JVM на вариант и размер; очередность размеров 32/128 чередуется. Дополнительно off/verify проверяются по одному разу на ячейку. Итого 120 JVM, 7 680 измеренных batches и 96 representative trace replays.

Setter batch содержит 8 192 реальных чередующихся setters. Создание сессии, baseline observation и checked finish вынесены за таймер. Export batch содержит 16 циклов с созданием сессии, наблюдениями, мутациями и full checked finish внутри таймера. Результаты native операций читаются в volatile sink. Внутренние phase timers отключены. Thread allocation измеряется отдельно от wall time. Raw CSV, JVM commands, порядок, environment, per-JVM и агрегированные медианы сохранены рядом с отчетом.

Медианы per-JVM medians для on (setter — нс на вызов, export — мкс на цикл):

| Размер | Нагрузка | Native | Slow disabled | Fast disabled | Slow attached | Fast attached |
| ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 32 | Setter, нс | 13.39 | 49.93 | 11.77 | 1 426.84 | 1 490.29 |
| 128 | Setter, нс | 15.07 | 48.60 | 13.64 | 1 389.87 | 1 634.18 |
| 32 | Export, мкс | 0.262 | 0.568 | 0.264 | 804.45 | 935.13 |
| 128 | Export, мкс | 0.273 | 0.591 | 0.207 | 1 980.31 | 2 116.25 |

Парная медиана fast/slow disabled равна 0.312/0.301 для setter и 0.463/0.407 для export (32/128). Парная экономия setter — 31.13/31.33 нс на вызов; export — 0.309/0.325 мкс на цикл. Allocation в native и обоих disabled вариантах — 0 B/op. Fast disabled находится в диапазоне native; местами меньшие цифры не трактуются как ускорение native.

**Attached path не улучшен.** Парные fast/slow медианы для 32 составили 1.010 (setter) и 0.995 (export), для 128 — 1.140 и 1.172. Следовательно, этот набор не квалифицирует отсутствие attached-регрессии; volatile guard может влиять на JIT/inlining, но причинность отдельно не установлена. Диапазоны JVM medians широки, и влияние shared-host вариации не отделено. Ускорение отключенного bridge не переносится на включенную диагностику.

Attached allocation для setter: slow 1 739.34 B/op в обоих размерах, fast 1 784.47/1 739.34 B/op; для export: slow 789 083/2 667 072 B/cycle, fast 789 824/2 669 703 B/cycle. Полные журналы остаются основной стоимостью активной диагностики. Следующий отдельный эксперимент должен исследовать attached guard/JIT и подтверждать его стоимость, сохраняя full authority gate.

Native-контроли совпали во всех 120 JVM, representative traces обоих attached вариантов совпали побайтно и прошли 96 replay-проверок. Общий объем этого этапа — 148 финальных JVM.

Это shared container без CPU pinning и эксклюзивности хоста; статистическая значимость и end-to-end inference speedup не заявляются. Начальная серия во время доработки воспроизводимой сборки исключена; финальная серия запускается целиком после компиляции.

## Воспроизведение

Нужны Java 17, ECJ 3.33 в `../tooling/ecj.jar`, bundled jline и чистый `../K3-observer-native` на точном native commit выше. Из корня репозитория:

```bash
python docs/tvalue-observer-fast-disabled/build.py
python docs/tvalue-observer-fast-disabled/run.py
python docs/tvalue-observer-fast-disabled/wiring.py
python docs/tvalue-observer-fast-disabled/qualify.py
python docs/tvalue-observer-fast-disabled/cost.py
python docs/tvalue-observer-fast-disabled/cost_analyze.py
python docs/tvalue-observer-fast-disabled/manifest.py
```

Старые этапы и manifests заморожены; их build scripts требуют соответствующего старого checkout. Текущий build самостоятельно собирает cost fixture и проверяет совпадение его bytecode с прежним драйвером.

SMART persistence, новый storage reclamation lifecycle, полный inference corpus, arbitrary extensions и concurrent native sessions остаются неквалифицированными. Повторные ID после root clear диагностируются и запрещают export, но не исправлены в ядре. Защитная ветка `JOURNAL_DISAGREEMENT` по-прежнему отдельно не fault-injected.
