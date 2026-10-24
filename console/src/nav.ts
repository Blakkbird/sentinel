import { ScrollText, type LucideIcon } from "lucide-react";

/** The sections of the console, in the order they appear in the sidebar. */
export const NAV: { to: string; label: string; icon: LucideIcon }[] = [
  { to: "/rules", label: "Rules", icon: ScrollText },
];
