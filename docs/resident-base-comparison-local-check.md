# Локальная проверка residentBaseComparison

Ветка: `experiment/3.8.0-resident-base-comparison`. После переключения выполните
`mvn clean verify` и перезапустите Java language server в VS Code, если он использует
старые классы. Основной модуль запуска — kanger-console, main org.kanger.Kanger.

В обоих вариантах шесть интегрированных оптимизаций включены. В launch.json:

```json
"vmArgs": "-Dkanger.experiment.preserveTValueIndex=true -Dkanger.experiment.versionedSolveSync=true -Dkanger.experiment.resolvedCauseWeights=true -Dkanger.experiment.compactCauseWeights=true -Dkanger.experiment.candidateMembershipFilter=true -Dkanger.experiment.singleTValueLookup=true -Dkanger.experiment.residentBaseComparison=false"
```

Для ON замените только последнее `false` на `true`. После изменения запускайте
новую JVM: флаг читается при загрузке класса. Это отдельный эксперимент поверх
базы develop/3.8.0; остальные новые оптимизации из параллельных экспериментальных
веток в этом срезе отсутствуют.

С теми же знаниями и БД выполните `?$x son(John,x);` и получите оптимизированные
гипотезы тем же способом, которым проверяли прежние варианты. Шесть повторов в
каждой JVM: первые два записать отдельно, сравнить медиану последних четырёх.
Повторить в обратном порядке OFF/ON и ON/OFF при возможности. Тексты гипотез,
логический результат и решения должны совпадать между вариантами.

Второй сценарий — `options test 08_02` с тем же порядком и прогревом. Этот сценарий
ещё не измерен для данного прототипа; выигрыша в нём заранее не предполагаем.

Проверить привычные случаи use/get, переоткрытие БД, commit/rollback и коллизии.
Если используются собственные UDF/подклассы, отдельно сравнить их результаты.
Для callback-диагностик есть ResidentBaseComparisonRunner (48 проверок), а
автоматическая CI-матрица выполняет его в OFF/ON на Java 8/21/26.

В коротком отчёте достаточно: версия Java, OFF/ON времена с отмеченным холодным
запуском, одинаковы ли гипотезы/результаты и появились ли ошибки. До принятия
решения о переносе ветка остаётся экспериментальной, флаг default OFF.
