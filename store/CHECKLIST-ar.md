# قائمة النشر على Google Play

الملفات الجاهزة في هذا المستودع:
- التطبيق AAB: Releases → latest → app-release.aab
- الأيقونة: store/icon-512.png
- الصورة الترويجية: store/feature-graphic-1024x500.png
- نصوص المتجر: store/listing-ar.md
- نموذج Data safety: store/data-safety-ar.md
- سياسة الخصوصية وحذف الحساب: docs/ (تُنشر على GitHub Pages)

الخطوات:
1. سجّل حساب مطوّر على play.google.com/console (25$) وأكمل التحقق من الهوية.
2. أنشئ تطبيقاً جديداً (App → Create app)، لغة افتراضية عربية.
3. App signing: اترك Play App Signing مفعّلاً وارفع app-release.aab. مفتاح الرفع هو الذي وُقّع به الملف.
4. مهم: بعد الرفع افتح App integrity → App signing وانسخ بصمة SHA-1 لـ "App signing key certificate"،
   ثم أضفها كـ Android OAuth client جديد في Google Cloud (نفس اسم الحزمة com.app.wrapper)،
   وإلا لن يعمل الدخول عبر Google في نسخة المتجر.
5. أكمل App content: سياسة الخصوصية، Data safety، Content rating، Target audience (18+)، Ads (لا)،
   Financial features، وحذف الحساب (رابط docs/delete-account.html).
6. إن كان حسابك شخصياً وجديداً: شغّل اختباراً مغلقاً بالعدد والمدة التي تطلبها Google قبل طلب الإنتاج.
7. أرسل للمراجعة.

نواقص يجب إغلاقها قبل التقديم:
- تم: زر «حذف حسابي» داخل التطبيق (الملف الشخصي) + رابط ويب docs/delete-account.html. في نموذج Play Console ضع رابط صفحة الحذف، ورابط الخصوصية، واملأ Data safety بأن المستخدم يستطيع طلب الحذف.
- استبدل alzmat66@gmail.com في docs/*.html ببريدك الحقيقي.
- التقط لقطات الشاشة.
