-- Non-secret starting values for a FRESH install (copied from your live site).
-- Payment / OpenAI / OneSignal keys are NOT here: enter them in Admin → Settings
-- or as environment variables.
INSERT INTO settings (setting_key, setting_value) VALUES
 ('priceDaily','49'), ('originalPriceDaily','199'),
 ('priceWeekly','299'), ('originalPriceWeekly','1399'),
 ('priceMonthly','999'), ('originalPriceMonthly','5999'),
 ('referralEnabled','1'), ('referralCommissionType','percentage'),
 ('referralCommissionDaily','50'), ('referralCommissionWeekly','50'), ('referralCommissionMonthly','50'),
 ('minWithdrawalAmount','203'),
 ('enable_razorpay','1'), ('enable_cashfree','0'), ('enable_paypal','0'),
 ('enableRegenerate','0'), ('enableDummyData','0'),
 ('enableGenerationTimer','0'), ('timerMinHours','1'), ('timerMaxHours','2'),
 ('default_theme','light'), ('offerEnabled','1'), ('offerDurationDays','30');
