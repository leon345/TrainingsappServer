package de.trainingsapp.infrastructure.persistenz;

import de.trainingsapp.application.Transaktion;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/** Umsetzung des Ports {@link Transaktion} mit JTA ({@code QuarkusTransaction.joiningExisting()}, E26). */
@ApplicationScoped
public class QuarkusTransaktion implements Transaktion {

    @Override
    public <T> T ausfuehren(Supplier<T> arbeit) {
        return QuarkusTransaction.joiningExisting().call(arbeit::get);
    }

    @Override
    public void ausfuehren(Runnable arbeit) {
        QuarkusTransaction.joiningExisting().run(arbeit);
    }
}
