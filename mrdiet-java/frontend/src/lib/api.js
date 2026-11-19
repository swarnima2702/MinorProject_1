/**
 * Mr. Diet API client — talks to the Spring Boot backend.
 *
 * Every function keeps the SAME name and arguments as the old PHP version, so
 * none of the pages had to change. What's different underneath:
 *  - URLs are REST routes (/api/profile, /api/subscriptions …) instead of *.php
 *  - every request sends "Authorization: Bearer <token>" from the login
 *  - user_id / admin_id arguments are still accepted but the server ignores
 *    them: it always uses the logged-in user from the token.
 */

export const API_BASE_URL = "/api";

export const GOOGLE_CLIENT_ID = "785889991022-6jfg7dqe45cfecf1n0e3kubor89lof3p.apps.googleusercontent.com";

const AUTH_KEY = "mrdiet_auth_user";

export const getEnableDummyData = () => window.APP_CONFIG?.enableDummyData === "1";
export const getEnableRegenerate = () => window.APP_CONFIG?.enableRegenerate === "1";

export const syncConfig = (newConfig) => {
    window.APP_CONFIG = newConfig;
    localStorage.setItem("app_public_settings", JSON.stringify(newConfig));
    window.dispatchEvent(new Event("config-updated"));
};

/** Logo / upload path stored in settings ("uploads/logo.png") → URL the backend serves. */
export const getLogoUrl = (logoPath) => {
    if (!logoPath) return "";
    if (typeof logoPath !== "string") return URL.createObjectURL(logoPath);
    if (logoPath.startsWith("http") || logoPath.startsWith("data:")) return logoPath;
    const cleanPath = logoPath.startsWith("/") ? logoPath.substring(1) : logoPath;
    return `${API_BASE_URL}/${cleanPath}`;
};

// ---------------------------------------------------------------------------
// Low-level helpers
// ---------------------------------------------------------------------------

/** The JWT saved with the user object at login. */
export const getToken = () => {
    try {
        return JSON.parse(localStorage.getItem(AUTH_KEY) || "null")?.token || null;
    } catch {
        return null;
    }
};

/** Headers for every request (no-cache + login token). Exported for the few pages that call fetch directly. */
export const authHeaders = (extra = {}, json = true) => {
    const headers = {
        "Cache-Control": "no-cache, no-store, must-revalidate",
        Pragma: "no-cache",
        Expires: "0",
        ...extra,
    };
    if (json) headers["Content-Type"] = "application/json";
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    return headers;
};

const withNoCache = (url) => `${url}${url.includes("?") ? "&" : "?"}_t=${Date.now()}`;

/** Parse a response body as JSON; empty body → null. */
const readJson = async (response) => {
    const text = await response.text();
    if (!text) return null;
    try {
        return JSON.parse(text);
    } catch {
        return { error: text.substring(0, 200) };
    }
};

/** Login expired / token missing on a protected call → back to the login page. */
const handleUnauthorized = (response, path) => {
    if (response.status !== 401 || path.startsWith("/auth/")) return;
    // Not logged in at all → the page itself decides what to show.
    // Logged in with an expired token (or an old PHP-era session without one) → log in again.
    if (!localStorage.getItem(AUTH_KEY)) return;
    localStorage.removeItem(AUTH_KEY);
    if (!window.location.pathname.startsWith("/auth") && !window.location.pathname.startsWith("/admin/auth")) {
        const admin = window.location.pathname.startsWith("/admin");
        window.location.href = admin ? "/admin/auth" : "/auth";
    }
};

/**
 * Core request. By default throws Error(message) on non-2xx, like the old
 * functions did. Pass { raw: true } to get the parsed body even on errors.
 */
const request = async (path, { method = "GET", body, form, raw = false, fallbackError = "Request failed" } = {}) => {
    const url = `${API_BASE_URL}${path}`;
    const response = await fetch(method === "GET" ? withNoCache(url) : url, {
        method,
        headers: authHeaders({}, !form),
        body: form ? form : body === undefined ? undefined : JSON.stringify(body),
    });
    handleUnauthorized(response, path);
    const data = await readJson(response);
    if (!response.ok && !raw) {
        throw new Error(data?.message || data?.error || fallbackError);
    }
    return data;
};

const qs = (params) =>
    new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "")).toString();

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

export const api = {
    auth: {
        signup: (email, password, name, referralCode) =>
            request("/auth/signup", {
                method: "POST",
                body: { email, password, data: { name, referral_code: referralCode } },
                fallbackError: "Signup failed",
            }),
        login: (email, password) =>
            request("/auth/login", { method: "POST", body: { email, password }, fallbackError: "Login failed" }),
        /** Google ID token ("credential") from the GoogleLogin button. */
        googleLogin: (credential) =>
            request("/auth/google", { method: "POST", body: { credential }, fallbackError: "Google login failed" }),
        /**
         * useGoogleLogin() flow. MUST include access_token — the server checks it
         * with Google. The email/name fields are ignored by the server now.
         */
        googleLoginManual: (userInfo) =>
            request("/auth/google", {
                method: "POST",
                body: { access_token: userInfo?.access_token, referral_code: userInfo?.referral_code },
                fallbackError: "Google login failed",
            }),
        me: () => request("/auth/me"),
        logout: () => localStorage.removeItem(AUTH_KEY),
    },

    payment: {
        /** The server charges the price of your pending plan — `amount` is only informational now. */
        createOrder: (amount, user, gateway = "cashfree", extras = {}) => {
            let returnUrl = `${window.location.origin}/dashboard?order_id={order_id}`;
            const isLocalhost = /^https?:\/\/(localhost|127\.0\.0\.1)/i.test(returnUrl);
            if (returnUrl.startsWith("http:") && !isLocalhost) returnUrl = returnUrl.replace("http:", "https:");
            return request("/payments/orders", {
                method: "POST",
                body: {
                    gateway,
                    customer_phone: user?.phone,
                    customer_name: user?.name,
                    customer_email: user?.email,
                    return_url: returnUrl,
                    coupon_code: extras.coupon_code || "",
                    plan_type: extras.plan_type || "",
                },
                fallbackError: "Payment initiation failed",
            });
        },
        verifyOrder: (orderId) =>
            request("/payments/verify", { method: "POST", body: { gateway: "cashfree", order_id: orderId }, raw: true }),
        verifyRazorpay: (payload) =>
            request("/payments/verify", { method: "POST", body: { gateway: "razorpay", ...payload }, raw: true }),
    },

    profiles: {
        create: (profileData) =>
            request("/profile", { method: "POST", body: profileData, fallbackError: "Failed to process request" }),
        /** Returns null when there is no account (404). */
        getByUserId: async (_userId) => {
            try {
                return await request("/profile", { fallbackError: "Failed to fetch profile" });
            } catch (e) {
                if (/not found/i.test(e.message)) return null;
                throw e;
            }
        },
        update: (_userId, data) => request("/profile", { method: "POST", body: data, raw: true }),
    },

    subscriptions: {
        create: (subscriptionData) =>
            request("/subscriptions", { method: "POST", body: subscriptionData, fallbackError: "Failed to create subscription" }),
        update: (id, data) =>
            request(`/subscriptions/${id}`, { method: "PATCH", body: data, fallbackError: "Failed to update subscription" }),
        getActiveByUserId: (_userId) => request("/subscriptions/active", { fallbackError: "Failed to fetch subscription" }),
        getAllByUserId: async (_userId) => {
            try {
                return (await request("/subscriptions")) || [];
            } catch {
                return [];
            }
        },
        previewChange: (_userId, newPlan) =>
            request(`/subscriptions/preview?${qs({ new_plan: newPlan })}`, { raw: true }),
    },

    dietPlans: {
        createBatch: (plans) =>
            request("/diet-plans/batch", { method: "POST", body: plans, fallbackError: "Failed to create diet plans" }),
        getBySubscription: async (subscriptionId) =>
            (await request(`/diet-plans?${qs({ subscription_id: subscriptionId })}`, {
                fallbackError: "Failed to fetch diet plans",
            })) || [],
        unlock: (subscriptionId, dayNumber, mealPlan, isUnlocked = true) =>
            request(`/diet-plans/${subscriptionId}/days/${dayNumber}`, {
                method: "PATCH",
                body: { is_unlocked: isUnlocked, unlocked_at: isUnlocked ? new Date().toISOString() : null, meal_plan: mealPlan },
                fallbackError: "Failed to update plan status",
            }),
        generate: (userProfile, dayNumber, planType, language, notes, subscriptionId) =>
            request("/diet-plans/generate", {
                method: "POST",
                body: { userProfile, dayNumber, planType, language, notes, subscription_id: subscriptionId },
                fallbackError: "Failed to generate plan",
            }),
        checkIn: (data) => request("/checkins", { method: "POST", body: data, fallbackError: "Check-in failed" }),
        substituteMeal: (data) =>
            request("/diet-plans/substitute", { method: "POST", body: data, fallbackError: "Substitution failed" }),
    },

    timers: {
        get: async (subscriptionId, dayNumber) => {
            try {
                const json = await request(`/timers?${qs({ subscription_id: subscriptionId, day_number: dayNumber })}`);
                return json?.timer ?? null;
            } catch (e) {
                console.error("Timer Get Error:", e);
                return null;
            }
        },
        create: async (subscriptionId, dayNumber, unlockAt) => {
            await request("/timers", {
                method: "POST",
                body: { subscription_id: subscriptionId, day_number: dayNumber, unlock_at: unlockAt },
                fallbackError: "Failed to create timer",
            });
        },
        delete: async (subscriptionId, dayNumber) => {
            await request(`/timers?${qs({ subscription_id: subscriptionId, day_number: dayNumber })}`, {
                method: "DELETE",
                raw: true,
            });
        },
    },

    admin: {
        getAnalytics: (_adminId) => request("/admin/analytics", { raw: true }),
        getUsers: (_adminId) => request("/admin/users", { raw: true }),
        changeUserPlan: (_adminId, userId, planType) =>
            request(`/admin/users/${userId}/plan`, {
                method: "POST",
                body: { plan_type: planType },
                fallbackError: "Failed to change user plan",
            }),
        /** Body { userIds: [...] } or { userId } — used by AdminUsers. */
        deleteUsers: (_adminId, ids) =>
            request("/admin/users", {
                method: "DELETE",
                body: Array.isArray(ids) ? { userIds: ids } : { userId: ids },
                raw: true,
            }),
        getUserProfiles: (_adminId) => request("/admin/profiles", { raw: true }),
        getUserProfile: (_adminId, profileId) => request(`/admin/profiles/${profileId}`, { raw: true }),
        updateUserProfile: (_adminId, profileId, data) =>
            request(`/admin/profiles/${profileId}`, { method: "PATCH", body: data, fallbackError: "Failed to save changes" }),
        deleteUserProfile: (_adminId, profileId) =>
            request(`/admin/profiles/${profileId}`, { method: "DELETE", raw: true }),
        getHealthMetrics: (_adminId, profileId) =>
            request(`/admin/health-metrics?${qs({ profile_id: profileId })}`, { raw: true }),
        updateHealthMetrics: (_adminId, profileId, data) =>
            request("/admin/health-metrics", { method: "POST", body: { profile_id: profileId, ...data }, raw: true }),
        getSettings: (_adminId) => request("/admin/settings", { raw: true }),
        updateSettings: (_adminId, settings) => {
            const formData = new FormData();
            Object.keys(settings).forEach((key) => {
                if (settings[key] !== null && settings[key] !== undefined) formData.append(key, settings[key]);
            });
            return request("/admin/settings", { method: "POST", form: formData, raw: true });
        },
        getWithdrawals: (_adminId) => request("/admin/withdrawals", { raw: true }),
        processWithdrawal: (_adminId, formData) =>
            request("/admin/withdrawals", { method: "POST", form: formData, raw: true }),
        /** Public CMS page (privacy policy, terms …). */
        getPage: (slug) => request(`/pages/${encodeURIComponent(slug)}`, { raw: true }),
        savePage: (_adminId, pageData) => request("/admin/pages", { method: "POST", body: pageData, raw: true }),
        adjustBalance: (_adminId, userId, amount, type, description) =>
            request("/admin/balance-adjustments", {
                method: "POST",
                body: { user_id: userId, amount, type, description },
                fallbackError: "Failed to adjust balance",
            }),
        coupons: {
            list: (_adminId) => request("/admin/coupons", { raw: true }),
            create: (_adminId, data) =>
                request("/admin/coupons", { method: "POST", body: data, fallbackError: "Failed to create coupon" }),
            update: (_adminId, id, data) =>
                request(`/admin/coupons/${id}`, { method: "PATCH", body: data, fallbackError: "Failed to update coupon" }),
            remove: (_adminId, id) => request(`/admin/coupons/${id}`, { method: "DELETE", raw: true }),
            analytics: (_adminId) => request("/admin/coupons/analytics", { raw: true }),
        },
        transactions: {
            list: (_adminId, opts = {}) => request(`/admin/transactions?${qs(opts)}`, { raw: true }),
            updateStatus: (_adminId, payload) =>
                request(`/admin/transactions/${payload.id}`, { method: "PATCH", body: payload, fallbackError: "Update failed" }),
        },
    },

    coupons: {
        validate: (code, plan_type, amount) =>
            request("/coupons/validate", { method: "POST", body: { code, plan_type, amount }, raw: true }),
    },

    config: {
        fetchPublic: () => request("/settings/public", { raw: true }),
    },

    referral: {
        getStats: (_userId) => request("/partner/stats", { raw: true }),
        requestWithdrawal: (_userId, amount) => request("/partner/payouts", { method: "POST", body: { amount }, raw: true }),
    },

    partner: {
        getStats: (_userId) => request("/partner/stats", { raw: true }),
        getHistory: (_userId) => request("/partner/stats", { raw: true }),
        requestPayout: (_userId, amount) => request("/partner/payouts", { method: "POST", body: { amount }, raw: true }),
    },

    periodTracker: {
        getLogs: (_userId, month) =>
            request(`/period/logs?${qs({ month })}`, { fallbackError: "Failed to fetch period logs" }),
        getSummary: (_userId) => request("/period/summary", { fallbackError: "Failed to fetch cycle summary" }),
        saveLog: (logData) =>
            request("/period/logs", { method: "POST", body: logData, fallbackError: "Failed to save period log" }),
        deleteLog: (_userId, logDate) =>
            request(`/period/logs?${qs({ log_date: logDate })}`, { method: "DELETE", fallbackError: "Failed to delete period log" }),
        updateCycleConfig: (_userId, config) =>
            request("/period/config", { method: "PUT", body: config, fallbackError: "Failed to update cycle config" }),
    },

    notifications: {
        get: (_userId) => request("/notifications/preferences", { fallbackError: "Failed to fetch notification preferences" }),
        save: (_userId, preferences) =>
            request("/notifications/preferences", { method: "POST", body: preferences, fallbackError: "Failed to save preferences" }),
        registerDevice: (_userId, playerId) =>
            request("/notifications/device", { method: "POST", body: { player_id: playerId }, fallbackError: "Failed to register device" }),
        sendTest: (_userId) =>
            request("/notifications/test", { method: "POST", body: {}, fallbackError: "Failed to send test notification" }),
        unregisterDevice: (_userId) => request("/notifications/device", { method: "DELETE", raw: true }),
    },
};
