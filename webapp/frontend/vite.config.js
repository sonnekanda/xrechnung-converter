import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Build nach ../src/main/resources/static -> die webapp liefert die SPA selbst aus.
// Im Dev-Modus werden API-Aufrufe an die lokale webapp (Port 8080) weitergereicht.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static",
    emptyOutDir: true,
  },
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
