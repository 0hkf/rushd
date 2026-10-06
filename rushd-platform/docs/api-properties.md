# عروض العقارات — تحديث 6 أكتوبر 2026

## الصلاحيات

GET /api/properties عام ويعرض ACTIVE فقط. GET /api/properties/{id} عام للحالات ACTIVE وSOLD؛ المسودات والحالات غير المنشورة متاحة للأدمن فقط، وغيره يحصل على 404.

POST /api/properties وPUT /api/properties/{id} يتطلبان access cookie لحساب ADMIN مع CSRF. الأدمن يستطيع تعديل جميع العقارات، بما فيها القديمة، دون تغيير ناشرها الأصلي. الحساب غير المسجل يحصل على 401 وغير الأدمن على 403.

GET /api/admin/properties للأدمن فقط، يعرض جميع الحالات بصفحات من 20 سجلًا، page يبدأ من صفر، وترتيب createdAt ثم id تنازليًا.

## البيع والإيجار

- listingType: SALE أو RENT، الافتراضي SALE عند غياب الحقل؛ null غير مقبول.
- rentalPeriod: MONTHLY أو YEARLY؛ مطلوب للإيجار، ويجب أن يكون null أو غائبًا للبيع.
- price: قيمة موجبة؛ إجمالي سعر البيع أو قيمة الإيجار للفترة المحددة. لا تحويل آلي بين الشهري والسنوي.
- purpose يبقى استخدام العقار، ولا يستبدل نوع العرض.
- status يبقى ACTIVE/DRAFT/INACTIVE/SOLD؛ حالة «مؤجر» ليست منفذة.

الطلب يحافظ على الحقول المطلوبة السابقة: title,type,city,district,area,price,purpose. PUT هو تعديل كامل للمواصفات، وليس PATCH؛ omission لحقول الموقع الاختيارية يزيل قيمها، وغياب listingType يجعله SALE.

مثال إنشاء إيجار:

```json
{
  "title":"شقة في حي الياسمين",
  "type":"APARTMENT",
  "city":"الرياض",
  "district":"الياسمين",
  "area":150,
  "price":4500,
  "purpose":"RESIDENTIAL",
  "listingType":"RENT",
  "rentalPeriod":"MONTHLY",
  "status":"ACTIVE",
  "neighborhood":"حي الياسمين"
}
```

## الموقع المنظم

googlePlaceId (255 حرفًا)، formattedAddress (500)، neighborhood (150)، latitude من -90 إلى 90، longitude من -180 إلى 180. جميعها اختيارية وتقبل null. المدينة والحي يبقيان مطلوبين.

التخزين latitude/longitude من نوع numeric(10,7). يوجد فهرس غير فريد على google_place_id؛ عدة عقارات يمكن أن تشترك في الموقع نفسه. لا خرائط ولا URL موقع ولا اتصال بخدمات Google ولا مفتاح API.

## الاستجابة والمرشحات

الاستجابة تعيد listingType,rentalPeriod وجميع حقول الموقع السابقة. بيانات الناشر أصبحت publisher وpublisherId وpublisherName بدل seller؛ publisher يحتوي id,name,role. هذا تغيير مقصود لعقد الاستجابة؛ جميع مستهلكي العقد يجب تحديثهم، والواجهة الحالية محدثة.

القائمة العامة تدعم listingType وrentalPeriod إضافة إلى city,district,type,minPrice,maxPrice,page,size,sort. size من 1 إلى 100. minPrice/maxPrice يرشحان القيمة المسجلة، لذلك يفضل تحديد فترة الإيجار عند مقارنة أسعار الإيجار.

## ترقية PostgreSQL

إعداد JPA هو ddl-auto: none؛ لا تتم ترقية القاعدة تلقائيًا. قبل تشغيل الإصدار على قاعدة قديمة، راجع وخذ نسخة احتياطية ثم طبّق [ملف SQL](../backend/db/20261006-admin-sale-rent.sql) عبر أداة PostgreSQL المعتمدة. الملف يفترض وجود جدول properties ومخطط الإصدار السابق؛ ليس إنشاءً لقاعدة فارغة.

الترقية تضيف أعمدة الموقع ونوع العرض وفترة الإيجار، وتعتبر السجلات القديمة للبيع، وتضيف تحققًا وفهرسًا. لا تغير seller_id ولا الحسابات القديمة. لم يُطبق الملف تلقائيًا على قاعدة فعلية.

حساب أدمن موجود أو تهيئة إدارية موثوقة مطلوب للإدارة؛ التسجيل العام لا ينشئ أدمن.
