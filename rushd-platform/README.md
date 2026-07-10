# رُشد | Rushd

رُشد منصة ويب تساعد المستخدم على تحليل القرار العقاري قبل الشراء أو الاستثمار.

## المشكلة

Many buyers do not know if a land/property price is fair.

يساعد رُشد المستخدم على قراءة الأرقام بشكل أوضح، مثل سعر المتر، المقارنة مع متوسط السوق، ونقاط القوة والمخاطر قبل اتخاذ القرار.

## الخصائص الرئيسية

- User authentication
- Roles: Admin, Seller, Buyer
- Add property
- Browse properties
- Property details page
- Calculate price per meter
- Compare with market average entered manually
- Rushd Score from 100
- Strengths and risks
- Compare properties
- Favorites
- Contact seller

## Tech Stack

- Spring Boot
- PostgreSQL
- React
- Docker Compose
- REST API

## التوثيق

- [معمارية المشروع](docs/architecture.md) — مخطط التدفق، الطبقات، نقاط النهاية، وخطوات التشغيل

## Docker و PostgreSQL

لتشغيل قاعدة البيانات محلياً باستخدام Docker Compose:

```bash
docker compose up -d
```

لإيقاف الخدمات:

```bash
docker compose down
```

لعرض الحاويات التي تعمل حالياً:

```bash
docker ps
```

لعرض سجلات PostgreSQL:

```bash
docker logs rushd_postgres
```

## تشغيل Backend

لتشغيل Spring Boot backend:

```bash
cd backend
mvn spring-boot:run
```

ملاحظة: لم يتم إنشاء Maven Wrapper في هذه المرحلة، لذلك نستخدم `mvn spring-boot:run`.

لاختبار Health endpoint:

```bash
curl http://localhost:8080/health
```

## تشغيل Frontend

لتشغيل React frontend:

```bash
cd frontend
npm install
npm run dev
```

يعمل Frontend على: `http://localhost:5173`

الصفحات المتاحة:

| الصفحة | الرابط |
|--------|--------|
| الرئيسية | http://localhost:5173/ |
| تسجيل الدخول | http://localhost:5173/login |
| العقارات | http://localhost:5173/properties |
| لوحة التحكم | http://localhost:5173/dashboard |

## تشغيل المشروع كاملاً (Full Stack)

لتشغيل المشروع بالكامل محلياً:

**1. تشغيل PostgreSQL:**
```bash
docker compose up -d
```

**2. تشغيل Backend (Spring Boot):**
```bash
cd backend
mvn spring-boot:run
```

**3. تشغيل Frontend (React):**
```bash
cd frontend
npm run dev
```

**4. فتح التطبيق:**
```
http://localhost:5173
```

**5. التحقق من الاتصال:**

الصفحة الرئيسية ستعرض: `✅ Rushd API is running`

إذا لم يعمل Backend، ستظهر رسالة: `⚠️ تعذّر الاتصال بالخادم`

## أخطاء شائعة وحلولها

| المشكلة | السبب | الحل |
|---------|-------|------|
| `CORS error` في المتصفح | Backend لا يسمح لـ localhost:5173 | تأكد أن `SecurityConfig.java` يحتوي على CORS |
| `Connection refused` | Backend لا يعمل | شغّل `mvn spring-boot:run` في مجلد `backend/` |
| `Database connection error` | PostgreSQL لا يعمل | شغّل `docker compose up -d` أولاً |
| الصفحة تعرض رسالة الخطأ العربية | Backend متوقف | ابدأ Spring Boot وأعد تحميل الصفحة |

## تنبيه مهم

تحليل رُشد هو أداة تقديرية لدعم القرار فقط، ولا يعتبر نصيحة مالية أو قانونية أو عقارية.

## حالة المشروع

المشروع يحتوي على:
- PostgreSQL عبر Docker
- Spring Boot backend مع `/health` endpoint
- React frontend مع اتصال حي بالـ Backend

سيتم إضافة المصادقة والخصائص الأساسية في المراحل القادمة.
