# P04 — استكمال الباكند الحالي: مقترح API وJPA والترقية

بدأ الفحص في18 أكتوبر2026. الحالة: **انتقل المقترح إلى التنفيذ بناءً على طلب المستخدم المتابعة بعد عرضه؛ نتائج الإغلاق في تقرير P04**. أبقي اسم الملف لاستمرار الروابط. المرجع الدلالي المعتمد: [P03](p03-property-specifications-draft.md). لا تعاد أسئلة المواصفات أو قواعد الدمج التي حسمت هناك.

## 1. نتيجة الفحص وإعادة الاستخدام

الفرع `feature/property-google-location` وشجرةGit تحتوي تعديلات سابقة محفوظة. لا ملفاتProperty مطلوبة مفقودة، ولم توجد تعليماتAGENTS محلية في المسار أو أسلافه.

الباكند ليس مشروعًا جديدًا أو مجرد ملفات فارغة: Spring Boot/JPA قائم، وفيهProperty وجدولproperties وCRUD وCreate/Update/Response وMapper وخدمة وRepository، وبيع/إيجار وموقع منظم ودقةBigDecimal، وصلاحياتADMIN وcookies وCSRF، ومطابقةR1/R3 واختبارات. لا توجد حاليًا مواصفات منظمة للغرف والعمر والتشطيب والتجهيزات أو مساحات مسماة منفصلة.

| الموجود الذي سيعاد استخدامه | الإضافة المحدودة |
|---|---|
| [Property](../backend/src/main/java/com/rushd/entity/Property.java) وproperties | كائنJPA مضمن بأعمدة اختيارية في الجدول نفسه |
| [Create/Update](../backend/src/main/java/com/rushd/dto/CreatePropertyRequest.java) و[Update](../backend/src/main/java/com/rushd/dto/UpdatePropertyRequest.java) | قسمspecifications اختياري؛ لا استبدالDTO أو تغيير دلالة الحقول القديمة |
| [PropertyResponse](../backend/src/main/java/com/rushd/dto/PropertyResponse.java) | قسم استجابة إضافي بنفس أسماء حقول المواصفات |
| [PropertyMapper](../backend/src/main/java/com/rushd/mapper/PropertyMapper.java) | إعادة استخدام تحويل العقار الحالي وإضافة تحويل المواصفات مرة واحدة |
| [PropertyService](../backend/src/main/java/com/rushd/service/PropertyService.java) | دمج مرشح مستقل، والتحقق من الحالة النهائية قبل تعديل الكيان المحفوظ |
| [SafeDecimalPrecision](../backend/src/main/java/com/rushd/validation/SafeDecimalPrecisionValidator.java) و[PlainDecimalStringSerializer](../backend/src/main/java/com/rushd/dto/serialization/PlainDecimalStringSerializer.java) | إعادة استخدام حماية الدقة والعرض الدقيق، لا إعدادJSON عام |
| سياسة [StrictStringEnumDeserializer](../backend/src/main/java/com/rushd/dto/deserialization/StrictStringEnumDeserializer.java) | اتباع سياسة أسماء enum الصريحة ورفض ordinal داخل القارئ الموضعي الجديد، لا إعادة استخدام الصنف نفسه |
| اختباراتMockMvc وround-trip الحالية و[runner PostgreSQL](../backend/scripts/verify-postgres.sh) | حالات المواصفات والترقية والدمج، مع بقاء الاختبارات القديمة |

الاختبارات الحالية التي روجعت تشمل إنشاء/تعديل الموقع، عدم صلاحيةSELLER/BUYER للنشر، تثبيت الناشر، القراءة الخاصة والعامة، أسعار وإحداثيات دقيقة بعدflush/clear، رفضRENT+SOLD قبلPropertyRepository، وحدودR1/R3. إعداد الإنتاج `ddl-auto: none`، والاختبارات الافتراضيةH2؛ لاFlyway/Liquibase أوjsonb حاليًا، ولا حاجة لإضافةdependencies لهذه الحزمة.

## 2. التمثيل المختار بعد طلب المتابعة

1. إبقاءAPI الحالي ومساراته وأسماء الحقول الحالية، وإضافة **كائن واحد مسطح اختياري اسمه `specifications`** فيCreate/Update، وإضافته إلىPropertyResponse.
2. تخزين **37 عمودًا جديدًا، كلهاnullable** فيproperties، بكائن `@Embeddable PropertySpecifications` داخلProperty، وليس جدولًا ثانيًا أوكيانProperty بديلًا أوJSONB. أسماء الأعمدة تبدأ بـ`spec_` لتمييزها عن الحقول القائمة.
3. استجابةspecifications كائن دائم الشكل، بجميع المفاتيح الـ37، وقيمnull للمجهول/غير المنطبق؛ السجل القديم يعيد كائنًا كل قيمهnull، لا يكتب ذلك إلى القاعدة من مجرد القراءة.
4. حقول المساحات الجديدة نصوص عشريةASCII plain أوnull فيالطلب والاستجابة؛ العدادات أعدادJSON صحيحة أوnull؛ التجهيزاتJSONboolean أوnull؛ والقوائم أسماءenum أوnull. هذا تشديد موضعي للحقول الجديدة فقط، لا تغييرلقبول الطلبات القديمة فيprice/area/الموقع.
5. ترقيةSQL مضافة منفصلة تراجع وتختبر علىPG معزول فقط؛ لا تطبيق علىقاعدةالمشروع ولاDDL تلقائي.

السبب: المواصفات تشترك في دورة حياةالعقار، وعددها محدود، وستستخدم فياستعلاماتP06 لاحقًا؛ الأعمدة الواضحة تسمح بحدودوقواعدDB وJPA دون إدارةjoin أوتعقيد تخزينJSON. الكلفة: جدولأعرض واستجابةأكبر. البديل جدولواحداختياريمرتبط1:1 يبقيpropertiesأصغر، لكنه يضيفjoin ودورةحياة وتحميلًا يحتاج ضبطًا، دون حاجة مثبتة الآن. لا فهارس للمواصفات قبل تحديد استعلاماتP06 وقياسها.

## 3. قاموس الحقول والتمثيل المقترح

كل صف حقل مستقل داخلspecifications وعمودمستقل فيproperties. كلها تقبلnull؛ لاdefaults لأرقام أوbooleans أوenums. الأنواعفيJava هيBigDecimal وInteger وBoolean وenum/String، لاprimitives تخترع0/false.

| حقلJSON/Java | العمود المقترح | نوعSQL | المجال/المعنى وفقP03 |
|---|---|---|---|
| `primaryAreaSource` | `spec_primary_area_source` | varchar(32) |LAND / UNIT_NET / UNIT_GROSS / TOTAL_BUILT؛ null=معلنةغيرمصنفة فيالحالةالمحفوظة |
| `landArea` | `spec_land_area` | numeric(12,2) |0.01..9999999999.99، م²قطعةأرض |
| `unitNetArea` | `spec_unit_net_area` | numeric(12,2) |المجال نفسه، صافيالوحدة |
| `unitGrossArea` | `spec_unit_gross_area` | numeric(12,2) |المجال نفسه، إجماليالوحدة |
| `totalBuiltArea` | `spec_total_built_area` | numeric(12,2) |المجال نفسه، إجماليالبناء |
| `bedrooms` | `spec_bedrooms` | integer |0..100 |
| `bathrooms` | `spec_bathrooms` | integer |0..100 |
| `livingRooms` | `spec_living_rooms` | integer |0..100 |
| `floorNumber` | `spec_floor_number` | integer |−10..200؛0أرضي |
| `floorCount` | `spec_floor_count` | integer |1..200 فوقالأرض شاملًاالأرضي |
| `totalUnits` | `spec_total_units` | integer |0..10000 |
| `residentialUnits` | `spec_residential_units` | integer |0..10000 |
| `commercialUnits` | `spec_commercial_units` | integer |0..10000 |
| `parkingSpaces` | `spec_parking_spaces` | integer |0..1000، مواقفمخصصة |
| `estimatedAgeYears` | `spec_estimated_age_years` | integer |0..1000، تقدير لايُزادآليًا |
| `ageAsOfYear` | `spec_age_as_of_year` | integer |1900..السنةالحاليةفيالباكند؛ زوجالعمر |
| `condition` | `spec_condition` | varchar(32) |UNDER_CONSTRUCTION / NEW / USED؛ ليسstatus |
| `finishing` | `spec_finishing` | varchar(32) |UNFINISHED / PARTIAL / FULL |
| `furnishing` | `spec_furnishing` | varchar(32) |UNFURNISHED / PARTIAL / FULL |
| `hasElevator` | `spec_has_elevator` | boolean |مصعديخدمموضعالمعروض |
| `hasAirConditioning` | `spec_has_air_conditioning` | boolean |تكييفمركب، لاضمانتغطيةوحالة |
| `hasFittedKitchen` | `spec_has_fitted_kitchen` | boolean |مطبخراكب |
| `hasMaidRoom` | `spec_has_maid_room` | boolean |غرفةعاملة |
| `hasDriverRoom` | `spec_has_driver_room` | boolean |غرفةسائق |
| `hasPool` | `spec_has_pool` | boolean |وجود، لاخصوصيةمستنتجة |
| `hasYard` | `spec_has_yard` | boolean |وجود، لاخصوصيةمستنتجة |
| `hasBalcony` | `spec_has_balcony` | boolean |وجود، لاخصوصيةمستنتجة |
| `hasPrivateRoof` | `spec_has_private_roof` | boolean |سطحخاصصراحة |
| `electricityConnected` | `spec_electricity_connected` | boolean |موصولةفعلًا، لاشبكةقريبة |
| `waterConnected` | `spec_water_connected` | boolean |موصولةفعلًا |
| `sewerConnected` | `spec_sewer_connected` | boolean |موصولفعلًا |
| `hasIrrigation` | `spec_has_irrigation` | boolean |وجودريفيالمزرعة |
| `wellCount` | `spec_well_count` | integer |0..100؛ لايدّعيترخيصًا |
| `waterSource` | `spec_water_source` | varchar(32) |NETWORK / WELL / EXTERNAL_DELIVERY / MULTIPLE / OTHER / NONE؛ المجهولnull |
| `commercialScope` | `spec_commercial_scope` | varchar(32) |UNIT / WHOLE_BUILDING |
| `commercialSubtype` | `spec_commercial_subtype` | varchar(32) |SHOP / OFFICE / WAREHOUSE / SHOWROOM / OTHER |
| `otherTypeDescription` | `spec_other_type_description` | varchar(100) |اختياري؛ بعدtrim، الفارغnull، لااستنتاجمنه |

المجموع:4مساحات،12عددًا،13boolean،7enums،ونصواحد=37. مصدرالرئيسيةيحددأيالمساحاتالأربع، لكنه لايلغي`area` المطلوبةالقائمة. لايوجدحقلhasParkingمكرر، ولاسنةبناءأوحالة«فاخر»أوحقلنشاطمرخص.

## 4. الانطباق والتحقق

الانطباق تابع للجدول المعتمد في P03؛ تقديم false أو0 لحقل غير منطبق ليس بديلًا عن null. الغائب يحفظ؛ تقديم null صريح لمسح حقل غير منطبق يسمح بتنظيفه، دون حفظ قيمة غير منطبقة. لا تعميم لحقول السكن على الأرض أو المزرعة.

- LAND:landArea،parkingSpaces وخدماتالكهرباء/الماء/الصرف.
- APARTMENT:unitNetArea/unitGrossArea،غرف/حمامات/صالات،floorNumber،زوجالعمر،الحالة/التشطيب/التأثيث،والتجهيزاتالعشرة.
- VILLA:landArea/totalBuiltArea،غرف/حمامات/صالات،floorCount،زوجالعمر،القوائم الثلاث والتجهيزاتالعشرة.
- BUILDING:landArea/totalBuiltArea،floorCount والوحداتالثلاثوparkingSpaces،زوجالعمر،الحالة/التشطيب،مصعد/تكييف/مسبح/حوش/سطحخاص،وخدماتالكهرباء/الماء/الصرف؛ لا تأثيثأوغرفوحدةمفترضة.
- COMMERCIAL:commercialScope/commercialSubtype،حمامات ومواقف وعمر وحالة وتشطيب؛ ومصعد/تكييف/مطبخ/مسبح/حوش/شرفة/سطحخاص والخدمات. UNIT يسمحunitNetArea/unitGrossAreaوfloorNumber؛ WHOLE_BUILDING يسمحlandArea/totalBuiltAreaوfloorCountوالوحداتالثلاث. النطاقاختياريما لمتقدمالحقولالمرتبطةبه، ويُقرأمنالحالةالمحفوظةبعدالدمج.
- FARM:landArea/totalBuiltArea،parkingSpaces/hasPool/hasYard،electricityConnected/hasIrrigation/wellCount/waterSource. لا ننسبعمرأوتشطيبمبنىغيرموصوفللمزرعةكلها.
- OTHER:المساحاتالأربع،وصفالنوعالاختياري،والتجهيزاتالعشرة؛ لا غرفأوأدوارمستنتجة.

قواعداتساقعلىالحالةالنهائية:الصافي≤الإجمالي للوحدةنفسها؛ لا البناء≤الأرض؛ areaتساويالمصدرالمحددبمقارنةBigDecimal؛ المصدرالمحددلابدأنتوجدقيمتهوينطبقعلىالنوع؛ العمر/سنةتقديرهكلاهمامعلومانأوكلاهماnull ويقدمانمعًاعندالتغيير/المسح؛ أجزاءالوحداتومجموعهالاتتجاوزالإجماليإنكانمعلومًا، دونفرضالمساواةأوحسابإجماليمنجزء.

## 5. عقد الطلب والتعديل المقترح

يبقى POST/PUT الحاليان، والحقول القديمة المطلوبة كما هي. لا PATCH جديد أو مسار موازٍ أو صلاحيات جديدة. دلالات الجديد معتمدة في P03؛ المقترح هنا اسم قسمها وتمثيلها:

| ماوصللـspecifications | Create | Update |
|---|---|---|
| غائب |الكلnull |حفظكلالمواصفاتالمحفوظة |
| `{}` |الكلnull |بلا أثر |
| `null` |400 |400؛ ليسمسحًاجماعيًا |
| حقلمحددبقيمته |تحققوحفظ |دمجذلكالحقلثمتحققالنتيجة |
| حقلمحددبـnull |مجهول |مسحصريحللفرد، معقواعدالاتساق |

المساحات الجديدة: نصوص ASCII عشرية plain بلا أس أو فاصل تجميع، ضمن الدقة والمجال. الأرقام العربية/الفارسية تحولها واجهة P05 قبل الإرسال؛ لا تحويل ضمني غامض داخل API الجديد. العدادات تقبل توكن JSON integer فقط: لا `"3"` أو `3.0` أو `3e0` أو كسور يجري قصها. Boolean فقط true/false/null، لا `"false"` أو0. enums أسماء معتمدة حساسة للحالة، لا أرقام ordinal. الحقول المجهولة أو المكررة داخل specifications ترفض400 بدل إسقاط بيانات بصمت؛ هذا لا يغير سياسة الحقول المجهولة في الطلب القديم.

وسيلة التنفيذ دون dependency جديدة: قارئ Jackson موضعي خاص بالقسم الجديد يحصر المفاتيح والأنواع ويحتفظ بمجموعة الحضور، فلا يضيع الفرق بين الغائب والـnull. لا إعداد ObjectMapper عام، ولا معاني presence في الاستجابة. التحقق الشكلي وعديم الحالة يرد400 قبل PropertyRepository؛ أما القواعد التي تحتاج المخزون المحفوظ فتراجع في الخدمة بعد قراءته وقبل كتابته.

في التعديل: نسخة مرشحة مستقلة من المواصفات المحفوظة والحقول المقدمة، مع type/area المطلوبين؛ يتحقق المجال والانطباق والروابط، ثم يطبق التغيير على Property عند نجاح الجميع فقط. رفض التعارض لا يمس العنوان أو الأرقام أو updatedAt؛ لا نغير كيانًا managed قبل التحقق ثم نعتمد على rollback لإخفاء تعديل ذاكرة حدث فعلًا.

تغيير النوع أو نطاق التجاري لا يمسح قيمًا خفيًا؛ يجب إرسال null لكل الحقول التي ستصبح غير منطبقة وتصحيح المصدر صراحة. إذا غاب ذلك يرفض400. تأكيد الأدمن في واجهة P05، لا flag مبهم بالخادم يفتح حذفًا جماعيًا. العميل القديم المتوافق يستمر؛ تعارض area أو النوع يرفض حسب P03.

## 6. مثالwire مقترح

إنشاء شقة؛ بقية الحقول الاختيارية القائمة يمكن إرسالها كالمعتاد:

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
    "bathrooms": 2,
    "floorNumber": 0,
    "estimatedAgeYears": 8,
    "ageAsOfYear": 2026,
    "condition": "USED",
    "finishing": "FULL",
    "furnishing": "UNFURNISHED",
    "hasElevator": true,
    "parkingSpaces": 0
  }
}
```

مثال جزئي من الاستجابة، وليس حذفًا لبقية مفاتيحها: تبقى id والناشر والموقع والمساحات القديمة والتواريخ، وتضاف specifications بالمفاتيح المقدمة وقيم null لغيرها:

```json
{
  "area": "150.25",
  "price": "9007199254740993.01",
  "specifications": {
    "primaryAreaSource": "UNIT_NET",
    "landArea": null,
    "unitNetArea": "150.25",
    "unitGrossArea": "180.00",
    "totalBuiltArea": null,
    "bedrooms": 3,
    "parkingSpaces": 0,
    "hasElevator": true,
    "hasPool": null
  }
}
```

تغيير مطبخ ومسح شرفة لشقة محفوظة يرسل ضمن PUT الكامل القديم: `"specifications":{"hasFittedKitchen":true,"hasBalcony":null}`؛ لا يمسح مصعدها أو عمرها أو مساحاتها. تصحيح المساحة الرئيسية المرتبطة ينسق `area` والحقل المسمى في الطلب نفسه. لفك الربط صراحة: `"primaryAreaSource":null` مع قيمة area المطلوبة، دون مسح المساحات المسماة.

الأخطاء400 تستخدم بنية ErrorResponse الموجودة؛ مفاتيح أخطاء المواصفات تحت `message` مثل `specifications.unitNetArea`، لا استجابة نجاح كاذبة أو خطأ DB500. 401/403/404 وسياسة ACTIVE/SOLD وCSRF والناشر تبقى كما هي.

## 7. خطةJPA والترقية والسلامة

- `PropertySpecifications` كائنقيمةمضمن، لاRepositoryثانٍ ولاحسابناشرمنالطلب. القيمقدتعودnullللمضمنعندقراءةسجلقديمكلأعمدتهالجديدةnull؛ Mapperينشئاستجابةفارغةدونتعديلالكيان.
- `PropertySpecificationsRequest/Response` ودمج/تحققموضعي، معإبقاءالمحولاتوالـconstructorsالمستخدمةحاليًا متوافقةقدرالإمكان؛ لاتكرارتحويلPropertyكاملًا.
- ملفترقيةجديدمثل`backend/db/20261018-property-specifications.sql`، داخلtransactionوبفحصوجودpublic.propertiesوالمخططالأساسي، وفشلآمنعندوجودترقيةسابقة/جزئيةأوأعمدةغيرمتوقعة؛ لا`IF NOT EXISTS`يخفيتعارضأنواع. التاريخاسمملفمقترحلاSQLمطبق.
- الأعمدةnullableدونUPDATE/backfill/defaults، معقيودمجال/enums/زوجالعمر/مصدرالمساحةوصافيهاأوجزءالوحداتوالانطباق. السنةالحاليةتراجعفيالخدمة؛ قيدDBثابت1900..9999لاتاريخمتحركضمنCHECK. هذهطبقةدفاعإضافية؛numericقديقربكتابةمباشرةعلىDB، لذادقةالطلبتتحقققبلالحفظ.
- يبقىbaselineالحاليملفتهيئةتاريخيةولايدمجمراجعةجديدةفيمخططمطبق. runnerالمعزوليطبقالـbaselineثمترقيةP04، ويشغلتطبيقًاHibernatevalidate؛ يختبرسجلًقديمًامعبقاءالقيموالعددوالعلاقاتقبل/بعدالترقيةوالفشلالذريداخلبيئةاختبارفقط.
- لاتشغيلترقيةمنSpringSQLinitأوddl-autoupdate. لاتطبيقلقاعدةdb_rushdولاDockerrestartولاإعدادمستخدمأومفاتيح. قبلالنشرلاحقًاتطبقخطةترقيةومراجعةونسخةاحتياطيةباعتمادمنفصل؛ الرجوعلايعنيحذفالأعمدةوالبياناتالجديدة. تشغيلكاتبقديملناسمواصفاتحديثةيحتاجحذروخطة؛ قيودDBقدترفضتعارضهلكنهلنيعطي400الموضحبالخدمةالجديدة.

## 8. التوافق والتسليم

إضافة حقل response ليست تغيير نوع للحقول القائمة، لكن عميلًا خارجيًا يرفض مفاتيح JSON إضافية سيحتاج تحديثًا. واجهة المشروع الحالية تقرأ الحقول المعروفة وتتجاهل الإضافة؛ الفورم يرسل whitelist قديمة، فيحفظ الباكند المواصفات الغائبة بعد P04. سيعجز الفورم القديم عن تعديل متعارض لنوع/area في عقار غني بالمواصفات حتى P05؛ يظهر400 بدل حذف خفي، وهو الاستثناء المعتمد في P03.

R1/R3 تستخدم DTO وتحويلًا مستقلين؛ لا تضاف specifications إلى طلب الاحتياجات أو نتيجة المطابقة ولا مرشحات أو أوزان جديدة. لا تعديل للثيم أو الخط أو صور أو خرائط أو AI.

خطةالاختباربعدالاعتماد:نجاحكلنوعومنعغيرالمنطبقحتىfalse/0؛ null/الغائب/القسمالفارغ/القسمnull؛ حدودكلحقلوالكسوروالصيغوالأسسالضخمة؛ طلبقديموسجلقديم؛ تساويمصدرالمساحةو0.01اختلافو500/500.00تطابق؛ المحافظةعلىالغائبوالمسحصريح؛ تغييرالنوع/النطاقوالزوجوالإجماليات؛ no-save/no-partial وupdatedAtunchanged؛ round-tripمعflush/clearوكلقراءاتProperty؛ ADMIN/BUYER/SELLER/CSRFوعدمكشفمسودة؛ اختباراتR1/R3القديمةوترقيةPGوالرجوعالآمنمنالفشل.

بوابة القرار عند عرض المقترح (سجل تاريخي): طلب اعتماد اسم specifications، و37 عمودًا nullable في properties بـEmbedded، وأسماء wire والأنواع والاستجابة الإضافية والترقية التي تُراجع فقط. كان التنفيذ مشروطًا بطلب المتابعة بعد عرض الأثر؛ سجل المتابعة التالي يوضح انتقاله إلى التنفيذ. يظل الفورم P05 والمطابقة حسب النوع P06 حزمتين تاليتين.

سجل المتابعة: بعد عرض هذا الأثر وسؤال الاعتماد، طلب المستخدم «كمل التاسك الي بعد»؛ وُضح أن المتابعة لتنفيذP04 على المقترح نفسه دون بدءP05. نُفذت الإضافة على هذا الأساس، مع قفل صف العقار في مسار التعديل لحماية قراءة/دمج/حفظ متزامنة، دون قفل القراءات العامة أو تغيير عقد الحقول القديمة.

## 9. التحقق في مرحلة الفحص فقط

شغل `mvn clean test` في18 أكتوبر2026 على الباكند الموجود دون تعديل كوده: **278 اختبارًا،0 فشل،0 أخطاء،0 تخطي،BUILD SUCCESS**. جرى تثبيت datasource صراحة على H2 في الذاكرة باسم `rushd_p04_inspection`، مع driver/dialect خاصين بـH2 وcreate-drop داخل الذاكرة فقط وSQL init معطل. فحصت أسماء متغيرات البيئة وملفات Maven المؤثرة قبل التشغيل دون كشف قيم أو أسرار؛ لا تجاوزات موروثة أو ملفات إعدادMaven محلية مؤثرة.

هذا خط أساس تاريخي لمرحلة الفحص قبل التنفيذ، وليس نتائج P04 النهائية. لم يشغل runner أو Docker أو SQL على أي قاعدة في مرحلة الفحص الأولى وكانت تغييراتها توثيقية؛ تلتها مرحلة التنفيذ والتحقق المعزول الموثقة في [تقرير P04](p04-property-specifications-verification.md). لا تطبيق على قاعدة المشروع الفعلية ولا commit/merge/push.
