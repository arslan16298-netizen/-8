# Практическая работа №8. Инкапсуляция, enum и управление состоянием объекта

Студент: Ахметгалиев Арслан Альбертович, группа ЭФБО-17-25. Вариант 2.

Система оформления командировок: заявка `BusinessTrip` проходит состояния `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`,
`CANCELLED`, `COMPLETED`. Открытого `setStatus` нет, статус меняют только `submit`, `approve`, `reject`, `cancel`, `complete`.
По варианту 2 `cancel()` разрешён только из `DRAFT` и `SUBMITTED` (часть А); добавлен метод
`BusinessTripService.addIsCancellable(BusinessTrip)` (часть Б).

Java SE 21, без Maven и сторонних библиотек, без Stream API и лямбд.

## Структура

```
src/ru/edu/pr03/TripStatus.java            enum статусов
src/ru/edu/pr03/BusinessTrip.java          модель: конструктор и 5 доменных методов
src/ru/edu/pr03/BusinessTripService.java   сервис: create, findByStatus, addIsCancellable
src/ru/edu/pr03/SelfCheck.java             main, unit-проверки, data-driven и интерактивный режимы
data/sample.csv                            id;start;end;action;expected
diagrams/                                  activity, class, states (.dot, .png)
DECISIONS.md                               решения, ответы на вопросы, анализ фрагмента
```

## Компиляция

```
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java")
```

## Запуск

```
java -Dstdout.encoding=UTF-8 -cp out ru.edu.pr03.SelfCheck               # unit-проверки + data/sample.csv
java -Dstdout.encoding=UTF-8 -cp out ru.edu.pr03.SelfCheck --csv         # только data/sample.csv
java -Dstdout.encoding=UTF-8 -cp out ru.edu.pr03.SelfCheck --interactive # команды из консоли
```

Запускать из корня проекта. Код выхода 1, если есть FAIL.
Windows PowerShell: вместо `$(find ...)` используйте `javac -encoding UTF-8 -d out src\ru\edu\pr03\*.java`.

Интерактивный режим: `create T-01 2026-10-10 2026-10-12`, `submit`, `approve`, `reject <причина>`, `cancel`,
`complete [yyyy-MM-dd]`, `status`, `list <STATUS>`, `exit`.

## Таблица переходов состояний

| Из статуса | Метод | В статус | Условие |
|---|---|---|---|
| (создание) | конструктор | DRAFT | id не пустой (R1), start ≤ end (R2) |
| DRAFT | `submit()` | SUBMITTED | R3 |
| SUBMITTED | `approve()` | APPROVED | R5 |
| SUBMITTED | `reject(reason)` | REJECTED | R5; причина ≥ 10 символов (R6) |
| DRAFT, SUBMITTED | `cancel()` | CANCELLED | R7, вариант 2: из APPROVED нельзя |
| APPROVED | `complete(today)` | COMPLETED | R8; today ≥ end (R9) |
| REJECTED, CANCELLED, COMPLETED | любой метод | — | терминальные: `IllegalStateException` |

## Какие инварианты защищает BusinessTrip

- id не пустой, start ≤ end — проверяет конструктор, поля `id`, `start`, `end` `final`.
- Статус меняется только по таблице переходов; `status` и `rejectionReason` приватные, setter нет (R10).
- `rejectionReason` записывается только в `reject()` и только вместе с переходом в `REJECTED`.
- При любой ошибке объект остаётся в прежнем состоянии: проверки идут до присваивания.

## Формат data/sample.csv

`id;start;end;action;expected`. `action`: `SUBMIT`, `APPROVE`, `REJECT[=причина]`, `CANCEL`, `COMPLETE[@yyyy-MM-dd]`,
можно несколько через запятую. `expected`: `OK:status=X`, `ERROR:IllegalStateException`, `ERROR:IllegalArgumentException`,
`SKIP:<причина>` (ошибка конструктора). Для каждой строки создаётся новая заявка. Строки с `#` — комментарии.

## Граничные проверки (результаты прогона)

| Проверка | Вход | Результат |
|---|---|---|
| Причина отклонения, нижняя граница | `reject("1234567890")` (10) | `REJECTED`, причина сохранена |
| Ниже границы | `reject("123456789")` (9), `reject("abc")` | `IllegalArgumentException: … at least 10 characters, got 9` |
| Дата завершения, граница включена | `complete(today = end)` | `COMPLETED` |
| День до окончания | `complete(today = end − 1)` | `IllegalStateException: complete возможен только после 2026-10-12`, статус `APPROVED` |
| start = end | 2026-10-10 / 2026-10-10 | заявка создана |
| start > end | 2026-10-12 / 2026-10-10 | `IllegalArgumentException: start must be <= end` |
| Пустое значение | id = `""`, start = `null`, reason = `null` | `IllegalArgumentException` |
| Неверный формат даты (CSV) | `2026-13-10` | SKIP: `start is not a date` |
| Повторный submit | `submit()` в `SUBMITTED` | `IllegalStateException: SUBMITTED → SUBMITTED недопустим` |
| Часть А | `cancel()` в `APPROVED` | `IllegalStateException: APPROVED → CANCELLED недопустим` |
| Терминальные | любой метод в `REJECTED`, `CANCELLED`, `COMPLETED` | `IllegalStateException`, статус не меняется |

Итог прогона: `PASS 85, FAIL 0` (59 unit-проверок, из них 18 базовых и 13 по варианту, и 26 строк CSV).

## Принятые допущения

- `complete(LocalDate today)` принимает дату параметром для проверки границ; `complete()` использует `LocalDate.now()`.
- Причина отклонения учитывается без пробелов по краям; строка из одних пробелов — ошибка.
- Поле `employee` с диаграммы-заготовки не добавлено: оно не входит ни в сигнатуру конструктора, ни в сущности задания.
