# TValue observer: исследование attached path

Ветка `experiment/3.8.0-tvalue-observer-attached-study`, родитель `1867139081ecd2a48a67e656a948f6d774bd924b`. Native-база остается `2422f7f6d9e1af7608203b1566df64b5f29fe344`.

## Результат

Прежнее удорожание fast attached на 128 значениях не воспроизвелось в новой серии. Это не доказывает отсутствия регрессии: отношение времен существенно зависит от запуска, и причинность guard/JIT не установлена. Новый вариант прямых callbacks дает смешанные результаты и не принят в diagnostic runtime. Production, defaults, диагностический модуль, существующие fixtures и замороженные материалы прежних этапов не изменены.

## Сравнение

Три runtime используют одинаковые native classes, hooks, diagnostic jar и bytecode cost driver. Slow — сохраненный synchronized dispatch через lambda. Fast — текущий volatile guard перед тем же dispatch. Direct — отдельный сгенерированный исследовательский bridge с volatile guard и прямыми вызовами observer внутри прежнего monitor, без Callback/lambda. Все 12 методов сохраняют повторное чтение attachment под monitor, проверку owner, taint при чужом потоке/Throwable и исходное значение beforeClear; attach/close/failed не изменены. Direct компилируется только в отдельный generated runtime; исходник текущего bridge остается прежним.

Шесть перестановок трех вариантов, по шесть JVM на вариант и размер (32/128), с чередованием порядка размеров. Еще четыре cost JVM проверяют direct при off/verify. 16 warmup и 32 measured batches, неизмененные 8192 setters или 16 checked export cycles на batch. Phase timers выключены; оба authority journals работают. Allocation измеряется отдельно. Команды, environment, сырые CSV, журналы и трассы сохранены в соседнем каталоге.

Медиана парных отношений к slow внутри одной новой серии (меньше 1 означает меньшее время):

| Размер | Нагрузка | Fast / slow | Direct / slow |
| ---: | --- | ---: | ---: |
| 32 | Setter | 1.200 | 1.166 |
| 32 | Checked export | 1.034 | 0.803 |
| 128 | Setter | 0.835 | 0.893 |
| 128 | Checked export | 0.797 | 0.927 |

Для fast setter на 128 диапазон парных отношений 0.461–1.232; для direct — 0.570–1.395. Direct export на 32 быстрее slow во всех шести парах, но общий выигрыш setter/export не подтвержден. Активная диагностика по-прежнему выделяет примерно 1.7 KB на setter и 0.8/2.7 MB на export cycle (32/128); удаление callback lambda не устраняет эту стоимость.

Shared host без pinning/эксклюзивности, Java 17.0.20; абсолютные timing предыдущего окружения не сопоставляются с новыми. Серия не разделяет host noise, JIT и стоимость журналов. Статистическая значимость, постоянная цена volatile guard и end-to-end inference speedup не заявляются. Повторение прежних чисел 14–17% как установленной регрессии было бы необоснованным.

## Проверки

50 JVM: 40 cost, 3 consumer и 3 owner/failure/recovery (on/off/verify), 4 wiring-refusal. 2560 measured batches; 80 representative cost traces совпали побайтно с прежними и прошли replay. Все native cost controls совпали. 21 accepted consumer trace, native rows, diagnostics и console output совпали побайтно с прежним stage. Recycled-ID export остается запрещенным. Owner/failure/recovery fixtures прошли. Vanilla без bridge и каждый отсутствующий protocol marker дают обязательный NO_INSTRUMENTATION.

Исходный fast/slow runtime восстановлен прежним build script, включая его class hash assertions. Direct build дополнительно проверяет, что все классы, кроме TValueObservation, совпадают с fast runtime. Manifest проверяет неизменность native checkout и ограничение изменений отдельными материалами этого эксперимента.

## Воспроизведение

Java 17, ECJ 3.33 с прежним SHA в `../tooling/ecj.jar`, чистые native checkout `../K3-observer-native` на `2422f7f6` и `../K3-smart-native` на `5d5f6aff` (второй нужен для frozen source hash assertions). Из корня репозитория:

```bash
python docs/tvalue-observer-fast-disabled/build.py
python docs/tvalue-observer-attached-study/build.py
python docs/tvalue-observer-attached-study/run.py
python docs/tvalue-observer-attached-study/wiring.py
python docs/tvalue-observer-attached-study/analyze.py
python docs/tvalue-observer-attached-study/manifest.py
```

Старый build может переставить JSON dictionary entries в прежних manifests, не меняя содержимое; эти два файла следует восстановить после проверки structured equality, чтобы не переписывать frozen evidence. В опубликованном этапе они идентичны родителю.

Следующий шаг — отдельно измерить bridge с минимальным observer и получить JIT/inlining evidence, после чего вернуться к полной диагностике. Короткий dispatch microtest не заменяет full authority qualification. SMART persistence, storage reclamation lifecycle, полный inference corpus, concurrent sessions и исправление root-clear ID reuse остаются открытыми.
