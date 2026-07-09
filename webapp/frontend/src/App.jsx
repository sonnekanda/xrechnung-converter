import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth.jsx";
import Layout from "./components/Layout.jsx";
import Login from "./pages/Login.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import Upload from "./pages/Upload.jsx";
import Pruefbildschirm from "./pages/Pruefbildschirm.jsx";
import Stammdaten from "./pages/Stammdaten.jsx";
import Firma from "./pages/Firma.jsx";

function Protected({ children, adminOnly }) {
  const { user, isAdmin } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (adminOnly && !isAdmin) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  const { user } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to="/" replace /> : <Login />} />
      <Route
        path="/"
        element={
          <Protected>
            <Layout />
          </Protected>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="upload" element={<Upload />} />
        <Route path="pruefen/:id" element={<Pruefbildschirm />} />
        <Route
          path="stammdaten"
          element={
            <Protected adminOnly>
              <Stammdaten />
            </Protected>
          }
        />
        <Route
          path="firma"
          element={
            <Protected adminOnly>
              <Firma />
            </Protected>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
