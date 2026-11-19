-- H2 (MySQL mode) copy of the live schema, for tests only.
-- TEXT/LONGTEXT become VARCHAR so H2 returns plain strings like MySQL does.

CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NULL,
  name VARCHAR(255) NOT NULL,
  google_id VARCHAR(255) NULL UNIQUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_admin TINYINT DEFAULT 0,
  referral_code VARCHAR(20) NULL UNIQUE,
  referred_by INT NULL
);

CREATE TABLE profiles (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  age INT NOT NULL,
  gender VARCHAR(50) NOT NULL,
  height DECIMAL(5,2) NOT NULL,
  weight DECIMAL(5,2) NOT NULL,
  goal VARCHAR(255) NOT NULL,
  activity_level VARCHAR(255) NOT NULL,
  dietary_restrictions VARCHAR(20000) NULL,
  allergies VARCHAR(255) NULL,
  meal_preference VARCHAR(255) NULL,
  extended_data VARCHAR(100000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  bmi DECIMAL(5,2) NULL,
  cycle_length INT DEFAULT 28,
  period_duration INT DEFAULT 5,
  last_period_start DATE NULL
);

CREATE TABLE subscriptions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  profile_id INT NOT NULL,
  plan_type VARCHAR(50) NOT NULL,
  plan_days INT NOT NULL,
  start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  end_date DATE NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  payment_method VARCHAR(50) NOT NULL,
  payment_status VARCHAR(50) DEFAULT 'pending',
  transaction_id VARCHAR(100) NULL,
  admin_note VARCHAR(10000) NULL,
  status VARCHAR(20) DEFAULT 'active'
);

CREATE TABLE daily_diet_plans (
  id INT AUTO_INCREMENT PRIMARY KEY,
  subscription_id INT NOT NULL,
  day_number INT NOT NULL,
  plan_date DATE NOT NULL,
  meal_json VARCHAR(1000000) NULL,
  is_unlocked TINYINT DEFAULT 0,
  unlocked_at DATETIME NULL,
  user_name VARCHAR(255) NULL,
  meal_changes_count INT DEFAULT 0,
  UNIQUE (subscription_id, day_number)
);

CREATE TABLE daily_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  subscription_id INT NOT NULL,
  day_number INT NOT NULL,
  weight_log DECIMAL(5,2) NULL,
  mood_log VARCHAR(255) NULL,
  notes VARCHAR(10000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (subscription_id, day_number)
);

CREATE TABLE checkins (
  id INT AUTO_INCREMENT PRIMARY KEY,
  subscription_id INT NULL,
  day_number INT NULL,
  weight DECIMAL(10,2) NULL,
  mood VARCHAR(50) NULL,
  notes VARCHAR(10000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE generation_timers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  subscription_id INT NOT NULL,
  day_number INT NOT NULL,
  unlock_at DATETIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (subscription_id, day_number)
);

CREATE TABLE timers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  subscription_id INT NULL,
  day_number INT NULL,
  unlock_at BIGINT NULL
);

CREATE TABLE health_metrics (
  id INT AUTO_INCREMENT PRIMARY KEY,
  profile_id INT NOT NULL UNIQUE,
  bmi DECIMAL(5,2) NULL,
  target_calories INT NULL,
  calculated_bmi DECIMAL(5,2) NULL,
  calculated_calories INT NULL,
  last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by_admin TINYINT DEFAULT 0,
  notes VARCHAR(10000) NULL
);

CREATE TABLE coupons (
  id INT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  discount_type VARCHAR(10) NOT NULL DEFAULT 'percent',
  discount_value DECIMAL(10,2) NOT NULL DEFAULT 0,
  max_uses INT NULL,
  used_count INT NOT NULL DEFAULT 0,
  min_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  applies_to VARCHAR(50) NOT NULL DEFAULT 'all',
  valid_from DATETIME NULL,
  valid_until DATETIME NULL,
  is_active TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE settings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  setting_key VARCHAR(255) NOT NULL UNIQUE,
  setting_value VARCHAR(10000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE referral_earnings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  referred_user_id INT NULL,
  subscription_id INT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) DEFAULT 'completed',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  description VARCHAR(10000) NULL,
  adjusted_by_admin_id INT NULL,
  is_admin_adjustment TINYINT DEFAULT 0
);

CREATE TABLE withdrawals (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) DEFAULT 'pending',
  transaction_id VARCHAR(255) NULL,
  screenshot_url VARCHAR(255) NULL,
  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at DATETIME NULL,
  notes VARCHAR(10000) NULL
);

CREATE TABLE withdrawal_requests (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) DEFAULT 'pending',
  transaction_id VARCHAR(255) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pages (
  id INT AUTO_INCREMENT PRIMARY KEY,
  slug VARCHAR(100) NOT NULL UNIQUE,
  title VARCHAR(255) NOT NULL,
  content VARCHAR(100000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE period_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  profile_id INT NOT NULL,
  log_date DATE NOT NULL,
  is_period_day TINYINT DEFAULT 0,
  flow_level VARCHAR(20) NULL,
  symptoms VARCHAR(10000) NULL,
  mood VARCHAR(50) NULL,
  notes VARCHAR(10000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, log_date)
);

CREATE TABLE notification_preferences (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL UNIQUE,
  onesignal_player_id VARCHAR(255) NULL,
  water_reminder_enabled TINYINT DEFAULT 0,
  water_reminder_interval INT DEFAULT 60,
  water_reminder_start TIME DEFAULT '08:00:00',
  water_reminder_end TIME DEFAULT '22:00:00',
  meal_reminder_enabled TINYINT DEFAULT 0,
  breakfast_time TIME DEFAULT '08:00:00',
  lunch_time TIME DEFAULT '12:30:00',
  dinner_time TIME DEFAULT '19:00:00',
  period_reminder_enabled TINYINT DEFAULT 0,
  period_reminder_days_before INT DEFAULT 2,
  notifications_paused TINYINT DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  type VARCHAR(50) NOT NULL,
  sent_date DATE NOT NULL,
  sent_time TIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO settings (setting_key, setting_value) VALUES
 ('priceDaily', '49'), ('priceWeekly', '299'), ('priceMonthly', '999'),
 ('openai_api_key', 'sk-test-should-never-be-public'),
 ('razorpay_key_secret', 'rzp-secret'), ('enable_razorpay', '1');
