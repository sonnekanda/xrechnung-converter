import { useEffect, useState } from "react";
import { NavLink, Outlet, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth.jsx";
import { api } from "../api";

export default function Layout() {
  const { user, isAdmin, branding, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  // Startzustand aus dem je Benutzer gespeicherten Wert (Server), Fallback offen.
  const [expanded, setExpanded] = useState(user?.navExpanded !== false);
  const [offen, setOffen] = useState(0);
  const [settingsOpen, setSettingsOpen] = useState(false);

  function toggleNav() {
    setExpanded((v) => {
      const next = !v;
      api.setNav(next).catch(() => {}); // je Benutzer serverseitig speichern
      return next;
    });
  }

  // Einstellungen öffnen: bei eingeklappter Sidebar diese zuerst ausklappen,
  // damit der Klick eine sichtbare Reaktion hat (Textpunkte brauchen Platz).
  function openSettings() {
    if (!expanded) {
      setExpanded(true);
      api.setNav(true).catch(() => {});
      setSettingsOpen(true);
    } else {
      setSettingsOpen((o) => !o);
    }
  }

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

  // Hauptmenü: häufig genutzt (Stammdaten bleibt hier).
  const items = [
    { to: "/", ico: "▤", label: "Übersicht", end: true },
    { to: "/upload", ico: "⭱", label: "Hochladen" },
    ...(isAdmin ? [{ to: "/stammdaten", ico: "☷", label: "Stammdaten" }] : []),
  ];

  // Einstellungen: seltener genutzt, als aufklappbare Gruppe unten bei „Abmelden".
  const settingsItems = isAdmin ? [
    { to: "/firma", ico: "☗", label: "Eigene Firma" },
    { to: "/benutzer", ico: "☺", label: "Benutzer" },
    { to: "/ordner-automatik", ico: "📂", label: "Ordner-Automatik" },
    { to: "/ki-assistent", ico: "✦", label: "KI-Assistent" },
    { to: "/protokoll", ico: "≡", label: "Protokoll" },
    { to: "/einstellungen", ico: "⚙", label: "Konfiguration" },
  ] : [];

  const onSettingsRoute = settingsItems.some((it) => location.pathname === it.to);
  // Automatik: auf einer Einstellungs-Seite aufklappen, beim Verlassen wieder zu.
  useEffect(() => { setSettingsOpen(onSettingsRoute); }, [onSettingsRoute]);

  function doLogout() {
    logout();
    navigate("/login");
  }

  return (
    <div className="app">
      <nav className={`sidebar ${expanded ? "" : "collapsed"}`}>
        <button className="sidebar-toggle" onClick={toggleNav}
                title="Menü ein-/ausklappen" aria-label="Menü ein-/ausklappen">
          {expanded ? "‹" : "›"}
        </button>
        <div className="brand">
          {branding.logoUrl
            ? <img className="logo" src={branding.logoUrl} alt={branding.name} />
            : <span className="logo">{(branding.name || "s")[0].toUpperCase()}</span>}
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

        {settingsItems.length > 0 && (
          <div className="nav-group">
            <button className={`nav-item group-toggle ${onSettingsRoute ? "active-parent" : ""}`}
                    onClick={openSettings} title="Einstellungen"
                    aria-expanded={settingsOpen}>
              <span className="ico">⚙</span>
              {expanded && <span>Einstellungen</span>}
            </button>
            {expanded && settingsOpen && settingsItems.map((it) => (
              <NavLink key={it.to} to={it.to} className="nav-item sub" title={it.label}>
                {it.label}
              </NavLink>
            ))}
          </div>
        )}

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
