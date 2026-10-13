# Экспорт TValue: JIT и GC

Ветка `experiment/3.8.0-tvalue-export-jit-gc`, родитель `23f01d8816f821f92d983ae71da26bc505d731c3`.
Сравниваются frozen context-snapshots и TOUCH strings jars, неизменённый
ExportPhaseRunner и fast-disabled runtime. Native база `2422f7f6`.
Production, runtime, diagnostic source и прежние fixtures не менялись.

## Серия

24 JVM на 32 значениях: шесть пар baseline/candidate в каждом из двух профилей.
Default — стандартная фоновая tiered compilation; batch — только экспериментальный
флаг `-Xbatch`. Порядок модулей чередуется. Оба профиля включают LogCompilation
и gc* logging; измерения с логированием не равны прежним timing сериям.
Драйвер, 64 warmup/128 measured blocks, восемь циклов на блок и phase timers
сохраняются из [предыдущего исследования](tvalue-export-phase-study.md).
Измерено 24576 циклов. Все 24 native контроли и полные export трассы совпадают
побайтно с прежним эталоном. Диагностический код не менялся; failure/wiring
квалификацию предыдущего этапа повторно не запускали.

| Профиль | Медиана парного wall отношения candidate/baseline | Диапазон | Пар медленнее |
|---|---:|---:|---:|
| Default | 0.959 | 0.628–1.160 | 2/6 |
| Xbatch | 0.991 | 0.892–1.025 | 2/6 |

Замедление +5% из предыдущей фазовой серии здесь не повторилось.
Нельзя утверждать ни устранение причины, ни устойчивое ускорение: общий хост,
разные дни запуска, logging и чувствительность к компиляции остаются факторами.

Default allocations mutate_b: 10280 → 7080 байт, mutate_a: 10544 → 7344
(медианы JVM, на восемь setters). Batch: 9128 → 6696 и 9392 → 6960.
Экономия callback allocations сохраняется, её величина зависит от профиля JIT.
Baseline/changed/restored/finish allocations в batch совпадают между модулями
по медиане JVM. Это согласуется с тем, что исходные различия проверочных фаз
чувствительны к оптимизациям JVM; причинность не доказана.

## Наблюдения JIT

В default оба journal private observe(Session,State,String) в выбранных
компиляциях остаются на C1 level 3. В batch оба observe достигают C2 level 4
во всех двенадцати JVM. MetadataTouch достигает C2 у обоих модулей и профилей.
Отдельный touchRow candidate в batch имеет C1 compilation; отдельный уровень
метода не определяет уровень всех его inlined copies. Записи task содержат
успешные inline решения и причины отказов; summary хранит их агрегаты.
Такая зависимость от режима компиляции установлена; она не доказывает,
что именно JIT вызвал прежнюю разницу времени.

## GC

Медианы по полному времени жизни JVM, включая создание fixture и прогрев:

| Профиль | Модуль | Число пауз | Сумма пауз, мс |
|---|---|---:|---:|
| Default | Baseline | 16 | 46.857 |
| Default | Candidate | 15 | 47.535 |
| Batch | Baseline | 16 | 45.581 |
| Batch | Candidate | 16 | 42.195 |

GC запись относится ко всей JVM; драйвер не пишет отметки границ measurement
interval в GC log. Поэтому эти числа не позволяют исключить влияние GC
на отдельный блок или приписать его конкретной фазе. GC не установлен
причиной прежнего замедления.

## Evidence и воспроизведение

CSV содержит total и восемь фаз, wall/CPU/allocations как в предыдущем этапе.
GC логи сохранены полностью. Для JIT сохранены task_queued/nmethod records
по двум журналам, двум authority readers, consumer и драйверу; полные task
деревья только для journal observe, finish, metadataTouch, touchRow и scope.
Это выбранная часть LogCompilation, не полный JVM журнал. SHA256 и размер
исходного XML сохранены в корне выбранного XML; исходный XML после фильтрации
удаляется. Для нового полного XML уберите raw.unlink() из локальной копии
run.py; точный hash повторного запуска не обязан совпасть.

Получить frozen jars/runtime/driver как описано в предыдущих двух reports.
Java 17.0.20, ECJ только для восстановления frozen драйвера; данный этап
ничего не компилирует.

```sh
python docs/tvalue-export-jit-gc/run.py
python docs/tvalue-export-jit-gc/analyze.py
python docs/tvalue-export-jit-gc/manifest.py
```

analyze.py проверяет hashes модулей/драйвера, native контроли и полные traces,
структуру всех CSV, exit status, GC pauses и выбранные compilation records.
Manifest проверяет неизменность всех tracked parent файлов и чистоту pinned
native; hashes runtime совпадают с frozen fast-disabled compilation manifest.
Не запускать параллельно другие benchmark процессы. Xbatch не меняет defaults
и не предлагается как эксплуатационная настройка.

## Решение

Экономия памяти TOUCH воспроизводится, устойчивый экспортный регресс не доказан.
Кандидат остаётся отдельно: текущая серия не является end-to-end квалификацией.
Следующий полезный шаг — контролируемый повтор без logging, с одинаковой
скомпилированной фазой observe; затем проверка на более прикладной нагрузке.
SMART persistence, полный inference corpus и native root-clear ID repair остаются
открытыми. Develop и основной strand не затрагивались.
