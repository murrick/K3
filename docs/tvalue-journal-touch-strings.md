# TValue: непосредственная сборка TOUCH

Экспериментальная ветка `experiment/3.8.0-tvalue-journal-touch-strings`.
Родитель: `73c5d6c283697114b962e75eff8dd2ddd0e8107c` (context snapshots).
Native база остаётся `2422f7f6d9e1af7608203b1566df64b5f29fe344`.

## Изменение и границы

В BeforeAuthorityJournal и StreamAuthorityJournal три места формирования TOUCH
теперь собирают строку одним StringBuilder. Промежуточная строка scope не создаётся.
Scope для остальных записей остаётся прежним. Заголовок и reason собираются прежде
чтения variable/value/term, сохраняя порядок getter вызовов. Объекты builder
локальные, между callbacks не переиспользуются. Dirty, observed, счетчики,
маршруты, контекстные массивы и обе authority проверки остаются прежними.
Изменены только два диагностических outer class; остальные class hashes совпадают
с родителем. Production, defaults, runtime, fixtures и develop не менялись.

## Квалификация

Основная серия: 55 JVM, включая 48 запусков матрицы, 3 проверки расхождения
журналов и 4 отказа при отсутствии инструментирования. Все прошли.
63 ранее принятые трассы потребителя и 56 cost-трасс совпадают побайтно
с прежними эталонами; replay проверяет обе authority трассы.
Native строки, диагностика и контроли сохранились.
100 проверок порядка signed-long маршрутов и удаления дубликатов,
24 синтетические проверки регистрации/retirement во время обхода проходят;
baseline и candidate имеют одинаковые полные трассы этих проб.
JOURNAL_DISAGREEMENT по-прежнему запрещает экспорт и допускает восстановление
в следующей сессии. Owner/failure/recovery и NO_INSTRUMENTATION проходят.

Из-за сигнала замедления экспорта на 32 значениях проведена дополнительная серия
из 12 JVM (6 пар, обратный порядок модулей относительно первой серии).
Все 24 её cost-трассы совпадают с эталоном и проходят replay.
Итого 67 JVM, 2560 измеренных блоков, 80 cost replay и 63 accepted trace.
Проверки повторного входа используют синтетический diagnostic getter hook;
они не квалифицируют произвольные пользовательские расширения.

## Замеры

Оба модуля сравниваются на одном неизменённом fast-disabled runtime с
неизменённым ObserverCostRunner. Включены оба полных журнала.
Каждая JVM даёт 32 блока setter по 8192 операций и 32 блока export по 16 операций.
Сначала медиана блоков внутри JVM, затем медиана шести пар.

| Серия | Значений | Нагрузка | Парная экономия байт/операцию | Медиана candidate/baseline времени |
|---|---:|---|---:|---:|
| Основная | 32 | Setter | 341.72 | 0.865 |
| Основная | 128 | Setter | 352.00 | 0.812 |
| Основная | 32 | Export | 5169.88 | 1.322 |
| Основная | 128 | Export | -1200.00 | 0.993 |
| Повторная | 32 | Setter | 340.40 | 0.915 |
| Повторная | 32 | Export | 2620.00 | 1.071 |

Снижение диагностических allocations setter воспроизводится: ещё около
340–352 байт, примерно 29%, сверх предыдущих оптимизаций. Это callback
с одним зарегистрированным контекстом, а не измерение native inference.
Временные setter отношения по отдельным парам пересекают 1;
гарантированное ускорение не установлено.

Для export-32 candidate медленнее в четырёх из шести пар в каждой серии.
Диапазоны отношения: 0.752–1.939 в основной и 0.905–1.476 в повторной.
Парная медиана замедления +32% и +7% соответственно. Сигнал остаётся открытым;
candidate не рекомендуется переносить в основной strand до исследования.
Экспорт-128 не показывает устойчивой разницы, allocation export также смешан.
Хост общий, без закрепления CPU и эксклюзивного доступа, статистическая
значимость не заявлена. Полные исходные CSV и парные медианы сохранены.

## Воспроизведение

Из корня репозитория, Java 17 и ECJ `../tooling/ecj.jar`.
Сначала восстановить frozen этапы, описанные в
[tvalue-journal-context-snapshots.md](tvalue-journal-context-snapshots.md):
native checkout, fast-disabled runtime/fixtures и three-route fixtures.
На frozen родителе `73c5d6c2` запустить его context-snapshots build.py,
чтобы получить контрольный jar и tests. Сохранить generated sibling build
каталоги при переходе на текущую ветку. Прежние build scripts нельзя запускать
на текущих изменённых диагностических исходниках.

```sh
python docs/tvalue-journal-touch-strings/build.py
python docs/tvalue-journal-touch-strings/run.py
python docs/tvalue-journal-touch-strings/gate.py
python docs/tvalue-journal-touch-strings/wiring.py
python docs/tvalue-journal-touch-strings/recheck.py
python docs/tvalue-journal-touch-strings/recheck_analyze.py
python docs/tvalue-journal-touch-strings/manifest.py
```

run.py: 48 JVM; gate.py: 3; wiring.py: 4; recheck.py: 12.
Не запускать параллельно timing серии. recheck_analyze.py повторяет основную
проверку и проверяет повторную серию. manifest.py проверяет pin/чистоту native,
неизменность production/runtime/fixtures и прежних evidence, hashes jar,
исходников и классов, а также записывает SHA256 текущих evidence.
Файлы `.before.java` — текстовые копии родителя, в компиляцию не входят.

## Дальше

Исследовать стоимость экспорта на 32 значениях: локализация/JIT/распределение
allocations и отделение формирования TOUCH от остальных фаз export.
SMART persistence, освобождение storage, полный inference corpus и исправление
повторных ID после native root clear остаются неквалифицированными.
