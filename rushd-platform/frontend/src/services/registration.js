// Local checks improve feedback; the server remains responsible for validation and authorization.
export function registrationErrors({ name, email, password }) {
  const errors = {}
  if (!name.trim()) errors.name = 'الاسم مطلوب.'
  else if (name.length > 255) errors.name = 'الاسم يجب ألا يتجاوز 255 حرفًا.'
  if (!email.trim()) errors.email = 'البريد الإلكتروني مطلوب.'
  else if (email.length > 255 || email.trim().toLowerCase().length > 255) errors.email = 'البريد الإلكتروني طويل جدًا.'
  if (!password.trim()) errors.password = 'كلمة المرور مطلوبة.'
  else if (password.length < 6) errors.password = 'كلمة المرور يجب أن تكون 6 أحرف على الأقل.'
  else if (new TextEncoder().encode(password).length > 72) errors.password = 'كلمة المرور تتجاوز 72 بايت. قد يستهلك الحرف العربي أكثر من بايت.'
  return errors
}

export function registrationErrorMessage(error) {
  const status = error?.response?.status
  if (status === 400) return 'تعذر إنشاء الحساب. تحقق من الاسم والبريد وكلمة المرور ثم حاول مجددًا.'
  if (status === 409) return 'هذا البريد مستخدم بالفعل. سجّل الدخول أو استخدم بريدًا آخر.'
  if (status === 429) return 'تجاوزت عدد محاولات التسجيل المسموح. انتظر دقيقة ثم حاول مجددًا.'
  if (status === 403) return 'تعذر التحقق من أمان الطلب. أعد تحميل الصفحة وحاول مجددًا.'
  if (!error?.response) return 'تعذر الاتصال بالخدمة. قد يكون الحساب أُنشئ إذا انقطع الاتصال أثناء الإرسال؛ جرّب تسجيل الدخول قبل المحاولة مجددًا.'
  return 'تعذر إنشاء الحساب حاليًا. حاول مرة أخرى لاحقًا.'
}
