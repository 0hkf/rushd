# روافد العقارية | Rawafed Real Estate

روافد العقارية منصة ويب لعروض البيع والإيجار، تستهدف مساعدة المستخدم على اختيار عقار مناسب لاحتياجاته وفهم مواصفاته وسعره.

## المشكلة

Many buyers do not know if a land/property price is fair.

تساعد روافد العقارية المستخدم على قراءة الأرقام بشكل أوضح، مثل سعر المتر، المقارنة مع متوسط السوق، ونقاط القوة والمخاطر قبل اتخاذ القرار.

المنفذ حاليًا: جمع احتياجات المستخدم ومطابقتها مع المواصفات المشتركة للعروض المنشورة، بترتيب تفضيلات وأسباب بقوالب واضحة. تقييم عقار يختاره المستخدم وتحليل سعر السوق والتفسير عبر AI لم تنفذ بعد؛ ملاءمة الاحتياجات منفصلة عن عدالة السعر.

## الخصائص الرئيسية

- User authentication (public registration: BUYER only)
- Roles: Admin, Buyer (no seller publishing)
- Admin manually adds and updates sale/rental property specifications
- Browse Makkah listings with existing API filters, precise prices and URL-backed pagination
- Property details page
- Authenticated structured property-needs form with backend validation (no permanent storage)
- ACTIVE-property matching: required criteria, equally weighted preferences, type-specific specifications, explicit area meanings, factual explanations and pagination (no AI/market valuation)
- Planned: type-appropriate price indicators (price per m² only for comparable area definitions)
- Planned: compare with market average entered manually
- Planned: evaluation of a selected property (advanced suitability classifications not approved yet)
- Planned: strengths and risks
- Planned: compare properties
- Personal favorites with idempotent save/remove, pagination and privacy-safe unavailable placeholders (P13/P14, disabled by default pending approved activation)
- Contact platform administration by phone/WhatsApp for ACTIVE listings (public build-time configuration)
- Admin image upload/ordering/primary selection and public thumbnails/gallery (P10–P12, isolated verification; disabled by default pending approved database/storage activation)

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
- [P04: مواصفات الأنواع والتحقق المعزول](docs/p04-property-specifications-verification.md) — طُبقت الترقية محليًا على db_rushd باعتماد منفصل في21 أكتوبر؛ [سجل التشغيل والدمي داتا](docs/local-development-setup.md).
- [P05: إدخال وعرض مواصفات الأنواع](docs/p05-property-specifications-ui-verification.md) — الفورم والتفاصيل مختبران مع PostgreSQL معزول؛ لا تفعيل تلقائي للقاعدة الفعلية.
- [P06: مطابقة المواصفات حسب النوع](docs/p06-type-matching-verification.md) — معايير اختيارية وأدلة دقيقة، دون حكم سعري أو AI.
- [P07: اتصال وواتساب الإدارة](docs/p07-administration-contact-verification.md) — إعداد عام وقت البناء وACTIVE فقط، دون بيانات حساب الناشر.
- [P08: مرشحات التصفح والصفحات](docs/p08-catalog-filters-verification.md) — نطاق مكة المعتمد، ووحدات سعر واضحة، وحفظ البحث في الرابط؛ دون تغيير المطابقة الشخصية.
- [P09: عقد الصور والتخزين المعتمد](docs/p09-property-images-contract-draft.md) — محلي خاص للتطوير،12صورة و8MiB.
- [P10: backend الصور والتحقق](docs/p10-property-images-verification.md) — [عقد API](docs/api-property-images.md)، معطل افتراضيًا؛ لا تفعيل على قاعدة المشروع.
- [P11/P12: إدارة الصور والمعرض وشرح الأكواد](docs/p11-p12-property-images-ui-verification.md) — رفع ومعاينة وترتيب ورئيسية وحذف، وصور البطاقات ومعرض متجاوب؛ اختبار معزول دون تفعيل فعلي.
- [P13/P14: المفضلة الشخصية وشرح الأكواد](docs/p13-p14-favorites-verification.md) — [عقد API](docs/api-favorites.md)، مع حفظ وإزالة وصفحات وخصوصية المخفي؛ التفعيل معطل افتراضيًا دون ترقية قاعدة المشروع.
- [احتياجات المستخدم وعقد التحقق](docs/api-property-needs.md) — نموذج للمسجلين، دون حفظ دائم
- [قواعد المطابقة ونتيجة التحقق](docs/requirement-02-property-matching.md) — تنفيذ أساسي متحقق على H2 وPostgreSQL معزول
- [تحقق P02 وتشغيل PostgreSQL الاختباري المعزول](docs/p02-postgres-matching-verification.md) — الحزمة الكاملة ونتائج المتصفح وحدود التحقق
- [عقد API للمطابقة](docs/api-property-matching.md) — شروط وتفضيلات بلا AI أو تقييم سوقي
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

حالة التطوير المحلية مرجعها [سجل التهيئة والتفعيل](docs/local-development-setup.md): طُبقت P04 علىdb_rushd باعتماد21أكتوبر ونسخة احتياطية مختبرة؛ ترقيتا الصور P10 والمفضلة P13 لم تطبقا. [تقرير P04](docs/p04-property-specifications-verification.md) سجل تاريخي للاختبار المعزول قبل التفعيل. لا تعِد تنفيذbaseline أوSQL ولا تستخدمddl-auto=update بسبب اختلاف تقرير قديم عن الحالة الحالية.

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

الواجهة بخلفية بيج وهوية عنابية، والدخول والقائمة والمواصفات والإدارة متصلة بالباكند. الأدمن وحده يضيف ويعدل العروض؛ لا يوجد نشر بائع. الإيجار شهري أو سنوي حسب العقار. الصور والمفضلة منفذتان ومختبرتان معزولًا، ومعطلتان افتراضيًا حتى اعتماد ترقية وتفعيل بيئة المشروع. [المقارنة P15](docs/p15-property-comparison-verification.md) منفذة لعقارين أو ثلاثة ولو اختلف النوع، باختيارات مؤقتة ووحدات أصلية ودون تغيير القاعدة؛ التحليل ما زال مخططًا.

المصادقة تعتمد access token لمدة ساعة وrefresh token متدوّر لمدة ثلاثة أيام داخل HttpOnly cookies. يلزم تجهيز JWT_SECRET وHTTPS وCORS وترقية قاعدة البيانات وفق [توثيق المصادقة](docs/api-auth.md).

حساب الأدمن يلزم تهيئته بإجراء موثوق خارج التسجيل العام؛ لم تتم إضافة حساب افتراضي. ملف SQL للترقية مرفق ولم يطبق تلقائيًا على أي قاعدة فعلية.

الاسم الظاهر للمنتج هو «روافد العقارية». تبقى مسارات المشروع وحزم Java ومعرّفات قاعدة البيانات باسم rushd للتوافق التقني.
