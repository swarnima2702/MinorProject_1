/**
 * DEMO MODE ONLY (npm run build:demo). Never included in the normal build.
 *
 * A tiny in-browser copy of the Spring Boot API: it answers every /api/* call
 * the React app makes, with the same routes, JSON shapes and rules (server-side
 * pricing, payment-gated activation, ownership checks, admin-only routes).
 * Data lives in this browser's localStorage. Payments and AI are simulated.
 */
import { samplePlan, sampleSubstitution } from "./sampleMeals";

const DB_KEY = "mrdiet_demo_db_v1";
const PAID = ["completed", "active", "paid", "success", "captured"];
let db = null;

// ----------------------------------------------------------------- utils

const pad = (n) => String(n).padStart(2, "0");
const dateStr = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const today = () => dateStr(new Date());
const addDays = (s, n) => {
  const d = new Date(`${s}T00:00:00`);
  d.setDate(d.getDate() + n);
  return dateStr(d);
};
const daysBetween = (a, b) => Math.round((new Date(`${b}T00:00:00`) - new Date(`${a}T00:00:00`)) / 86400000);
const now = () => {
  const d = new Date();
  return `${dateStr(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};
const money = (v) => Math.round(Number(v || 0) * 100) / 100;
const fail = (status, message) => {
  throw { status, message };
};
const nextId = (table) => (db.ids[table] = (db.ids[table] || 0) + 1);
const save = () => {
  try {
    localStorage.setItem(DB_KEY, JSON.stringify(db));
  } catch {
    /* storage blocked — demo keeps working in memory */
  }
};
const code8 = () => Math.random().toString(36).slice(2, 10).toUpperCase();

const plans = {
  days: (t) => (t.includes("monthly") ? 30 : t.includes("weekly") ? 7 : t.includes("daily") || t.includes("trial") ? 1 : 0),
  tier: (t) => (t.includes("monthly") ? 3 : t.includes("weekly") ? 2 : t.includes("daily") ? 1 : 0),
  price: (t) => {
    const key = t.includes("monthly") ? "priceMonthly" : t.includes("weekly") ? "priceWeekly" : t.includes("daily") ? "priceDaily" : null;
    return key ? money(db.settings[key]) : 0;
  },
};

// ----------------------------------------------------------------- seed data

function seed() {
  const t = today();
  db = {
    ids: {},
    settings: {
      priceDaily: "49", originalPriceDaily: "199", priceWeekly: "299", originalPriceWeekly: "1399",
      priceMonthly: "999", originalPriceMonthly: "5999", referralEnabled: "1", referralCommissionType: "percentage",
      referralCommissionDaily: "10", referralCommissionWeekly: "10", referralCommissionMonthly: "10",
      minWithdrawalAmount: "100", enable_razorpay: "1", enable_cashfree: "0", enable_paypal: "0",
      enableRegenerate: "0", enableDummyData: "0", enableGenerationTimer: "0", timerMinHours: "0.5", timerMaxHours: "2",
      default_theme: "system", offerEnabled: "1", offerStartDate: `${t}T00:00:00`, offerDurationDays: "30",
      affiliate_text: "Share your transformation and earn **10%** of every plan your friends buy.",
      razorpay_key_id: "rzp_demo_key", razorpay_key_secret: "demo-secret", openai_api_key: "demo-key-hidden",
    },
    users: [], profiles: [], subscriptions: [], daily_diet_plans: [], daily_logs: [], timers: [], coupons: [],
    referral_earnings: [], withdrawals: [], pages: [], period_logs: [], notification_preferences: [],
    payment_orders: [], health_metrics: [],
  };
  const user = (email, name, extra = {}) => {
    const u = { id: nextId("users"), email, password: "demo1234", name, google_id: null, created_at: now(),
      is_admin: 0, referral_code: code8(), referred_by: null, ...extra };
    db.users.push(u);
    return u;
  };
  const profile = (u, p) => {
    const row = { id: nextId("profiles"), user_id: u.id, name: u.name, email: u.email, age: 28, gender: "male",
      height: 170, weight: 72, goal: "weight-loss", activity_level: "moderate", dietary_restrictions: "[]",
      allergies: "", meal_preference: "Vegetarian", extended_data: "{}", created_at: now(), bmi: 24.9,
      cycle_length: 28, period_duration: 5, last_period_start: null, ...p };
    db.profiles.push(row);
    return row;
  };
  const sub = (u, p, planType, startOffset, status = "completed", method = "razorpay") => {
    const start = addDays(t, startOffset);
    const days = plans.days(planType);
    const s = { id: nextId("subscriptions"), user_id: u.id, profile_id: p.id, plan_type: planType, plan_days: days,
      start_date: `${start} 10:15:00`, end_date: addDays(start, days), amount: plans.price(planType),
      payment_method: method, payment_status: status, transaction_id: status === "completed" ? `pay_demo_${code8()}` : null,
      admin_note: null, status: "active", coupon_code: null, replaces_subscription_id: null };
    db.subscriptions.push(s);
    for (let d = 1; d <= days; d++) {
      db.daily_diet_plans.push({ id: nextId("daily_diet_plans"), subscription_id: s.id, day_number: d,
        plan_date: addDays(start, d - 1), meal_json: null, is_unlocked: 0, unlocked_at: null, user_name: u.name, meal_changes_count: 0 });
    }
    return s;
  };

  user("admin@mrdiet.demo", "Admin", { is_admin: 1, referral_code: "ADMIN001" });
  const priya = user("priya@mrdiet.demo", "Priya Sharma", { referral_code: "PRIYA123" });
  const pp = profile(priya, {
    age: 27, gender: "women", height: 160, weight: 64, goal: "weight-loss", activity_level: "light", bmi: 25,
    dietary_restrictions: JSON.stringify(["No onion on Tuesdays"]), allergies: "Peanuts",
    last_period_start: addDays(t, -12),
    extended_data: JSON.stringify({ phone: "9876543210", city: "Lucknow", dietType: ["Vegetarian"], expectedWeight: 58, sleepHours: "7", stressLevel: "Medium" }),
  });
  const ps = sub(priya, pp, "weekly", -2);
  // Days 1-2 already generated & unlocked; today (day 3) waits for the user to check in.
  [1, 2].forEach((d) => {
    const row = db.daily_diet_plans.find((r) => r.subscription_id === ps.id && r.day_number === d);
    row.meal_json = JSON.stringify(samplePlan(d, pp, "", null));
    row.is_unlocked = 1;
    row.unlocked_at = `${row.plan_date} 07:30:00`;
  });
  [-40, -39, -38, -37, -12, -11, -10, -9, -8].forEach((o) =>
    db.period_logs.push({ id: nextId("period_logs"), user_id: priya.id, profile_id: pp.id, log_date: addDays(t, o),
      is_period_day: 1, flow_level: o === -12 || o === -40 ? "heavy" : "medium", symptoms: JSON.stringify(["Cramps"]), mood: "Tired", notes: null, created_at: now(), updated_at: now() }));

  const rahul = user("rahul@mrdiet.demo", "Rahul Verma", { referred_by: priya.id });
  const rp = profile(rahul, { goal: "muscle-gain", weight: 68, height: 175, activity_level: "active" });
  const rs = sub(rahul, rp, "monthly", -5);
  const ananya = user("ananya@mrdiet.demo", "Ananya Iyer", { referred_by: priya.id });
  const ap = profile(ananya, { gender: "women", age: 31, goal: "pcos", weight: 70, height: 158 });
  const anSub = sub(ananya, ap, "daily", -9);
  const vikram = user("vikram@mrdiet.demo", "Vikram Singh");
  const vp = profile(vikram, { age: 45, goal: "diabetes", weight: 82, height: 172, activity_level: "sedentary" });
  sub(vikram, vp, "weekly", 0, "pending");

  [rs, anSub].forEach((s) => commission(s));
  db.withdrawals.push({ id: nextId("withdrawals"), user_id: priya.id, amount: 100, status: "approved",
    transaction_id: "UPI-DEMO-4471", screenshot_url: null, requested_at: `${addDays(t, -3)} 18:20:00`, processed_at: `${addDays(t, -2)} 11:00:00`, notes: null });

  db.coupons.push(
    { id: nextId("coupons"), code: "DEMO100", discount_type: "percent", discount_value: 100, max_uses: null, used_count: 0,
      min_amount: 0, applies_to: "all", valid_from: null, valid_until: null, is_active: 1, created_at: now() },
    { id: nextId("coupons"), code: "WELCOME20", discount_type: "percent", discount_value: 20, max_uses: 100, used_count: 12,
      min_amount: 0, applies_to: "all", valid_from: null, valid_until: null, is_active: 1, created_at: now() },
  );
  save();
}

// ----------------------------------------------------------------- shared rules (ported from the Java services)

function commission(sub) {
  if (!(Number(sub.amount) > 0)) return;
  const buyer = db.users.find((u) => u.id === sub.user_id);
  if (!buyer?.referred_by || db.referral_earnings.some((e) => e.subscription_id === sub.id)) return;
  if (db.settings.referralEnabled !== "1") return;
  const t = sub.plan_type;
  const key = t.includes("monthly") ? "referralCommissionMonthly" : t.includes("daily") ? "referralCommissionDaily" : "referralCommissionWeekly";
  const v = Number(db.settings[key] || 0);
  const amt = db.settings.referralCommissionType === "percentage" ? money((sub.amount * v) / 100) : money(v);
  if (amt > 0) {
    db.referral_earnings.push({ id: nextId("referral_earnings"), user_id: buyer.referred_by, referred_user_id: buyer.id,
      subscription_id: sub.id, amount: amt, status: "pending", created_at: now(), description: null,
      adjusted_by_admin_id: null, is_admin_adjustment: 0 });
  }
}

function activate(sub, txn) {
  if (PAID.includes(String(sub.payment_status).toLowerCase())) return false;
  sub.payment_status = "completed";
  if (txn) sub.transaction_id = txn;
  if (sub.replaces_subscription_id) {
    const old = db.subscriptions.find((s) => s.id === sub.replaces_subscription_id && s.user_id === sub.user_id);
    if (old) Object.assign(old, { payment_status: "upgraded", end_date: today() });
  }
  if (sub.coupon_code) {
    const c = db.coupons.find((x) => x.code.toUpperCase() === sub.coupon_code.toUpperCase());
    if (c) c.used_count += 1;
  }
  commission(sub);
  return true;
}

function couponCheck(rawCode, planType, amount) {
  const code = String(rawCode || "").trim().toUpperCase();
  const bad = (message) => ({ valid: false, message, discount: 0, final: amount });
  if (!code) return bad("Enter a coupon code");
  if (!(amount > 0)) return bad("Invalid amount");
  const c = db.coupons.find((x) => x.code.toUpperCase() === code);
  if (!c) return bad("Coupon not found");
  const applies = String(c.applies_to || "all").toLowerCase();
  if (applies !== "all" && applies !== String(planType || "all").toLowerCase()) return bad("Coupon not applicable to this plan");
  if (!Number(c.is_active)) return bad("Coupon is inactive");
  if (c.valid_until && new Date(c.valid_until.replace(" ", "T")) < new Date()) return bad("Coupon expired");
  if (c.max_uses != null && c.used_count >= c.max_uses) return bad("Coupon limit reached");
  if (amount < Number(c.min_amount)) return bad(`Minimum order ₹${c.min_amount} required`);
  let discount = c.discount_type === "percent" ? money((amount * c.discount_value) / 100) : money(c.discount_value);
  discount = Math.min(discount, amount);
  return { valid: true, message: "Coupon applied!", code: c.code, c, discount, final: money(amount - discount) };
}

function activeSub(userId) {
  const t = today();
  return db.subscriptions
    .filter((s) => s.user_id === userId && s.payment_status === "completed" && s.end_date >= t)
    .sort((a, b) => b.start_date.localeCompare(a.start_date) || b.id - a.id)[0] || null;
}

function withNames(s) {
  return s && {
    ...s,
    profile_name: db.profiles.find((p) => p.id === s.profile_id)?.name,
    user_name: db.users.find((u) => u.id === s.user_id)?.name,
  };
}

function quote(userId, planType, couponCode) {
  const t = today();
  const base = plans.price(planType);
  const days = plans.days(planType);
  const cur = activeSub(userId);
  let mode = "new", credit = 0, startBase = t, replaces = null, blocked = false, daysRem = 0;
  let message = "No active plan — full price applies.";
  if (cur) {
    daysRem = Math.max(0, daysBetween(t, cur.end_date));
    const ct = plans.tier(cur.plan_type), nt = plans.tier(planType);
    if (ct === nt) {
      mode = "renew";
      if (cur.end_date > t) startBase = cur.end_date;
      message = `Renewal — your access will extend by ${days} day(s) after the current period.`;
    } else if (nt < ct) {
      mode = "downgrade";
      blocked = true;
      message = `Downgrade not allowed mid-cycle. Your ${cur.plan_type} plan is active till ${cur.end_date}.`;
    } else {
      mode = "upgrade";
      credit = money((Number(cur.amount) * daysRem) / Math.max(1, cur.plan_days));
      replaces = cur.id;
      message = `Upgrade — ₹${credit} credit from your remaining ${daysRem} day(s) applied.`;
    }
  }
  let discount = 0, coupon = null;
  if (couponCode) {
    const c = couponCheck(couponCode, planType, base);
    if (c.valid) ({ discount, code: coupon } = c);
  }
  const final = Math.max(0, money(base - credit - discount));
  return { mode, base, credit, discount, final, days, startBase, endDate: addDays(startBase, days), replaces, coupon, blocked, message, cur, daysRem };
}

function cycleSummary(userId, profile) {
  const L = Number(profile.cycle_length) || 28;
  const P = Number(profile.period_duration) || 5;
  const days = db.period_logs.filter((l) => l.user_id === userId && Number(l.is_period_day)).map((l) => l.log_date).sort();
  let last = profile.last_period_start || days[days.length - 1] || null;
  const out = { last_period_start: last, next_period_date: null, cycle_length: L, period_duration: P, current_cycle_day: null,
    fertile_window_start: null, fertile_window_end: null, ovulation_date: null, days_until_next_period: null, current_phase: "unknown", history: [] };
  const t = today();
  if (last) {
    const since = daysBetween(last, t);
    let cd = (since % L) + 1;
    if (cd < 1) cd += L;
    const next = addDays(last, (Math.floor(since / L) + 1) * L);
    const ovDay = L - 14;
    const ov = addDays(next, -14);
    Object.assign(out, {
      current_cycle_day: cd, next_period_date: next, days_until_next_period: Math.abs(daysBetween(t, next)),
      ovulation_date: ov, fertile_window_start: addDays(ov, -5), fertile_window_end: addDays(ov, 1),
      current_phase: cd <= P ? "period" : cd < ovDay - 5 ? "follicular" : cd <= ovDay + 1 ? "ovulation" : "luteal",
    });
  }
  if (days.length) {
    const starts = [days[0]];
    for (let i = 1; i < days.length; i++) if (daysBetween(days[i - 1], days[i]) > 4) starts.push(days[i]);
    for (let i = 0; i < starts.length - 1; i++) {
      const len = Math.abs(daysBetween(starts[i], starts[i + 1]));
      if (len >= 20 && len <= 60) {
        out.history.push({ month: new Date(`${starts[i]}T00:00:00`).toLocaleString("en", { month: "short" }), length: len, date: starts[i] });
      }
    }
    out.history = out.history.slice(-4);
  }
  return out;
}

function planPhase(profile) {
  const g = String(profile.gender || "").toLowerCase();
  if ((g !== "women" && g !== "female") || !profile.last_period_start) return null;
  const L = Number(profile.cycle_length) || 28, P = Number(profile.period_duration) || 5;
  const cd = (Math.abs(daysBetween(profile.last_period_start, today())) % L) + 1;
  return { phase: cd <= P ? "period" : cd <= 13 ? "follicular" : cd <= 16 ? "ovulation" : "luteal", cycleDay: cd };
}

const parseJson = (s, fallback) => {
  try {
    return s == null || s === "" ? fallback : JSON.parse(s);
  } catch {
    return fallback;
  }
};
const publicUser = (u, token) => ({ id: u.id, email: u.email, name: u.name, is_admin: Number(u.is_admin) ? 1 : 0,
  referral_code: u.referral_code, ...(token ? { token } : {}) });
const session = (u) => {
  const token = `demo-token-${u.id}`;
  return { user: publicUser(u, token), token, session: { access_token: token } };
};
const owned = (me, subId) => {
  const s = db.subscriptions.find((x) => x.id === Number(subId) && x.user_id === me.id);
  if (!s) fail(404, "Plan not found");
  return s;
};
const myProfile = (me) => db.profiles.find((p) => p.user_id === me.id);

// ----------------------------------------------------------------- routes

const routes = [];
const on = (method, pattern, handler, opts = {}) =>
  routes.push({ method, re: new RegExp(`^${pattern.replace(/:(\w+)/g, "(?<$1>[^/]+)")}$`), handler, ...opts });

// --- auth
on("POST", "/auth/signup", ({ body }) => {
  const email = String(body.email || "").trim();
  const password = body.password || "";
  if (!email || !password) fail(400, "Email and password required");
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) fail(400, "Please enter a valid email address");
  if (password.length < 6) fail(400, "Password must be at least 6 characters");
  if (db.users.some((u) => u.email.toLowerCase() === email.toLowerCase())) fail(400, "Email already registered");
  const ref = String(body.data?.referral_code || "").trim().toUpperCase();
  const referrer = ref && db.users.find((u) => u.referral_code === ref || String(u.id) === ref);
  const u = { id: nextId("users"), email, password, name: body.data?.name || email.split("@")[0], google_id: null,
    created_at: now(), is_admin: 0, referral_code: code8(), referred_by: referrer ? referrer.id : null };
  db.users.push(u);
  return session(u);
}, { public: true });
on("POST", "/auth/login", ({ body }) => {
  const u = db.users.find((x) => x.email.toLowerCase() === String(body.email || "").trim().toLowerCase());
  if (!u || u.password !== body.password) fail(401, "Invalid email or password");
  return session(u);
}, { public: true });
on("POST", "/auth/google", () =>
  fail(400, "Google sign-in needs the real backend — use email sign-up in this demo"), { public: true });
on("GET", "/auth/me", ({ me }) => publicUser(me));

// --- public
on("GET", "/settings/public", () =>
  Object.fromEntries(Object.entries(db.settings).filter(([k]) => !/secret|api_key|key_secret|password/i.test(k))), { public: true });
on("GET", "/pages/:slug", ({ params }) => db.pages.find((p) => p.slug === params.slug) || null, { public: true });
on("POST", "/coupons/validate", ({ body }) => {
  const c = couponCheck(body.code, body.plan_type, money(body.amount));
  return c.valid
    ? { valid: true, code: c.c.code, discount_type: c.c.discount_type, discount_value: c.c.discount_value,
        discount_amount: c.discount, final_amount: c.final, message: c.message }
    : { valid: false, message: c.message };
}, { public: true });

// --- profile
on("GET", "/profile", ({ me }) => {
  const p = myProfile(me) || {};
  const ext = parseJson(p.extended_data, {}) || {};
  return {
    id: p.id ?? null, user_id: me.id, name: p.name || me.name, email: p.email || me.email,
    phone: ext.phone || ext.formData?.phone || "", city: ext.city || ext.formData?.city || "",
    weight: Number(p.weight || 0), height: Number(p.height || 0), age: Number(p.age || 0), gender: p.gender || "",
    bmi: Number(p.bmi || 0), goal: p.goal || "", activity_level: p.activity_level || "",
    dietary_restrictions: parseJson(p.dietary_restrictions, []), allergies: p.allergies || "",
    meal_preference: p.meal_preference || "", cycle_length: p.cycle_length ?? null, period_duration: p.period_duration ?? null,
    last_period_start: p.last_period_start || null, extended_data: ext,
  };
});
on("POST", "/profile", ({ me, body: d }) => {
  let p = myProfile(me);
  if (!p) {
    p = { id: nextId("profiles"), user_id: me.id, name: me.name, email: me.email, age: 0, gender: "", height: 0, weight: 0,
      goal: "", activity_level: "", dietary_restrictions: "[]", allergies: "", meal_preference: "", extended_data: "{}",
      created_at: now(), bmi: null, cycle_length: 28, period_duration: 5, last_period_start: null };
    db.profiles.push(p);
  }
  const has = (k) => d[k] !== undefined && d[k] !== null;
  if (String(d.name || "").trim()) p.name = d.name.trim();
  if (String(d.email || "").trim()) p.email = d.email.trim();
  ["age", "height", "weight", "bmi"].forEach((k) => has(k) && (p[k] = Number(d[k]) || 0));
  ["gender", "goal", "activity_level", "allergies", "meal_preference"].forEach((k) => has(k) && (p[k] = String(d[k])));
  if (has("dietary_restrictions")) p.dietary_restrictions = JSON.stringify(d.dietary_restrictions);
  const ext = parseJson(p.extended_data, {}) || {};
  for (const src of [d.extended_data, d.formData]) {
    const obj = typeof src === "string" ? parseJson(src, null) : src;
    if (obj && typeof obj === "object" && !Array.isArray(obj)) Object.assign(ext, obj);
  }
  if (has("phone")) ext.phone = d.phone;
  if (has("city")) ext.city = d.city;
  p.extended_data = JSON.stringify(ext);
  if (String(d.name || "").trim()) me.name = d.name.trim();
  if (String(d.password || "").trim()) {
    if (d.password.length < 6) fail(400, "Password must be at least 6 characters");
    me.password = d.password;
  }
  return { ...p, dietary_restrictions: parseJson(p.dietary_restrictions, []), extended_data: ext, phone: ext.phone || "",
    city: ext.city || "", message: "Profile updated securely without data loss." };
});

// --- subscriptions
on("GET", "/subscriptions/active", ({ me }) => withNames(activeSub(me.id)));
on("GET", "/subscriptions", ({ me }) =>
  db.subscriptions.filter((s) => s.user_id === me.id).sort((a, b) => b.id - a.id).map(withNames));
on("GET", "/subscriptions/preview", ({ me, query }) => {
  const q = quote(me.id, String(query.get("new_plan") || "").toLowerCase(), null);
  return { mode: q.mode, current_plan: q.cur?.plan_type ?? null, ...(q.replaces ? { current_sub_id: q.replaces } : {}),
    new_plan: query.get("new_plan"), ...(q.cur ? { current_end: q.cur.end_date } : {}), new_price: q.base, credit: q.credit,
    final_amount: q.final, days_remaining: q.daysRem, ...(q.blocked ? { blocked: true } : {}), message: q.message };
});
const updateStatus = (me, id, body) => {
  const s = owned(me, id);
  const req = String(body.payment_status || "").toLowerCase();
  const cur = String(s.payment_status).toLowerCase();
  if (PAID.includes(req)) {
    if (PAID.includes(cur)) return { success: true };
    const order = db.payment_orders.find((o) => o.subscription_id === s.id && o.status === "paid");
    if (!order) fail(402, "Payment not confirmed yet");
    activate(s, order.payment_id || order.order_id);
    return { success: true };
  }
  if ((req === "failed" || req === "cancelled") && cur === "pending") {
    s.payment_status = req;
    return { success: true };
  }
  fail(400, "Status change not allowed");
};
on("POST", "/subscriptions", ({ me, body }) => {
  if (body.action === "update_status" && body.id) return updateStatus(me, body.id, body);
  const planType = String(body.plan_type || "").trim().toLowerCase();
  const prof = db.profiles.find((p) => p.id === Number(body.profile_id) && p.user_id === me.id) || myProfile(me);
  if (!prof) fail(400, "Please complete your profile first");
  const base = { id: nextId("subscriptions"), user_id: me.id, profile_id: prof.id, start_date: now(), admin_note: null,
    status: "active", transaction_id: null, coupon_code: null, replaces_subscription_id: null };
  let s;
  if (planType === "trial") {
    if (db.subscriptions.some((x) => x.user_id === me.id && x.plan_type === "trial")) fail(409, "Your free trial has already been used");
    s = { ...base, plan_type: "trial", plan_days: 1, end_date: addDays(today(), 1), amount: 0, payment_method: "free", payment_status: "completed" };
    db.subscriptions.push(s);
  } else {
    if (!["daily", "weekly", "monthly"].includes(planType)) fail(400, `Unknown plan: ${planType}`);
    const q = quote(me.id, planType, body.coupon_code);
    if (q.blocked) fail(400, q.message);
    s = { ...base, plan_type: planType, plan_days: q.days, end_date: q.endDate, amount: q.final,
      payment_method: String(body.payment_method || "online"), payment_status: "pending", coupon_code: q.coupon, replaces_subscription_id: q.replaces };
    db.subscriptions.push(s);
    if (q.final === 0) activate(s, `FREE-${s.id}`);
  }
  return { id: s.id, user_id: me.id, profile_id: prof.id, payment_status: s.payment_status, amount: s.amount };
});
on("PATCH", "/subscriptions/:id", ({ me, params, body }) => updateStatus(me, params.id, body));

// --- payments (Razorpay is simulated by a demo checkout dialog)
on("POST", "/payments/orders", ({ me }) => {
  const s = db.subscriptions.filter((x) => x.user_id === me.id && x.payment_status === "pending").sort((a, b) => b.id - a.id)[0];
  if (!s) fail(400, "No pending plan found. Please choose your plan again.");
  if (!(s.amount > 0)) fail(400, "Nothing to pay for this plan");
  const orderId = `order_demo_${code8()}`;
  db.payment_orders.push({ id: nextId("payment_orders"), order_id: orderId, user_id: me.id, subscription_id: s.id, gateway: "razorpay",
    amount: s.amount, status: "created", payment_id: null, created_at: now(), paid_at: null });
  return { gateway: "razorpay", order_id: orderId, amount: Math.round(s.amount * 100), currency: "INR", key_id: "rzp_demo_key",
    prefill: { name: me.name, email: me.email, contact: "9999999999" } };
});
on("POST", "/payments/verify", ({ me, body }) => {
  const orderId = body.razorpay_order_id || body.order_id;
  const order = db.payment_orders.find((o) => o.order_id === orderId && o.user_id === me.id);
  const ok = !!order && body.razorpay_signature === "demo-signature";
  if (ok && order.status !== "paid") {
    Object.assign(order, { status: "paid", payment_id: body.razorpay_payment_id, paid_at: now() });
    const s = db.subscriptions.find((x) => x.id === order.subscription_id);
    if (s) activate(s, body.razorpay_payment_id);
  }
  return { gateway: "razorpay", order_status: ok ? "PAID" : "FAILED", order_id: orderId, payment_id: body.razorpay_payment_id };
});

// --- diet plans
on("GET", "/diet-plans", ({ me, query }) => {
  const subId = Number(query.get("subscription_id"));
  if (subId <= 0) return [];
  owned(me, subId);
  return db.daily_diet_plans.filter((r) => r.subscription_id === subId).sort((a, b) => a.day_number - b.day_number)
    .map(({ meal_json, ...r }) => ({ ...r, meal_plan: parseJson(meal_json, null), is_unlocked: !!Number(r.is_unlocked) }));
});
on("POST", "/diet-plans/batch", ({ me, body }) => {
  if (!Array.isArray(body) || !body.length) fail(400, "Invalid data format");
  body.forEach((r) => {
    const s = owned(me, r.subscription_id);
    const day = Number(r.day_number);
    if (day < 1 || day > s.plan_days) fail(400, `Day ${day} is outside this plan`);
    if (db.daily_diet_plans.some((x) => x.subscription_id === s.id && x.day_number === day)) return;
    db.daily_diet_plans.push({ id: nextId("daily_diet_plans"), subscription_id: s.id, day_number: day,
      plan_date: String(r.plan_date || addDays(today(), day - 1)).slice(0, 10), meal_json: null,
      is_unlocked: r.is_unlocked ? 1 : 0, unlocked_at: null, user_name: r.user_name || "Unknown", meal_changes_count: 0 });
  });
  return { message: "Plans created successfully" };
});
on("PATCH", "/diet-plans/:sub/days/:day", ({ me, params, body }) => {
  const s = owned(me, params.sub);
  const row = db.daily_diet_plans.find((r) => r.subscription_id === s.id && r.day_number === Number(params.day));
  if (!row) return { message: "No changes" };
  if (body.meal_plan != null) row.meal_json = JSON.stringify(body.meal_plan);
  if (body.is_unlocked != null) row.is_unlocked = body.is_unlocked ? 1 : 0;
  if (body.unlocked_at != null) row.unlocked_at = now();
  return { message: "Updated successfully" };
});
const requireAccess = (me, subId) => {
  if (subId > 0) {
    const s = owned(me, subId);
    if (!PAID.includes(String(s.payment_status).toLowerCase())) fail(402, "This plan is not active yet");
    if (s.end_date < today()) fail(402, "This plan has expired");
    return;
  }
  if (db.subscriptions.some((x) => x.user_id === me.id && x.plan_type === "trial")) fail(402, "Please choose a plan to continue");
};
on("POST", "/diet-plans/generate", async ({ me, body }) => {
  const subId = Number(body.subscription_id || 0);
  requireAccess(me, subId);
  await new Promise((r) => setTimeout(r, 1800)); // feels like the AI is thinking
  const prof = { ...(body.userProfile || {}), ...(myProfile(me) || {}) };
  return { mealPlan: samplePlan(Number(body.dayNumber || 1), prof, body.notes || "", planPhase(prof)) };
});
on("POST", "/diet-plans/substitute", async ({ me, body }) => {
  const subId = Number(body.subscription_id || 0);
  if (!String(body.user_has || "").trim()) fail(400, "user_has is required for meal substitution");
  let row = null;
  if (subId > 0) {
    owned(me, subId);
    row = db.daily_diet_plans.find((r) => r.subscription_id === subId && r.day_number === Number(body.day_number || 1));
    if (row && row.meal_changes_count >= 5) fail(429, "You've used all 5 meal swaps for today");
  }
  await new Promise((r) => setTimeout(r, 900));
  if (row) row.meal_changes_count += 1;
  return { meal: sampleSubstitution(body.user_has, body.target_macros), changes_remaining: row ? Math.max(0, 5 - row.meal_changes_count) : 5 };
});
on("POST", "/checkins", ({ me, body }) => {
  const subId = Number(body.subscription_id || 0);
  if (subId <= 0) return { success: true, message: "Free day — nothing to save" };
  owned(me, subId);
  const day = Number(body.day_number);
  const ex = db.daily_logs.find((l) => l.subscription_id === subId && l.day_number === day);
  const vals = { weight_log: body.weight ?? null, mood_log: body.mood ?? null, notes: body.notes ?? null };
  if (ex) Object.assign(ex, vals);
  else db.daily_logs.push({ id: nextId("daily_logs"), subscription_id: subId, day_number: day, created_at: now(), ...vals });
  return { success: true, message: "Daily log saved successfully" };
});
on("GET", "/timers", ({ me, query }) => {
  const subId = Number(query.get("subscription_id"));
  if (subId <= 0) return { timer: null };
  owned(me, subId);
  const t = db.timers.find((x) => x.subscription_id === subId && x.day_number === Number(query.get("day_number")));
  return { timer: t ? { ...t, unlock_at: new Date(t.unlock_ms).toISOString().slice(0, 19) + "Z" } : null };
});
on("POST", "/timers", ({ me, body }) => {
  const subId = Number(body.subscription_id || 0);
  if (subId <= 0) return { success: true };
  owned(me, subId);
  db.timers = db.timers.filter((x) => !(x.subscription_id === subId && x.day_number === Number(body.day_number)));
  db.timers.push({ id: nextId("timers"), subscription_id: subId, day_number: Number(body.day_number), unlock_ms: Number(body.unlock_at) });
  return { success: true };
});
on("DELETE", "/timers", ({ me, query }) => {
  const subId = Number(query.get("subscription_id"));
  if (subId > 0) {
    owned(me, subId);
    db.timers = db.timers.filter((x) => !(x.subscription_id === subId && x.day_number === Number(query.get("day_number"))));
  }
  return { success: true };
});

// --- partner programme
const balanceOf = (uid) => {
  const earned = db.referral_earnings.filter((e) => e.user_id === uid).reduce((a, e) => a + Number(e.amount), 0);
  const w = (st) => db.withdrawals.filter((x) => x.user_id === uid && x.status === st).reduce((a, x) => a + Number(x.amount), 0);
  return { earned, withdrawn: w("approved"), pending: w("pending") };
};
on("GET", "/partner/stats", ({ me }) => {
  db.subscriptions.filter((s) => PAID.includes(s.payment_status)).forEach(commission);
  const { earned, withdrawn, pending } = balanceOf(me.id);
  const nameOf = (id) => db.users.find((u) => u.id === id)?.name;
  const earnings = db.referral_earnings.filter((e) => e.user_id === me.id).sort((a, b) => b.id - a.id).slice(0, 10)
    .map((e) => ({ ...e, referred_user_name: nameOf(e.referred_user_id), user_name: nameOf(e.referred_user_id) || nameOf(e.adjusted_by_admin_id) }));
  const payouts = db.withdrawals.filter((w) => w.user_id === me.id).sort((a, b) => b.id - a.id).slice(0, 20);
  const referred = db.users.filter((u) => u.referred_by === me.id).map((u) => {
    const e = db.referral_earnings.find((x) => x.referred_user_id === u.id && x.user_id === me.id);
    return { id: u.id, name: u.name, user_name: u.name, email: u.email, created_at: u.created_at, earning_amount: e?.amount ?? null, commission_amount: e?.amount ?? null };
  });
  const f = (v) => money(v).toFixed(2);
  const min = db.settings.minWithdrawalAmount || "100";
  return { referral_code: me.referral_code, stats: { total_referrals: referred.length, total_earned: f(earned),
    total_withdrawn: f(withdrawn), pending_withdrawal: f(pending), min_payout: min }, recentEarnings: earnings,
    withdrawalHistory: payouts, referredUsers: referred, referrals: referred.length, referrals_list: referred,
    earnings, payouts, total_pay: f(earned), balance: f(earned - withdrawn - pending), min_payout: min };
});
on("POST", "/partner/payouts", ({ me, body }) => {
  const amount = money(body.amount);
  if (!(amount > 0)) fail(400, "Enter a valid amount");
  const min = Number(db.settings.minWithdrawalAmount || 100);
  if (amount < min) fail(400, `Minimum payout is ₹${min}`);
  const { earned, withdrawn, pending } = balanceOf(me.id);
  if (amount > earned - withdrawn - pending) fail(400, "Insufficient balance");
  db.withdrawals.push({ id: nextId("withdrawals"), user_id: me.id, amount, status: "pending", transaction_id: null,
    screenshot_url: null, requested_at: now(), processed_at: null, notes: null });
  return { success: true, message: "Payout request submitted" };
});

// --- period tracker
const femaleProfile = (me) => {
  const p = myProfile(me);
  if (!p) fail(403, "Your health profile is missing. Please complete health metrics first.");
  if (!["women", "female"].includes(String(p.gender).toLowerCase())) fail(403, "Period tracking is only available for female users.");
  return p;
};
const periodResult = (me, message) => ({ success: true, message, summary: cycleSummary(me.id, femaleProfile(me)) });
on("GET", "/period/logs", ({ me, query }) => {
  femaleProfile(me);
  const month = String(query.get("month") || today().slice(0, 7)).slice(0, 7);
  return db.period_logs.filter((l) => l.user_id === me.id && l.log_date.startsWith(month))
    .map((l) => ({ ...l, is_period_day: !!Number(l.is_period_day), symptoms: parseJson(l.symptoms, []) }));
});
on("GET", "/period/summary", ({ me }) => cycleSummary(me.id, femaleProfile(me)));
on("POST", "/period/logs", ({ me, body }) => {
  const p = femaleProfile(me);
  if (!body.log_date) fail(400, "Invalid data. log_date is required.");
  const date = String(body.log_date).slice(0, 10);
  const vals = { is_period_day: body.is_period_day ? 1 : 0, flow_level: body.flow_level ?? null,
    symptoms: body.symptoms != null ? JSON.stringify(body.symptoms) : null, mood: body.mood ?? null, notes: body.notes ?? null, updated_at: now() };
  const ex = db.period_logs.find((l) => l.user_id === me.id && l.log_date === date);
  if (ex) Object.assign(ex, vals);
  else db.period_logs.push({ id: nextId("period_logs"), user_id: me.id, profile_id: p.id, log_date: date, created_at: now(), ...vals });
  return periodResult(me, "Log saved successfully.");
});
on("DELETE", "/period/logs", ({ me, query }) => {
  femaleProfile(me);
  db.period_logs = db.period_logs.filter((l) => !(l.user_id === me.id && l.log_date === query.get("log_date")));
  return periodResult(me, "Log deleted.");
});
on("PUT", "/period/config", ({ me, body }) => {
  const p = femaleProfile(me);
  Object.assign(p, { cycle_length: Number(body.cycle_length) || 28, period_duration: Number(body.period_duration) || 5,
    last_period_start: body.last_period_start ? String(body.last_period_start).slice(0, 10) : null });
  return periodResult(me, "Config updated.");
});

// --- notifications (push itself needs OneSignal + the real backend)
const prefsOf = (me) => db.notification_preferences.find((p) => p.user_id === me.id);
on("GET", "/notifications/preferences", ({ me }) => {
  const p = prefsOf(me);
  return p || { user_id: me.id, onesignal_player_id: null, water_reminder_enabled: false, water_reminder_interval: 60,
    water_reminder_start: "08:00:00", water_reminder_end: "22:00:00", meal_reminder_enabled: false, breakfast_time: "08:00:00",
    lunch_time: "12:30:00", dinner_time: "19:00:00", period_reminder_enabled: false, period_reminder_days_before: 2, notifications_paused: false };
});
on("POST", "/notifications/preferences", ({ me, body }) => {
  const p = prefsOf(me) || (db.notification_preferences.push({ user_id: me.id, onesignal_player_id: null }), prefsOf(me));
  Object.assign(p, body, { user_id: me.id });
  return { success: true, message: "Preferences saved" };
});
on("POST", "/notifications/device", ({ me, body }) => {
  const p = prefsOf(me) || (db.notification_preferences.push({ user_id: me.id }), prefsOf(me));
  p.onesignal_player_id = body.player_id;
  return { success: true, message: "Device registered" };
});
on("DELETE", "/notifications/device", ({ me }) => {
  const p = prefsOf(me);
  if (p) p.onesignal_player_id = null;
  return { success: true, message: "Device unregistered" };
});
on("POST", "/notifications/test", () => fail(400, "Push notifications need OneSignal keys and the real backend"));

// --- admin (only users with is_admin = 1, checked on every call)
const adminOnly = { admin: true };
const paidRevenue = (list) => money(list.filter((s) => ["completed", "SUCCESS"].includes(s.payment_status)).reduce((a, s) => a + Number(s.amount), 0));
on("GET", "/admin/analytics", () => {
  const t = today();
  const subs = db.subscriptions;
  const paid = subs.filter((s) => ["completed", "SUCCESS"].includes(s.payment_status));
  const active = paid.filter((s) => s.end_date >= t).length;
  const revMap = {};
  paid.forEach((s) => (revMap[s.start_date.slice(0, 10)] = money((revMap[s.start_date.slice(0, 10)] || 0) + Number(s.amount))));
  const revDays = Object.keys(revMap).sort().filter((d) => d >= addDays(t, -30));
  const userMap = {}; db.users.forEach((u) => (userMap[u.created_at.slice(0, 10)] = (userMap[u.created_at.slice(0, 10)] || 0) + 1));
  const months = {}; paid.forEach((s) => {
    const k = new Date(`${s.start_date.slice(0, 10)}T00:00:00`).toLocaleString("en", { month: "short", year: "numeric" });
    months[k] = money((months[k] || 0) + Number(s.amount));
  });
  return {
    stats: { totalUsers: db.users.length, totalProfiles: db.profiles.length, totalRevenue: paidRevenue(subs),
      mrr: money(paid.filter((s) => s.plan_days >= 28 && s.end_date >= t).reduce((a, s) => a + Number(s.amount), 0)),
      pendingPayments: subs.filter((s) => s.payment_status === "pending").length,
      revenueToday: paidRevenue(subs.filter((s) => s.start_date.startsWith(t))), activeSubscriptions: active,
      totalReferralPayout: money(db.referral_earnings.reduce((a, e) => a + Number(e.amount), 0)),
      pendingWithdrawalsCount: db.withdrawals.filter((w) => w.status === "pending").length,
      pendingWithdrawalsAmount: money(db.withdrawals.filter((w) => w.status === "pending").reduce((a, w) => a + Number(w.amount), 0)),
      conversionRate: db.profiles.length ? Math.round((active / db.profiles.length) * 1000) / 10 : 0 },
    recentTransactions: [...subs].sort((a, b) => b.id - a.id).slice(0, 10).map((s) => {
      const u = db.users.find((x) => x.id === s.user_id) || {};
      return { ...s, name: u.name, email: u.email };
    }),
    growthChart: Object.keys(userMap).sort().map((date) => ({ date, count: userMap[date] })),
    revenueChart: revDays.map((date) => ({ date, amount: revMap[date] })),
    revenueHistory: Object.entries(months).map(([month, revenue]) => ({ month, revenue })),
  };
}, adminOnly);
on("GET", "/admin/users", () => {
  const t = today();
  return [...db.users].reverse().map(({ password, ...u }) => {
    const act = db.subscriptions.filter((s) => s.user_id === u.id && s.payment_status === "completed" && s.end_date >= t)
      .sort((a, b) => b.end_date.localeCompare(a.end_date))[0];
    const { earned, withdrawn, pending } = balanceOf(u.id);
    return { ...u, active_subscription_id: act?.id ?? null, active_plan: act?.plan_type ?? null, active_plan_end_date: act?.end_date ?? null,
      sub_count: db.subscriptions.filter((s) => s.user_id === u.id).length, total_earned: money(earned), current_balance: money(earned - withdrawn - pending) };
  });
}, adminOnly);
on("POST", "/admin/users/:id/plan", ({ me, params, body }) => {
  const planType = String(body.plan_type || "").toLowerCase();
  if (!["daily", "weekly", "monthly"].includes(planType)) fail(400, "user_id and a valid plan_type are required");
  const u = db.users.find((x) => x.id === Number(params.id));
  if (!u) fail(404, "User not found");
  const t = today();
  let p = db.profiles.find((x) => x.user_id === u.id);
  if (!p) { p = { id: nextId("profiles"), user_id: u.id, name: u.name, email: u.email, age: 0, gender: "", height: 0, weight: 0, goal: "", activity_level: "", dietary_restrictions: "[]", extended_data: "{}", created_at: now() }; db.profiles.push(p); }
  db.subscriptions.filter((s) => s.user_id === u.id && s.payment_status === "completed" && s.end_date >= t)
    .forEach((s) => Object.assign(s, { status: "expired", end_date: addDays(t, -1), admin_note: `Admin changed plan to ${planType} on ${t}` }));
  const days = plans.days(planType);
  const s = { id: nextId("subscriptions"), user_id: u.id, profile_id: p.id, plan_type: planType, plan_days: days, start_date: now(),
    end_date: addDays(t, days), amount: plans.price(planType), payment_method: "admin", payment_status: "completed",
    transaction_id: `ADMIN-${u.id}`, admin_note: `Plan assigned by admin #${me.id} on ${t}`, status: "active" };
  db.subscriptions.push(s);
  for (let d = 1; d <= days; d++) db.daily_diet_plans.push({ id: nextId("daily_diet_plans"), subscription_id: s.id, day_number: d,
    plan_date: addDays(t, d - 1), meal_json: null, is_unlocked: 0, unlocked_at: null, user_name: u.name, meal_changes_count: 0 });
  return { success: true, subscription_id: s.id, user_id: u.id, plan_type: planType, plan_days: days, end_date: s.end_date, amount: s.amount };
}, adminOnly);
on("DELETE", "/admin/users", ({ me, body }) => {
  const ids = (body.userIds || (body.userId != null ? [body.userId] : [])).map(Number)
    .filter((id) => id !== me.id && !Number(db.users.find((u) => u.id === id)?.is_admin));
  const subIds = db.subscriptions.filter((s) => ids.includes(s.user_id)).map((s) => s.id);
  db.daily_diet_plans = db.daily_diet_plans.filter((r) => !subIds.includes(r.subscription_id));
  ["subscriptions", "profiles", "period_logs", "withdrawals", "notification_preferences", "payment_orders", "referral_earnings"]
    .forEach((tbl) => (db[tbl] = db[tbl].filter((r) => !ids.includes(r.user_id))));
  db.users.forEach((u) => ids.includes(u.referred_by) && (u.referred_by = null));
  const before = db.users.length;
  db.users = db.users.filter((u) => !ids.includes(u.id));
  const deleted = before - db.users.length;
  return { success: true, deleted, message: `${deleted} user(s) deleted successfully` };
}, adminOnly);
const PROFILE_COLS = ["name", "email", "age", "gender", "height", "weight", "goal", "activity_level", "dietary_restrictions",
  "allergies", "meal_preference", "extended_data", "bmi", "cycle_length", "period_duration", "last_period_start"];
on("GET", "/admin/profiles", () => {
  const t = today();
  return [...db.profiles].reverse().map((p) => ({ ...p, active_plan: db.subscriptions.find((s) => s.user_id === p.user_id &&
    s.payment_status === "completed" && s.end_date >= t)?.plan_type || "Trial" }));
}, adminOnly);
on("GET", "/admin/profiles/:id", ({ params }) => db.profiles.find((p) => p.id === Number(params.id)) || null, adminOnly);
on("PATCH", "/admin/profiles/:id", ({ params, body }) => {
  const p = db.profiles.find((x) => x.id === Number(params.id));
  if (!p) fail(404, "Profile not found");
  PROFILE_COLS.forEach((k) => {
    if (body[k] === undefined || body[k] === null) return;
    p[k] = typeof body[k] === "object" ? JSON.stringify(body[k]) : body[k];
  });
  if (body.phone != null) {
    const ext = parseJson(p.extended_data, {}) || {};
    ext.phone = body.phone;
    p.extended_data = JSON.stringify(ext);
  }
  return { success: true };
}, adminOnly);
on("DELETE", "/admin/profiles/:id", ({ params }) => {
  db.profiles = db.profiles.filter((p) => p.id !== Number(params.id));
  return { success: true, deleted: Number(params.id) };
}, adminOnly);
on("GET", "/admin/health-metrics", ({ query }) => db.health_metrics.find((h) => h.profile_id === Number(query.get("profile_id"))) || null, adminOnly);
on("POST", "/admin/health-metrics", ({ body }) => {
  const pid = Number(body.profile_id);
  const ex = db.health_metrics.find((h) => h.profile_id === pid);
  const vals = { bmi: Number(body.bmi) || 0, target_calories: Number(body.target_calories) || 0, notes: body.notes || "", updated_by_admin: 1, last_updated: now() };
  if (ex) Object.assign(ex, vals); else db.health_metrics.push({ id: nextId("health_metrics"), profile_id: pid, ...vals });
  return { success: true };
}, adminOnly);
on("GET", "/admin/settings", () => ({ ...db.settings }), adminOnly);
on("POST", "/admin/settings", async ({ form }) => {
  for (const [k, v] of form.entries()) {
    if (k === "admin_id") continue;
    db.settings[k] = v instanceof File ? await fileToDataUrl(v) : String(v);
  }
  return { success: true };
}, adminOnly);
on("GET", "/admin/withdrawals", () =>
  [...db.withdrawals].sort((a, b) => b.id - a.id).map((w) => {
    const u = db.users.find((x) => x.id === w.user_id) || {};
    return { ...w, name: u.name, email: u.email };
  }), adminOnly);
on("POST", "/admin/withdrawals", async ({ form }) => {
  const w = db.withdrawals.find((x) => x.id === Number(form.get("id")));
  const status = form.get("status");
  if (!w || !["pending", "approved", "rejected"].includes(status)) fail(400, "ID and status required");
  const shot = form.get("screenshot");
  if (shot instanceof File && shot.size) w.screenshot_url = await fileToDataUrl(shot);
  Object.assign(w, { status, transaction_id: form.get("transaction_id") || "", processed_at: now() });
  return { success: true, screenshot_url: w.screenshot_url };
}, adminOnly);
on("POST", "/admin/balance-adjustments", ({ me, body }) => {
  const amt = money(body.amount);
  if (!(amt > 0)) fail(400, "Invalid amount. Must be a positive number.");
  if (!db.users.some((u) => u.id === Number(body.user_id))) fail(404, "User not found");
  db.referral_earnings.push({ id: nextId("referral_earnings"), user_id: Number(body.user_id), referred_user_id: null, subscription_id: null,
    amount: body.type === "remove" ? -amt : amt, description: body.description || "", adjusted_by_admin_id: me.id,
    is_admin_adjustment: 1, status: "completed", created_at: now() });
  return { success: true, message: "Balance adjusted successfully" };
}, adminOnly);
on("GET", "/admin/coupons", () => [...db.coupons].sort((a, b) => b.id - a.id), adminOnly);
on("GET", "/admin/coupons/analytics", () => {
  const rows = [...db.coupons].sort((a, b) => b.used_count - a.used_count);
  const n = new Date();
  return { total_coupons: rows.length, active_coupons: rows.filter((c) => Number(c.is_active)).length,
    expired_coupons: rows.filter((c) => c.valid_until && new Date(c.valid_until.replace(" ", "T")) < n).length,
    exhausted_coupons: rows.filter((c) => c.max_uses != null && c.used_count >= c.max_uses).length,
    total_redemptions: rows.reduce((a, c) => a + c.used_count, 0), top_coupons: rows.slice(0, 5) };
}, adminOnly);
const couponFields = (b) => {
  const o = {};
  if (b.code != null) o.code = String(b.code).trim().toUpperCase();
  if (b.discount_type != null) o.discount_type = b.discount_type;
  if (b.discount_value != null) o.discount_value = Number(b.discount_value) || 0;
  if (b.max_uses !== undefined) o.max_uses = b.max_uses === "" || b.max_uses == null ? null : Number(b.max_uses);
  if (b.min_amount != null) o.min_amount = Number(b.min_amount) || 0;
  if (b.applies_to != null) o.applies_to = b.applies_to;
  if (b.valid_from !== undefined) o.valid_from = b.valid_from || null;
  if (b.valid_until !== undefined) o.valid_until = b.valid_until || null;
  if (b.is_active !== undefined) o.is_active = b.is_active ? 1 : 0;
  return o;
};
on("POST", "/admin/coupons", ({ body }) => {
  const f = couponFields(body);
  if (!f.code) fail(400, "Code required");
  if (db.coupons.some((c) => c.code === f.code)) fail(400, "Coupon code already exists");
  const c = { id: nextId("coupons"), discount_type: "percent", discount_value: 0, max_uses: null, used_count: 0, min_amount: 0,
    applies_to: "all", valid_from: null, valid_until: null, is_active: 1, created_at: now(), ...f };
  db.coupons.push(c);
  return { success: true, id: c.id };
}, adminOnly);
on("PATCH", "/admin/coupons/:id", ({ params, body }) => {
  const c = db.coupons.find((x) => x.id === Number(params.id));
  if (c) Object.assign(c, couponFields(body));
  return { success: true };
}, adminOnly);
on("DELETE", "/admin/coupons/:id", ({ params }) => {
  db.coupons = db.coupons.filter((c) => c.id !== Number(params.id));
  return { success: true };
}, adminOnly);
on("GET", "/admin/transactions", ({ query }) => {
  const status = (query.get("status") || "all").toLowerCase();
  const gw = (query.get("gateway") || "all").toLowerCase();
  const q = (query.get("q") || "").trim().toLowerCase();
  const rows = [...db.subscriptions].sort((a, b) => b.id - a.id).map((s) => {
    const u = db.users.find((x) => x.id === s.user_id) || {};
    return { ...s, email: u.email, user_name: u.name, profile_name: db.profiles.find((p) => p.id === s.profile_id)?.name };
  }).filter((s) => (status === "all" || s.payment_status === status) && (gw === "all" || String(s.payment_method).toLowerCase().includes(gw)) &&
    (!q || [s.email, s.user_name, s.transaction_id, String(s.id)].some((v) => String(v || "").toLowerCase().includes(q))));
  return { transactions: rows, stats: { total: rows.length, revenue: paidRevenue(rows.filter((r) => r.payment_status === "completed")),
    completed_count: rows.filter((r) => r.payment_status === "completed").length, pending_count: rows.filter((r) => r.payment_status === "pending").length,
    failed_count: rows.filter((r) => ["failed", "cancelled"].includes(r.payment_status)).length } };
}, adminOnly);
on("PATCH", "/admin/transactions/:id", ({ params, body }) => {
  const s = db.subscriptions.find((x) => x.id === Number(params.id));
  if (!s) fail(404, "Subscription not found");
  const allowed = ["pending", "completed", "failed", "cancelled", "upgraded", "refunded"];
  if (!allowed.includes(body.payment_status)) fail(400, `Invalid status. Allowed: ${allowed.join(", ")}`);
  if (body.payment_status === "completed" && !PAID.includes(s.payment_status)) activate(s, body.transaction_id);
  else s.payment_status = body.payment_status;
  if (body.transaction_id != null) s.transaction_id = body.transaction_id;
  if (body.admin_note != null) s.admin_note = body.admin_note;
  return { success: true };
}, adminOnly);
on("POST", "/admin/pages", ({ body }) => {
  const ex = db.pages.find((p) => p.slug === body.slug);
  if (ex) Object.assign(ex, { title: body.title, content: body.content, updated_at: now() });
  else db.pages.push({ id: nextId("pages"), slug: body.slug, title: body.title, content: body.content, created_at: now(), updated_at: now() });
  return { success: true };
}, adminOnly);

const fileToDataUrl = (file) =>
  new Promise((resolve) => {
    const r = new FileReader();
    r.onload = () => resolve(r.result);
    r.onerror = () => resolve("");
    r.readAsDataURL(file);
  });

// ----------------------------------------------------------------- fetch interceptor

async function handle(url, init) {
  const u = new URL(url, window.location.href);
  const path = u.pathname.slice(u.pathname.indexOf("/api") + 4) || "/";
  const method = String(init.method || "GET").toUpperCase();
  const headers = init.headers || {};
  const auth = headers.Authorization || headers.authorization || "";
  const userId = Number((auth.match(/^Bearer demo-token-(\d+)$/) || [])[1]);
  const me = db.users.find((x) => x.id === userId) || null;
  let body = {};
  let form = null;
  if (init.body instanceof FormData) form = init.body;
  else if (typeof init.body === "string" && init.body) body = parseJson(init.body, {});

  for (const r of routes) {
    if (r.method !== method) continue;
    const m = path.match(r.re);
    if (!m) continue;
    if (!r.public && !me) return { status: 401, data: { error: "Please log in again", message: "Please log in again" } };
    if (r.admin && !Number(me.is_admin)) return { status: 403, data: { error: "Forbidden", message: "Forbidden" } };
    try {
      const data = await r.handler({ me, body, form, params: m.groups || {}, query: u.searchParams });
      save();
      return { status: 200, data: data === undefined ? null : data };
    } catch (e) {
      if (e && e.status) return { status: e.status, data: { error: e.message, message: e.message } };
      console.error("[demo api]", e);
      return { status: 500, data: { error: "Something went wrong. Please try again.", message: "Something went wrong. Please try again." } };
    }
  }
  return { status: 404, data: { error: "Not found", message: "Not found" } };
}

// ----------------------------------------------------------------- demo chrome: banner + fake Razorpay

function injectUi() {
  const style = document.createElement("style");
  style.textContent = `
  #mrdiet-demo{position:fixed;left:12px;bottom:12px;z-index:2147483000;font:13px/1.45 Poppins,system-ui,sans-serif;
    background:#111827;color:#f9fafb;border:1px solid #d4af37;border-radius:14px;box-shadow:0 10px 30px rgba(0,0,0,.35);max-width:340px}
  #mrdiet-demo summary{cursor:pointer;padding:9px 14px;font-weight:600;list-style:none;color:#facc15}
  #mrdiet-demo summary::-webkit-details-marker{display:none}
  #mrdiet-demo .b{padding:0 14px 12px}
  #mrdiet-demo code{background:#1f2937;padding:1px 6px;border-radius:6px;color:#fde68a}
  #mrdiet-demo button{margin-top:8px;background:#d4af37;color:#111;border:0;border-radius:8px;padding:6px 10px;font-weight:600;cursor:pointer}
  #mrdiet-pay{position:fixed;inset:0;z-index:2147483600;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center;font-family:Poppins,system-ui,sans-serif}
  #mrdiet-pay .card{background:#fff;color:#111;border-radius:16px;padding:22px 24px;width:min(360px,92vw);box-shadow:0 20px 50px rgba(0,0,0,.4)}
  #mrdiet-pay h3{margin:0 0 4px;font-size:18px} #mrdiet-pay p{margin:6px 0;color:#444;font-size:13px}
  #mrdiet-pay .amt{font-size:28px;font-weight:700;margin:10px 0}
  #mrdiet-pay .row{display:flex;gap:10px;margin-top:16px}
  #mrdiet-pay button{flex:1;border:0;border-radius:10px;padding:11px;font-weight:700;cursor:pointer}
  #mrdiet-pay .pay{background:#2563eb;color:#fff} #mrdiet-pay .cancel{background:#e5e7eb}`;
  document.head.appendChild(style);

  const box = document.createElement("details");
  box.id = "mrdiet-demo";
  box.open = true;
  box.innerHTML = `<summary>🧪 Demo mode</summary><div class="b">
    The Spring Boot backend is simulated in your browser; payments and AI are fake.<br/>
    User: <code>priya@mrdiet.demo</code><br/>Admin: <code>admin@mrdiet.demo</code><br/>Password: <code>demo1234</code><br/>
    Free checkout coupon: <code>DEMO100</code><br/><button type="button">Reset demo data</button></div>`;
  box.querySelector("button").onclick = () => {
    try {
      localStorage.removeItem(DB_KEY);
      localStorage.removeItem("mrdiet_auth_user");
    } catch { /* ignore */ }
    window.location.hash = "#/";
    window.location.reload();
  };
  document.body.appendChild(box);
}

class DemoRazorpay {
  constructor(opts) {
    this.opts = opts;
    this.handlers = {};
  }
  on(evt, cb) {
    this.handlers[evt] = cb;
  }
  open() {
    const o = this.opts;
    const wrap = document.createElement("div");
    wrap.id = "mrdiet-pay";
    wrap.innerHTML = `<div class="card"><h3>${o.name || "MRDiet"} · Demo checkout</h3>
      <p>${o.description || ""}</p><div class="amt">₹${(Number(o.amount) / 100).toFixed(2)}</div>
      <p>This stands in for Razorpay. The demo backend still records the order and verifies the "payment" before activating the plan.</p>
      <div class="row"><button class="cancel" type="button">Cancel</button><button class="pay" type="button">Pay now</button></div></div>`;
    document.body.appendChild(wrap);
    wrap.querySelector(".cancel").onclick = () => {
      wrap.remove();
      o.modal?.ondismiss?.();
    };
    wrap.querySelector(".pay").onclick = () => {
      wrap.remove();
      o.handler?.({ razorpay_order_id: o.order_id, razorpay_payment_id: `pay_demo_${code8()}`, razorpay_signature: "demo-signature" });
    };
  }
}

/** Called from main.jsx before React renders, only in the demo build. */
export function installDemo() {
  try {
    db = JSON.parse(localStorage.getItem(DB_KEY) || "null");
  } catch {
    db = null;
  }
  if (!db || !db.users) seed();

  const realFetch = window.fetch.bind(window);
  window.fetch = async (input, init = {}) => {
    const url = typeof input === "string" ? input : input.url;
    const target = new URL(url, window.location.href);
    if (target.origin !== window.location.origin || !target.pathname.includes("/api/")) {
      return realFetch(input, init); // not our API (e.g. Google) — leave it alone
    }
    await new Promise((r) => setTimeout(r, 120));
    const { status, data } = await handle(url, init);
    return new Response(data === null ? "" : JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });
  };

  // Hard redirects (window.location.href = "/dashboard") become hash routes in the demo build.
  Object.defineProperty(window, "__demoHref", {
    set(v) {
      const s = String(v);
      window.location.hash = `#${s.startsWith("/") ? s : `/${s}`}`;
    },
    configurable: true,
  });
  document.addEventListener("click", (e) => {
    const a = e.target.closest && e.target.closest('a[href^="/"]');
    if (a && !a.target) {
      e.preventDefault();
      window.location.hash = `#${a.getAttribute("href")}`;
    }
  }, true);

  window.Razorpay = DemoRazorpay;
  if (document.body) injectUi();
  else document.addEventListener("DOMContentLoaded", injectUi);
}
