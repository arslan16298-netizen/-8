package ru.edu.pr03;

import java.time.LocalDate;

/**
 * Заявка на командировку. Статус меняется только доменными методами
 * submit / approve / reject / cancel / complete; открытого setStatus нет (R10).
 */
public class BusinessTrip {

    /** R6: минимальная длина причины отклонения. Единственное место, где задан порог. */
    public static final int MIN_REASON_LENGTH = 10;

    private final String id;
    private final LocalDate start;
    private final LocalDate end;
    private TripStatus status = TripStatus.DRAFT;
    private String rejectionReason;

    /** Fail-fast: R1 (id), затем R2 (даты). Некорректную заявку создать нельзя. */
    public BusinessTrip(String id, LocalDate start, LocalDate end) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is null or blank");
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("start must be <= end (start or end is null)");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must be <= end");
        }
        this.id = id;
        this.start = start;
        this.end = end;
    }

    /** R3, R4: SUBMITTED только из DRAFT; данные (id, start, end) уже проверены конструктором. */
    public void submit() {
        if (status != TripStatus.DRAFT) {
            throw transitionError(TripStatus.SUBMITTED);
        }
        status = TripStatus.SUBMITTED;
    }

    /** R5: APPROVED только из SUBMITTED. */
    public void approve() {
        if (status != TripStatus.SUBMITTED) {
            throw transitionError(TripStatus.APPROVED);
        }
        status = TripStatus.APPROVED;
    }

    /** R5: REJECTED только из SUBMITTED; R6: причина не короче MIN_REASON_LENGTH (после strip). Статус проверяется первым. */
    public void reject(String reason) {
        if (status != TripStatus.SUBMITTED) {
            throw transitionError(TripStatus.REJECTED);
        }
        int length = reason == null ? 0 : reason.strip().length();
        if (length < MIN_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "rejectionReason must be at least " + MIN_REASON_LENGTH + " characters, got " + length);
        }
        this.rejectionReason = reason.strip();
        this.status = TripStatus.REJECTED;
    }

    /** R7 (вариант 2, часть А): отмена разрешена только из DRAFT и SUBMITTED, из APPROVED уже нельзя. */
    public void cancel() {
        if (!isCancellable()) {
            throw transitionError(TripStatus.CANCELLED);
        }
        status = TripStatus.CANCELLED;
    }

    /** Доменный запрос: можно ли отменить заявку сейчас. Единственное место правила отмены (используется в cancel). */
    public boolean isCancellable() {
        return status == TripStatus.DRAFT || status == TripStatus.SUBMITTED;
    }

    /** R8: COMPLETED только из APPROVED; R9: today >= end (в день окончания завершать можно). */
    public void complete(LocalDate today) {
        if (status != TripStatus.APPROVED) {
            throw transitionError(TripStatus.COMPLETED);
        }
        if (today == null) {
            throw new IllegalArgumentException("today is null");
        }
        if (today.isBefore(end)) {
            throw new IllegalStateException("complete возможен только после " + end);
        }
        status = TripStatus.COMPLETED;
    }

    /** Завершение по текущей дате системы. */
    public void complete() {
        complete(LocalDate.now());
    }

    private IllegalStateException transitionError(TripStatus target) {
        return new IllegalStateException("Ошибка перехода: " + status + " → " + target + " недопустим");
    }

    public String getId() {
        return id;
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public TripStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    @Override
    public String toString() {
        return "BusinessTrip{" + id + ", " + start + " – " + end + ", " + status + "}";
    }
}
