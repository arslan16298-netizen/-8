package ru.edu.pr03;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Сервисный слой: создание и поиск заявок. Логика переходов находится в BusinessTrip, а не здесь. */
public class BusinessTripService {

    private final List<BusinessTrip> trips = new ArrayList<>();

    /** Создаёт заявку (проверки R1, R2 выполняет конструктор) и сохраняет её в списке сервиса. */
    public BusinessTrip create(String id, LocalDate start, LocalDate end) {
        BusinessTrip trip = new BusinessTrip(id, start, end);
        trips.add(trip);
        return trip;
    }

    /** Заявки с указанным статусом; возвращается копия, внутренний список снаружи не изменить. */
    public List<BusinessTrip> findByStatus(TripStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status is null");
        }
        List<BusinessTrip> result = new ArrayList<>();
        for (BusinessTrip trip : trips) {
            if (trip.getStatus() == status) {
                result.add(trip);
            }
        }
        return List.copyOf(result);
    }

    /** Вариант 2, часть Б: true, если заявку можно отменить (статус DRAFT или SUBMITTED). */
    public boolean addIsCancellable(BusinessTrip trip) {
        if (trip == null) {
            throw new IllegalArgumentException("trip is null");
        }
        return trip.isCancellable();
    }
}
