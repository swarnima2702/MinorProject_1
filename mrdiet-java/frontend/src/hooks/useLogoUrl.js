import { useTheme } from "next-themes";
import { getLogoUrl } from "@/lib/api";
import { themedLogoPath } from "@/lib/theme";

/** Logo URL that follows the current theme (Admin → Settings has a light and a dark logo). */
export function useLogoUrl(settings) {
  const { resolvedTheme } = useTheme();
  return getLogoUrl(themedLogoPath(settings, resolvedTheme));
}
