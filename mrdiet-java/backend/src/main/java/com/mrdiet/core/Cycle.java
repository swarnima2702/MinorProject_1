package com.mrdiet.core;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Menstrual-cycle maths (pure Java). Two separate ports, kept exactly as the
 * PHP had them because the dashboard and the AI prompt relied on each:
 *  - {@link #planPhase} — from generate_plan.php (feeds the AI prompt)
 *  - {@link #summary}   — from period_tracker.php getCycleSummary()
 */
public final class Cycle {

    private Cycle() {}

    public record Phase(String phase, int cycleDay, int cycleLength, int daysUntilNext) {}

    /** generate_plan.php: day 1..periodDur = period, ≤13 follicular, ≤16 ovulation, else luteal. */
    public static Phase planPhase(LocalDate lastStart, LocalDate today, int cycleLength, int periodDuration) {
        int len = cycleLength > 0 ? cycleLength : 28;
        int dur = periodDuration > 0 ? periodDuration : 5;
        long daysSince = Math.abs(ChronoUnit.DAYS.between(lastStart, today)); // PHP ->diff()->days is absolute
        int cycleDay = (int) (daysSince % len) + 1;
        String phase;
        if (cycleDay <= dur) phase = "period";
        else if (cycleDay <= 13) phase = "follicular";
        else if (cycleDay <= 16) phase = "ovulation";
        else phase = "luteal";
        return new Phase(phase, cycleDay, len, len - cycleDay);
    }

    /**
     * period_tracker.php getCycleSummary().
     * @param lastPeriodStart may be null (then most fields are null and phase is "unknown")
     * @param periodDaysAsc   all logged period days, oldest first (for the history chart)
     */
    public static Map<String, Object> summary(LocalDate lastPeriodStart, LocalDate today, Integer cycleLengthIn,
                                              Integer periodDurationIn, List<LocalDate> periodDaysAsc) {
        int cycleLength = (cycleLengthIn == null || cycleLengthIn == 0) ? 28 : cycleLengthIn;
        int periodDuration = (periodDurationIn == null || periodDurationIn == 0) ? 5 : periodDurationIn;

        String nextPeriodDate = null, fertileStart = null, fertileEnd = null, ovulationDate = null;
        Integer currentCycleDay = null, daysUntilNext = null;
        String phase = "unknown";

        if (lastPeriodStart != null) {
            long daysSinceStart = ChronoUnit.DAYS.between(lastPeriodStart, today); // signed, like PHP invert
            int cycleDay = (int) (daysSinceStart % cycleLength) + 1;
            if (cycleDay < 1) cycleDay += cycleLength;
            currentCycleDay = cycleDay;

            long cyclesPassed = Math.floorDiv(daysSinceStart, cycleLength) + 1;
            LocalDate nextStart = lastPeriodStart.plusDays(cyclesPassed * cycleLength);
            nextPeriodDate = nextStart.toString();
            daysUntilNext = (int) Math.abs(ChronoUnit.DAYS.between(today, nextStart));

            int ovulationDay = cycleLength - 14;
            LocalDate ov = nextStart.minusDays(14);
            ovulationDate = ov.toString();
            fertileStart = ov.minusDays(5).toString();
            fertileEnd = ov.plusDays(1).toString();

            if (cycleDay <= periodDuration) phase = "period";
            else if (cycleDay < (ovulationDay - 5)) phase = "follicular";
            else if (cycleDay <= (ovulationDay + 1)) phase = "ovulation";
            else phase = "luteal";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("last_period_start", lastPeriodStart == null ? null : lastPeriodStart.toString());
        out.put("next_period_date", nextPeriodDate);
        out.put("cycle_length", cycleLength);
        out.put("period_duration", periodDuration);
        out.put("current_cycle_day", currentCycleDay);
        out.put("fertile_window_start", fertileStart);
        out.put("fertile_window_end", fertileEnd);
        out.put("ovulation_date", ovulationDate);
        out.put("days_until_next_period", daysUntilNext);
        out.put("current_phase", phase);
        out.put("history", history(periodDaysAsc));
        return out;
    }

    /** Groups logged period days into cycles (gap &gt; 4 days = new period), keeps the last 4 lengths. */
    static List<Map<String, Object>> history(List<LocalDate> daysAsc) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (daysAsc == null || daysAsc.isEmpty()) return history;

        List<LocalDate> starts = new ArrayList<>();
        LocalDate eventStart = daysAsc.get(0);
        LocalDate last = daysAsc.get(0);
        for (int i = 1; i < daysAsc.size(); i++) {
            LocalDate d = daysAsc.get(i);
            if (ChronoUnit.DAYS.between(last, d) > 4) {
                starts.add(eventStart);
                eventStart = d;
            }
            last = d;
        }
        starts.add(eventStart);

        for (int i = 0; i < starts.size() - 1; i++) {
            long length = Math.abs(ChronoUnit.DAYS.between(starts.get(i), starts.get(i + 1)));
            if (length >= 20 && length <= 60) {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("month", starts.get(i).getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                h.put("length", length);
                h.put("date", starts.get(i).toString());
                history.add(h);
            }
        }
        return history.size() > 4 ? new ArrayList<>(history.subList(history.size() - 4, history.size())) : history;
    }
}
