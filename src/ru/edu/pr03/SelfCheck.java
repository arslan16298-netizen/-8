package ru.edu.pr03;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * Единственная точка входа (main) и тестовый раннер.
 * Без аргументов: unit-проверки + data/sample.csv; csv (или --csv): только data/sample.csv;
 * interactive (или --interactive): команды из System.in.
 */
public class SelfCheck {

    private static final String CSV_PATH = "data/sample.csv";
    private static final LocalDate START = LocalDate.of(2026, 10, 10);
    private static final LocalDate END = LocalDate.of(2026, 10, 12);
    private static final BusinessTripService SERVICE = new BusinessTripService();

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "all";
        if (mode.equals("interactive") || mode.equals("--interactive")) {
            interactive();
            return;
        }
        if (!mode.equals("csv") && !mode.equals("--csv")) {
            unitChecks();
        }
        dataDriven();
        System.out.println("Итого: PASS " + passed + ", FAIL " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ================= Режим 1: unit-проверки =================

    private static void unitChecks() {
        baseChecks();     // 18 обязательных проверок (1–18)
        extraChecks();    // дополнительные граничные и переходные проверки
        variantPartA();   // вариант 2, часть А: cancel только из DRAFT и SUBMITTED
        variantPartB();   // вариант 2, часть Б: addIsCancellable
        System.out.println("Unit-проверок выполнено: " + (passed + failed));
    }

    private static BusinessTrip trip() {
        return new BusinessTrip("T-01", START, END);
    }

    private static BusinessTrip submitted() {
        BusinessTrip t = trip();
        t.submit();
        return t;
    }

    private static BusinessTrip approved() {
        BusinessTrip t = submitted();
        t.approve();
        return t;
    }

    private static BusinessTrip completed() {
        BusinessTrip t = approved();
        t.complete(END);
        return t;
    }

    private static BusinessTrip rejected() {
        BusinessTrip t = submitted();
        t.reject("Недостаточно бюджета");
        return t;
    }

    private static BusinessTrip cancelled() {
        BusinessTrip t = trip();
        t.cancel();
        return t;
    }

    private static void baseChecks() {
        // --- конструктор (1–5) ---
        try {
            BusinessTrip t = trip();
            check("1 Конструктор: корректные данные", t.getStatus() == TripStatus.DRAFT && t.getId().equals("T-01"), "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("1 Конструктор: корректные данные", false, e.toString());
        }
        expectCtorError("2 Конструктор: id=null", null, START, END, "id is null or blank");
        expectCtorError("3 Конструктор: id=\"\"", "", START, END, "id is null or blank");
        expectCtorError("4 Конструктор: start=null", "T-01", null, END, "start must be <= end");
        expectCtorError("5 Конструктор: start > end", "T-01", END, START, "start must be <= end");

        // --- submit (6–7) ---
        try {
            BusinessTrip t = trip();
            t.submit();
            check("6 submit: из DRAFT (R3)", t.getStatus() == TripStatus.SUBMITTED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("6 submit: из DRAFT (R3)", false, e.toString());
        }
        try {
            submitted().submit();
            check("7 submit: из SUBMITTED (R3)", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("7 submit: из SUBMITTED (R3)", e.getMessage().contains("SUBMITTED → SUBMITTED недопустим"), e.getMessage());
        }

        // --- approve (8–9) ---
        try {
            BusinessTrip t = submitted();
            t.approve();
            check("8 approve: из SUBMITTED (R5)", t.getStatus() == TripStatus.APPROVED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("8 approve: из SUBMITTED (R5)", false, e.toString());
        }
        try {
            trip().approve();
            check("9 approve: из DRAFT (R5)", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("9 approve: из DRAFT (R5)", e.getMessage().contains("DRAFT → APPROVED недопустим"), e.getMessage());
        }

        // --- reject (10–12) ---
        try {
            BusinessTrip t = submitted();
            t.reject("1234567890");
            check("10 reject: причина 10 символов (R6)",
                    t.getStatus() == TripStatus.REJECTED && t.getRejectionReason().equals("1234567890"), "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("10 reject: причина 10 символов (R6)", false, e.toString());
        }
        try {
            submitted().reject("abc");
            check("11 reject: причина 3 символа (R6)", false, "исключение не выброшено");
        } catch (IllegalArgumentException e) {
            check("11 reject: причина 3 символа (R6)", e.getMessage().contains("rejectionReason must be at least 10 characters"), e.getMessage());
        }
        try {
            trip().reject("Достаточно длинная причина");
            check("12 reject: из DRAFT (R5)", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("12 reject: из DRAFT (R5)", e.getMessage().contains("DRAFT → REJECTED недопустим"), e.getMessage());
        }

        // --- cancel (13–14) ---
        try {
            BusinessTrip t = trip();
            t.cancel();
            check("13 cancel: из DRAFT (R7)", t.getStatus() == TripStatus.CANCELLED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("13 cancel: из DRAFT (R7)", false, e.toString());
        }
        try {
            completed().cancel();
            check("14 cancel: из COMPLETED (R7)", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("14 cancel: из COMPLETED (R7)", e.getMessage().contains("COMPLETED → CANCELLED недопустим"), e.getMessage());
        }

        // --- complete (15–17) ---
        try {
            BusinessTrip t = approved();
            t.complete(LocalDate.now().isAfter(END) ? LocalDate.now() : END);
            check("15 complete: из APPROVED, сегодня >= end (R8, R9)", t.getStatus() == TripStatus.COMPLETED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("15 complete: из APPROVED, сегодня >= end (R8, R9)", false, e.toString());
        }
        try {
            submitted().complete(END);
            check("16 complete: из SUBMITTED (R8)", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("16 complete: из SUBMITTED (R8)", e.getMessage().contains("SUBMITTED → COMPLETED недопустим"), e.getMessage());
        }
        try {
            approved().complete(END.minusDays(1));
            check("17 complete: сегодня < end (R9)", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("17 complete: сегодня < end (R9)", e.getMessage().contains("complete возможен только после " + END), e.getMessage());
        }

        // --- getRejectionReason (18) ---
        try {
            BusinessTrip t = submitted();
            t.reject("Конфликт расписания");
            check("18 getRejectionReason: после reject", "Конфликт расписания".equals(t.getRejectionReason()), "reason=" + t.getRejectionReason());
        } catch (RuntimeException e) {
            check("18 getRejectionReason: после reject", false, e.toString());
        }
    }

    private static void extraChecks() {
        // конструктор и даты
        expectCtorError("+ Конструктор: end=null", "T-01", START, null, "start must be <= end");
        expectCtorError("+ Конструктор: id из пробелов", "   ", START, END, "id is null or blank");
        try {
            BusinessTrip t = new BusinessTrip("T-02", START, START);
            check("+ Конструктор: start = end (однодневная) допустим", t.getStatus() == TripStatus.DRAFT, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("+ Конструктор: start = end (однодневная) допустим", false, e.toString());
        }
        check("+ Конструктор: после создания статус DRAFT, причины нет", trip().getStatus() == TripStatus.DRAFT && trip().getRejectionReason() == null,
                "состояние не совпало");

        // reject: граница 9 / 10, пробелы, null, порядок проверок
        try {
            submitted().reject("123456789");
            check("+ reject: причина 9 символов", false, "исключение не выброшено");
        } catch (IllegalArgumentException e) {
            check("+ reject: причина 9 символов", e.getMessage().contains("got 9"), e.getMessage());
        }
        try {
            submitted().reject(null);
            check("+ reject: причина null", false, "исключение не выброшено");
        } catch (IllegalArgumentException e) {
            check("+ reject: причина null", e.getMessage().contains("must be at least 10 characters, got 0"), e.getMessage());
        }
        try {
            submitted().reject("            ");
            check("+ reject: причина из одних пробелов", false, "исключение не выброшено");
        } catch (IllegalArgumentException e) {
            check("+ reject: причина из одних пробелов", e.getMessage().contains("got 0"), e.getMessage());
        }
        try {
            BusinessTrip t = submitted();
            try {
                t.reject("короткая");
            } catch (IllegalArgumentException ignored) {
                // ожидаем отказ
            }
            check("+ reject: после отказа статус SUBMITTED, причина не сохранена",
                    t.getStatus() == TripStatus.SUBMITTED && t.getRejectionReason() == null, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("+ reject: после отказа статус SUBMITTED, причина не сохранена", false, e.toString());
        }
        try {
            trip().reject("abc");
            check("+ reject: из DRAFT с короткой причиной — сначала ISE (R5 раньше R6)", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("+ reject: из DRAFT с короткой причиной — сначала ISE (R5 раньше R6)", true, "");
        } catch (IllegalArgumentException e) {
            check("+ reject: из DRAFT с короткой причиной — сначала ISE (R5 раньше R6)", false, "выброшено IAE");
        }

        // complete: граница даты
        try {
            BusinessTrip t = approved();
            t.complete(END);
            check("+ complete: today = end (граница включена)", t.getStatus() == TripStatus.COMPLETED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("+ complete: today = end (граница включена)", false, e.toString());
        }
        try {
            BusinessTrip t = approved();
            t.complete(END.plusDays(5));
            check("+ complete: today > end", t.getStatus() == TripStatus.COMPLETED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("+ complete: today > end", false, e.toString());
        }
        try {
            BusinessTrip t = approved();
            try {
                t.complete(END.minusDays(1));
            } catch (IllegalStateException ignored) {
                // ожидаем отказ
            }
            check("+ complete: после отказа по дате статус остаётся APPROVED", t.getStatus() == TripStatus.APPROVED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("+ complete: после отказа по дате статус остаётся APPROVED", false, e.toString());
        }
        try {
            approved().complete(null);
            check("+ complete: today=null", false, "исключение не выброшено");
        } catch (IllegalArgumentException e) {
            check("+ complete: today=null", e.getMessage().contains("today is null"), e.getMessage());
        }
        try {
            BusinessTrip t = new BusinessTrip("T-03", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2));
            t.submit();
            t.approve();
            t.complete();   // перегрузка без параметра: текущая дата системы, командировка давно закончилась
            check("+ complete(): по текущей дате для прошедшей командировки", t.getStatus() == TripStatus.COMPLETED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("+ complete(): по текущей дате для прошедшей командировки", false, e.toString());
        }

        // терминальные состояния: нет ни одного допустимого перехода
        expectTerminal("REJECTED", rejected());
        expectTerminal("CANCELLED", cancelled());
        expectTerminal("COMPLETED", completed());

        // недопустимые переходы из промежуточных состояний
        try {
            approved().submit();
            check("+ submit: из APPROVED", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("+ submit: из APPROVED", e.getMessage().contains("APPROVED → SUBMITTED"), e.getMessage());
        }
        try {
            approved().approve();
            check("+ approve: из APPROVED (повторно)", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("+ approve: из APPROVED (повторно)", e.getMessage().contains("APPROVED → APPROVED"), e.getMessage());
        }
        try {
            trip().complete(END);
            check("+ complete: из DRAFT", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("+ complete: из DRAFT", e.getMessage().contains("DRAFT → COMPLETED"), e.getMessage());
        }
        try {
            BusinessTrip t = submitted();
            t.cancel();
            check("+ cancel: из SUBMITTED", t.getStatus() == TripStatus.CANCELLED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("+ cancel: из SUBMITTED", false, e.toString());
        }
        try {
            BusinessTrip t = cancelled();
            t.cancel();
            check("+ cancel: из CANCELLED (повторно)", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("+ cancel: из CANCELLED (повторно)", e.getMessage().contains("CANCELLED → CANCELLED"), e.getMessage());
        }

        // сервис
        BusinessTripService s = new BusinessTripService();
        BusinessTrip a = s.create("S-1", START, END);
        BusinessTrip b = s.create("S-2", START, END);
        b.submit();
        check("+ Service: findByStatus(DRAFT) возвращает только DRAFT", s.findByStatus(TripStatus.DRAFT).size() == 1
                && s.findByStatus(TripStatus.DRAFT).get(0) == a, "size=" + s.findByStatus(TripStatus.DRAFT).size());
        check("+ Service: findByStatus(SUBMITTED)", s.findByStatus(TripStatus.SUBMITTED).size() == 1
                && s.findByStatus(TripStatus.SUBMITTED).get(0) == b, "size=" + s.findByStatus(TripStatus.SUBMITTED).size());
        check("+ Service: findByStatus(COMPLETED) — пустой список", s.findByStatus(TripStatus.COMPLETED).isEmpty(), "список не пуст");
        try {
            s.findByStatus(TripStatus.DRAFT).add(trip());
            check("+ Service: findByStatus возвращает неизменяемую копию", false, "исключение не выброшено");
        } catch (UnsupportedOperationException e) {
            check("+ Service: findByStatus возвращает неизменяемую копию", true, "");
        }
        try {
            s.findByStatus(null);
            check("+ Service: findByStatus(null)", false, "исключение не выброшено");
        } catch (IllegalArgumentException e) {
            check("+ Service: findByStatus(null)", e.getMessage().contains("status is null"), e.getMessage());
        }
        try {
            s.create("", START, END);
            check("+ Service: create с пустым id (заявка не сохраняется)", false, "исключение не выброшено");
        } catch (IllegalArgumentException e) {
            check("+ Service: create с пустым id (заявка не сохраняется)", s.findByStatus(TripStatus.DRAFT).size() == 1, "список изменился");
        }
    }

    /** Вариант 2, часть А: R7 — cancel разрешён только из DRAFT и SUBMITTED. */
    private static void variantPartA() {
        try {
            approved().cancel();
            check("A: cancel из APPROVED запрещён", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("A: cancel из APPROVED запрещён", e.getMessage().contains("APPROVED → CANCELLED недопустим"), e.getMessage());
        }
        try {
            BusinessTrip t = approved();
            try {
                t.cancel();
            } catch (IllegalStateException ignored) {
                // ожидаем отказ
            }
            check("A: после отказа cancel статус остаётся APPROVED", t.getStatus() == TripStatus.APPROVED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("A: после отказа cancel статус остаётся APPROVED", false, e.toString());
        }
        try {
            BusinessTrip t = submitted();
            t.cancel();
            check("A: cancel из SUBMITTED разрешён", t.getStatus() == TripStatus.CANCELLED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("A: cancel из SUBMITTED разрешён", false, e.toString());
        }
        try {
            BusinessTrip t = trip();
            t.cancel();
            check("A: cancel из DRAFT разрешён", t.getStatus() == TripStatus.CANCELLED, "status=" + t.getStatus());
        } catch (RuntimeException e) {
            check("A: cancel из DRAFT разрешён", false, e.toString());
        }
        try {
            BusinessTrip t = rejected();
            t.cancel();
            check("A: cancel из REJECTED запрещён", false, "исключение не выброшено");
        } catch (IllegalStateException e) {
            check("A: cancel из REJECTED запрещён", e.getMessage().contains("REJECTED → CANCELLED"), e.getMessage());
        }
    }

    /** Вариант 2, часть Б: BusinessTripService.addIsCancellable(BusinessTrip). */
    private static void variantPartB() {
        BusinessTripService s = new BusinessTripService();
        check("B: addIsCancellable(DRAFT) = true", s.addIsCancellable(trip()), "вернул false");
        check("B: addIsCancellable(SUBMITTED) = true", s.addIsCancellable(submitted()), "вернул false");
        check("B: addIsCancellable(APPROVED) = false", !s.addIsCancellable(approved()), "вернул true");
        check("B: addIsCancellable(REJECTED) = false", !s.addIsCancellable(rejected()), "вернул true");
        check("B: addIsCancellable(CANCELLED) = false", !s.addIsCancellable(cancelled()), "вернул true");
        check("B: addIsCancellable(COMPLETED) = false", !s.addIsCancellable(completed()), "вернул true");
        try {
            BusinessTrip t = approved();
            boolean answer = s.addIsCancellable(t);
            boolean cancelWorks;
            try {
                t.cancel();
                cancelWorks = true;
            } catch (IllegalStateException e) {
                cancelWorks = false;
            }
            check("B: результат addIsCancellable совпадает с поведением cancel()", answer == cancelWorks, "answer=" + answer + ", cancel=" + cancelWorks);
        } catch (RuntimeException e) {
            check("B: результат addIsCancellable совпадает с поведением cancel()", false, e.toString());
        }
        try {
            s.addIsCancellable(null);
            check("B: addIsCancellable(null)", false, "исключение не выброшено");
        } catch (IllegalArgumentException e) {
            check("B: addIsCancellable(null)", e.getMessage().contains("trip is null"), e.getMessage());
        }
    }

    // ---------- вспомогательные методы unit-проверок ----------

    private static void expectCtorError(String name, String id, LocalDate start, LocalDate end, String fragment) {
        try {
            new BusinessTrip(id, start, end);
            check(name, false, "исключение не выброшено");
        } catch (IllegalArgumentException e) {
            check(name, e.getMessage().contains(fragment), "сообщение: " + e.getMessage());
        }
    }

    /** Из терминального состояния недопустимы submit, approve, reject, cancel и complete. */
    private static void expectTerminal(String label, BusinessTrip t) {
        TripStatus before = t.getStatus();
        int refused = 0;
        try {
            t.submit();
        } catch (IllegalStateException e) {
            refused++;
        }
        try {
            t.approve();
        } catch (IllegalStateException e) {
            refused++;
        }
        try {
            t.reject("Достаточно длинная причина");
        } catch (IllegalStateException e) {
            refused++;
        }
        try {
            t.cancel();
        } catch (IllegalStateException e) {
            refused++;
        }
        try {
            t.complete(END.plusDays(30));
        } catch (IllegalStateException e) {
            refused++;
        }
        check("+ " + label + " терминально: все 5 переходов отклонены, статус не меняется",
                refused == 5 && t.getStatus() == before, "отклонено " + refused + ", статус " + t.getStatus());
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name + ": " + detail);
        }
    }

    // ================= Режим 2: data-driven (data/sample.csv) =================

    /**
     * Формат: id;start;end;action;expected. Для каждой строки создаётся BusinessTrip и применяются действия.
     * action: SUBMIT | APPROVE | REJECT[=причина] | CANCEL | COMPLETE[@yyyy-MM-dd]; несколько действий через запятую.
     * expected: OK:status=X | ERROR:IllegalStateException (ISE) | ERROR:IllegalArgumentException (IAE) | SKIP[:причина].
     * Строки с # и пустые игнорируются, первая значимая строка – заголовок.
     */
    private static void dataDriven() {
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(CSV_PATH));
        } catch (IOException e) {
            failed++;
            System.out.println("FAIL data: не удалось прочитать " + CSV_PATH);
            return;
        }
        int n = 0;
        int ok = 0;
        boolean headerSkipped = false;
        for (String row : lines) {
            if (row.isBlank() || row.startsWith("#")) {
                continue;
            }
            if (!headerSkipped) {
                headerSkipped = true;
                continue;
            }
            n++;
            String problem;
            try {
                problem = checkRow(row);
            } catch (RuntimeException e) {
                problem = "необработанное исключение: " + e;
            }
            if (problem == null) {
                ok++;
                passed++;
                System.out.println("PASS data/" + n);
            } else {
                failed++;
                System.out.println("FAIL data/" + n + ": " + problem);
            }
        }
        System.out.println("Data-driven: " + ok + "/" + n + " passed");
    }

    /** Возвращает null, если строка выполнена как ожидалось, иначе описание расхождения. */
    private static String checkRow(String row) {
        String[] f = row.split(";", -1);
        if (f.length != 5) {
            return "ожидалось 5 колонок, получено " + f.length;
        }
        String expected = f[4];

        // этап 1: создание заявки; ошибка здесь – SKIP
        BusinessTrip t;
        try {
            t = new BusinessTrip(noSpaces("id", f[0]), parseDate("start", f[1]), parseDate("end", f[2]));
        } catch (IllegalArgumentException e) {
            if (expected.equals("SKIP") || expected.startsWith("SKIP:")) {
                String reason = expected.length() > 5 ? expected.substring(5) : "";
                return e.getMessage().contains(reason) ? null : "ожидалась причина «" + reason + "», получено «" + e.getMessage() + "»";
            }
            if (expected.equals("ERROR:IllegalArgumentException") || expected.equals("ERROR:IAE")) {
                return null;
            }
            return "ожидалось " + expected + ", получено исключение конструктора: " + e.getMessage();
        }

        // этап 2: действия по порядку
        String kind = "OK";
        String message = "";
        try {
            for (String action : f[3].split(",", -1)) {
                apply(t, action);
            }
        } catch (IllegalStateException e) {
            kind = "ISE";
            message = e.getMessage();
        } catch (IllegalArgumentException e) {
            kind = "IAE";
            message = e.getMessage();
        }

        String actual = kind.equals("OK") ? "OK:status=" + t.getStatus() : kind + ": " + message;
        if (expected.startsWith("OK:status=")) {
            return actual.equals(expected) ? null : "ожидалось " + expected + ", получено " + actual;
        }
        if (expected.equals("ERROR") || expected.equals("ERROR:IllegalStateException") || expected.equals("ERROR:ISE")) {
            return kind.equals("ISE") ? null : "ожидался IllegalStateException, получено " + actual;
        }
        if (expected.equals("ERROR:IllegalArgumentException") || expected.equals("ERROR:IAE")) {
            return kind.equals("IAE") ? null : "ожидался IllegalArgumentException, получено " + actual;
        }
        if (expected.equals("SKIP") || expected.startsWith("SKIP:")) {
            return "ожидался SKIP (ошибка конструктора), но заявка создана: " + actual;
        }
        return "неизвестное значение expected: " + expected;
    }

    private static void apply(BusinessTrip t, String action) {
        if (action.equals("SUBMIT")) {
            t.submit();
        } else if (action.equals("APPROVE")) {
            t.approve();
        } else if (action.equals("REJECT")) {
            t.reject("");
        } else if (action.startsWith("REJECT=")) {
            t.reject(action.substring("REJECT=".length()));
        } else if (action.equals("CANCEL")) {
            t.cancel();
        } else if (action.equals("COMPLETE")) {
            t.complete(LocalDate.now());
        } else if (action.startsWith("COMPLETE@")) {
            t.complete(parseDate("today", action.substring("COMPLETE@".length())));
        } else {
            throw new IllegalArgumentException("unknown action: \"" + action + "\"");
        }
    }

    // ---------- разбор полей: ошибки формата – контролируемые IllegalArgumentException ----------

    private static String noSpaces(String field, String value) {
        if (!value.isBlank() && !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " has surrounding spaces: \"" + value + "\"");
        }
        return value;
    }

    private static LocalDate parseDate(String field, String raw) {
        noSpaces(field, raw);
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " is not a date (yyyy-MM-dd): \"" + raw + "\"");
        }
    }

    // ================= Режим 3: интерактивный =================

    private static void interactive() {
        System.out.println("Команды: create <id> <start> <end> | submit | approve | reject <причина> | cancel | complete [yyyy-MM-dd] | status | list <STATUS> | exit");
        System.out.println("Пустая строка или exit – выход.");
        Scanner in = new Scanner(System.in, "UTF-8");
        BusinessTripService service = new BusinessTripService();
        BusinessTrip current = null;
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.isBlank() || line.strip().equals("exit")) {
                break;
            }
            try {
                current = handle(line.strip(), service, current);
            } catch (IllegalArgumentException | IllegalStateException e) {
                String m = e.getMessage();
                System.out.println(m.startsWith("Ошибка") ? m : "Ошибка: " + m);
            }
        }
    }

    /** Выполняет одну команду; возвращает текущую заявку (create заменяет её). */
    private static BusinessTrip handle(String line, BusinessTripService service, BusinessTrip current) {
        String[] p = line.split("\\s+", 2);
        String cmd = p[0];
        String rest = p.length > 1 ? p[1].strip() : "";
        switch (cmd) {
            case "create" -> {
                String[] a = rest.split("\\s+");
                if (a.length != 3) {
                    throw new IllegalArgumentException("ожидалось: create <id> <start> <end>");
                }
                current = service.create(a[0], parseDate("start", a[1]), parseDate("end", a[2]));
                System.out.println("Создана: " + current);
            }
            case "list" -> {
                TripStatus st;
                try {
                    st = TripStatus.valueOf(rest);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("ожидался статус: DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED или COMPLETED");
                }
                List<BusinessTrip> found = service.findByStatus(st);
                for (BusinessTrip t : found) {
                    System.out.println(t);
                }
                System.out.println("Найдено: " + found.size());
            }
            case "submit", "approve", "reject", "cancel", "complete", "status" -> {
                if (current == null) {
                    throw new IllegalStateException("сначала выполните create");
                }
                switch (cmd) {
                    case "submit" -> current.submit();
                    case "approve" -> current.approve();
                    case "reject" -> current.reject(rest);
                    case "cancel" -> current.cancel();
                    case "complete" -> current.complete(rest.isEmpty() ? LocalDate.now() : parseDate("today", rest));
                    default -> { }
                }
                System.out.println("Статус: " + current.getStatus()
                        + (current.getRejectionReason() != null ? " (причина: " + current.getRejectionReason() + ")" : ""));
            }
            default -> throw new IllegalArgumentException("неизвестная команда: " + cmd);
        }
        return current;
    }
}
