/**
 * Light / Dark / System theme settings.
 *
 * The visitor's own choice is saved in localStorage under THEME_STORAGE_KEY by
 * next-themes. Visitors who never chose get the admin's "default_theme" setting
 * (Admin → Settings), and if that isn't set, "system" — follow the device.
 */
export const THEME_STORAGE_KEY = "mrdiet-theme";
export const THEMES = ["light", "dark", "system"];

/** Admin default from the cached public settings (available before React starts). */
export function adminDefaultTheme() {
  try {
    const t = JSON.parse(localStorage.getItem("app_public_settings") || "{}").default_theme;
    return THEMES.includes(t) ? t : "system";
  } catch {
    return "system";
  }
}

/** Logo for the theme being shown: light logo on light backgrounds, dark logo otherwise. */
export function themedLogoPath(settings, resolvedTheme) {
  if (resolvedTheme === "light") return settings?.logo_light || settings?.logo_dark || "";
  return settings?.logo_dark || settings?.logo_light || "";
}
