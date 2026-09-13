package in.bushansirgur.billingsoftware.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate ddl-auto=update does not widen existing Postgres CHECK constraints
 * when new enum values are added (e.g. PARTIALLY_REFUNDED).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusConstraintMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE tbl_orders DROP CONSTRAINT IF EXISTS tbl_orders_order_status_check");
            jdbcTemplate.execute("""
                    ALTER TABLE tbl_orders
                    ADD CONSTRAINT tbl_orders_order_status_check
                    CHECK (order_status IS NULL OR order_status IN (
                        'COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'VOIDED'
                    ))
                    """);
            log.info("Updated tbl_orders_order_status_check to allow PARTIALLY_REFUNDED");
        } catch (Exception ex) {
            log.warn("Could not update order_status check constraint: {}", ex.getMessage());
        }
    }
}
