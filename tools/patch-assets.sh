#!/bin/sh
# يُطبَّق بعد نسخ public/ الجديدة إلى app/src/main/assets (نسخ nb.js قبل الحذف وإعادته)
A=app/src/main/assets
grep -q '__SERVER||location.origin' $A/index.html || sed -i 's/location\.origin/(window.__SERVER||location.origin)/g' $A/index.html $A/merchant-ext.js
for f in index.html admin.html; do grep -q 'src="/nb.js"' $A/$f || sed -i '0,/<head>/s//<head><script src="\/nb.js"><\/script>/' $A/$f; done
python3 - <<'P'
p='app/src/main/assets/google-ext.js'
s=open(p,encoding='utf-8').read()
key="    if (!(window.google && google.accounts && google.accounts.oauth2)) {"
add="""    if (window.Native && Native.googleSignIn) { // داخل التطبيق: دخول Google الأصلي
      setBusy(true);
      window.__nbGoogle = (t, err) => {
        if (t) return finish('idt:' + t);
        setBusy(false);
        // إلغاء المستخدم الفعلي صامت؛ أي «إلغاء» آخر (خطأ بصمة/معرّف) يُعرض بسببه
        if (err && !/^cancel:?\\s*(activity is cancelled by the user\\.?)?\\s*$/i.test(err)) fail('تعذر الدخول عبر Google: ' + err);
      };
      setTimeout(() => { if (busy) setBusy(false); }, 120000);
      return Native.googleSignIn(cfg.clientId);
    }
"""
if "Native.googleSignIn" not in s and key in s:
    s=s.replace(key,add+key,1)
open(p,'w',encoding='utf-8').write(s)
P
python3 - <<'P'
p='app/src/main/assets/admin-ext.js'
s=open(p,encoding='utf-8').read()
a="  // تحديث دوري كل 5 ثوانٍ\n  setInterval(async () => {"
b="  }, 5000);\n})();"
if a in s and b in s and "__adminTick" not in s:
    s=s.replace(a,"  // تحديث دوري كل 5 ثوانٍ\n  window.__adminTick = async () => {",1)
    i=s.rindex(b)
    s=s[:i]+"  }; setInterval(window.__adminTick, 5000);\n})();"+s[i+len(b):]
open(p,'w',encoding='utf-8').write(s)
P
# حذف الحساب من داخل التطبيق (متطلب Google Play): ينسخ الملف ويضيف وسمه إن لم يوجد
cp tools/account-delete.js $A/account-delete.js
cp docs/privacy-policy.html docs/delete-account.html $A/ 2>/dev/null
grep -q 'account-delete.js' $A/index.html || sed -i 's#<script src="/user-ext.js"></script>#<script src="/user-ext.js"></script><script src="/account-delete.js?v=1"></script>#' $A/index.html
# مزامنة العدادات مع السيرفر (السيرفر هو المرجع)
cp tools/counters-sync.js $A/counters-sync.js
grep -q 'counters-sync.js' $A/index.html || sed -i 's#<script src="/account-delete.js?v=1"></script>#<script src="/account-delete.js?v=1"></script><script src="/counters-sync.js?v=1"></script>#' $A/index.html
