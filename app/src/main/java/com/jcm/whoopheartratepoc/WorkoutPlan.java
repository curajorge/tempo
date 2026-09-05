package com.jcm.whoopheartratepoc;

final class WorkoutPlan {
    final String name;
    final Phase[] phases;
    String notes = "";

    WorkoutPlan(String name, Phase... phases) {
        this.name = name;
        this.phases = phases;
    }

    int totalSeconds() {
        int total = 0;
        for (Phase phase : phases) total += phase.seconds;
        return total;
    }

    @Override
    public String toString() {
        int total = totalSeconds();
        return name + "  ·  " + (total < 120 ? total + " sec" : total / 60 + " min");
    }

    static final class Phase {
        final String name;
        final int zone;
        final int seconds;

        Phase(String name, int zone, int seconds) {
            this.name = name;
            this.zone = zone;
            this.seconds = seconds;
        }
    }
}
