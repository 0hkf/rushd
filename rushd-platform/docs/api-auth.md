# توثيق Authentication API — مشروع Rushd

## المتطلبات المحلية

| الخدمة | الوصف |
|---|---|
| PostgreSQL | قاعدة البيانات الرئيسية (port 5432) |
| Java 17+ | لتشغيل الـ backend |
| Maven | لبناء المشروع |

---

## تشغيل الخدمات المحلية

### تشغيل PostgreSQL عبر Docker

```bash
# من مجلد rushd-platform/
docker compose up -d
```

### التحقق من تشغيل قاعدة البيانات

```bash
docker compose ps
```

### تشغيل الـ Backend

```bash
cd rushd-platform/backend
mvn spring-boot:run
```

الـ backend يعمل على:
```
http://localhost:8080
```

---

## نظرة عامة على الـ Endpoints

### Endpoints العامة (لا تحتاج token)

| Method | Endpoint | الوصف |
|---|---|---|
| `GET` | `/health` | فحص حالة الـ API |
| `POST` | `/api/auth/register` | تسجيل مستخدم جديد |
| `POST` | `/api/auth/login` | تسجيل الدخول والحصول على JWT |

### Endpoints المحمية (تحتاج Bearer token)

| Method | Endpoint | الوصف |
|---|---|---|
| `GET` | `/api/auth/me` | معلومات المستخدم المُسجَّل دخوله حالياً |

---

## الأدوار (Roles)

| Role | الوصف |
|---|---|
| `BUYER` | مشتري — يتصفح العقارات |
| `SELLER` | بائع — ينشر العقارات |
| `ADMIN` | مدير — يدير المنصة |

---

## POST /api/auth/register

### الوصف
تسجيل مستخدم جديد في المنصة. يُحوَّل الـ email إلى lowercase تلقائياً. كلمة المرور تُخزَّن كـ BCrypt hash — لا تُعاد أبداً في أي response.

### Request Body

```json
{
  "name": "اسم المستخدم",
  "email": "user@example.com",
  "password": "password123",
  "role": "BUYER"
}
```

| الحقل | النوع | مطلوب | القيود |
|---|---|---|---|
| `name` | String | ✅ | غير فارغ |
| `email` | String | ✅ | صيغة email صحيحة |
| `password` | String | ✅ | 6 أحرف على الأقل |
| `role` | String | ✅ | `BUYER` أو `SELLER` أو `ADMIN` |

### Response — نجاح (201 Created)

```json
{
  "id": 1,
  "name": "Demo Buyer",
  "email": "buyer@rushd.local",
  "role": "BUYER",
  "createdAt": "2026-07-13T10:00:00"
}
```

> **ملاحظة:** حقل `password` غائب تماماً من الـ response.

### Response — بريد مكرر (409 Conflict)

```json
{
  "message": "البريد الإلكتروني مستخدم بالفعل"
}
```

### Response — بيانات غير صحيحة (400 Bad Request)

```json
{
  "name": "الاسم مطلوب",
  "email": "صيغة البريد الإلكتروني غير صحيحة",
  "password": "كلمة المرور يجب أن تكون 6 أحرف على الأقل"
}
```

### curl — تسجيل BUYER ناجح

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Demo Buyer",
    "email": "buyer@rushd.local",
    "password": "Buyer123",
    "role": "BUYER"
  }'
```

### curl — تسجيل SELLER ناجح

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Demo Seller",
    "email": "seller@rushd.local",
    "password": "Seller123",
    "role": "SELLER"
  }'
```

### curl — تسجيل ADMIN ناجح

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Demo Admin",
    "email": "admin@rushd.local",
    "password": "Admin123",
    "role": "ADMIN"
  }'
```

### curl — بريد إلكتروني مكرر (409)

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Demo Buyer",
    "email": "buyer@rushd.local",
    "password": "Buyer123",
    "role": "BUYER"
  }'
```

### curl — اسم فارغ (400)

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "",
    "email": "test@rushd.local",
    "password": "Test123",
    "role": "BUYER"
  }'
```

### curl — بريد إلكتروني غير صحيح (400)

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "not-an-email",
    "password": "Test123",
    "role": "BUYER"
  }'
```

### curl — كلمة مرور أقل من 6 أحرف (400)

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test2@rushd.local",
    "password": "123",
    "role": "BUYER"
  }'
```

### curl — دور غير صحيح (400)

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test3@rushd.local",
    "password": "Test123",
    "role": "MANAGER"
  }'
```

---

## POST /api/auth/login

### الوصف
تسجيل الدخول بالبريد الإلكتروني وكلمة المرور. يُعيد JWT token صالح لمدة 24 ساعة (86,400,000 millisecond).

### Request Body

```json
{
  "email": "buyer@rushd.local",
  "password": "Buyer123"
}
```

| الحقل | النوع | مطلوب |
|---|---|---|
| `email` | String | ✅ |
| `password` | String | ✅ |

### Response — نجاح (200 OK)

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQlVZRVIiLCJzdWIiOiJidXllckBydXNoZC5sb2NhbCIsImlhdCI6MTc1MjM5ODQwMCwiZXhwIjoxNzUyNDg0ODAwfQ.XXXX",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "id": 1,
    "name": "Demo Buyer",
    "email": "buyer@rushd.local",
    "role": "BUYER",
    "createdAt": "2026-07-13T10:00:00"
  }
}
```

### Response — كلمة مرور خاطئة أو بريد غير موجود (401 Unauthorized)

```json
{
  "message": "Invalid email or password"
}
```

### curl — تسجيل دخول ناجح

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "buyer@rushd.local",
    "password": "Buyer123"
  }'
```

### curl — كلمة مرور خاطئة (401)

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "buyer@rushd.local",
    "password": "wrongpassword"
  }'
```

### curl — بريد غير موجود (401)

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "ghost@rushd.local",
    "password": "anything"
  }'
```

---

## GET /api/auth/me

### الوصف
يُعيد معلومات المستخدم المُسجَّل دخوله حالياً بناءً على الـ JWT token المرسَل في الـ header.

### كيفية استخدام الـ Bearer token

بعد تسجيل الدخول، خذ قيمة `token` من الـ response وأضفها في كل طلب محمي بهذه الطريقة:

```
Authorization: Bearer <token>
```

### Response — نجاح (200 OK)

```json
{
  "id": 1,
  "name": "Demo Buyer",
  "email": "buyer@rushd.local",
  "role": "BUYER",
  "createdAt": "2026-07-13T10:00:00"
}
```

> **ملاحظة:** حقل `password` غائب تماماً من الـ response.

### Response — بدون token أو token غير صحيح (401 Unauthorized)

```json
{
  "status": 401,
  "message": "Authentication is required"
}
```

### curl — بدون token (401)

```bash
curl -s -X GET http://localhost:8080/api/auth/me
```

### curl — token غير صحيح (401)

```bash
curl -s -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer this.is.an.invalid.token"
```

### curl — token صحيح (200) — خطوتان

```bash
# الخطوة 1: تسجيل الدخول والحصول على الـ token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@rushd.local","password":"Buyer123"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# الخطوة 2: استخدام الـ token
curl -s -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

---

## ملخص رموز HTTP

| الحالة | الكود | الوصف |
|---|---|---|
| تسجيل ناجح | `201 Created` | المستخدم أُنشئ بنجاح |
| دخول ناجح | `200 OK` | token صالح في الـ response |
| بيانات `/me` | `200 OK` | معلومات المستخدم |
| بيانات غير صحيحة | `400 Bad Request` | validation error |
| بريد مكرر | `409 Conflict` | الـ email مستخدم بالفعل |
| بيانات دخول خاطئة | `401 Unauthorized` | email أو password غلط |
| بدون token | `401 Unauthorized` | `Authentication is required` |
| token منتهي/غير صحيح | `401 Unauthorized` | `Authentication is required` |

---

## المستخدمون التجريبيون (للبيئة المحلية فقط)

> **تحذير:** هذه البيانات للتطوير المحلي فقط. لا تستخدمها في الإنتاج.

| الاسم | البريد الإلكتروني | الدور |
|---|---|---|
| Demo Buyer | `buyer@rushd.local` | BUYER |
| Demo Seller | `seller@rushd.local` | SELLER |
| Demo Admin | `admin@rushd.local` | ADMIN |

لإنشاء هؤلاء المستخدمين تلقائياً، شغّل:

```bash
bash rushd-platform/scripts/test-auth.sh
```
