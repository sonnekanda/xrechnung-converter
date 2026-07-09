// Zentraler API-Zugriff. Haengt das JWT an und liefert verstaendliche Fehler.
const TOKEN_KEY = "erechnung.token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}
export function setToken(t) {
  if (t) localStorage.setItem(TOKEN_KEY, t);
  else localStorage.removeItem(TOKEN_KEY);
}

async function request(method, path, { body, form } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  let payload;
  if (form) {
    payload = form;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }
  const res = await fetch(`/api/v1${path}`, { method, headers, body: payload });
  if (res.status === 204) return null;
  const isJson = (res.headers.get("content-type") || "").includes("application/json");
  const data = isJson ? await res.json() : await res.text();
  if (!res.ok) {
    const msg = isJson && data && data.meldung ? data.meldung : "Es ist ein Fehler aufgetreten.";
    throw new Error(msg);
  }
  return data;
}

export const api = {
  login: (email, password) => request("POST", "/auth/login", { body: { email, password } }),
  branding: () => request("GET", "/branding"),
  invoices: () => request("GET", "/invoices"),
  invoice: (id) => request("GET", `/invoices/${id}`),
  patchField: (id, block, feld, wert, positionIndex = null) =>
    request("PATCH", `/invoices/${id}`, { body: { block, feld, wert, positionIndex } }),
  generate: (id) => request("POST", `/invoices/${id}/generate`),
  reopen: (id) => request("POST", `/invoices/${id}/reopen`),
  status: (id) => request("GET", `/files/${id}/status`),
  stammdaten: () => request("GET", "/stammdaten"),
  createStammdaten: (r) => request("POST", "/stammdaten", { body: r }),
  deleteStammdaten: (id) => request("DELETE", `/stammdaten/${id}`),
  upload: (pdf, csv) => {
    const fd = new FormData();
    if (pdf) fd.append("pdf", pdf);
    if (csv) fd.append("csv", csv);
    return request("POST", "/files/upload", { form: fd });
  },
  // Geschuetzte Downloads/Anzeige: mit JWT laden und als Blob-URL bereitstellen.
  blobUrl: async (path) => {
    const res = await fetch(path, { headers: { Authorization: `Bearer ${getToken()}` } });
    if (!res.ok) throw new Error("Datei konnte nicht geladen werden.");
    return URL.createObjectURL(await res.blob());
  },
  download: async (path, filename) => {
    const res = await fetch(path, { headers: { Authorization: `Bearer ${getToken()}` } });
    if (!res.ok) {
      const isJson = (res.headers.get("content-type") || "").includes("application/json");
      const data = isJson ? await res.json() : null;
      throw new Error(data?.meldung || "Download nicht moeglich.");
    }
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  },
  pdfPath: (id) => `/api/v1/invoices/${id}/pdf`,
  exportPath: (id) => `/api/v1/invoices/${id}/export`,
  reportPath: (id) => `/api/v1/invoices/${id}/validation-report`,
};
