# Mr. Diet: Java + Spring Boot rewrite

This is the same Mr. Diet site, rebuilt on a new stack:

| Layer | Before | Now |
|---|---|---|
| Frontend | React 18 + **TypeScript** (Lovable) | React 18 + **JavaScript** + Tailwind + shadcn/ui |
| Backend | Plain **PHP** (one file per endpoint) | **Java 21 + Spring Boot 3.5** |
| Database | MySQL `mrdietweb` | **The same database.** No data migration is needed |
| AI | OpenAI `gpt-4o-mini`, JSON mode | Same model. The prompts were ported word for word |
| Payments | Cashfree + Razorpay | Same gateways, but the server now verifies every payment |
| Push | OneSignal + a Hostinger cron job | OneSignal + a built-in scheduler that runs every 5 minutes |
| Auth | `mock_token_<id>` (not real) | Signed JWT tokens. Old passwords still work |

```
mrdiet-java/
├── backend/            Spring Boot API  (mvn spring-boot:run)
│   ├── src/main/java/com/mrdiet/
│   │   ├── core/          plain-Java business rules (prices, calories, cycle, AI prompt)
│   │   ├── auth/ profile/ subscription/ payment/ diet/ coupon/
│   │   ├── partner/ period/ notification/ cms/ settings/ admin/
│   │   ├── security/      JWT + who-can-call-what
│   │   └── common/ config/
│   ├── src/test/          security tests (mvn test, uses in-memory H2)
│   └── .env.example
├── frontend/           React app (npm run dev / npm run build)
└── database/           SQL: full schema (fresh installs), the one new table, default settings
```

---

## 1. Run it on your computer

**You need:** Java 21, Maven 3.9+, Node 18+ and MySQL/MariaDB with your `mrdietweb` database.

```bash
# Backend
cd backend
cp .env.example .env          # fill in DB_* and JWT_SECRET (openssl rand -base64 48)
set -a; source .env; set +a
mvn spring-boot:run           # → http://localhost:8081/api/health  says {"status":"ok"}

# Frontend (second terminal)
cd frontend
npm install
npm run dev                   # → http://localhost:8080  (/api is proxied to 8081)
```

On first start the backend adds one table (`payment_orders`) and two columns to
`subscriptions` (`coupon_code`, `replaces_subscription_id`). That's the only change it makes to your schema.
The same SQL is in `database/002_payment_orders.sql` if you'd rather run it yourself.

**Tests:** `cd backend && mvn test` runs `ApiSecurityTest`, which checks the security
fixes listed below against an in-memory database.

### Demo mode (no Java, no MySQL)

```bash
cd frontend && npm run build:demo    # → dist-demo/  (open with any static server)
```

`src/demo/` answers every `/api` call inside the browser, using the same routes, rules
and JSON as the Spring Boot backend, with seeded sample data. It also simulates Razorpay
(a fake checkout dialog) and AI plans (a sample Indian meal-plan generator).
Log in as `priya@mrdiet.demo` or `admin@mrdiet.demo`, password `demo1234`.
Coupon `DEMO100` gives a free checkout.
Use it for showing the app. It is never included in the normal `npm run build`.

---

## 2. Deploy

> **Important:** Hostinger *shared* hosting runs PHP only. It **cannot run Java**.
> You need a **Hostinger VPS** (KVM 1 is plenty) or any other server where you can install Java 21.
> The React build can still be served by Apache/Nginx on that same VPS.

```bash
# On your machine
cd backend  && mvn clean package          # → target/mrdiet-api.jar
cd frontend && npm run build              # → dist/

# On the VPS
sudo apt install openjdk-21-jre-headless
scp target/mrdiet-api.jar  → /opt/mrdiet/
scp .env                   → /opt/mrdiet/.env    (chmod 600)
scp -r dist/*              → /var/www/mrdiet/
cp -r <old PHP site>/api/uploads/*  /var/www/mrdiet/uploads/   # logos & payout screenshots
```

`/etc/systemd/system/mrdiet.service`:

```ini
[Unit]
Description=Mr. Diet API
After=network.target mysql.service

[Service]
User=www-data
WorkingDirectory=/opt/mrdiet
EnvironmentFile=/opt/mrdiet/.env
ExecStart=/usr/bin/java -Xmx512m -jar /opt/mrdiet/mrdiet-api.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now mrdiet
```

**Web server:** send `/api/*` to `127.0.0.1:8081` and everything else to the React app.

*Apache:* `frontend/public/.htaccess` already does this. Enable `mod_proxy` and `mod_proxy_http`.

*Nginx:*

```nginx
server {
    server_name mrdietplan.in www.mrdietplan.in;
    root /var/www/mrdiet;
    client_max_body_size 12m;
    location /api/ { proxy_pass http://127.0.0.1:8081; proxy_set_header Host $host;
                     proxy_set_header X-Forwarded-Proto $scheme; proxy_set_header X-Forwarded-For $remote_addr; }
    location / { try_files $uri /index.html; }
}
```

You can delete the Hostinger cron job: reminders now run inside the app.

---

## 3. Switching over from the PHP site

- **Users:** everyone logs in once more, because old browser sessions had no real token. Their passwords are unchanged.
- **Google sign-in:** the backend now checks the token with Google and requires it to be issued to your client ID.
- **Keys:** Admin → Settings values still win over environment variables, same as before.
  **After you rotate your OpenAI key, paste the new one into Admin → Settings.** Otherwise the old leaked key stored in the database keeps being used.
- **Old files:** take `api/`, `*.log`, `*.sql` dumps and `config*.php` off the web server completely.

### Light / Dark / System theme

- **Switch:** visitors use the ☀️ / 🌙 / 🖥 switch in the navbar, user sidebar or admin sidebar. *System* follows the phone or computer setting. Each visitor's choice is saved in their browser.
- **Admin default:** visitors who never used the switch get **Admin → Settings → Theme & Brand → Default theme for visitors**, which is stored as `default_theme` and falls back to *System*.
  Your live database currently has `default_theme = light`.
- **Logos:** the light-mode and dark-mode logos uploaded on the same tab now switch automatically.
- **Code:** colors live in `src/index.css`. `:root` holds the light palette and `.dark` the original dark look.
  Use the theme tokens (`text-ink`, `border-ink/10`, `bg-surface`, `bg-gold-tint`) instead of `text-white` or `bg-[#0f0f0f]`,
  so new screens work in both themes.

### Old → new endpoints

| PHP | Spring Boot |
|---|---|
| `auth.php?action=signup / login / verify_google(_manual)` | `POST /api/auth/signup`, `/login`, `/google` |
| `profiles.php` | `GET/POST /api/profile` |
| `subscriptions.php` (+ `?action=preview_change`) | `GET /api/subscriptions`, `/active`, `/preview`, `POST /api/subscriptions`, `PATCH /api/subscriptions/{id}` |
| `payment.php?action=create_order / verify` | `POST /api/payments/orders`, `/api/payments/verify` |
| `daily_diet_plans.php`, `generate_plan.php` | `/api/diet-plans`, `/batch`, `/{sub}/days/{day}`, `/generate`, `/substitute` |
| `checkin.php`, `timers.php` | `POST /api/checkins`, `GET/POST/DELETE /api/timers` |
| `coupons.php`, `settings_public.php` | `POST /api/coupons/validate`, `GET /api/settings/public` |
| `partner.php` | `GET /api/partner/stats`, `POST /api/partner/payouts` |
| `period_tracker.php` | `/api/period/logs`, `/summary`, `/config` |
| `notifications.php` | `/api/notifications/preferences`, `/device`, `/test` |
| `admin/*.php` | `/api/admin/...` (admins only, checked on the server) |
| `cron/send_reminders.php` | `ReminderScheduler` (automatic) |

The React pages didn't change. `src/lib/api.js` keeps the same function names and maps them to these routes.

---

## 4. Security fixes

| PHP problem | Now |
|---|---|
| Tokens were `mock_token_<id>` and no endpoint checked them. `?admin_id=1` gave admin access | Signed JWT tokens. The user always comes from the token. Admin status is re-checked in the database on every request |
| `verify_google_manual` logged you in as anyone from just an email | The Google token is verified with Google, including the audience check |
| `POST subscriptions` with `payment_status: completed` gave a free paid plan. `PATCH` also paid out referral money | A plan only becomes active after the gateway confirms the payment (`payment_orders`). The browser can't set the status |
| The browser chose the amount to pay | The server prices every plan: settings price − upgrade credit − coupon |
| Coupons were applied twice | The coupon is applied once, against the plan price |
| An upgrade cancelled the old plan before payment | The old plan is closed only when the new one is paid |
| `generate_plan.php` was open to anyone, so anyone could spend your OpenAI credit | Requires a login and an active plan, and is rate-limited per user (`AI_MAX_PER_HOUR`) |
| Plaintext passwords in `auth_request.log` (publicly downloadable) | Nothing sensitive is logged |
| Payouts subtracted a `'processed'` status that never exists, so approved money could be withdrawn twice | Balance = earned − approved − pending, with a row lock |
| Admin profile edit accepted any key as a column name (SQL injection) | Only whitelisted columns are accepted |
| Settings uploads accepted any file type (a `.php` upload could run code) | Images only (plus PDF for payout proofs) |
| The admin users list sent password hashes to the browser | Hashes are removed |
| Secrets were hard-coded in `config.php` | Secrets come from environment variables or Admin → Settings |
| `display_errors=1` showed internal errors to visitors | Errors are logged on the server. Users see a generic message |

---

## 5. Not done yet (good next steps)

- **Payment webhooks.** A plan activates when the user returns to the site after paying.
  If they close the tab first, the plan stays *pending* until you mark it completed
  in Admin → Transactions. Adding Razorpay and Cashfree webhooks would close this gap.
- **Unlock timer** stays client-side on purpose, because the dashboard has an "early unlock" feature.
- **PayPal** toggle exists in settings but was never wired up in the PHP version either.
