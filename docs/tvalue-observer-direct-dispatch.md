# TValue observer: прямой dispatch и повторная проверка attached path

Ветка `experiment/3.8.0-tvalue-observer-direct-dispatch` продолжает `1867139081ecd2a48a67e656a948f6d774bd924b`. Native-база остается `2422f7f6d9e1af7608203b1566df64b5f29fe344`. Изменяется только экспериментальный bridge; production/defaults, develop, диагностические журналы и cost fixture не меняются.

## Контракт и изменение

Все 12 callback-методов сохраняют быстрый volatile guard. При подключенном observer они берут прежний monitor, заново читают attachment, проверяют owner и непосредственно вызывают соответствующий observer-метод. Исключение callback помечает attachment как failed и не заменяет native исход. `beforeClear` сохраняет возвращаемый token и null при отказе. `attach`, `attached`, `failed` и `close` сохраняют прежнюю синхронизацию и владение.

Удалены private `Callback`, общий lambda-dispatch и захват параметров в lambda. Проверка owner вынесена в один приватный helper, вызываемый только под monitor. Снятие monitor и расширение на concurrent native sessions не выполняются. API и protocol markers остаются прежними. Как и раньше, callback, пересекающий конкурентное изменение подключения, не получает нового контракта привязки к сессии.

Два замороженных control bridge собраны заново на идентичной native-базе: `slow` из fresh-base этапа и `guarded` из предыдущего fast-disabled этапа. Их class hashes полностью совпадают с соответствующими опубликованными evidence. Между всеми runtime меняются только bridge-классы; удаление private Callback уменьшает direct runtime до 711 классов против 712 в контролях. Native тела, четыре инструментированных native класса, независимый diagnostic jar и прежние fixtures совпадают. Обратное удаление hooks восстанавливает native sources побайтно.

## Квалификация

31 JVM: 18 consumer-matrix, 6 owner/failure/recovery, 4 wiring-refusal и 3 новых bridge-only contract runs (slow/guarded/direct). Все 63 принятые consumer traces, native rows и вывод совпадают с предыдущей квалификацией побайтно. Recycled-ID, missing-event/adapter/no-session и обязательный `NO_INSTRUMENTATION` сохраняются.

Новый contract driver проверяет все 12 методов без native мутации: disabled no-op; точное число callback и передачу token; throwing observer на каждом методе; чужой поток без вызова observer с taint; foreign close/failed refusal; nested attach refusal; повторное close; закрытие старого attachment после открытия нового; recovery. Полная native owner/failure matrix отдельно проверяет сохранение native успеха и исходного исключения setter.

## Сравнение стоимости

Attached block сравнивает slow, guarded и direct. Disabled block сравнивает native, guarded и direct. На каждый блок выполнены все шесть перестановок трех вариантов; очередность блоков и размеров 32/128 чередуется. Всего 72 on-JVM и 24 off/verify-JVM: 96 JVM, 6 144 measured batches и 96 representative trace replays. Все варианты используют одну native-базу, неизменный cost fixture и два полных журнала.

Setter измеряет 8 192 реальных чередующихся native setters; создание сессии, seed observation и checked finish находятся вне таймера. Export измеряет 16 полных циклов, включая сессию, наблюдения, мутации и full finish. Native результат потребляется volatile sink, внутренние phase timers выключены. Размеры — 32/128 native значений. Raw CSV, команды, allocation, JVM medians, парные отношения и ranges сохранены в `cost/`.

**Общее ускорение attached path не подтверждено; direct остается экспериментальным кандидатом, guarded bridge предыдущей ветки — опорным вариантом.** Корректность direct квалифицирована, но выбирать его как универсальную performance-замену эти данные не позволяют.

Медианы JVM medians для on, setter — нс/вызов, export — мкс/цикл:

| Размер | Нагрузка | Native | Guarded disabled | Direct disabled | Slow attached | Guarded attached | Direct attached |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 32 | Setter, нс | 16.19 | 18.00 | 16.09 | 1 839.90 | 1 762.08 | 2 044.39 |
| 128 | Setter, нс | 16.75 | 17.15 | 15.28 | 2 167.50 | 2 312.61 | 1 839.56 |
| 32 | Export, мкс | 0.216 | 0.268 | 0.314 | 1 205.81 | 1 032.83 | 984.88 |
| 128 | Export, мкс | 0.245 | 0.266 | 0.238 | 2 572.67 | 2 667.37 | 2 370.71 |

Парные медианы времени; 1 означает равенство, меньше 1 — меньшую стоимость числителя:

| Размер | Нагрузка | Guarded / slow attached | Direct / guarded attached | Direct / slow attached | Direct / guarded disabled |
| ---: | --- | ---: | ---: | ---: | ---: |
| 32 | Setter | 0.868 | 1.276 | 1.168 | 0.870 |
| 128 | Setter | 1.109 | 0.836 | 0.925 | 0.927 |
| 32 | Export | 0.925 | 1.031 | 0.848 | 1.077 |
| 128 | Export | 0.930 | 0.895 | 0.878 | 0.975 |

Прежние +14–17% guarded/slow для 128 не воспроизвелись в том же виде: setter теперь +10.9%, export −7.0%; для 32 отношения ниже 1. Это не доказывает отсутствие регрессии: ranges широки, в том числе guarded/slow export128 от 0.674 до 2.176. Но устойчивость прежней общей оценки не подтверждается.

Direct/guarded attached ranges: setter32 0.655–1.530, setter128 0.545–2.037, export32 0.706–1.411, export128 0.476–1.111. Направление результата зависит от размера/нагрузки; JIT или volatile не объявляются установленной причиной. Разница между отношением агрегированных медиан и медианой парных отношений ожидаема: это разные статистики; сохраняются обе.

Allocation native и обоих disabled — 0 B/op. Guarded/direct attached setter — одинаковые 1 739.34 B/op в обоих размерах; slow — 1 739.34/1 747.34 B/op. Export32: slow/guarded/direct 786 868/786 159/786 768 B/cycle; export128 — 2 663 008/2 659 344/2 671 272 B/cycle. Удаление lambda не дает подтвержденного сокращения allocation в этих нагрузках. Оба полных журнала и authority gate сохраняются.

Native-контроли совпали во всех 96 JVM; 96 representative traces совпали между attached вариантами побайтно и прошли replay. Общий объем этапа — 127 финальных JVM. Следующий отдельный шаг: исследовать allocation в формировании journal metadata при сохранении событий и full authority comparison. Прямой dispatch не переносится в develop/defaults.

Среда — shared container без CPU pinning и гарантии эксклюзивного хоста. Статистическая значимость, причинность JIT-эффектов и end-to-end inference speedup не заявляются. Успех bounded legacy DUMB resident fixtures не квалифицирует SMART persistence, storage reclamation lifecycle, полный inference corpus, arbitrary extensions или concurrent native sessions. Root-clear ID reuse остается диагностируемым отказом, ядро не исправляется. Защитная ветка `JOURNAL_DISAGREEMENT` отдельно не fault-injected.

## Воспроизведение

Нужны Java 17, ECJ 3.33 в `../tooling/ecj.jar`, bundled jline и чистый `../K3-observer-native` на точном native commit выше. Из корня репозитория:

```bash
python docs/tvalue-observer-direct-dispatch/build.py
python docs/tvalue-observer-direct-dispatch/run.py
python docs/tvalue-observer-direct-dispatch/wiring.py
python docs/tvalue-observer-direct-dispatch/contract.py
python docs/tvalue-observer-direct-dispatch/qualify.py
python docs/tvalue-observer-direct-dispatch/cost.py
python docs/tvalue-observer-direct-dispatch/cost_analyze.py
python docs/tvalue-observer-direct-dispatch/manifest.py
```

Текущий build самостоятельно собирает native runtime, module, старые fixtures, cost driver и новый contract driver. Старые evidence и manifests заморожены; их build scripts требуют соответствующего stage checkout.
