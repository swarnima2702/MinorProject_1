import { useState, useEffect } from "react";

export function useAppConfig() {
  const [config, setConfig] = useState(window.APP_CONFIG || {});

  useEffect(() => {
    const update = () => {
      setConfig({ ...(window.APP_CONFIG || {}) });
    };

    window.addEventListener("config-updated", update);

    // Also check if config loaded after initial render
    const interval = setInterval(() => {
      const current = window.APP_CONFIG;
      if (current && Object.keys(current).length > 0) {
        setConfig({ ...current });
        clearInterval(interval);
      }
    }, 100);

    // Clear interval once config is loaded
    setTimeout(() => clearInterval(interval), 5000);

    return () => {
      window.removeEventListener("config-updated", update);
      clearInterval(interval);
    };
  }, []);

  return config;
}
