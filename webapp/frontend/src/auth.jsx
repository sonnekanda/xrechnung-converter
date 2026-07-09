import { createContext, useContext, useEffect, useState } from "react";
import { api, setToken } from "./api";

const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const raw = localStorage.getItem("erechnung.user");
    return raw ? JSON.parse(raw) : null;
  });
  const [branding, setBranding] = useState({ name: "senapsa", productTitle: "eRechnung-Konverter", primaryColor: "#0b5fff" });

  useEffect(() => {
    api.branding().then(setBranding).catch(() => {});
  }, []);

  useEffect(() => {
    document.documentElement.style.setProperty("--brand", branding.primaryColor || "#0b5fff");
  }, [branding]);

  async function login(email, password) {
    const res = await api.login(email, password);
    setToken(res.token);
    const u = { email: res.email, role: res.role };
    localStorage.setItem("erechnung.user", JSON.stringify(u));
    setUser(u);
    return u;
  }

  function logout() {
    setToken(null);
    localStorage.removeItem("erechnung.user");
    setUser(null);
  }

  return (
    <AuthCtx.Provider value={{ user, branding, login, logout, isAdmin: user?.role === "ADMIN" }}>
      {children}
    </AuthCtx.Provider>
  );
}

export function useAuth() {
  return useContext(AuthCtx);
}
