package com.mrdiet.notification;

import com.mrdiet.common.Db;
import com.mrdiet.common.Rows;
import com.mrdiet.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Replaces cron/send_reminders.php + cron/trigger.php. Runs inside the app every
 * 5 minutes (Indian time) — no Hostinger cron job or secret URL needed any more.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private record Meal(String column, String type, String title, String body) {}

    private static final List<Meal> MEALS = List.of(
            new Meal("breakfast_time", "breakfast", "🍳 Breakfast Time", "Your breakfast is ready! Don't skip the most important meal of the day."),
            new Meal("lunch_time", "lunch", "🥗 Lunch Time", "It's lunch time! Check your diet plan for today's meal."),
            new Meal("dinner_time", "dinner", "🍽️ Dinner Time", "Time for a healthy dinner. Follow your meal plan!"));

    private final Db db;
    private final OneSignalClient oneSignal;
    private final AppProperties props;

    public ReminderScheduler(Db db, OneSignalClient oneSignal, AppProperties props) {
        this.db = db;
        this.oneSignal = oneSignal;
        this.props = props;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "${app.timezone:Asia/Kolkata}")
    public void run() {
        if (props.reminders() == null || !props.reminders().enabled()) return;
        if (!oneSignal.configured()) {
            log.debug("Reminders skipped: OneSignal keys not configured");
            return;
        }
        try {
            sendDue(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        } catch (Exception e) {
            log.error("Reminder run failed", e);
        }
    }

    void sendDue(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();
        int nowMinutes = nowTime.getHour() * 60 + nowTime.getMinute();
        String dashboard = props.publicUrl() + "/dashboard";

        List<Map<String, Object>> users = db.list("""
                SELECT np.*, p.gender, p.cycle_length, p.period_duration, p.last_period_start
                FROM notification_preferences np
                LEFT JOIN profiles p ON p.user_id = np.user_id
                WHERE np.onesignal_player_id IS NOT NULL AND np.notifications_paused = 0""");

        for (Map<String, Object> u : users) {
            long userId = Rows.lng(u, "user_id");
            String player = Rows.str(u, "onesignal_player_id");

            // 💧 Water — every N minutes inside the user's window
            if (Rows.bool(u, "water_reminder_enabled")) {
                int start = minutes(u.get("water_reminder_start"), 8 * 60);
                int end = minutes(u.get("water_reminder_end"), 22 * 60);
                int interval = Math.max(1, Rows.integer(u, "water_reminder_interval", 60));
                if (nowMinutes >= start && nowMinutes <= end && nowMinutes % interval == 0) {
                    notifyOnce(userId, player, "water", today, nowTime, "💧 Water Reminder",
                            "Time to drink a glass of water! Stay hydrated for your health goals.", dashboard);
                }
            }

            // 🍽 Meals — in the 5 minutes before each meal time
            if (Rows.bool(u, "meal_reminder_enabled")) {
                for (Meal meal : MEALS) {
                    int mealMinutes = minutes(u.get(meal.column()), -1);
                    if (mealMinutes >= 0 && nowMinutes >= mealMinutes - 5 && nowMinutes <= mealMinutes) {
                        notifyOnce(userId, player, meal.type(), today, LocalTime.of(mealMinutes / 60, mealMinutes % 60),
                                meal.title(), meal.body(), dashboard);
                    }
                }
            }

            // 🌸 Period — once at 9 AM when the next period is within N days
            String gender = Rows.str(u, "gender", "").trim().toLowerCase(Locale.ROOT);
            LocalDate last = Rows.date(u, "last_period_start");
            if (Rows.bool(u, "period_reminder_enabled") && (gender.equals("women") || gender.equals("female"))
                    && last != null && nowTime.getHour() == 9) {
                int cycle = Math.max(1, Rows.integer(u, "cycle_length", 28));
                LocalDate next = last.plusDays(cycle);
                while (next.isBefore(today)) next = next.plusDays(cycle);
                long daysUntil = ChronoUnit.DAYS.between(today, next);
                if (daysUntil <= Rows.integer(u, "period_reminder_days_before", 2)) {
                    String when = daysUntil == 0 ? "today" : daysUntil == 1 ? "tomorrow" : "in " + daysUntil + " days";
                    notifyOnce(userId, player, "period", today, LocalTime.of(9, 0), "🌸 Period Reminder",
                            "Your period is expected " + when + ". Stay prepared and take care!",
                            props.publicUrl() + "/period-tracker");
                }
            }
        }
    }

    private void notifyOnce(long userId, String player, String type, LocalDate date, LocalTime slot,
                            String title, String body, String url) {
        Long sent = db.scalar("SELECT COUNT(*) FROM notification_logs WHERE user_id = ? AND type = ? AND sent_date = ? AND sent_time = ?",
                Long.class, userId, type, date, slot);
        if (sent != null && sent > 0) return;
        if (oneSignal.send(player, title, body, url).success()) {
            db.update("INSERT INTO notification_logs (user_id, type, sent_date, sent_time) VALUES (?, ?, ?, ?)",
                    userId, type, date, slot);
            log.info("{} reminder sent to user {}", type, userId);
        }
    }

    /** "08:30:00" / java.sql.Time / LocalTime → minutes after midnight. */
    private static int minutes(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            LocalTime t = value instanceof LocalTime lt ? lt
                    : value instanceof java.sql.Time st ? st.toLocalTime()
                    : LocalTime.parse(value.toString().length() == 5 ? value + ":00" : value.toString());
            return t.getHour() * 60 + t.getMinute();
        } catch (Exception e) {
            return fallback;
        }
    }
}
