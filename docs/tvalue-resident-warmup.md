# Resident inference: длительность прогрева

Ветка `experiment/3.8.0-tvalue-resident-warmup`, родитель
`f21b2f3f1cdf98fd1769d8dd37685635bc2c8fae`.
Native база по-прежнему `2422f7f6d9e1af7608203b1566df64b5f29fe344`.
Core, runtime, diagnostic source и driver classes не менялись.

## Проверяемая гипотеза

В предыдущем resident сценарии первые три цикла были прогревом, а следующие
шесть измерением. При таком коротком запуске candidate показывал неоднозначное
время, особенно в off. Теперь тот же frozen ResidentInferenceRunner выполняет
48 циклов по семь запросов. Меняется только аргумент repeats, не код нагрузки.
Каждый цикл по-прежнему проверяет TRUE/FALSE/UNKNOWN, обе точные подстановки
`[{x=Tom}]`, отсутствие storage, освобождение транзакций/observer и оба журнала.

18 timing JVM: on/off/verify ×3 пары baseline/candidate. Порядок модулей
чередуется. Отдельно две off JVM с LogCompilation дают выбранные JIT records;
они не входят в timing ratios. Итого 20 JVM и 6720 запросов, из которых 6048
относятся к timing сериям. Установлено 48 циклов, а не условие достижения
steady state. Точные границы фаз JIT и измерительных окон не совмещались.

Три заранее заданных окна, индексы с нуля:

| Окно | Индексы циклов | Циклов |
|---|---|---:|
| Early | 3–8 | 6 |
| Middle | 18–29 | 12 |
| Late | 36–47 | 12 |

Начальные 0–2 и промежутки также сохраняются и проверяются, хотя не входят
в агрегаты окон. Нельзя объединять окна как независимые JVM: это одни и те же
процессы. Границы времени и allocations сохраняются из
[resident inference](tvalue-resident-inference.md): полный интервал после
session open включает контрольные проверки, observe/finish/close, а query
intervals включают только вызовы Mind.query с автоматическими callbacks.
Диск остаётся вне измерительного интервала, но запись evidence между циклами
может влиять на режим работы JVM. Java 17.0.20, общий хост без CPU pinning.

## Корректность

Все 20 JVM прошли. Все 960 полных journal trace проходят authority replay,
480 baseline/candidate пар совпадают побайтно, включая callback counts.
Первые девять циклов каждого запуска совпадают с прежними independently
qualified clean native строками и baseline traces. Для последующих циклов
native строки после удаления номера итерации точно совпадают с первым циклом,
который сравнен с clean контролем. Между baseline/candidate совпадают все
полные native файлы. Новых 48-cycle clean JVM не запускали.

Драйвер проверяет все ответы и 1920 variable result rows
(20 JVM ×48 циклов ×2 запроса). Проверки корневой authority, отсутствия storage
и pending transaction reservations выполняются после каждого запроса.
Реальные callbacks сохраняются: metadata=9 и add=12 на цикл.
Jar/class hashes совпадают с frozen parent manifests, native pin чистый,
runtime class hashes совпадают с fast-disabled manifest. Все tracked parent
файлы неизменны. Failure/wiring негативные серии заново не запускались:
исполняемый диагностический код остался прежним.

## Время

Медиана циклов внутри окна JVM, затем медиана трёх пар candidate/baseline.

| Флаги | Окно | Полный интервал | Query intervals |
|---|---|---:|---:|
| on | Early | 1.095 | 1.078 |
| on | Middle | 1.059 | 1.056 |
| on | Late | 1.004 | 1.004 |
| off | Early | 1.395 | 1.393 |
| off | Middle | 1.026 | 1.032 |
| off | Late | 0.982 | 0.980 |
| verify | Early | 1.023 | 1.075 |
| verify | Middle | 1.022 | 1.024 |
| verify | Late | 0.985 | 0.988 |

Поздние полные интервалы отличаются примерно на +0.4%, −1.8%, −1.5%.
Отдельные поздние пары on: 1.070, 1.004, 0.664; off: 0.982, 1.010, 0.970;
verify: 0.951, 0.985, 0.990. Разброс и малая выборка не позволяют заявить
эквивалентность или устойчивое ускорение. Сигнал короткой серии off не
сохраняется в позднем окне этой серии. Чувствительность к длительности
предыстории установлена; причина не разделена на JIT, caching, GC и нагрузку
хоста. Рост результата не приписывается исключительно компилятору.

Allocation экономия сохраняется в позднем окне. Разности медиан JVM:
on 81396, off 68684, verify 55980 байт на семь запросов. Это именно разности
общих медиан, а не медианы парной экономии предыдущего этапа. Выигрыш остаётся
менее 1% полного диагностического allocation (~9.1–9.6 МБ/цикл). Экономия
памяти не означает доказанное ускорение самого inference.

## Отдельные JIT пробы

Для двух off JVM сохранены task_queued/nmethod records Mind, обоих журналов,
consumer и runner. В обеих private journal observe компилируются C1 level 3,
а Mind.query(String,Object[],boolean) — C1 level 2. Это отдельные компиляции,
не доказательство отсутствия C2 inlined copies и не весь список оптимизаций.
Запись компиляции появляется у baseline/candidate в разные моменты; она
подтверждает наличие активной tiered compilation в этих запусках, но не
сопоставляет её с конкретным timing окном. Logging JVM из timing исключены.

Выбранные XML содержат hash и размер исходного полного LogCompilation XML.
Полный XML после фильтрации удаляется; сохранены только перечисленные
compilation records, без inlining task trees и без GC log.

## Воспроизведение

Восстановить frozen runtime/modules и собрать unchanged fixture по
[предыдущему этапу](tvalue-resident-inference.md). В этом этапе компиляции нет.

```sh
python docs/tvalue-resident-warmup/run.py
python docs/tvalue-resident-warmup/analyze.py
python docs/tvalue-resident-warmup/manifest.py
```

Timing и JIT пробы идут последовательно. Analyzer проверяет все trace,
native строки, CSV, source/class/jar hashes и выбранные compilation records.
Manifest проверяет parent, native/runtime invariants и SHA256 evidence.

## Решение

Нельзя принимать прежние короткие timing отношения за устойчивое замедление.
В позднем окне кандидат близок к baseline, экономия allocations воспроизводится.
Кандидат остаётся отдельным, без переноса в develop и изменения defaults.
Локальное исследование прогрева завершено без нового патча и без заявления
inference speedup. Следующий шаг — проверить изменения актуального develop
относительно pinned native перед расширением corpus; timing на новый native
автоматически не переносится. SMART persistence, полный inference corpus
и root-clear recycled ID repair остаются неквалифицированными.
