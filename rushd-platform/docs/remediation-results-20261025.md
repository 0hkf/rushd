# نتائج إصلاح التدقيق — 25 أكتوبر 2026

النتيجة: أصلحت F02/F03/F04/F05/F06/F08/F09، وأضفت تخفيفًا محليًا معتمدًا لـF01. **لم تغلق جاهزية النشر:** BOM القديم لم يرقَّ، وF07 وقرار الحسابات/المسودات وتقوية التسجيل وSCA المحدث ما زالت بوابات منفصلة. لا تعتبر نجاح15,000 شهادة أمان أو تغطية شاملة.

## الفحص والنطاق

الفرع`feature/property-google-location` بقي كما هو. قرئت أدلة التدقيق والنتائج ودليل المصفوفة والمتطلبات وسجل التهيئة والعقود والاختبارات ذات الصلة. لا تعليمات محلية إضافية فعالة ظهرت. مساحة العمل كبيرة ومتغيرة قبل هذه المهمة؛ قائمة الملفات أدناه تخص التغييرات المضافة هنا، لا نسبة كاملGit diff لهذه الحزمة.

لا SQL أو بيانات اختبار علىdb_rushd، ولا تغيير Docker أو خوادم المستخدم أو تفعيلP10/P13. لا dependency أومفتاح أوAPI خارجي أضيف، ولاcommit/merge/push. حافظنا علىADMIN وCSRF وCORS وBigDecimal والمخطط والعقارات القديمة.

## إعادة الإثبات قبل الإصلاح

- `mvn clean test` في sandbox الأصلي تعذر علىMockito JVM attach:621 حالة،522 خطأ تجهيز. هذا ليس522 عيبًا جديدًا.
- رُفض تشغيلclean خارج العزل على مجلد الخادم الجاري؛ انتقلنا إلى نسخة مصدر مستقلة في`/private/tmp/rushd-remediation-u6KqNaKh` بدل إيقافه أو إعادة المحاولة عليه. كانت محاولةsandbox الأولى قد نظفتtarget؛ لم يتوقفPID الخادم، ولم تكرر العملية على الأصل.
- في النسخة المؤقتة، الأمر الافتراضي أعاد خط الأساس:621 حالة،13 failure، صفرerrors/skips؛ H2 يرفضregexp_replace`g`.
- اختبارات الانحدار الجديدة قبل الإصلاح:13 حالة،10 failures؛ منها الاسم الطويل،405/415،72بايت، وسباق محكوم ببوابة ينتظر عندها الطلبان بعدexistsByEmail=false.
- لاحقًا أثناء مراجعة البريد اكتشفت حالة إضافية: بريدUnicode بطول255 قبلlowercase يتمدد بعده؛ الاختبار أعاد500 قبل إضافة التحقق، وصار400 بعده.

سجلات الإثبات في`/private/tmp/rushd-remediation-baseline.log` و`rushd-remediation-snapshot-baseline.log` و`rushd-remediation-regressions-before.log` و`rushd-remediation-normalized-email-before.log`. لا تشارك السجلات كاملة دون مراجعة.

## ما تغير ولماذا

### F01: حد كلمة المرور — تخفيف محلي لا ترقية مكتبة

اعتمد المستخدم رفض ما يتجاوز72 **بايتUTF-8**، بما فيه أثر الحسابات القديمة. سابقًا كانت لاحقتان مختلفتان بعد البادئة72بايت تقبلان. `PasswordInputPolicy` يقيس البايتات دونtrim أوnormalization. الحقل يحتفظ بحده الأدنى السابق6 أحرف؛ لا تغيير صامت لسياسة الحد الأدنى.

`ValidPasswordByteLength` يتحقق عند التسجيل قبل الخدمة، ويعيد400 بخطأpassword. `AuthService.authenticate` يرفض الطول غير المدعوم بخطأ401 العام قبلAuthenticationManager، و`BoundedBCryptPasswordEncoder` يمنعencode الطويل ويرفضmatches الطويل كدفاع إضافي. BCrypt وصيغةhash وتكلفة10 لم تتغير، ولاpre-hash أو قص أو إعادة تعيين حساب.

72 ASCII بايت ليست72 حرفًا عربيًا:36 من«ع» تساوي72بايت؛ إضافةASCII واحد تجعلها73. الاختبارات تشمل ذلك وemoji والكلمات العادية وتغيير داخل البادئة وبعدها.

**حد تاريخي مهم:** hash لا يخبرنا بطول المدخل القديم؛ قد تبقى البادئة72بايت مطابقة لـhash قديم كان ناتج كلمة أطول. رفض المدخل الطويل لا يعيد بناء الجزء المهمل. يلزم مسار استرداد/إعادة تعيين معتمد قبل الجمهور، ولم يُنفذ مسار جديد أو تعدّل حسابات. مكتبةSecurity6.3.4 نفسها ما زالت تحتاج ترقية؛ راجع [خطة BOM والقرارات](remediation-follow-up-decisions-20261025.md).

### F02: حدود التسجيل، بما فيهاUnicode

`RegisterRequest` يرفضname/email فوق255، مع إبقاءNotBlank وEmail؛null/blank والطول ليست حالات مدمجة في catch لقاعدة البيانات. `FitsNormalizedEmailColumn` يفحص الطول بعدtrim/lowercase(Locale.ROOT) القائم أيضًا: مثل«İ» قد يتحول إلى أكثر منcode point. لم نغير معنى تطبيع البريد أو نقتطع قيمة طويلة.

الاختبار يقبل اسم255 ASCII/عربي وبريدًا صالحًا255، ويرفض256 قبلالحفظ، مع حالةUnicode المتمددة. لا تحويل عام لـDataIntegrityViolationException إلى400.

### F03: البريد المتزامن

بقيexistsByEmail لخطأ سريع، لكن سلامة التزامن تعتمد على قيدDB الفريد. استخدمتsaveAndFlush لرؤية تعارض القيد أثناء عملية الحفظ، و`RegistrationEmailConflict` يترجمSQLState23505 لقيد`uq_users_email` فقط إلى409. H2 له اسم backing index معروف؛ لا تحليل لقيمة البريد داخل رسالةSQL، وبقية القيود لا تصبح«بريد موجود».

تسميةJPA للقيد أصبحت مطابقة للـPostgreSQL baseline المراجَع؛ لا عمود أوDDL جديد على قاعدة المشروع، وإعدادهاnone بقي كما هو. قاعدة قديمة أخرى باسم قيد مختلف تحتاج مراجعة مستقلة، لا نفترض توافق جميع المخططات الخارجية.

اختبار متحكم فيه يجعل طلبين يتجاوزان الفحص المسبق معًا، ثم يثبت201+409 علىH2 وPG. المصفوفة تضيف طلبيHTTP متزامنين كذلك. لا حذف للقيد أوقفل حسابات عامة.

### F04: المحافظة علىHTTP

أضيف معالجان محددان لأخطاء method/content-type، يعيدان405/415 مع جسمErrorResponse الآمن وheaders الإطار، بما فيهاAllow. الاستثناء الحقيقي يبقى500؛ يسجل التطبيق نوعه وmethod فقط، لا الرسالة/cause أوbody/cookies/query. لا يخفي خطأDB كمدخل غير صالح.

اختباراتGET login/register وtext/plain نجحت، وكذلكmalformedJSON400 وCSRF403 والصلاحيات في المجموعة الكاملة. CSRF قد يسبق تحليل المحتوى، فلا نستخدم415 لتجاوز401/403.

### F05: أسماءenum فقط

اعتمد المستخدم التغيير بعد توضيح مثالLAND بدل0. أعدنا استخدام`StrictStringEnumDeserializer` محليًا فيRegister.role والحقول الستةCreate/Update: type,listingType,rentalPeriod,facade,purpose,status. الأرقام/النصوص الرقمية/boolean/أسماء غير معروفة ترفض400، دونJackson global setting.

الواجهة ترسل الأسماء أصلًا. أي عميل خارجي يستخدمordinals يحتاج تحديثًا. null/default/required لكل حقل كما كانت، والأرقام المالية الصحيحة القديمة تبقى مقبولة؛ النصوص العشرية الخمسة فيResponse وBigDecimal بلا تغيير. `role:0` سابقًا أعطىBUYER، **ليسADMIN**.

### F06: إعداد الاختبار لا منطق الإنتاج

أضيفMODE=PostgreSQL إلىH2 test URL لدعم معامل`g` المستخدم فيregexp_replace. لم تتغير عبارة الإنتاج أو صيغ مكة أو الفرز أو حدود السعر، ولم تحذف أو تتخطى حالة. استمرت بوابةPG الحقيقية بالمخطط المراجع وHibernate validate؛ نجاحH2 وحده ليس إثباتPostgreSQL.

### F08/F09:404 وتوثيق الحالة

صفحةNotFound عربية ضمنwildcard route، بعنوانh1 يستقبل التركيز ورابطيالعقارات/الرئيسية. أعيد استخدام الثيم والأزرار، بلاlayout جديد لبقية الصفحات. استخرجتAppRoutes دون نسخ جدولالتوجيه لكي يرسم الاختبار نفس routes الإنتاج باستخدامReactDOM/Vite الموجودين، بلا dependency إضافية؛ اختبار التركيز الفعلي فيالمتصفح.

وحّدتREADME والمتطلبات مع مرجعlocal-development-setup: P04 طبقت21أكتوبر، وP10/P13 لم تفعلا. التقارير القديمة بقيت سجلات تاريخية، وصحح ذكرTajawal إلىThmanyah محلي. حدّث عقدProperty لرفضordinal، ووضح عقدauth حد البايتات وتأثيره. لا سياسةCSP إنتاج تخمينية.

## أمثلةAPI

تسجيل صالح يبقىPOST /api/auth/register معCSRF وجسم مثل:

```json
{"name":"مستخدم تجريبي","email":"buyer@example.invalid","password":"Example-only-password!","role":"BUYER"}
```

يرجع201 ببياناتالمستخدم فقط دونpassword/hash. اسم256 أوكلمة>72بايت يعيدان400 وmessage باسم الحقل. بريد متكرر، بما فيه السباق، يرجع409. كلمةدخول طويلة ترجع401 العام دونتجربة بادئة.

```json
{"status":400,"error":"Bad Request","message":{"password":"كلمة المرور يجب ألا تتجاوز 72 بايت بترميز UTF-8 (قد يشغل الحرف العربي أكثر من بايت)"},"path":"/api/auth/register"}
```

المثال يختصرtimestamp. تغيير `role` إلى0 أو `type` إلى0 في طلب عقار مرفوض400. طلب أدمن صالح يبقى يقبل `"price":"9007199254740993.01"` أو أرقامJSON الدقيقة التي يقرأهاBigDecimal؛ الاستجابة تبقى `"price":"9007199254740993.01"`، و`"latitude":"-21.4225000"`، لاNumber في الواجهة. GET /api/auth/login يرجع405 معAllow:POST؛ text/plain معCSRF الصحيح415.

## لماذا ثلاث طبقات تحقق؟

- الواجهة تعطي تصحيحًا سريعًا وتحافظ على القيمة النصية؛ لا يمكن اعتمادها أمنيًا لأن العميل يستطيع تجاوزها.
- DTO/الخدمة تفرضان العقد قبلDB وتعيدان400/401 المحدد، بالمقارنة الدقيقة لا التقريب. لا Google API أو تحقق خارجي.
- قيودDB تبقى خط الدفاع النهائي، خصوصًاunique تحت التزامن؛ لا تغني عنDTO، ولا نترجم كل فشلها إلى400.

## الأوامر والنتائج الفعلية

أوامرMaven وPG والمصفوفة نفذت من **نسخة المصدر المؤقتة** لحمايةtarget/classes للخادم الجاري. طابقتbackend/src وfrontend/src بالأصل بعد آخر تغيير بواسطةdiff -qr؛ أوامر الواجهة أدناه نفذت على المشروع الأصلي.

| الأمر | النتيجة النهائية |
|---|---|
| mvn clean test |650/650، صفرfailure/error/skip |
| bash scripts/verify-postgres.sh |650/650 علىPG16 معزول، validate وفحوص الترقيات؛ تنظيف الحاوية |
| npm test |306/306، منها اختبار رسم routes404؛ ليست306 رحلة متصفح |
| npm run build |ناجح |
| npm run lint |ناجح |
| bash scripts/quality-15000/run.sh |15,000/15,000، صفر فشل أو تخطٍّ |
| bash -n / node --check / oxlint لملفات المصفوفة |ناجحة |
| git diff --check |ناجح |
| npm audit --json |تعذرDNS داخلsandbox؛ خارج العزل رُفض إذن إرسال شجرة الاعتماد؛ لا نتيجةSCA محدثة |

سجلات الإغلاق: `/private/tmp/rushd-remediation-backend-final.log`، `/private/tmp/rushd-remediation-postgres-final.log`، `/private/tmp/rushd-remediation-frontend-test.log`، `/private/tmp/rushd-remediation-build.log`، `/private/tmp/rushd-remediation-lint.log`، `/private/tmp/rushd-remediation-15000-closure.log`.

## المصفوفة قبل/بعد وعدّها

الخط الأساس14,973/15,000 مع27 فشلًا. لم تتغيرIDs أو عدد44 مجموعة، ولم تضاف الاختبارات القديمة أو إعادة التشغيل إلى العدد. تحديث oracle لكلمة المرور معلن فيREADME: تسجيل الطويل400، تسجيل72 بايت201، دخول بلاحقة401، ودخول القيمة الصحيحة200، لاexception عشوائي كنجاح. Java تختبرencoder التطبيق المحدود، **لا تثبت أن المكتبة نفسها ترقّت**.

محاولة مرحلية أعطت14,999 وحالة429 لأن الدخول الإضافي استهلك حصة10/دقيقة. لم نرفع الحد أو نغير المتوقع401 إلى429؛ أضيفpacing بنافذة60 ثانية قبل مرحلةauth لتفصل دخولfixtures، ثم أعيد التشغيل الكامل. الانتظار/retry ليس حالة إضافية.

**ملخص الإغلاق النهائي:** [summary.json](/private/var/folders/8r/_62plrr5571dlxbyyjhtx_700000gn/T/rushd-test-results/quality-15000-uR6FDC1N/summary.json)، و[فهرس الحالات](/private/var/folders/8r/_62plrr5571dlxbyyjhtx_700000gn/T/rushd-test-results/quality-15000-uR6FDC1N/cases.csv). اكتمل على آخر مصدر بعد اختبار البريد المطبّع:9,000 واجهة،4,000 Java،2,000 HTTP+PG، جميعها ناجحة. لا تضاعف العدد بإضافة المحاولات السابقة.

البصمات: frontend=`d871dba6be840320ab9cbaad0786a215e58946aa06f3084155f502106247ba15`؛ backend=`35082fd1476b41d6062f8a91cf7ff4bb54a8ae7de952fd592814a525061432f7`؛ HTTP=`50d812f81f6f17d36d7642c4c2174445b7ba687836f5fadce920290ed1658231`.

## المتصفح المعزول وحدوده

جلسة منفصلة: React127.0.0.1:5178،API18096،PGtmpfs جديد وقاعدةrushd_quality، مع إثبات ID/name/label ومنفذPG وPID/API وdatasource قبلfixture. الحسابات والصورة المصدرية للمصفوفة صناعية فقط؛ لم تستخدم بيانات الديمو الحقيقية أو.env المحلية.

- 404 ظهرت بعناوين وروابط صحيحة. التركيزh1، ثمTab يصل لرابط العقارات وEnter يفتحها. على390px:clientWidth=scrollWidth=390 واتجاهrtl؛ فحص1024px كذلك.
- دخول خاطئ عرض الخطأ ولم يمنح جلسة؛ الصحيح أدخلADMIN الصناعي.
- إنشاء بسعر `٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣٫٠١` ومساحة `٥٠٠٫٢٥`؛ latitude=٩١ رفض محليًا وبقي العنوان/السعر/القيمة. بعد إحداثيات صحيحة سالبة حفظ، ثم عدل السعر بالفارسية إلى `۹۰۰۷۱۹۹۲۵۴۷۴۰۹۹۳٫۰۲` وظهر بالتفاصيل `9٬007٬199٬254٬740٬993٫02`.
- **عيب باقٍ مؤكد:** التنقل داخلReact ضيّع مسودة غير محفوظة؛ والخروج من تبويب ثان ثم حفظ الأول نقله للدخول، وبعد إعادة الدخول المسودة فارغة. لم نضع مسودة فيlocalStorage أو نغير الجلسة. [سياسة مقترحة تنتظر الاعتماد](remediation-follow-up-decisions-20261025.md).
- لا محاكاة انقطاع شبكة/save500 بالمتصفح هنا، ولا انتظار انقضاء ساعةJWT؛ فقد الجلسة حوكي بخروج صريح من تبويب ثان. SSR404 لا يعادل اختبارReact تفاعلي شامل للنموذج. هذه حدود معلنة وليست حالات نجاح مفترضة.

أدلة الصور: [404 على الجوال](/private/tmp/rushd-browser-AOjJmFxM/not-found-mobile.png)، [404 على سطح المكتب](/private/tmp/rushd-browser-AOjJmFxM/not-found-desktop.png)، [التحقق مع بقاء المدخلات](/private/tmp/rushd-browser-AOjJmFxM/form-invalid-preserved.png)، [السعر الدقيق](/private/tmp/rushd-browser-AOjJmFxM/exact-details-mobile.png)، [فقد الجلسة](/private/tmp/rushd-browser-AOjJmFxM/session-lost.png). ملفاتtmp قد ينظفها النظام؛ لا تعتمدها كأرشيف دائم.

أغلقت تبويبات الفحص وأعيدviewport، وأوقف الخادمان والحاوية المملوكة فقط. بقي خادمJava83753 وVite83807 كما كانا؛ **الباكند الأصلي لم يحمل الإصلاحات بإعادة تشغيل**. لا تختبر النتيجة على8080 ثم تفترض أنها البناء الجديد.

## الملفات التي عدلتها هذه الحزمة

داخل `/Users/azzam/Documents/rushd/rushd-platform/` فقط، مع السكربتات والأدلة المؤقتة المشار إليها خارجGit:

- Backend: `src/main/java/com/rushd/dto/RegisterRequest.java` و`CreatePropertyRequest.java` و`UpdatePropertyRequest.java`؛ `entity/User.java`؛ `service/AuthService.java` و`RegistrationEmailConflict.java`؛ `security/PasswordInputPolicy.java` و`BoundedBCryptPasswordEncoder.java`؛ `config/SecurityConfig.java`؛ `exception/GlobalExceptionHandler.java`؛ `validation/ValidPasswordByteLength.java` و`PasswordByteLengthValidator.java` و`FitsNormalizedEmailColumn.java` و`NormalizedEmailLengthValidator.java`.
- اختباراتBackend: `controller/AuthInputContractTest.java` و`StrictPublicEnumContractTest.java`؛ `service/RegistrationConcurrencyTest.java` و`RegistrationEmailConflictTest.java`؛ `security/PasswordInputPolicyTest.java`؛ `src/test/resources/application.yml`.
- Frontend: `src/App.jsx` و`src/pages/NotFound.jsx` و`src/services/notFound.test.js`.
- المصفوفة: `scripts/quality-15000/BackendMatrix.java` و`http.mjs` و`run.sh` و`README.md`. انتقلت النتائج إلىtmp الحقيقي لتوافق صلاحيات الكتابة ورفضsymlink في تخزين الصور؛ لا عنوانDB قابل للتمرير أضيف.
- التوثيق: `README.md` و`docs/requirements.md` و`api-auth.md` و`api-properties.md` و`local-development-setup.md`، وهذا التقرير ووثيقة القرارات، وإشارات متابعة في تقارير التدقيق/النتائج السابقة.

لا تغييراتpom أوlockfile أوDDL أوثيم أوصلاحيات ضمن هذه الحزمة؛ ما يظهر بها فيGit قد يكون من أعمال سابقة.

## الإغلاق الجزئي والقرارات المتبقية

النتائج تغلق العيوب المحددة أعلاه فقط. يبقى اعتماد ترقيةBOM مع سلوكJSON/الاختبارات، واسترداد الحسابات الطويلة، وتقويةregister، وسياسة المسودات، واختيار التسجيل الذاتي أو الإداري، وحصر المنافذ/إعادة تشغيل الكود، وإذنnpm. [التفاصيل والخيارات](remediation-follow-up-decisions-20261025.md).

لا تشغيل للصور/المفضلة الفعلية أوAI/P16–P31 أوخرائط أوPDF. تبقى قيودstatelessJWT حتى انتهاء ساعة بعدlogout وrefresh عبر التبويبات وغيابMFA وreadiness/CI كما وردت بالتدقيق. لاSafari/Firefox أوأجهزة حقيقية أوTLS/proxy أوحمل أوتغطية/WCAG شاملة. البناء والعدد الكبير لا يعنيان جاهزية النشر.
