package com.hackaton.papasud.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackaton.papasud.support.ApiIntegrationTest;
import com.hackaton.papasud.support.TestDataSeeder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test 14 del plan: dos operadores pidiendo el mismo stock al mismo tiempo.
 *
 * <p>Es el escenario que la auditoria marco como P0: sin lock, ambos leen 1000, ambos
 * validan 800 y el saldo termina en -600.
 */
class ConcurrentStockTest extends ApiIntegrationTest {

    @Test
    @DisplayName("14. dos transferencias de 800 sobre 1000: una confirma, la otra falla, nunca queda negativo")
    void concurrentOverdrawIsImpossible() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        // Una barrera para que ambos hilos entren al endpoint lo mas juntos posible.
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Integer> transfer800 = () -> {
            startTogether.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(jsonPost("/api/movements", Map.of(
                            "action", "transfer",
                            "origin", "Galpon Test",
                            "destination", "Frigorifico Test",
                            "items", List.of(Map.of("lotCode", "A-1000", "quantity", 800, "unit", "kg")))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        try {
            Future<Integer> first = pool.submit(transfer800);
            Future<Integer> second = pool.submit(transfer800);

            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));

            long confirmed = statuses.stream().filter(status -> status == 201).count();
            long rejected = statuses.stream().filter(status -> status == 422 || status == 409).count();

            assertThat(confirmed).as("exactamente una confirmacion, statuses=%s", statuses).isEqualTo(1);
            assertThat(rejected).as("exactamente un rechazo, statuses=%s", statuses).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        BigDecimal originAfter = seeder.registered(
                fixture.lotA().getId(), fixture.origin().getId(), "kg");
        BigDecimal destinationAfter = seeder.registered(
                fixture.lotA().getId(), fixture.destination().getId(), "kg");

        assertThat(originAfter).as("saldo de origen").isEqualByComparingTo("200");
        assertThat(destinationAfter).as("saldo de destino").isEqualByComparingTo("800");
        assertThat(originAfter.signum()).as("el stock nunca puede quedar negativo").isNotNegative();
    }

    @Test
    @DisplayName("14b. dos transferencias que caben ambas se aplican las dos")
    void concurrentTransfersThatFitBothSucceed() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Integer> transfer400 = () -> {
            startTogether.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(jsonPost("/api/movements", Map.of(
                            "action", "transfer",
                            "origin", "Galpon Test",
                            "destination", "Frigorifico Test",
                            "items", List.of(Map.of("lotCode", "A-1000", "quantity", 400, "unit", "kg")))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        try {
            Future<Integer> first = pool.submit(transfer400);
            Future<Integer> second = pool.submit(transfer400);
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactly(201, 201);
        } finally {
            pool.shutdownNow();
        }

        assertThat(seeder.registered(fixture.lotA().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("200");
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("800");
    }
}
