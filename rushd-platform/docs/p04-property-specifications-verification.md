# P04 — تنفيذ مواصفات العقارات والتحقق منها

بدأ العمل في 18 أكتوبر واكتمل التحقق في 19 أكتوبر 2026. النتيجة: **اكتمل الباكند ضمن نطاق P04 على بيئات معزولة، ولم تُطبّق ترقية قاعدة المشروع الفعلية**. المرجع: [المعاني المعتمدة في P03](p03-property-specifications-draft.md) و[التمثيل التنفيذي الذي عُرض قبل التنفيذ](p04-property-specifications-proposal.md).

## 1. الفحص وما أعيد استخدامه

كل ملفات Property المطلوبة موجودة؛ لا إعادة بناء للباكند أو تبديل للتقنيات. استخدمنا Property/DTO/Service/Mapper/Repository والمسارات الحالية، وحدود الأرقام الدقيقة والاستجابات، وصلاحية ADMIN وcookies وCSRF وقواعد القراءة كما هي. الفرع `feature/property-google-location` وشجرة Git تتضمن أعمالًا سابقة محفوظة لا تُنسب إلى P04.

كانت الأنواع السبعة موجودة، لكن لا بيانات منظمة للغرف أو العمر أو التجهيزات أو المساحات المسماة. المشكلة الأهم في توسيع PUT هي أن null في DTO وحده لا يميز الحقل الغائب من المسح الصريح؛ تطبيق سلوك الحقول القديمة على الجديدة كان سيمحو مواصفات محفوظة عند التعديل من واجهة قديمة.

## 2. ما تغير ولماذا

| الطبقة | التغيير |
|---|---|
| JPA | `PropertySpecifications` مضمن في Property؛ 37 عمودًا nullable في properties، دون جدول موازٍ أو JSONB أو defaults أو backfill |
| الطلب | كائن specifications اختياري؛ قارئ Jackson موضعي يحفظ حضور كل مفتاح ويرفض الأنواع الخاطئة والمفاتيح غير المعروفة أو المكررة؛ لا تغيير عام في ObjectMapper |
| التحقق الأولي | قواعد المجال والدقة وزوج العمر عبر Bean Validation قبل PropertyRepository؛ حماية الدقة تعيد استخدام الحساب الآمن من SafeDecimalPrecisionValidator |
| الخدمة | قراءة صف التعديل بقفل، ثم نسخة مرشحة ودمج الحقول المقدمة، والتحقق من الحالة النهائية قبل تعديل الكيان؛ لا حفظ جزئي أو مسح تابع صامت |
| الاستجابة والتحويل | كائن specifications دائم الشكل بـ37 مفتاحًا؛ null للمجهول/غير المنطبق، والمساحات نصوص دقيقة عبر PlainDecimalStringSerializer؛ إعادة استخدام تحويل Property الحالي |
| PostgreSQL | ترقية إضافية مستقلة بـ37 عمودًا و64 قيدًا؛ تحمي المجالات والانطباق وenums وزوج العمر والربط والصافي/الإجمالي والوحدات؛ لا تطبيق تلقائي |
| الاختبارات | 104 اختبارات جديدة فوق 278 سابقة، إضافة إلى فحوص SQL للترقية؛ لا تغيير لقواعد R1/R3 أو الواجهة |

الكائن المضمن يعود null من JPA إذا كانت كل أعمدته null؛ يحوله Mapper إلى استجابة بكل القيم null دون كتابة إلى القاعدة من GET. الأعداد Integer وBoolean قابلة لـnull، وليست primitives توحي بأن القيمة معروفة وتساوي 0/false.

قفل الصف خاص بمسار التعديل، لا بالقراءات العامة. يحمي دمج حقلين مختلفين في طلبين متزامنين. لا version أو ETag جديد؛ PUT ما زال يستبدل الحقول القديمة كاملة، وليس حلًا عامًا لتعارض تغييرات المستخدم على تلك الحقول.

## 3. الحقول والتحقق

القاموس الكامل وأسماء الأعمدة في [عقد P04](p04-property-specifications-proposal.md#3-قاموس-الحقول-والتمثيل-المقترح). المجموع: 4 مساحات، و12 عددًا، و13 boolean، و7 enums، ونص واحد، كلها اختيارية وفق انطباق النوع.

- المساحات الجديدة BigDecimal، بدقة precision=12 وscale=2، من 0.01 إلى 9999999999.99؛ الطلب نص عشري ASCII عادي أو null، والاستجابة نص دقيق أو null. لا قص أو تقريب. الحقول القديمة price/area/الموقع تستمر في قبول طلباتها القديمة الصحيحة.
- الغرف/الحمامات/الصالات: 0–100؛ المواقف: 0–1000؛ الوحدات: 0–10000؛ عدد الأدوار: 1–200؛ رقم الدور: −10–200، وصفر يعني الأرضي؛ العمر: 0–1000؛ سنة التقدير: 1900–الحالية حسب Asia/Riyadh؛ الآبار: 0–100؛ وصف OTHER: حتى 100 حرف بعد trim.
- العدادات أعداد JSON صحيحة فقط، لا `"3"` أو `3.0` أو `3e0`؛ التجهيزات true/false/null، لا 0 أو `"false"`. أسماء enum صريحة وحساسة للحالة، لا ordinal؛ المجهول null، لا اسم UNKNOWN جديد.
- لا تُقبل حقول غير منطبقة حتى لو كانت false أو 0. التجاري يحترم نطاق UNIT/WHOLE_BUILDING في الحالة الناتجة بعد الدمج، دون إلزام تكرار نطاق محفوظ.
- صافي الوحدة ≤ إجماليها؛ لا نشترط أن يكون البناء ≤ الأرض. المصدر المحدد للمساحة الرئيسية يجب أن توجد قيمته وأن يساوي area عدديًا، دون فك ربط تلقائي عند التعارض.
- العمر وسنة التقدير يُرسلان/يُمسحان معًا؛ أجزاء الوحدات ومجموعها لا تتجاوز إجماليًا معلومًا. لا استنتاج لسنة البناء أو NEW أو ستوديو أو التأثيث أو خصوصية المسبح.

تحقق الواجهة الحالي لتحسين الإدخال القديم؛ إدخال الحقول الجديدة يأتي في P05. تحقق الباكند هو الحاجز الإلزامي للطلبات، ثم قيود DB دفاع إضافي. نوع numeric في DB قد يقرب كتابة SQL مباشرة قبل CHECK؛ لذلك لا نزعم أن قيود DB وحدها تمنع فقدان الدقة في كل كتابة خارج API.

## 4. أثر API والتوافق

المسارات وأسماء وأنواع الحقول القديمة لا تتغير. يضاف specifications إلى Create/Update/PropertyResponse. عميل خارجي يرفض أي مفتاح استجابة إضافي قد يحتاج تحديثًا. بيانات نتائج R3 وطلب R1 لم تُضف إليها حقول أو أوزان.

| الطلب الجديد | النتيجة |
|---|---|
| القسم غائب عند الإنشاء | مواصفات مجهولة |
| القسم غائب عند التعديل أو `{}` | حفظ المواصفات السابقة |
| حقل مقدم بقيمة | دمجه فقط |
| حقل مقدم بـnull | مسحه فقط، بشرط اتساق الحالة النهائية |
| القسم كله null | 400، لا مسح جماعي |
| تعديل قديم متعارض للنوع أو area | 400 بلا تعديل جزئي أو حذف خفي |

مثال إنشاء شقة:

```json
{
  "title": "شقة في النوارية",
  "type": "APARTMENT",
  "listingType": "SALE",
  "city": "مكة المكرمة",
  "district": "النوارية",
  "area": "150.25",
  "price": "9007199254740993.01",
  "purpose": "RESIDENTIAL",
  "status": "ACTIVE",
  "specifications": {
    "primaryAreaSource": "UNIT_NET",
    "unitNetArea": "150.25",
    "unitGrossArea": "180.00",
    "bedrooms": 3,
    "floorNumber": 0,
    "hasElevator": true,
    "parkingSpaces": 0
  }
}
```

الاستجابة تحتفظ بكل الحقول القديمة، وتضيف القيم نفسها داخل specifications؛ بقية مفاتيحه تظهر null. لإزالة معلومة المصعد فقط، يُرسل PUT كاملًا للحقول القديمة مع `"specifications":{"hasElevator":null}`؛ بقية مواصفاته لا تُمسح. المضمون بعد القراءة من DB هو القيمة العددية الدقيقة، لا تهجئة الرقم مثل 150 مقابل 150.00؛ الأصفار النهائية قد تختلف حسب scale.

أخطاء التحقق/الاتساق تعيد 400 في ErrorResponse الحالي بخريطة message ومفاتيح مثل `specifications.primaryAreaSource`؛ أخطاء الشكل والأنواع تعيد 400 لطلب غير صالح. لا نعتمد على خطأ DB من نوع 500 لمعالجة مجال/دقة غير صالحة. تبقى 401/403 وCSRF وقواعد إخفاء المسودات وعدم نقل الناشر كما كانت.

## 5. نتائج التحقق الفعلية

| الأمر/البيئة | النتيجة |
|---|---|
| `mvn clean test` مع datasource مثبت على H2 في الذاكرة فقط | 382 اختبارًا، بلا فشل أو أخطاء أو تخطٍ |
| `bash scripts/verify-postgres.sh` من backend | 382 اختبارًا على PostgreSQL 16.11 مع Hibernate validate، بلا فشل أو أخطاء أو تخطٍ؛ وفحوص الترقية ناجحة |
| `npm test` من frontend | 59 ناجحًا |
| `npm run build` من frontend | نجح |
| `npm run lint` من frontend | نجح |
| `bash -n scripts/verify-postgres.sh` من backend | نجح |
| `git diff --check` | نجح |

الاختبارات الجديدة وعددها 104: منها 75 لربط الحقول والتحقق منها، و17 للـCRUD والتوافق والمجال والصلاحيات، و11 لمصفوفة الانطباق وقواعد الدمج والنسخ، وواحد للتزامن بمعاملتين. تشمل القراءة بعد flush/clear، وقوائم Property العامة والأدمن والتفاصيل، وقيمًا تتجاوز دقة JavaScript، والحدود والصيغ والكسور وnull والطلبات القديمة، وعدم مس updatedAt عند رفض التعديل.

احتوت الجولات الأولى أخطاء في fixtures الاختبار الجديدة، لا ضرورة لتغيير سلوك الإنتاج: بريد ADMIN بأحرف كبيرة لا يطابق التطبيع القائم، وفك JSON العربي بالترميز الافتراضي لـMockMvc بدل UTF-8؛ صُححت fixtures وقراءة bytes. النتائج النهائية أعلاه بعد التصحيح. بقيت تحذيرات Java 25 عن التحميل الديناميكي وبعض تحذيرات deprecation/unchecked السابقة؛ ليست فشلًا.

PostgreSQL معزول من الصورة المخبأة postgres:16، بإصدار 16.11 وصورة SHA256 تبدأ بـ`6e9bbed548cc`، وDocker 29.1.3. يرفض runner عناوين URL أو overrides عشوائية، وينشئ حاوية مملوكة بتسمية فريدة وtmpfs ومنفذ loopback مؤقت؛ لا pull ولا إعادة تشغيل لخدمات المستخدم. آخر جولة استخدمت الحاوية `3ffda59beac18139860df7275bd014e38e41f182e5fbcac4ce8b511c7a64ed73` ثم أزيلت بعد التحقق من ملكيتها. بقيت rushd_postgres بنفس المعرف `cacefb4c4b3aa9e1347c6a980ed091bd97e227e12c8aec5478508fcd03c68efa`.

تزرع فحوص SQL سجلين قديمين فقط في قاعدة runner المملوكة، وتأخذ snapshot لكل الحقول القديمة، وتثبت بقاء القيم والمعرفات والناشر والتواريخ والأرقام وإحداثياتها، وأن كل المواصفات الجديدة null. تختبر رفض المخطط الجزئي وإعادة الترقية دون DDL جزئي، وتسع حالات رفض CHECK: مجال غير صالح، وfalse غير منطبق، وتعارض area، ومسح مصدر مرتبط، وصافٍ أكبر من الإجمالي، ودور خارج الحد، ونطاق تجاري مفقود، ومجموع وحدات خاطئ، وزوج عمر ناقص. وتختبر عدم استنتاج NEW من عمر 0. تزال fixtures قبل حزمة التطبيق؛ رسالتا ERROR للترقية الجزئية والمكررة متوقعتان ضمن هذا الاختبار.

## 6. الملفات الخاصة بـP04

تعديل الموجود:

- `backend/src/main/java/com/rushd/entity/Property.java`
- `backend/src/main/java/com/rushd/dto/CreatePropertyRequest.java`, `UpdatePropertyRequest.java`, `PropertyResponse.java`
- `backend/src/main/java/com/rushd/mapper/PropertyMapper.java`
- `backend/src/main/java/com/rushd/service/PropertyService.java`
- `backend/src/main/java/com/rushd/repository/PropertyRepository.java`
- `backend/src/main/java/com/rushd/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/rushd/validation/SafeDecimalPrecisionValidator.java`
- `backend/src/test/java/com/rushd/controller/PropertyDecimalValidationControllerTest.java` — تهيئة/تحقق مسار القراءة المقفلة فقط.
- `backend/scripts/verify-postgres.sh` — تطبيق/اختبار الترقية داخل حاويته فقط.

إضافات:

- `entity/PropertySpecifications.java` وقوائم enum: `PropertyAreaSource`, `PropertyCondition`, `PropertyFinishing`, `PropertyFurnishing`, `PropertyWaterSource`, `CommercialScope`, `CommercialSubtype`.
- `dto/PropertySpecificationsRequest.java`, `PropertySpecificationsResponse.java`, و`dto/deserialization/PropertySpecificationsDeserializer.java`.
- `validation/PropertySpecificationFields.java`, `ValidPropertySpecifications.java`, `PropertySpecificationsValidator.java`, `PropertySpecificationsRules.java`.
- `exception/InvalidPropertySpecificationsException.java`.
- اختبارات `PropertySpecificationsControllerTest`, `PropertySpecificationsValidationControllerTest`, `PropertySpecificationsConcurrencyTest`، ومساعد `PropertySpecificationsFixtures`، واختبار `PropertySpecificationsRulesTest`.
- `backend/db/20261018-property-specifications.sql` وملفا `backend/src/test/resources/db/property-specifications-before.sql` و`property-specifications-after.sql`.
- توثيق API وP04 والخطة والمتطلبات ودليل التشغيل المحلي وREADME؛ لم تُعدّل ملفات frontend في هذه الدفعة.

المسارات المختصرة entity/dto/validation/exception تحت `backend/src/main/java/com/rushd/`، والاختبارات تحت `backend/src/test/java/com/rushd/`. لا تُنسب التعديلات السابقة الأخرى في Git لهذه الحزمة.

## 7. التجربة والتفعيل وما لم يختبر

لإعادة التحقق المعزول: من backend شغّل `bash scripts/verify-postgres.sh`؛ لا يفحص قاعدة مشروعك أو يعدلها. إذا كان Docker أو الصورة غير متاحين، يتوقف بدل اتخاذ قاعدة بديلة. H2 ليس دليلًا على PostgreSQL؛ لذلك شُغّلت البيئتان فعليًا.

لا ينبغي تشغيل كود P04 على قاعدة المشروع القديمة قبل ترقيتها: استعلام Property الجديد يتوقع أعمدة spec_. **ملف الترقية سُلّم للمراجعة ولم يُطبّق على db_rushd**؛ التفعيل الحقيقي يحتاج نسخة احتياطية ومراجعة وموافقة تطبيق منفصلة. لم يُنفذ SQL في هذه الدفعة على قاعدة المشروع، ولم تُحذف بيانات أو تُنشأ حسابات اختبار فيها.

بعد تجهيز بيئة تشغيل معزولة أو ترقية موافق عليها: سجّل الدخول بأدمن عبر الآلية الحالية، واستخدم POST بالمثال الكامل أعلاه؛ أعد GET للمعرف، ثم PUT بنفس الحقول القديمة مع مفتاح specifications واحد، وأعد GET. جرّب null لمعلومة واحدة، وغياب القسم لحفظ المجموعة، وarea متعارضًا مع مصدره لترى 400 دون حفظ. لا يوجد إدخال مرئي جديد لهذه المفاتيح في الفورم الحالي؛ هذا غرض P05.

لم تُجرَ رحلة متصفح لحقول المواصفات الجديدة أو فحوص متصفحات/جوال لها؛ لا واجهة جديدة في P04. اختبارات HTTP هنا هي MockMvc مع مصادقة وCSRF واستمرارية حقيقية على قاعدتي الاختبار، وليست متصفحًا إنتاجيًا. أُعيد بناء واجهة المشروع الحالية واختبار خدماتها للتحقق من عدم التراجع فقط. لم تُختبر أحمال أو بيئة متعددة العقد أو ترقية إنتاج أو نسخ/استعادة حقيقية لبيانات المشروع.

النطاق لم يشمل P05/P06 أو صورًا أو خرائط أو AI أو مفضلة أو فلاتر جديدة، ولا يغيّر الثيم أو الخط أو المصادقة أو سياسة النشر. لا dependencies أو مفاتيح جديدة، ولا commit أو merge أو push. التالي P05 للفورم والعرض وتأكيد تغيير النوع؛ المتبقي 27 حزمة، من P05 إلى P31، وليس 27 يومًا.
