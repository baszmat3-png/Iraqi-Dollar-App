ملفات السيرفر (ترفع إلى مستودع السيرفر على Render، وليست جزءاً من الـ APK):
1) google-auth-ext.js : يستبدل الملف الحالي (يدعم الدخول الأصلي عبر Google من التطبيق).
2) poke-ext.js        : ملف جديد بجانب server.js، ويُضاف في server.js بعد app.set('trust proxy', 1); السطر:
                        require('./poke-ext')(app);
