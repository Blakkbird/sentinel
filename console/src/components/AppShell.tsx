import { NavLink, Outlet } from "react-router";
import { LogOut } from "lucide-react";
import { Wordmark } from "./Wordmark";
import { NAV } from "@/nav";
import { useCurrentUser, useSignOut } from "@/auth/useAuth";
import { cn } from "@/lib/utils";

/** The frame around every signed-in page: sidebar on wide screens, top bar on narrow ones. */
export function AppShell() {
  const me = useCurrentUser();
  const signOut = useSignOut();
  const user = me.data;

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[216px_minmax(0,1fr)]">
      <aside className="flex items-center gap-4 border-b bg-card px-4 py-3 md:sticky md:top-0 md:h-dvh md:flex-col md:items-stretch md:gap-0 md:border-r md:border-b-0 md:px-0 md:py-0">
        <div className="md:px-5 md:pt-5 md:pb-6">
          <Wordmark />
        </div>

        <nav aria-label="Sections" className="flex flex-1 gap-1 md:flex-none md:flex-col md:px-3">
          {NAV.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  "relative flex h-9 items-center gap-2.5 rounded-md px-3 text-[14px] transition-colors",
                  isActive
                    ? "bg-muted font-medium text-foreground md:before:absolute md:before:inset-y-2 md:before:-left-3 md:before:w-[3px] md:before:rounded-r md:before:bg-foreground"
                    : "text-muted-foreground hover:bg-muted/60 hover:text-foreground",
                )
              }
            >
              <Icon className="size-4" aria-hidden="true" />
              <span className="hidden sm:inline">{label}</span>
            </NavLink>
          ))}
        </nav>

        {user && (
          <div className="flex items-center gap-2 md:mt-auto md:border-t md:px-5 md:py-4">
            <div className="hidden min-w-0 flex-1 md:block">
              <p className="truncate text-[13px] font-medium">{user.displayName}</p>
              <p className="text-[12px] text-muted-foreground">{user.role === "ADMIN" ? "Admin" : "Analyst"}</p>
            </div>
            <button
              type="button"
              onClick={() => signOut.mutate()}
              disabled={signOut.isPending}
              className="inline-flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
              title="Sign out"
            >
              <LogOut className="size-4" aria-hidden="true" />
              <span className="sr-only">Sign out</span>
            </button>
          </div>
        )}
      </aside>

      <main className="min-w-0 px-4 py-6 sm:px-6 md:px-8 md:py-7">
        <Outlet />
      </main>
    </div>
  );
}
