import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import "./index.css";

const start = () => createRoot(document.getElementById("root")).render(<App />);

// `npm run build:demo` only: answers /api calls in the browser so the site can be
// tried without the Spring Boot backend. The normal build drops this branch entirely.
if (import.meta.env.MODE === "demo") {
  import("./demo/demoServer").then(({ installDemo }) => {
    installDemo();
    start();
  });
} else {
  start();
}
