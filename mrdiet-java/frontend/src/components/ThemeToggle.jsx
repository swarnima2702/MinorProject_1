import { useEffect, useState } from "react";
import { useTheme } from "next-themes";
import { Monitor, Moon, Sun } from "lucide-react";
import { cn } from "@/lib/utils";

const OPTIONS = [
  { value: "light", label: "Light", Icon: Sun },
  { value: "dark", label: "Dark", Icon: Moon },
  { value: "system", label: "System", Icon: Monitor },
];

/**
 * Three-way theme switch: Light / Dark / System (follows the phone or computer setting).
 * `showLabels` adds the words next to the icons (used in sidebars and settings).
 */
export function ThemeToggle({ className = "", showLabels = false, size = "md" }) {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const icon = size === "sm" || showLabels ? "h-3.5 w-3.5 shrink-0" : "h-4 w-4";
  return (
    <div
      role="radiogroup"
      aria-label="Colour theme"
      className={cn(
        "inline-flex items-center gap-0.5 rounded-full border border-border/60 bg-card/70 p-1 backdrop-blur",
        className,
      )}
    >
      {OPTIONS.map(({ value, label, Icon }) => {
        const active = mounted && theme === value;
        return (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={active}
            aria-label={`${label} theme`}
            title={value === "system" ? "System (match device)" : `${label} mode`}
            onClick={() => {
              try {
                localStorage.setItem("mrdiet-theme-chosen", "1");
              } catch {
                /* storage blocked — theme still changes for this visit */
              }
              setTheme(value);
            }}
            className={cn(
              "inline-flex flex-1 items-center justify-center gap-1.5 rounded-full font-semibold transition-colors",
              showLabels ? "min-w-0 px-2 py-1.5 text-[10px] gap-1" : size === "sm" ? "p-1.5" : "p-2",
              active
                ? "bg-gold text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:bg-ink/5 hover:text-foreground",
            )}
          >
            <Icon className={icon} aria-hidden="true" />
            {showLabels && <span>{label}</span>}
          </button>
        );
      })}
    </div>
  );
}

export default ThemeToggle;
