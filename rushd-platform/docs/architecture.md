# معمارية مشروع روافد العقارية

## نظرة عامة

روافد العقارية منصة ويب مكوّنة من ثلاث طبقات رئيسية:

| الطبقة | التقنية | المنفذ |
|--------|---------|--------|
| الواجهة الأمامية | React (Vite) | `localhost:5173` |
| الخادم (Backend) | Spring Boot | `localhost:8080` |
| قاعدة البيانات | PostgreSQL | `localhost:5432` |

---

## مخطط التدفق

```
المستخدم
   │
   ▼
React (localhost:5173)
   │  HTTP REST API
   ▼
Spring Boot (localhost:8080)
   │  JDBC / JPA
   ▼
PostgreSQL (localhost:5432)
```

1. **المستخدم** يفتح المتصفح ويتعامل مع واجهة React.
2. **React** ترسل طلبات HTTP إلى الـ Backend عبر Axios.
3. **Spring Boot** يعالج الطلبات ويطبّق منطق الأعمال.
4. **PostgreSQL** تخزّن البيانات وتردّ على استعلامات JPA.

---

## طبقة الواجهة الأمامية — React

- **التقنية:** React 19 + Vite
- **المنفذ:** `http://localhost:5173`
- **مكتبة HTTP:** Axios — ملف الإعداد: `frontend/src/services/api.js`
- **التوجيه:** React Router v7

### الصفحات

| الصفحة | المسار |
|--------|--------|
| الرئيسية | `/` |
| تسجيل الدخول | `/login` |
| العقارات | `/properties` |
| لوحة التحكم | `/dashboard` |
| مواصفات العقار | `/properties/:id` |

---

## طبقة الخادم — Spring Boot

- **التقنية:** Spring Boot 3.3 + Java 17
- **المنفذ:** `http://localhost:8080`
- **الأمان:** Spring Security مع CORS مفعّل لـ `localhost:5173`

### نقاط النهاية الحالية (Endpoints)

| الطريقة | المسار | الوصف |
|---------|--------|-------|
| `GET` | `/health` | فحص حالة الخادم — يعيد `Rawafed Real Estate API is running` |
| `POST` | `/api/auth/register` | تسجيل مستخدم |
| `POST` | `/api/auth/login` | الدخول وإصدار HttpOnly cookies |
| `GET` | `/api/auth/me` | المستخدم الحالي |
| `GET` | `/api/properties` | قائمة عامة مع مرشحات البيع والإيجار |
| `GET` | `/api/admin/properties` | قائمة جميع حالات النشر للأدمن فقط |
| `GET` | `/api/properties/{id}` | تفاصيل العقار |
| `POST` | `/api/properties` | إنشاء عرض بيع/إيجار بواسطة ADMIN فقط |
| `PUT` | `/api/properties/{id}` | تعديل بواسطة ADMIN فقط |

### النموذج والأمان

الإدخال يدوي بواسطة الأدمن. التسجيل العام BUYER فقط. تبقى SELLER قيمة تراثية دون صلاحية نشر. السعر للإيجار مرتبط بفترة MONTHLY أو YEARLY. لا اتصالات Google أو خرائط أو صور ضمن هذا النطاق.

تفاصيل الحقول وترقية PostgreSQL في [api-properties.md](api-properties.md). إعداد ddl-auto هو none؛ يجب تطبيق الترقية المعتمدة قبل تشغيل التطبيق على قاعدة قديمة.

### إعداد CORS

السماح للـ Frontend بالاتصال بالـ Backend مُعرَّف في:
`backend/src/main/java/com/rushd/config/SecurityConfig.java`

```java
config.setAllowedOrigins(List.of("http://localhost:5173"));
```

---

## طبقة قاعدة البيانات — PostgreSQL

- **التقنية:** PostgreSQL 15
- **المنفذ:** `localhost:5432`
- **الإدارة:** Docker Compose
- **الاتصال:** Spring Data JPA

---

## فحص الصحة (Health Check)

```
GET http://localhost:8080/health
```

**الاستجابة:**
```
Rawafed Real Estate API is running
```

تتحقق الصفحة الرئيسية من الاتصال عند التحميل، وتعرض حالة عربية مختصرة بدل النص التقني: «الخدمة متصلة» أو «الخدمة غير متاحة حاليًا».

تلتزم جميع صفحات الواجهة بـ[نظام التصميم المعتمد](design.md). الألوان والمكونات المشتركة معرفة في `frontend/src/index.css`، وتستخدم الهوية الظاهرة «روافد العقارية».

---

## تشغيل المشروع محلياً

### 1. تشغيل قاعدة البيانات

```bash
docker compose up -d
```

### 2. تشغيل الخادم

```bash
cd backend
mvn spring-boot:run
```

### 3. تشغيل الواجهة الأمامية

```bash
cd frontend
npm install   # عند أول تشغيل فقط
npm run dev
```

### 4. التحقق من الاتصال

```bash
curl http://localhost:8080/health
# المتوقع: Rawafed Real Estate API is running
```

ثم افتح `http://localhost:5173` — الصفحة الرئيسية ستعرض حالة الاتصال تلقائياً.

---

## هيكل الملفات

```
rushd-platform/
├── backend/
│   └── src/main/java/com/rushd/
│       ├── config/
│       │   └── SecurityConfig.java   # CORS + Spring Security
│       └── controller/
│           └── HealthController.java  # GET /health
├── frontend/
│   └── src/
│       ├── services/
│       │   └── api.js                # Axios client
│       ├── pages/
│       │   ├── Home.jsx              # يستدعي /health عند التحميل
│       │   ├── Login.jsx
│       │   ├── Properties.jsx
│       │   └── Dashboard.jsx
│       └── components/
│           └── Navbar.jsx
├── docs/
│   ├── architecture.md               # هذا الملف
│   ├── backlog.md
│   └── mvp-scope.md
└── docker-compose.yml                # PostgreSQL
```
