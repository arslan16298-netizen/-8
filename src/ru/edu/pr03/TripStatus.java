package ru.edu.pr03;

/** Статус заявки на командировку. REJECTED, CANCELLED и COMPLETED — терминальные состояния. */
public enum TripStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
