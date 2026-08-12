const API = '';
function token() { return localStorage.getItem('token') || ''; }
function authHeaders() {
  const h = {};
  if (token()) h['X-Session-Token'] = token();
  return h;
}
async function api(path, opts={}) {
  const headers = Object.assign({'Content-Type':'application/json'}, authHeaders(), opts.headers||{});
  if (opts.body instanceof FormData) delete headers['Content-Type'];
  const res = await fetch(API + path, Object.assign({}, opts, { headers }));
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = { message: text }; }
  if (!res.ok) throw new Error(data.message || data.error || text || res.statusText);
  return data;
}
