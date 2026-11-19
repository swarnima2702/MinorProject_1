package com.mrdiet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;

/**
 * Adds the few things the secure payment flow needs to your EXISTING database,
 * once, at startup. Safe to run every time — it only creates what's missing.
 * (The same SQL is in database/002_payment_orders.sql if you prefer to run it by hand.)
 */
@Component
public class SchemaMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);
    private final JdbcTemplate jdbc;

    public SchemaMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS payment_orders (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  order_id VARCHAR(100) NOT NULL UNIQUE,
                  user_id INT NOT NULL,
                  subscription_id INT NULL,
                  gateway VARCHAR(20) NOT NULL,
                  amount DECIMAL(10,2) NOT NULL,
                  status VARCHAR(20) NOT NULL DEFAULT 'created',
                  payment_id VARCHAR(100) NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  paid_at DATETIME NULL
                )""");
        addColumnIfMissing("subscriptions", "coupon_code", "VARCHAR(50) NULL");
        addColumnIfMissing("subscriptions", "replaces_subscription_id", "INT NULL");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Boolean exists = jdbc.execute((ConnectionCallback<Boolean>) con -> {
            try (ResultSet rs = con.getMetaData().getColumns(con.getCatalog(), null, table, column)) {
                return rs.next();
            }
        });
        if (!Boolean.TRUE.equals(exists)) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("Added column {}.{}", table, column);
        }
    }
}
