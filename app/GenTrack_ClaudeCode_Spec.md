# GenTrack — Claude Code Full Specification Document
> Read this entire document before writing a single line of code.
> Every decision here is final unless explicitly told otherwise during a phase.

---

## 0. Project Identity

**App Name:** GenTrack  
**Package:** `com.gentrack`  
**Description:** Generator Subscription Management Admin System — a single-admin Android app for managing generator customers, billing cycles, payment tracking, and operational announcements.  
**Min SDK:** 26 | **Compile SDK:** 36 | **Java:** 11  
**Build System:** Gradle (Groovy DSL)

This is NOT a notes app, NOT a chat system, NOT a demo CRUD app.  
It is a domain-specific admin tool with real billing logic, payment tracking, and multi-technology integration.

---

## 1. Architecture Rules (HARD — never violate)

### Layer Responsibilities
- **Activities** — handle UI events, navigate between screens, observe results. Zero calculation logic.
- **Service classes** — own all business logic (billing math, payment deduction, status calculation). Pure Java, no Android UI imports.
- **DatabaseHandler** — owns every SQL statement. No raw queries outside this class. Ever.
- **VolleyService** — owns the singleton RequestQueue and all HTTP methods. No Volley calls in Activities.
- **Utils** — Constants, SessionManager, DateUtils, PhoneUtils, PdfGenerator. Stateless helpers.

### Rules
- All string constants (table names, column names, status values, API URLs, SharedPreferences keys) live in `Constants.java`. No magic strings anywhere else.
- Every DatabaseHandler query method accepts `ownerUid` as a parameter and appends `WHERE owner_uid = ?`.
- BillingService and PaymentService are singletons accessed via `getInstance(Context)`.
- No anonymous inner classes for business logic — only for UI callbacks (onClick, dialog buttons).
- No MVVM, no ViewModel, no LiveData, no Repository pattern. Activities talk to Services directly.
- `startActivityForResult` is banned — use `ActivityResultLauncher` everywhere image/file picking is needed.
- All errors shown to user via `AlertDialog` with human-readable messages. Never `.toString()` of an exception.
- `ProgressBar` visibility is toggled manually around every async operation.

---

## 2. Package Structure

```
com.gentrack/
  activities/
    LoginActivity.java
    DashboardActivity.java
    CustomerListActivity.java
    CustomerDetailActivity.java
    BillListActivity.java
    BillDetailActivity.java
    AnnouncementsActivity.java
    BaseActivity.java
  adapters/
    CustomerAdapter.java
    BillAdapter.java
    PaymentAdapter.java
    AnnouncementAdapter.java
  models/
    Customer.java
    Bill.java
    Payment.java
    Announcement.java
    MonthlyReport.java
    RemoteConfig.java
  services/
    BillingService.java
    PaymentService.java
  db/
    DatabaseHandler.java
  network/
    VolleyService.java
    ApiCallback.java
  utils/
    Constants.java
    SessionManager.java
    DateUtils.java
    PhoneUtils.java
    PdfGenerator.java
    NotificationHelper.java
  workers/
    UnpaidBillWorker.java
```

---

## 3. SQLite Schema

### Table: customers
```sql
CREATE TABLE customers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_uid TEXT NOT NULL,
  name TEXT NOT NULL,
  phone TEXT,
  location TEXT,
  amps INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'Active',
  notes TEXT,
  image_url TEXT,
  created_at TEXT NOT NULL
)
```

### Table: bills
```sql
CREATE TABLE bills (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_uid TEXT NOT NULL,
  customer_id INTEGER NOT NULL,
  month TEXT NOT NULL,
  amps INTEGER NOT NULL,
  price_per_amp REAL NOT NULL,
  total REAL NOT NULL,
  previous_balance REAL NOT NULL DEFAULT 0,
  final_total REAL NOT NULL,
  status TEXT NOT NULL DEFAULT 'Unpaid',
  created_at TEXT NOT NULL,
  FOREIGN KEY (customer_id) REFERENCES customers(id)
)
```

### Table: payments
```sql
CREATE TABLE payments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_uid TEXT NOT NULL,
  bill_id INTEGER NOT NULL,
  amount_paid REAL NOT NULL,
  date TEXT NOT NULL,
  remaining_balance REAL NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (bill_id) REFERENCES bills(id)
)
```

### Table: sync_queue
```sql
CREATE TABLE sync_queue (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  table_name TEXT NOT NULL,
  operation TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL
)
```

### Rules
- `status` values for customers: `'Active'`, `'Unpaid'`, `'Disconnected'` — these are the only valid values. Store as-is.
- `status` values for bills: `'Paid'`, `'Partial'`, `'Unpaid'`
- `month` stored as `"YYYY-MM"` string (e.g. `"2025-05"`)
- All dates stored as `"YYYY-MM-DD HH:mm:ss"` via `DateUtils.now()`
- `onUpgrade` implemented with `ALTER TABLE` — NOT drop and recreate
- Customer deletion: BLOCK if any bills exist for that customer. Show AlertDialog: "Cannot delete customer with existing bills." Do not cascade.

---

## 4. Models (POJOs)

All models have:
- Private fields with getters and setters
- A no-arg constructor (required for Firestore deserialization)
- A full-arg constructor
- `toString()` override

`Customer` fields: `id, ownerUid, name, phone, location, amps, status, notes, imageUrl, createdAt`  
`Bill` fields: `id, ownerUid, customerId, month, amps, pricePerAmp, total, previousBalance, finalTotal, status, createdAt`  
`Payment` fields: `id, ownerUid, billId, amountPaid, date, remainingBalance, createdAt`  
`Announcement` fields: `id (Firestore doc id), uid, title, message, createdAt`  
`MonthlyReport` fields: `month, totalCustomersBilled, totalExpectedRevenue, ownerUid`  
`RemoteConfig` fields: `defaultPricePerAmp, generatorCapacity, ownerUid`

---

## 5. Business Logic Rules

### BillingService.generateBill(Customer customer, String month, double pricePerAmp)
1. Query DatabaseHandler for sum of `remaining_balance` across all Unpaid + Partial bills for this customer → `previousBalance`
2. `total = customer.getAmps() × pricePerAmp`
3. `finalTotal = total + previousBalance`
4. Return a new `Bill` object — do NOT insert. Caller inserts.
5. After insert, call `recalculateCustomerStatus(customer.getId())`

### BillingService.recalculateCustomerStatus(int customerId)
1. Query all bill statuses for this customer
2. If customer status is `'Disconnected'` → return immediately, do not change
3. If any bill status is `'Unpaid'` or `'Partial'` → set customer status to `'Unpaid'`
4. If all bills are `'Paid'` OR no bills exist → set customer status to `'Active'`
5. Update customer record in SQLite
6. Fire secondary PUT to online API (fire and forget)

### PaymentService.recordPayment(int billId, double amountPaid)
1. Fetch current bill from DatabaseHandler → get `remainingBalance`
2. `newRemaining = currentRemaining - amountPaid`
3. If `newRemaining <= 0` → `newRemaining = 0`, bill status → `'Paid'`
4. Else → bill status → `'Partial'`
5. Insert payment row into SQLite
6. Update bill row in SQLite
7. Call `BillingService.recalculateCustomerStatus(bill.getCustomerId())`
8. Fire secondary POSTs to online API for payment + bill update

### Batch Bill Generation
1. Query all customers where status = 'Active' for this owner
2. Open SQLite transaction: `db.beginTransaction()`
3. Loop: call `BillingService.generateBill()` for each, insert result
4. `db.setTransactionSuccessful()`
5. `db.endTransaction()` in finally block
6. After transaction success, loop again to fire Volley secondary writes (outside transaction)

### Customer Status — Disconnected Rule
`Disconnected` is set manually only via CustomerDetailActivity edit dialog.  
It is NEVER set automatically.  
When status is `Disconnected`, `recalculateCustomerStatus` returns immediately without modifying it.

---

## 6. Firebase Authentication

- `FirebaseAuth.getInstance()` — email + password sign-in only
- `LoginActivity` is the only Activity outside the auth gate
- `BaseActivity.onResume()` checks `FirebaseAuth.getInstance().getCurrentUser()` — if null, start LoginActivity and finish()
- On successful login, store UID in `SessionManager` (SharedPreferences wrapper)
- `SessionManager.getUid()` used by every DatabaseHandler call and every Volley request
- Logout in BaseActivity menu: `FirebaseAuth.signOut()` → clear SessionManager → start LoginActivity → finish()
- No RegisterActivity in the app — account created once via Firebase Console

### SessionManager
```java
// Wraps SharedPreferences
void saveSession(String uid, String email)
String getUid()
String getEmail()
void clearSession()
boolean isLoggedIn()
```

---

## 7. Online DB (PHP/MySQL via Volley)

### API Endpoints
```
POST   /customers/create.php
POST   /customers/update.php
POST   /customers/delete.php
POST   /bills/create.php
POST   /bills/update.php
POST   /payments/create.php
GET    /config/rates.php?uid=
POST   /reports/save.php
GET    /reports/monthly.php?uid=&limit=3
```

All endpoints receive/return JSON. All POST params include `owner_uid`.

### VolleyService (Singleton)
```java
VolleyService.getInstance(context)
void postCustomer(Customer c, ApiCallback cb)
void updateCustomer(Customer c, ApiCallback cb)
void deleteCustomer(int id, String uid, ApiCallback cb)
void postBill(Bill b, ApiCallback cb)
void updateBill(Bill b, ApiCallback cb)
void postPayment(Payment p, ApiCallback cb)
void fetchRemoteConfig(String uid, ApiCallback cb)
void postMonthlyReport(MonthlyReport r, ApiCallback cb)
void fetchMonthlyReports(String uid, ApiCallback cb)
```

### ApiCallback Interface
```java
interface ApiCallback {
    void onSuccess(JSONObject response);
    void onError(String message);
}
```

### Sync Queue Logic
- If any Volley call fails → insert into `sync_queue` table with `table_name`, `operation`, `payload_json`
- On `DashboardActivity.onCreate()` → call `VolleyService.retrySyncQueue()` which reads all pending rows and retries each
- On retry success → delete that sync_queue row
- On retry failure → leave it (will retry next launch)

### Remote Config Flow
- `DashboardActivity.onCreate()` → fetch `/config/rates.php?uid=`
- On success → store `defaultPricePerAmp` in SharedPreferences via `SessionManager`
- When bill generation dialog opens → pre-fill price field with `SessionManager.getDefaultPricePerAmp()`

### Monthly Reports Flow
- After batch bill generation → POST to `/reports/save.php` with month summary
- `DashboardActivity.onCreate()` → GET `/reports/monthly.php?uid=&limit=3`
- On success → feed into bar chart

---

## 8. Firestore (Announcements)

Collection: `announcements`  
Document fields: `uid (String), title (String), message (String), createdAt (Timestamp)`

Query: `.collection("announcements").whereEqualTo("uid", currentUid).orderBy("createdAt", DESCENDING).limit(10)`

- Dashboard shows latest 1 announcement as a banner card (title + truncated message)
- AnnouncementsActivity shows full list in RecyclerView
- AnnouncementsActivity: compose FAB opens a dialog with title + message fields → `.collection("announcements").add(map)`
- Optional snapshot listener: store `ListenerRegistration` field, remove in `onDestroy()`
- If Firestore fails → show AlertDialog "Could not load announcements" — do not crash

---

## 9. MPAndroidChart

Dependency: `com.github.PhilJay:MPAndroidChart:v3.1.0`  
Add JitPack to repositories.

### Bar Chart (Monthly Revenue) — DashboardActivity
- Data source: online DB monthly reports (last 3 months)
- `BarEntry(monthIndex, totalExpectedRevenue)`
- Color: accent color from theme
- ValueFormatter: prefix `$` on bar values
- No legend, minimal description

### Pie Chart (Customer Status) — DashboardActivity
- Data source: SQLite aggregate queries
- Three entries: Active (green), Unpaid (orange), Disconnected (red) — matching status chip colors
- Show percentage labels
- Hole radius 40% (donut style)

---

## 10. WorkManager (Unpaid Reminders)

Dependency: `androidx.work:work-runtime:2.9.0`

### UnpaidBillWorker extends Worker
- Query DatabaseHandler: bills where status = 'Unpaid' and `created_at` older than 30 days
- Group by customer
- Fire one local notification per customer with overdue count
- Return `Result.success()`

### Scheduling (in DashboardActivity.onCreate — once)
```java
PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
    UnpaidBillWorker.class, 1, TimeUnit.DAYS)
    .build();
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "unpaid_reminder", ExistingPeriodicWorkPolicy.KEEP, work);
```

### NotificationHelper
- Create channel `gentrack_reminders` on first launch (required Android 26+)
- Channel name: "Payment Reminders"
- Importance: `IMPORTANCE_DEFAULT`
- Request `POST_NOTIFICATIONS` permission at runtime (Android 13+)

---

## 11. PDF Generation (iText7)

Dependency: `com.itextpdf:itext7-core:7.2.5` (Android compatible build)

### PdfGenerator.generateBillPdf(Context, Bill, Customer, List<Payment>)
- Creates a PDF with: GenTrack header, customer info block, bill breakdown table (amps, rate, total, previous balance, final total), payment history table, remaining balance, status
- Saves to `context.getExternalFilesDir(null)/bills/bill_[id]_[month].pdf`
- Returns the `File` object

### Share Flow (in BillDetailActivity)
- Use `FileProvider` to get a shareable URI
- `Intent.ACTION_SEND` with MIME `application/pdf`
- FileProvider authority: `com.gentrack.fileprovider`
- Define `file_paths.xml` in `res/xml/`

---

## 12. Quick Contact (in CustomerDetailActivity)

```java
// SMS
Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone));
// WhatsApp
Intent wa = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + PhoneUtils.toInternational(phone)));
```

`PhoneUtils.toInternational(String phone)` — strips spaces, dashes, parentheses, prepends country code if missing.  
Wrap both in try/catch `ActivityNotFoundException` → show Toast "App not installed."

---

## 13. Theme & Color System

### colors.xml
```xml
<!-- Primary -->
<color name="colorPrimary">#1A1A2E</color>         <!-- Deep navy -->
<color name="colorPrimaryVariant">#16213E</color>   <!-- Darker navy -->
<color name="colorOnPrimary">#FFFFFF</color>

<!-- Accent -->
<color name="colorAccent">#E94560</color>           <!-- Vivid red-pink -->
<color name="colorAccentLight">#FF6B6B</color>

<!-- Surface -->
<color name="colorSurface">#0F3460</color>          <!-- Mid navy -->
<color name="colorBackground">#F8F9FA</color>       <!-- Off-white background -->
<color name="colorCardBackground">#FFFFFF</color>

<!-- Status Colors -->
<color name="statusActive">#2ECC71</color>          <!-- Green -->
<color name="statusUnpaid">#F39C12</color>          <!-- Orange -->
<color name="statusDisconnected">#E74C3C</color>    <!-- Red -->
<color name="statusPaid">#2ECC71</color>
<color name="statusPartial">#F39C12</color>

<!-- Text -->
<color name="textPrimary">#1A1A2E</color>
<color name="textSecondary">#6C757D</color>
<color name="textOnDark">#FFFFFF</color>
<color name="textHint">#ADB5BD</color>

<!-- Utility -->
<color name="divider">#E9ECEF</color>
<color name="ripple">#1A1A2E26</color>
```

### themes.xml (AppTheme)
```xml
<style name="AppTheme" parent="Theme.MaterialComponents.Light.NoActionBar">
    <item name="colorPrimary">@color/colorPrimary</item>
    <item name="colorPrimaryVariant">@color/colorPrimaryVariant</item>
    <item name="colorOnPrimary">@color/colorOnPrimary</item>
    <item name="colorSecondary">@color/colorAccent</item>
    <item name="colorSurface">@color/colorCardBackground</item>
    <item name="android:windowBackground">@color/colorBackground</item>
    <item name="android:fontFamily">@font/poppins</item>
</style>
```

### Custom Toolbar Style
```xml
<style name="AppToolbar" parent="Widget.AppCompat.Toolbar">
    <item name="android:background">@color/colorPrimary</item>
    <item name="titleTextColor">@color/textOnDark</item>
    <item name="subtitleTextColor">@color/textOnDark</item>
</style>
```

### Typography
- Font family: **Poppins** (Google Fonts — add via `res/font/`)
- Weights used: Regular (400), Medium (500), SemiBold (600), Bold (700)
- All `TextInputLayout` use `OutlinedBox` style
- All primary buttons use `Widget.MaterialComponents.Button` with accent background
- All secondary buttons use `Widget.MaterialComponents.Button.OutlinedButton`

---

## 14. UI Components Specification

### Status Chips (used in every list row and detail screen)
```xml
<!-- chip_status.xml — reusable layout -->
<TextView
  android:id="@+id/chipStatus"
  style="@style/StatusChip" />
```
```xml
<style name="StatusChip">
  <item name="android:paddingStart">12dp</item>
  <item name="android:paddingEnd">12dp</item>
  <item name="android:paddingTop">4dp</item>
  <item name="android:paddingBottom">4dp</item>
  <item name="android:textSize">11sp</item>
  <item name="android:textColor">@color/textOnDark</item>
  <item name="android:textStyle">bold</item>
  <item name="android:gravity">center</item>
  <!-- background set programmatically per status -->
</style>
```
In Java — `StatusHelper.applyStatusChip(TextView chip, String status)` in utils:
- Sets background to a rounded drawable
- Sets text color white
- Sets background color per status constant

### Cards
All list items and detail sections use `MaterialCardView`:
```xml
<com.google.android.material.card.MaterialCardView
  app:cardCornerRadius="12dp"
  app:cardElevation="2dp"
  app:cardBackgroundColor="@color/colorCardBackground"
  app:strokeWidth="0dp"
  android:layout_margin="8dp" />
```

### FAB
All list screens have a FAB for primary action (Add):
```xml
<com.google.android.material.floatingactionbutton.FloatingActionButton
  app:backgroundTint="@color/colorAccent"
  app:tint="@color/textOnDark"
  app:fabSize="normal" />
```

### Custom Dialogs
Every Add/Edit/Confirm dialog uses a custom layout inflated into `AlertDialog.Builder.setView()`.  
No default AlertDialog message strings for forms — always a custom XML layout.

Dialog layout rules:
- Root: `LinearLayout` with `padding="24dp"`, `orientation="vertical"`
- Title: `TextView` with `textSize="18sp"`, `textStyle="bold"`, `textColor="@color/textPrimary"`
- Fields: `TextInputLayout` (OutlinedBox) wrapping `TextInputEditText`
- Button row: horizontal `LinearLayout`, gravity end
  - Cancel: `TextButton` style
  - Confirm: `MaterialButton` with accent background
- Corner radius on dialog window: set via `dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)`

### RecyclerView Item Layouts
Every list item:
- `MaterialCardView` as root
- Left: Icon or status indicator strip (4dp wide colored bar on the left edge)
- Center: Name (bold, 15sp) + subtitle (secondary color, 13sp)
- Right: Status chip + optional chevron icon

### Bottom Navigation / Menu
`BaseActivity` inflates a `Toolbar` (not ActionBar) and sets as `setSupportActionBar()`.  
Menu items defined in `res/menu/menu_main.xml`:
- Announcements (bell icon)
- Logout (logout icon)

`CustomerDetailActivity` menu also has:
- Edit (pencil icon)
- Delete (trash icon)
- Call/SMS (phone icon)
- WhatsApp (message icon)

All icons sourced from Material Icons via `res/drawable/` as Vector Assets.

### Search Bar (CustomerListActivity)
Use `SearchView` in the toolbar menu, not a separate EditText.  
`app:iconifiedByDefault="false"` so it stays expanded.

### TextInputLayout Style
```xml
<com.google.android.material.textfield.TextInputLayout
  style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
  app:boxStrokeColor="@color/colorAccent"
  app:hintTextColor="@color/colorAccent"
  app:boxCornerRadiusTopStart="8dp"
  app:boxCornerRadiusTopEnd="8dp"
  app:boxCornerRadiusBottomStart="8dp"
  app:boxCornerRadiusBottomEnd="8dp" />
```

---

## 15. Screen-by-Screen UI Specification

### LoginActivity
- Full screen, no toolbar
- Background: `colorPrimary` (dark navy) — dark theme for this screen only
- Top: App logo (vector drawable of a lightning bolt or generator icon) centered, large
- App name "GenTrack" in Poppins Bold, white, 28sp
- Subtitle "Generator Management" in Poppins Regular, white 60% opacity, 14sp
- Card in center (white, rounded 16dp) containing:
  - Email TextInputLayout (OutlinedBox, white version)
  - Password TextInputLayout with password toggle
  - Login MaterialButton (accent color, full width, rounded 8dp)
- Error shown as `Snackbar` (not dialog) on this screen only
- ProgressBar centered below the card during login call

### DashboardActivity
- Toolbar: "GenTrack" title, dark background, bell icon + logout icon
- Content scrollable via `NestedScrollView`
- Section 1 — Stat Cards Row (horizontal, 3 cards):
  - Active Customers (green accent)
  - Unpaid Customers (orange accent)
  - Total Outstanding ($) (red accent)
  - Each card: icon on left, number bold large, label below
- Section 2 — Bar Chart card: "Monthly Revenue" title, chart below
- Section 3 — Pie Chart card: "Customer Status" title, donut chart
- Section 4 — Latest Announcement banner card (accent border left, title + truncated message + "View All" link)
- Section 5 — Quick Actions: two MaterialButtons side by side: "Customer List" and "View Bills"

### CustomerListActivity
- Toolbar: "Customers" title, search icon
- SearchView expands in toolbar
- RecyclerView below with `DividerItemDecoration` removed (cards handle separation)
- Each item: colored left border (status color), customer name, phone, location, amp subscription, status chip
- FAB bottom-right: plus icon, accent color
- Empty state: centered illustration (vector) + "No customers yet" text + "Add your first customer" button

### CustomerDetailActivity
- Toolbar: customer name as title, edit + delete + contact icons in menu
- Top section — customer info card:
  - Large initial avatar (circle, accent color background, white letter) on left
  - Name (bold 18sp), phone, location, amps + "A" unit label
  - Status chip (large version)
  - Notes if present (italic, secondary color)
- Divider with label "Bills"
- RecyclerView of bills: month, final total, status chip, chevron
- Bottom: two MaterialButtons row — "Generate Bill" (filled) and "Batch Generate" (outlined)

### Bill Generation Dialog
- Month: spinner or DatePicker for month/year only
- Price per amp: pre-filled from remote config default, editable
- Preview section (calculated live as user types): shows Total, Previous Balance, Final Total in a summary card within the dialog
- Confirm button labeled "Generate"

### BillListActivity (bills for one customer)
- Toolbar: "Bills — [Customer Name]"
- Filter chip row: All / Unpaid / Partial / Paid (horizontal scrollable)
- RecyclerView: bill month, amps, final total, status chip, payment progress bar (paid amount vs total)
- Tapping navigates to BillDetailActivity

### BillDetailActivity
- Toolbar: "Bill — [Month]" title, share icon (PDF)
- Bill summary card:
  - Month, amp value, price per amp
  - Subtotals: Total, Previous Balance, Final Total (each on its own row, right-aligned amounts)
  - Status chip large
- Payment History section: RecyclerView of payments (date, amount, remaining balance after)
- Remaining balance displayed prominently: large number, color-coded
- "Add Payment" FAB

### Payment Dialog
- Amount field (TextInputLayout, numeric)
- Date field (auto-filled today, tappable to open DatePickerDialog)
- Preview: "Remaining after payment: $X.XX" calculated live
- Confirm button

### AnnouncementsActivity
- Toolbar: "Announcements"
- RecyclerView: each item shows title (bold), message, timestamp (relative: "2 hours ago")
- FAB: compose icon, accent color → opens compose dialog
- Compose dialog: title field + message field + Post button

---

## 16. Drawables & Resources

### Required Vector Drawables (create as Vector Assets)
```
ic_generator.xml         — lightning bolt / generator (app logo)
ic_customers.xml         — person group icon
ic_bills.xml             — receipt/document icon
ic_payments.xml          — credit card icon
ic_announcements.xml     — bell icon
ic_dashboard.xml         — grid/home icon
ic_logout.xml            — exit door icon
ic_edit.xml              — pencil icon
ic_delete.xml            — trash icon
ic_phone.xml             — phone icon
ic_whatsapp.xml          — message/chat icon
ic_share.xml             — share icon
ic_add.xml               — plus icon
ic_search.xml            — search icon
ic_arrow_right.xml       — chevron right
ic_empty.xml             — empty state illustration (simple vector)
ic_active.xml            — checkmark circle
ic_warning.xml           — warning triangle
```

### Required Shape Drawables
```
bg_dialog_rounded.xml    — white background, cornerRadius 16dp
bg_status_active.xml     — rounded rect, fill statusActive
bg_status_unpaid.xml     — rounded rect, fill statusUnpaid
bg_status_disconnected.xml — rounded rect, fill statusDisconnected
bg_status_paid.xml       — rounded rect, fill statusPaid
bg_status_partial.xml    — rounded rect, fill statusPartial
bg_card_accent_left.xml  — white card with 4dp left border (parameterized by color)
bg_avatar_circle.xml     — circle, fill colorAccent
bg_button_primary.xml    — rounded rect 8dp, fill colorAccent
bg_chart_card.xml        — white rounded rect 12dp with subtle shadow
```

### Dimensions (dimens.xml)
```xml
<dimen name="card_corner_radius">12dp</dimen>
<dimen name="card_elevation">2dp</dimen>
<dimen name="card_margin">8dp</dimen>
<dimen name="dialog_padding">24dp</dimen>
<dimen name="section_padding">16dp</dimen>
<dimen name="text_title">18sp</dimen>
<dimen name="text_body">14sp</dimen>
<dimen name="text_caption">12sp</dimen>
<dimen name="text_large">24sp</dimen>
<dimen name="fab_margin">16dp</dimen>
```

---

## 17. Dependencies (build.gradle)

```groovy
dependencies {
    // AndroidX
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.cardview:cardview:1.0.0'
    implementation 'androidx.core:core:1.13.1'
    implementation 'androidx.work:work-runtime:2.9.0'

    // Material
    implementation 'com.google.android.material:material:1.12.0'

    // Firebase BOM
    implementation platform('com.google.firebase:firebase-bom:34.12.0')
    implementation 'com.google.firebase:firebase-auth'
    implementation 'com.google.firebase:firebase-firestore'

    // Volley
    implementation 'com.android.volley:volley:1.2.1'

    // Glide
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'

    // MPAndroidChart
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // iText7 PDF
    implementation 'com.itextpdf:itext7-core:7.2.5'

    // Google Fonts (Poppins via downloadable font or bundled)
}
```

JitPack in settings.gradle:
```groovy
dependsOn { repositories { maven { url 'https://jitpack.io' } } }
```

---

## 18. AndroidManifest Requirements

```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- FileProvider for PDF sharing -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="com.gentrack.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

`res/xml/file_paths.xml`:
```xml
<paths>
    <external-files-path name="bills" path="bills/" />
</paths>
```

Launcher: `DashboardActivity`

---

## 19. BaseActivity

```java
public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void onResume() {
        super.onResume();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    protected void setupToolbar(String title) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    protected void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    protected void showConfirm(String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage(message)
            .setPositiveButton("Yes", (d, w) -> onConfirm.run())
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            showConfirm("Are you sure you want to logout?", () -> {
                FirebaseAuth.getInstance().signOut();
                SessionManager.getInstance(this).clearSession();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return true;
        }
        if (item.getItemId() == R.id.action_announcements) {
            startActivity(new Intent(this, AnnouncementsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
```

---

## 20. Build Phases (Execution Order)

### Phase 1 — Foundation (no UI yet)
- Project setup with all dependencies, google-services.json
- `res/values/colors.xml`, `themes.xml`, `dimens.xml`, `strings.xml`
- Poppins font setup in `res/font/`
- All vector drawable assets
- All shape drawable backgrounds
- `Constants.java` with every string constant
- All model classes (Customer, Bill, Payment, Announcement, MonthlyReport, RemoteConfig)
- `DatabaseHandler.java` — all 4 tables, all CRUD methods, all aggregate queries
- `SessionManager.java`
- `DateUtils.java`, `PhoneUtils.java`
- `ApiCallback.java` interface
- `VolleyService.java` — singleton, all endpoint methods, sync queue retry
- `BillingService.java` — full logic
- `PaymentService.java` — full logic
- `NotificationHelper.java` — channel creation
- `UnpaidBillWorker.java`

**Verify:** DatabaseHandler compiles, all services instantiable, no Android UI in service layer.

### Phase 2 — Auth Screen
- `LoginActivity.java` + `activity_login.xml`
- Dark theme applied to this screen
- Firebase Auth wired
- SessionManager save on success
- Redirect to DashboardActivity on success

**Verify:** Login works end-to-end with Firebase.

### Phase 3 — Customer Module
- `BaseActivity.java` with toolbar, menu, auth guard
- `CustomerListActivity.java` + `activity_customer_list.xml`
- `CustomerAdapter.java` + `item_customer.xml`
- Add/Edit customer dialogs with custom layouts
- Delete with block logic
- Status chip display + StatusHelper utility
- SearchView filter via Filterable
- Volley secondary writes on add/edit/delete

**Verify:** Full customer CRUD working, status chips visible, search filters list.

### Phase 4 — Billing Module
- `CustomerDetailActivity.java` + `activity_customer_detail.xml`
- Bill list embedded in CustomerDetailActivity
- `BillListActivity.java` + `activity_bill_list.xml` + filter chips
- `BillAdapter.java` + `item_bill.xml`
- Generate bill dialog + batch generate
- `BillDetailActivity.java` + `activity_bill_detail.xml`
- Payment list in BillDetailActivity
- `PaymentAdapter.java` + `item_payment.xml`

**Verify:** Bill generation calculates correctly, previous balance rolls forward, status chips update.

### Phase 5 — Payment Module
- Add payment dialog in BillDetailActivity
- PaymentService wired fully
- Customer status auto-recalculation confirmed working
- Balance display updates after payment

**Verify:** Record partial payment → bill status = Partial → customer status = Unpaid. Record full payment → bill status = Paid → if all bills paid, customer = Active.

### Phase 6 — Dashboard
- `DashboardActivity.java` + `activity_dashboard.xml`
- SQLite aggregate queries wired to stat cards
- Remote config fetch on launch → stored in SharedPreferences
- Monthly reports GET from online DB → bar chart
- Latest announcement banner
- WorkManager scheduled

**Verify:** Dashboard shows live data, charts render, remote config pre-fills bill dialog.

### Phase 7 — Firebase Announcements
- `AnnouncementsActivity.java` + `activity_announcements.xml`
- `AnnouncementAdapter.java` + `item_announcement.xml`
- Compose dialog → Firestore write
- List loads from Firestore
- Dashboard banner wired

**Verify:** Write announcement in app → appears in list and on dashboard banner.

### Phase 8 — PDF + Share + Contact
- `PdfGenerator.java` implemented
- FileProvider configured in manifest
- Share PDF intent in BillDetailActivity
- SMS intent in CustomerDetailActivity
- WhatsApp intent in CustomerDetailActivity

**Verify:** PDF generates and opens when shared, SMS and WhatsApp intents launch correctly.

---

## 21. What Claude Code Must NOT Do

- Do not introduce ViewModel, LiveData, or Repository patterns
- Do not use Kotlin — Java only
- Do not use Retrofit — Volley only
- Do not use Room — SQLiteOpenHelper only
- Do not use Data Binding or View Binding (except if already present in the project template)
- Do not use Navigation Component
- Do not add dependencies not listed in Section 17 without explicit confirmation
- Do not put business logic in Activities
- Do not put SQL in Activities or Services
- Do not use hardcoded strings outside Constants.java
- Do not use `startActivityForResult` — use `ActivityResultLauncher`
- Do not show raw exception messages to the user
- Do not create separate Activities for add/edit forms — use dialogs

---

## 22. API Base URL

```java
// Constants.java
public static final String API_BASE_URL = "https://[YOUR_AWARDSPACE_DOMAIN]/gentrack/api";
```

Replace `[YOUR_AWARDSPACE_DOMAIN]` with the actual domain before Phase 1 is complete.

---

## 23. Quick Reference — Technology to Data Mapping

| Data / Feature | Technology | Direction |
|---|---|---|
| Customers | SQLite (primary) + PHP/MySQL (secondary write) | R/W local, W remote |
| Bills | SQLite (primary) + PHP/MySQL (secondary write) | R/W local, W remote |
| Payments | SQLite (primary) + PHP/MySQL (secondary write) | R/W local, W remote |
| Monthly reports | PHP/MySQL | R/W remote |
| Remote config (price defaults) | PHP/MySQL | R remote |
| Authentication | Firebase Auth | — |
| Announcements | Firestore | R/W remote |
| Dashboard charts | SQLite (pie) + PHP/MySQL (bar) | R |
| PDF generation | Local filesystem | Write local, share |
| Unpaid reminders | WorkManager + local notifications | Background |
| SMS / WhatsApp | Android Intent | No data |
| Sync retry | SQLite sync_queue | R/W local |

---

*End of specification. Begin with Phase 1. Do not skip phases. Do not begin Phase 2 until Phase 1 compiles cleanly.*
