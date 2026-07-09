import { useEffect, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth.jsx";
import { api } from "../api";

const NAV_KEY = "erechnung.navExpanded";

export default function Layout() {
  const { user, isAdmin, branding, logout } = useAuth();
  const navigate = useNavigate();
  const [expanded, setExpanded] = useState(() => localStorage.getItem(NAV_KEY) !== "false");
  const [offen, setOffen] = useState(0);

  useEffect(() => {
    localStorage.setItem(NAV_KEY, String(expanded));
  }, [expanded]);

  // Zaehler offener Pruefungen im Navigationsmenue.
  useEffect(() => {
    let alive = true;
    const load = () =>
      api.invoices().then((list) => {
        if (alive) setOffen(list.filter((i) => i.status === "PRUEFEN").length);
      }).catch(() => {});
    load();
    const t = setInterval(load, 4000);
    return () => { alive = false; clearInterval(t); };
  }, []);

  const items = [
    { to: "/", ico: "▤", label: "Übersicht", end: true },
    { to: "/upload", ico: "⭱", label: "Hochladen" },
    ...(isAdmin ? [
      { to: "/firma", ico: "☗", label: "Eigene Firma" },
      { to: "/stammdaten", ico: "☷", label: "Stammdaten" },
      { to: "/benutzer", ico: "☺", label: "Benutzer" },
    ] : []),
  ];

  function doLogout() {
    logout();
    navigate("/login");
  }

  return (
    <div className="app">
      <nav className={`sidebar ${expanded ? "" : "collapsed"}`}>
        <div className="brand">
          <span className="logo">{(branding.name || "s")[0].toUpperCase()}</span>
          {expanded && <span>{branding.name}</span>}
        </div>
        {items.map((it) => (
          <NavLink key={it.to} to={it.to} end={it.end} className="nav-item" title={it.label}>
            <span className="ico">{it.ico}</span>
            {expanded && <span>{it.label}</span>}
            {expanded && it.to === "/" && offen > 0 && <span className="badge">{offen}</span>}
          </NavLink>
        ))}
        <div className="spacer" />
        <button className="nav-item collapse-btn" onClick={() => setExpanded((v) => !v)} title="Menü ein-/ausklappen">
          <span className="ico">{expanded ? "«" : "»"}</span>
          {expanded && <span>Einklappen</span>}
        </button>
        <button className="nav-item collapse-btn" onClick={doLogout} title="Abmelden">
          <span className="ico">⏻</span>
          {expanded && <span>Abmelden</span>}
        </button>
      </nav>

      <div className="main">
        <header className="topbar">
          <h1>{branding.productTitle}</h1>
          <span className="user">{user?.email} · {user?.role}</span>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
