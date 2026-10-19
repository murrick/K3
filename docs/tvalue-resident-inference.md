# TValue observer: ограниченная resident inference нагрузка

Ветка `experiment/3.8.0-tvalue-resident-inference`, родитель
`efc8ed2768bb70bc743666f0caeef6b07487153e`.
Native база `2422f7f6d9e1af7608203b1566df64b5f29fe344`.
Новый только fixture/оркестрация: core, runtime, diagnostic source и defaults
не менялись. Develop и основной strand не затрагивались.

## Переход от setters к публичному API

ResidentInferenceRunner создаёт пять правил/фактов через Mind.query:

```text
!@x @y father(x,y) -> parent(x,y);
!@x @y parent(x,y) -> related(x,y);
!father(John,Tom);
!father(Tom,Sam);
!~male(Mary);
```

Затем девять раз выполняет семь запросов:

| Запрос | Ожидаемый результат | Значения |
|---|---|---|
| ?father(John,Tom); | TRUE | — |
| ?parent(John,Tom); | TRUE | — |
| ?related(John,Tom); | TRUE | — |
| ?male(Mary); | FALSE | — |
| ?unknown(John); | UNKNOWN | — |
| ?$x parent(John,x); | TRUE | [{x=Tom}] |
| ?$x related(x,Sam); | TRUE | [{x=Tom}] |

Два последних результата проверяются точно через публичный ValuesStore.
Отдельный QueryValues map в этом API после запроса пуст; он также записывается,
но не подменяет проверку ValuesStore. После каждого запроса проверяются
отсутствие storage, нулевые transaction reservations и root authority.
Полученные native строки сравниваются целиком между режимами, включая
ответы, значения, query string и authority. Контроль переданного query string
записан отдельно: getQueryString в этой версии пуст и не служит единственным
свидетельством исполненного запроса.

Факты создаются до диагностической сессии. На каждый повтор семи запросов
открывается свежая сессия, наблюдается root baseline, после каждого запроса
наблюдается root; finish выполняет обе authority проверки. Все создаваемые
native временные контексты проходят обычные hooks. Нет ручных setter вызовов
и искусственного вызова callbacks.

Один цикл даёт 27 journal контекстов, 26 retire, 87 authority наблюдений,
181 TOUCH; observer counts включают add=12, metadata=9, promoted=10,
constructed=26, mark/complete=76, begin/endSettlement=26, reset=52, visibility=9.
Это реальная работа подстановок при запросах, а не только ground ответы.
В этом сценарии финальные authority transitions после seed не содержат
CHANGE; проход через transient контексты не равен квалификации всех путей
изменения payload, которые проверялись отдельными прежними fixture.

## Матрица и проверки

36 финальных JVM: on/off/verify ×3 повтора ×4 режима:

- clean: неизменённый clean native runtime, без инструментированных тел;
- disabled: fast-disabled hooked runtime, observer не подключён;
- baseline: тот же hooked runtime + context-snapshots jar, observer подключён;
- candidate: тот же hooked runtime + TOUCH strings jar, observer подключён.

Каждая JVM выполняет 9 циклов; первые 3 используются как прогрев, последние
6 — для сводных timing/allocation медиан. Порядок модулей развёрнут в среднем
повторе. Всего 2268 запросов, 1512 в измеряемой части. Все JVM прошли.
Три предшествующих preflight JVM использовались при создании fixture и не
входят в эти totals: первый ground-only вариант не вызывал metadata,
поэтому расширен variable запросами; затем добавлено сравнение ValuesStore.
Финальная матрица использует один зафиксированный fixture и его class hashes.

Native строки всех режимов совпадают с соответствующим clean контролем.
162 attached trace проходят replay всех authority VIEW; 81 пара
baseline/candidate совпадает побайтно, как и callback counts.
Все expected ответы и 648 возвращённых variable rows проверены
(36 JVM ×9 циклов ×2 запроса); среди них 432 в измеряемой части.
Сессии освобождают observer и context references после каждого цикла.

## Стоимость

Время фиксируется в двух границах:

1. Цикл: после open, включая baseline observe, запросы, native assertions и
   запись контрольных строк в память, явные observe, finish и close. В него
   не входят создание fixture, session open и запись evidence на диск.
2. Queries: сумма семи интервалов непосредственно вокруг Mind.query.
   В attached режиме сюда входят callbacks и автоматические settlement probes;
   явные root observe/finish и внешние native assertions исключены.

Allocation intervals имеют те же границы. Это сравнительный diagnostic workload,
не чистая скорость core inference и не полная стоимость observer open.
Первые прогревочные циклы также сохраняются/проверяются. Медиана шести циклов
внутри JVM, затем медиана трёх пар candidate/baseline. Не смешивать её с
отношением общих медиан JVM.

| Флаги | Цикл: парное wall отношение | Query intervals: отношение | Парная экономия байт/цикл | Из неё query intervals |
|---|---:|---:|---:|---:|
| on | 1.075 | 1.075 | 69192 | 67452 |
| off | 1.145 | 1.136 | 72112 | 71116 |
| verify | 1.017 | 0.955 | 71200 | 71344 |

Кандидат экономит около 69–72 КБ из приблизительно 9.2–9.6 МБ на цикл,
то есть менее 1% полного диагностического allocation в этой нагрузке.
Гарантированное ускорение не получено. В on медленнее две из трёх пар,
в off все три, в verify две из трёх; cycle диапазоны соответственно
0.991–1.182, 1.042–1.151 и 0.811–1.165. Это новый открытый timing сигнал
прикладного сценария, а не возобновление доказанного прежнего export-32 дефекта.
Причина не установлена. Выборка мала, хост общий, без pinning/exclusivity;
статистическая значимость не заявлена.

Чистый/disabled контроль также измеряется, но timing их трёх JVM не даёт
надёжной общей оценки disabled overhead. Все исходные CSV и отдельные JVM
медианы сохранены. Подключённый diagnostic режим делает дополнительные полные
проверки и существенно дороже чистого ядра по allocations.

## Воспроизведение

Восстановить frozen native/runtime/modules как описано в
[tvalue-export-no-logging.md](tvalue-export-no-logging.md) и предыдущих reports.
Нужны оба frozen jars, clean/hooked runtimes, Java 17.0.20, ECJ
`../tooling/ecj.jar` и bundled jline. Запуск из корня репозитория:

```sh
python docs/tvalue-resident-inference/build.py
python docs/tvalue-resident-inference/run.py
python docs/tvalue-resident-inference/analyze.py
python docs/tvalue-resident-inference/manifest.py
```

Manifest фиксирует исходники/evidence, class/jar hashes, неизменность всех
tracked parent файлов и native pin/чистоту. Failure injection/wiring/ownership
негативные серии не повторялись, потому что диагностический код не менялся.

## Решение и следующий шаг

Корректность кандидата подтверждена на данном ограниченном public API сценарии;
эксплуатационная рекомендация и ускорение inference из этого не следуют.
Сохраняем candidate отдельно. Следующий шаг — проверить длительность прогрева
и query интервалы в этом сценарии с фазами компиляции; не переносить кандидат
в основной strand по одному выигрышу allocations.
Полный inference corpus, SMART persistence/storage, root-clear reused ID repair
остаются неквалифицированными. Текущая серия не охватывает актуальный develop:
результаты строго привязаны к указанной native базе.
