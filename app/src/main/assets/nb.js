/* جسر الشبكة: يمرّر طلبات /api إلى السيرفر عبر الكود الأصلي للتطبيق */
(function () {
  if (!window.Native || window.__nbReady) return;
  window.__nbReady = 1;
  var S = window.__SERVER = Native.server();
  var pend = {}, n = 0, of = window.fetch;
  function target(u) {
    try {
      var a = new URL(u, location.href);
      if ((a.origin === location.origin || a.origin === new URL(S).origin) && (a.pathname.indexOf('/api/') === 0 || a.pathname === '/config.json')) return S + a.pathname + a.search;
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
  var sses = {}, sn = 0;
  window.__nbSse = function (id, ev, data) {
    var es = sses[id]; if (!es) return;
    if (ev === 'error') { delete sses[id]; es.readyState = 2; }
    var e = { type: ev, data: data }, hs = es.h[ev] || [];
    hs.slice().forEach(function (f) { try { f(e); } catch (x) {} });
    var on = es['on' + ev]; if (on) try { on(e); } catch (x) {}
  };
  window.EventSource = function (url) {
    var t = target(url) || url, id = 's' + (++sn), es = this;
    es.url = url; es.readyState = 1; es.h = {};
    es.addEventListener = function (ev, f) { (es.h[ev] = es.h[ev] || []).push(f); };
    es.removeEventListener = function (ev, f) { es.h[ev] = (es.h[ev] || []).filter(function (x) { return x !== f; }); };
    es.close = function () { es.readyState = 2; delete sses[id]; Native.sseClose(id); };
    sses[id] = es;
    Native.sseStart(id, t);
  };

  /* ---- مزامنة فورية: أي حدث من السيرفر يُحدّث كل الأقسام فوراً ---- */
  var lastPoke = 0;
  function pokeAll() {
    var t = Date.now(); if (t - lastPoke < 800) return; lastPoke = t;
    try { document.dispatchEvent(new Event('visibilitychange')); } catch (e) {}
    try { window.dispatchEvent(new Event('focus')); } catch (e) {}
    try { if (window.__adminTick) window.__adminTick(); } catch (e) {}
  }
  window.__pokeAll = pokeAll;
  var ps = null, pd = 1000;
  function pokeConnect() {
    if (ps) { try { ps.close(); } catch (e) {} ps = null; }
    ps = new EventSource('/api/poke/stream');
    ps.addEventListener('hello', function () { pd = 1000; pokeAll(); });
    ps.addEventListener('poke', pokeAll);
    ps.addEventListener('error', function () { try { ps.close(); } catch (e) {} ps = null; setTimeout(pokeConnect, pd); pd = Math.min(pd * 1.7, 60000); });
  }
  window.__nbResume = function () { pokeAll(); if (!ps) { pd = 1000; pokeConnect(); } };
  setTimeout(pokeConnect, 1200);
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
