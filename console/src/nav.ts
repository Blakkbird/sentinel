import { Activity, History, ListFilter, ScrollText, type LucideIcon } from "lucide-react";

/** The sections of the console, in the order they appear in the sidebar. */
export const NAV: { to: string; label: string; icon: LucideIcon }[] = [
  { to: "/live", label: "Live", icon: Activity },
  { to: "/decisions", label: "Decisions", icon: ListFilter },
  { to: "/rules", label: "Rules", icon: ScrollText },
  { to: "/audit", label: "Audit", icon: History },
];
