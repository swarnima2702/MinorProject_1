import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";

/**
 * `npm run build:demo` → dist-demo/: a self-contained demo with the backend
 * simulated in the browser (src/demo). It uses hash URLs (#/dashboard) and
 * relative asset paths so it can be opened from any folder or static host.
 */
const demoMode = {
  name: "mrdiet-demo",
  enforce: "pre",
  transform(code, id) {
    if (!id.includes("/src/") || !/\.(jsx?)$/.test(id)) return null;
    let out = code.replace(/window\.location\.href\s*=(?!=)/g, "window.__demoHref =");
    if (id.endsWith("/src/App.jsx")) out = out.replace(/\bBrowserRouter\b/g, "HashRouter");
    return out === code ? null : { code: out, map: null };
  },
  transformIndexHtml(html) {
    return html
      .replace("window.location.origin + '/config.js?v='", "'config.js?v='")
      .replace('<link rel="stylesheet" href="https://unpkg.com/aos@next/dist/aos.css" />', "")
      .replace('href="/favicon.ico"', 'href="favicon.ico"');
  },
};

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  base: mode === "demo" ? "./" : "/",
  build: { outDir: mode === "demo" ? "dist-demo" : "dist" },
  server: {
    host: "::",
    port: 8080,
    strictPort: true,
    proxy: {
      // During development every /api call goes to the Spring Boot app
      // (mvn spring-boot:run → http://localhost:8081).
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 8082,
    strictPort: true,
  },
  plugins: [react(), mode === "demo" && demoMode].filter(Boolean),
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
}));
