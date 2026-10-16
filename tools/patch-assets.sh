#!/bin/sh
# يُطبَّق بعد نسخ public/ الجديدة إلى app/src/main/assets (نسخ nb.js قبل الحذف وإعادته)
A=app/src/main/assets
sed -i 's/location\.origin/(window.__SERVER||location.origin)/g' $A/index.html $A/merchant-ext.js
sed -i '0,/<head>/s//<head><script src="\/nb.js"><\/script>/' $A/index.html $A/admin.html
python3 - <<'P'
p='app/src/main/assets/google-ext.js'
s=open(p,encoding='utf-8').read()
key="    if (!(window.google && google.accounts && google.accounts.oauth2)) {"
add="""    if (window.Native && Native.googleSignIn) { // داخل التطبيق: دخول Google الأصلي
      setBusy(true);
      window.__nbGoogle = (t, err) => { if (t) return finish('idt:' + t); setBusy(false); if (err && err !== 'cancel') fail('تعذر الدخول عبر Google: ' + err); };
      return Native.googleSignIn(cfg.clientId);
    }
"""
if "Native.googleSignIn" not in s and key in s:
    s=s.replace(key,add+key,1)
open(p,'w',encoding='utf-8').write(s)
P
