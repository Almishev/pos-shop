package in.bushansirgur.billingsoftware.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-shot data cleanup: map legacy UPI payment method to CARD and drop Razorpay columns.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyPaymentCleanupRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer updated = jdbcTemplate.update(
                    "UPDATE tbl_orders SET payment_method = 'CARD' WHERE payment_method = 'UPI'");
            if (updated != null && updated > 0) {
                log.info("Migrated {} order(s) from UPI to CARD", updated);
            }
        } catch (Exception e) {
            log.warn("UPI→CARD migration skipped: {}", e.getMessage());
        }

        dropColumnIfExists("razorpay_order_id");
        dropColumnIfExists("razorpay_payment_id");
        dropColumnIfExists("razorpay_signature");
    }

    private void dropColumnIfExists(String column) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'tbl_orders' AND column_name = ?",
                    Integer.class,
                    column);
            if (count != null && count > 0) {
                jdbcTemplate.execute("ALTER TABLE tbl_orders DROP COLUMN IF EXISTS " + column);
                log.info("Dropped legacy column tbl_orders.{}", column);
            }
        } catch (Exception e) {
            log.warn("Could not drop column {}: {}", column, e.getMessage());
        }
    }
}
