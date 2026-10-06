# روافد العقارية | Rawafed Real Estate

روافد العقارية منصة ويب لعروض البيع والإيجار، تستهدف مساعدة المستخدم على اختيار عقار مناسب لاحتياجاته وفهم مواصفاته وسعره.

## المشكلة

Many buyers do not know if a land/property price is fair.

تساعد روافد العقارية المستخدم على قراءة الأرقام بشكل أوضح، مثل سعر المتر، المقارنة مع متوسط السوق، ونقاط القوة والمخاطر قبل اتخاذ القرار.

الفكرة المستهدفة الجديدة: يجمع مساعد احتياجات المستخدم ويرشح من العروض المنشورة مع تبرير واضح، أو يشرح ملاءمة عقار يختاره المستخدم. ملاءمة الاحتياجات منفصلة عن تحليل سعر السوق؛ هذه الميزة لم تنفذ بعد.

## الخصائص الرئيسية

- User authentication (public registration: BUYER only)
- Roles: Admin, Buyer (no seller publishing)
- Admin manually adds and updates sale/rental property specifications
- Browse properties
- Property details page
- Authenticated structured property-needs form with backend validation (no matching/AI yet)
- Planned: type-appropriate price indicators (price per m² only for comparable area definitions)
- Planned: compare with market average entered manually
- Planned: personalized property matching with grounded explanations (scoring rules not approved yet)
- Planned: strengths and risks
- Planned: compare properties
- Planned: favorites
- Planned: contact platform administration

## Tech Stack

- Spring Boot
- PostgreSQL
- React
- Docker Compose
- REST API

## التوثيق

- [مسودة المتطلبات الجديدة](docs/requirements.md) — القرارات المؤكدة والأسئلة المفتوحة
- [خطة العمل المبدئية](docs/roadmap.md) — مراحل مرنة، ثلاثة أيام عمل أسبوعيًا، وميزانية صفر

- [التصميم المعتمد](docs/design.md) — هوية روافد العقارية، الألوان، المكونات وقواعد جميع الصفحات
- [نطاق النسخة الأولى](docs/mvp-scope.md)
- [عقد العقارات وترقية قاعدة البيانات](docs/api-properties.md)
- [احتياجات المستخدم وعقد التحقق](docs/api-property-needs.md) — نموذج للمسجلين، دون حفظ دائم أو ترشيح بعد
- [المصادقة والأدوار](docs/api-auth.md)
- [الميزات المؤجلة](docs/backlog.md)
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
| مواصفات العقار | http://localhost:5173/properties/:id |

## تشغيل المشروع كاملاً (Full Stack)

لتشغيل المشروع بالكامل محلياً:

**1. تشغيل PostgreSQL:**
```bash
docker compose up -d
```

**2. ترقية مخطط قاعدة البيانات القديمة وفق [عقد العقارات](docs/api-properties.md)، ثم تشغيل Backend (Spring Boot):**
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

الصفحة الرئيسية تعرض حالة الاتصال بالخدمة بنص عربي مختصر: `الخدمة متصلة`.

إذا لم يعمل Backend، ستظهر رسالة: `الخدمة غير متاحة حاليًا`.

## أخطاء شائعة وحلولها

| المشكلة | السبب | الحل |
|---------|-------|------|
| `CORS error` في المتصفح | Backend لا يسمح لـ localhost:5173 | تأكد أن `SecurityConfig.java` يحتوي على CORS |
| `Connection refused` | Backend لا يعمل | شغّل `mvn spring-boot:run` في مجلد `backend/` |
| `Database connection error` | PostgreSQL لا يعمل | شغّل `docker compose up -d` أولاً |
| الصفحة تعرض رسالة الخطأ العربية | Backend متوقف | ابدأ Spring Boot وأعد تحميل الصفحة |

## تنبيه مهم

تحليل روافد العقارية هو أداة تقديرية لدعم القرار فقط، ولا يعتبر نصيحة مالية أو قانونية أو عقارية.

## حالة المشروع

المشروع يحتوي على:
- PostgreSQL عبر Docker
- Spring Boot backend مع `/health` endpoint
- React frontend مع اتصال حي بالـ Backend

المصادقة وواجهات إنشاء العقارات وعرضها وتعديلها منفذة في الباكند. تدعم بيانات العقار موقعًا منظمًا اختياريًا، دون الاتصال بخدمات Google.

الواجهة بخلفية بيج وهوية عنابية، والدخول والقائمة والمواصفات والإدارة متصلة بالباكند. الأدمن وحده يضيف ويعدل العروض؛ لا يوجد نشر بائع. الإيجار شهري أو سنوي حسب العقار. التحليل والمقارنة والمفضلة ما زالت مخططة وليست منفذة.

المصادقة تعتمد access token لمدة ساعة وrefresh token متدوّر لمدة ثلاثة أيام داخل HttpOnly cookies. يلزم تجهيز JWT_SECRET وHTTPS وCORS وترقية قاعدة البيانات وفق [توثيق المصادقة](docs/api-auth.md).

حساب الأدمن يلزم تهيئته بإجراء موثوق خارج التسجيل العام؛ لم تتم إضافة حساب افتراضي. ملف SQL للترقية مرفق ولم يطبق تلقائيًا على أي قاعدة فعلية.

الاسم الظاهر للمنتج هو «روافد العقارية». تبقى مسارات المشروع وحزم Java ومعرّفات قاعدة البيانات باسم rushd للتوافق التقني.
