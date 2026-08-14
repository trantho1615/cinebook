package com.cinebook.booking.domain;

import java.util.List;

public class SeatsUnavailableException extends RuntimeException {

    private final List<String> takenSeatLabels;

    public SeatsUnavailableException(List<String> takenSeatLabels) {
        super("Cac ghe sau da co nguoi giu: " + String.join(", ", takenSeatLabels));
        this.takenSeatLabels = List.copyOf(takenSeatLabels);
    }

    public List<String> getTakenSeatLabels() {
        return takenSeatLabels;
    }
}
