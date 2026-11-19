-- ---------------------------------------------------------------------------
-- Run once on your EXISTING database (optional — the Spring Boot app does this
-- automatically at startup via SchemaMigrator; this file is for doing it by hand).
-- ---------------------------------------------------------------------------

-- Every payment the gateway confirms is recorded here and linked to the plan it
-- pays for. A plan can only become "completed" if a matching paid order exists.
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
);

-- Remembered on the pending plan so the coupon / upgrade is applied when it is paid.
-- (MariaDB syntax. On MySQL 8 remove "IF NOT EXISTS" and run each line once.)
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(50) NULL;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS replaces_subscription_id INT NULL;
