# TValue observer: повторная квалификация после гигиены и reclamation

**Observer-подключение повторно квалифицировано на выбранном native HEAD `2422f7f6d9e1af7608203b1566df64b5f29fe344` в пределах прежних bounded legacy DUMB resident-сценариев.** Прежние timing-цифры остаются привязаны к `5d5f6aff271f1abf371fbde55d637744c53f0bc3`; этот этап не переносит на новый HEAD оценку стоимости или обещание ускорения.

Эксперимент продолжает `03f59bfd40f8728371f909dec76ba59fbfb0fa33`. Основной strand, develop, production/defaults, runtime API и диагностические реализации не меняются. Генерируется отдельный qualification runtime из чистого detached checkout новой базы.

## Аудит сдвига базы

Между старой и новой native-базой изменились 23 файла. Помимо гигиены консоли/компилятора и вынесения provenance/explanation из Mind, есть изменения жизненного цикла подключений в `kanger-data-dumb2`. Mind вызывает новый `IContextFederation.collectConnectionState()` при корневой quiescence, включая read-only storage, без разрешения изменять persistent записи. Эти изменения нельзя объявить только косметическими. Они требуют собственной storage-квалификации и не входят в наш legacy memory-контроль.

`TValueFactory` и `TValue` побайтно прежние. Все 20 записанных вставок observer-инструментирования, включая protocol markers, применяются к новой базе по уникальным anchors без адаптации. Обратное удаление вставок восстанавливает три исходных native-файла побайтно. Разница между clean и hooked сборками новой базы по-прежнему ограничена `Mind`, `Mind$1`, `TValueFactory` и `TValue`.

В compile manifest добавлены пять новых production-зависимостей: `ConsoleContextView`, `ConsoleSources`, `ConsoleTestCommand`, `ContextProvenance` и `ContextQueryExplanation`. Итого 392 native compile sources и 712 классов clean runtime, включая четыре класса runtime API. Относительно прежнего clean runtime отличаются 15 class hashes, в том числе пять новых классов; удалённых классов нет. Ни один такой сдвиг не маскируется требованием старых native class hashes.

Diagnostic jar и отдельно скомпилированные fixture/helper classes совпадают с предыдущим observer/session этапом побайтно. Новый native backend `dumb2` не компилируется в этот runtime и не квалифицируется этим совпадением.

[base-audit.json](tvalue-hygiene-base/base-audit.json), [build-validation.json](tvalue-hygiene-base/build-validation.json) и [structural-check.json](tvalue-hygiene-base/structural-check.json) фиксируют выбранные commits, изменённые файлы, source/class hashes и точные hooks.

## Повторные проверки

| Проверка | Финальные JVM | Результат |
| --- | ---: | --- |
| Прежняя consumer matrix: три повторения, on/off/verify, clean/hooked | 18 | 63 успешные трассы, native-контроли и console output совпадают с прежним observer этапом побайтно; 27 recycled-ID refusals и 27 missing-event/adapter/no-session refusals сохраняются |
| Exclusive owner, abandon/recovery, чужой поток, throwing observer и native setter exception | 6 | Прежние исходы и checks; capture errors запрещают экспорт и не заменяют native исход |
| Vanilla без bridge API и каждый отсутствующий protocol marker | 4 | Обязательный `NO_INSTRUMENTATION` до открытия журналов |
| Совместимость cost driver: 32/128 значений, clean/disabled/attached, verify | 6 | Native-контроли, console output и четыре representative traces совпадают с прежней базой побайтно |

Всего 34 JVM. В cost smoke исполняются те же прогрев и измеренные блоки, но по одному повторению на ячейку; raw CSV сохранены как доказательство выполнения нагрузки. Они не агрегируются в новый timing baseline. Будущий patch быстрого disabled-path должен измеряться вместе с неизменённым bridge на этой же новой native-базе.

Оба полноценных журнала и fresh authority comparison остаются включёнными. Неподдерживаемый root-clear recycled ID по-прежнему приводит к отказу, а не к экспорту. Исправление native ID не выполнялось. Защитная ветка `JOURNAL_DISAGREEMENT` остаётся отдельно не fault-injected.

## Воспроизведение

Сохраните старые checkouts и evidence. Создайте `../K3-observer-native` на точном commit `2422f7f6d9e1af7608203b1566df64b5f29fe344`, с чистым working tree. Нужны Java 17, ECJ 3.33 в `../tooling/ecj.jar`, bundled jline и предыдущие диагностические источники.

Из корня экспериментального репозитория:

```bash
python docs/tvalue-hygiene-base/build.py
python docs/tvalue-hygiene-base/run.py
python docs/tvalue-hygiene-base/wiring.py
# При отсутствии старых build outputs воспроизведите observer build на старом checkout.
# python kanger-qualification/diagnostics/build_observer.py
# Затем при необходимости соберите прежний cost driver.
python docs/tvalue-observer-cost/build.py
python docs/tvalue-hygiene-base/cost_smoke.py
python docs/tvalue-hygiene-base/analyze.py
```

Сборка проверяет неизменность предыдущих observer source hashes, затем независимо компилирует clean/hooked native, diagnostic module и fixtures. Она не использует Java-реализации из старых evidence как текущий диагностический модуль. Генерируемые native wrappers остаются reviewable evidence. `summary.json` и `manifest.json` собирают результаты и контрольные hashes.

## Что дальше

Новая база готова для ограниченного эксперимента быстрого обхода отключённого observer: проверка attachment до callback lambda и synchronized dispatch, с согласованной видимостью подключения. Изменённый и прежний bridge нужно квалифицировать и измерять рядом на одном `2422f7f6`.

SMART persistence, новый storage reclamation lifecycle, complete inference corpus, arbitrary extensions, concurrent native sessions, native recycled-ID repair и end-to-end inference speedup этой серией не квалифицированы. Успех memory fixtures не расширяет их границы.
