/* جسر الشبكة: يمرّر طلبات /api إلى السيرفر عبر الكود الأصلي للتطبيق */
(function () {
  if (!window.Native || window.__nbReady) return;
  window.__nbReady = 1;
  var S = window.__SERVER = Native.server();
  var pend = {}, n = 0, of = window.fetch;
  try { delete window.EventSource; window.EventSource = undefined; } catch (e) {} // يعتمد التطبيق على الفحص كل 5 ثوانٍ
  function target(u) {
    try {
      var a = new URL(u, location.href);
      if (a.origin === location.origin && (a.pathname.indexOf('/api/') === 0 || a.pathname === '/config.json')) return S + a.pathname + a.search;
    } catch (e) {}
    return null;
  }
  function b64(buf) { var b = new Uint8Array(buf), s = '', i; for (i = 0; i < b.length; i += 8192) s += String.fromCharCode.apply(null, b.subarray(i, i + 8192)); return btoa(s); }
  function unb64(s) { var r = atob(s), b = new Uint8Array(r.length), i; for (i = 0; i < r.length; i++) b[i] = r.charCodeAt(i); return b; }
  window.__nb = function (id, status, hb, bb) {
    var p = pend[id]; if (!p) return; delete pend[id];
    if (status < 0) return p.rej(new TypeError('Network request failed'));
    var h = {}; try { h = JSON.parse(new TextDecoder().decode(unb64(hb))); } catch (e) {}
    var nobody = status === 204 || status === 205 || status === 304;
    p.res(new Response(nobody ? null : unb64(bb), { status: status, headers: h }));
  };
  window.fetch = function (i, init) {
    var req;
    try { req = new Request(i, init); } catch (e) { return of.apply(this, arguments); }
    var t = target(req.url);
    if (!t) return of.apply(this, arguments);
    var m = req.method.toUpperCase();
    return (m === 'GET' || m === 'HEAD' ? Promise.resolve(new ArrayBuffer(0)) : req.arrayBuffer()).then(function (buf) {
      return new Promise(function (res, rej) {
        var id = 'r' + (++n), h = {};
        req.headers.forEach(function (v, k) { h[k] = v; });
        pend[id] = { res: res, rej: rej };
        Native.request(id, m, t, JSON.stringify(h), b64(buf));
      });
    });
  };
})();
