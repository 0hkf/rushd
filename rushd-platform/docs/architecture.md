# معمارية مشروع رُشد

## نظرة عامة

رُشد منصة ويب مكوّنة من ثلاث طبقات رئيسية:

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

- **التقنية:** React 18 + Vite
- **المنفذ:** `http://localhost:5173`
- **مكتبة HTTP:** Axios — ملف الإعداد: `frontend/src/services/api.js`
- **التوجيه:** React Router v6

### الصفحات

| الصفحة | المسار |
|--------|--------|
| الرئيسية | `/` |
| تسجيل الدخول | `/login` |
| العقارات | `/properties` |
| لوحة التحكم | `/dashboard` |

---

## طبقة الخادم — Spring Boot

- **التقنية:** Spring Boot 3.3 + Java 17
- **المنفذ:** `http://localhost:8080`
- **الأمان:** Spring Security مع CORS مفعّل لـ `localhost:5173`

### نقاط النهاية الحالية (Endpoints)

| الطريقة | المسار | الوصف |
|---------|--------|-------|
| `GET` | `/health` | فحص حالة الخادم — يعيد `Rushd API is running` |

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
Rushd API is running
```

تعرض الصفحة الرئيسية هذه الاستجابة مباشرةً عند التحميل:
- ✅ الخادم يعمل — يعرض النص المُعاد
- ⚠️ الخادم متوقف — يعرض رسالة خطأ عربية

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
# المتوقع: Rushd API is running
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
