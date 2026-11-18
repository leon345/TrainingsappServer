package de.trainingsapp.application;

import java.util.function.Supplier;

/** Fake fuer Unit-Tests (E26): fuehrt die Arbeit direkt aus und zaehlt die Aufrufe. */
public class DirekteTransaktion implements Transaktion {

    private int aufrufe;

    @Override
    public <T> T ausfuehren(Supplier<T> arbeit) {
        aufrufe++;
        return arbeit.get();
    }

    @Override
    public void ausfuehren(Runnable arbeit) {
        aufrufe++;
        arbeit.run();
    }

    public int aufrufe() {
        return aufrufe;
    }
}
