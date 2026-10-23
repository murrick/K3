# Интеграционный кандидат: имена JVM property keys

База `develop/3.8.0`: `60fdc34736c51fa4e5659982630347fb1d1b59fc`.
Она совпала с native pin исследовательской ветки при свежем fetch.

В `OptimizationOptions` добавлен приватный массив имён `kanger.experiment.*`.
Индекс получается из уже проверенного бита опции; это устраняет повторную
конкатенацию имени в JVM-default fallback. Значение `System.getProperty` читается
при каждом fallback вызове. Defaults, live individual User overrides, неизвестные
имена, public API и атомарная публикация Session/setAll прежние.

Product исходник побайтно совпадает с квалифицированным кандидатом.
ECJ 3.33.0 с прежним проверенным SHA256 дал те же два class hashes, включая
неизменённый `OptimizationOptions$Session.class`. Повторная контрактная проверка
прошла, включая 200 000 переключений setAll и проверку полных опубликованных слов.
Команды, source/class hashes и результат — [validation.json](option-property-keys/validation.json).

В новой интеграционной рабочей копии Maven reactor успешно собрал зависимости,
Core, Console, Server и qualification. Запущены четыре новых JUnit-теста
`OptimizationOptionsTest`: динамические properties после первого чтения,
User isolation/live overrides, setAll/child inheritance, неизвестные имена и
защитные API snapshots. Все четыре прошли без failures/errors/skips.
Стандартные exec corpus gates этим адресным запуском пропущены явно;
это не полный reactor qualification. Полный Maven log сохранён в maven.log.gz.
Первый сетевой сбой доставки зависимости и успешный proxy retry также сохранены.

Для обычной среды с JDK и Maven:

```sh
mvn --batch-mode --no-transfer-progress -pl kanger-qualification -am test \
  -Dtest=OptimizationOptionsTest -Dsurefire.failIfNoSpecifiedTests=false -Dexec.skip=true
```

В облаке Maven дополнительно использовал штатный session proxy и системное
хранилище CA; их локальные селекторы указаны в validation.json. Исходные файлы
Maven settings и значения credentials не публикуются.

## Предшествующая квалификация на той же базе

- [Первый кандидат](https://github.com/murrick/K3/blob/2a082580/docs/tvalue-option-keys.md):
  динамическое чтение, overrides, atomic setAll, native ответы и оба журнала.
- [Повтор с JFR и без](https://github.com/murrick/K3/blob/8d7a41ef/docs/tvalue-option-keys-repeat.md):
  26 JVM, 86 044 запроса, 6 148 replay traces. Без JFR снижение allocations
  воспроизвелось примерно на 11% clean и 6% attached в ограниченном OFF сценарии.
  Прежние 23% attached экономии не воспроизвелись. Устойчивый speedup не установлен.
- [Широкая семантическая проверка](https://github.com/murrick/K3/blob/d01146e0/docs/tvalue-option-keys-semantic.md):
  56 JVM, 2 108 выполнений тестовых методов, 28 совпавших пар. Три transaction
  пары OFF/ON/VERIFY совпали побайтно по states/work. Оба DUMB2 options процесса
  прошли с двумя одинаковыми диагностическими watchdog warnings каждый.

Большие evidence сохранены в исследовательских коммитах и не копируются в этот
патч. SMART ownership/ACL, полный backend fault/recovery inventory и устойчивый
inference speedup не квалифицированы. Эта ветка — кандидат для review, не release
или deployment. Изменение Core перед полной поставкой требует предусмотренной
lifecycle квалификации совместимого Server и полного repository artifact.
