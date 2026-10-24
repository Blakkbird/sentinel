import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { LoaderCircle } from "lucide-react";
import { useCurrentUser, useSignIn } from "@/auth/useAuth";
import { Wordmark } from "@/components/Wordmark";
import { TapeIllustration } from "@/components/TapeIllustration";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export function LoginPage() {
  const me = useCurrentUser();
  const signIn = useSignIn();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  if (me.data) {
    return <Navigate to={from} replace />;
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    signIn.mutate({ username, password }, { onSuccess: () => navigate(from, { replace: true }) });
  }

  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.15fr)]">
      <div className="flex flex-col px-6 py-8 sm:px-12">
        <Wordmark />
        <div className="my-auto w-full max-w-[360px] py-12">
          <h1 className="font-display text-[26px] leading-tight font-semibold tracking-[-0.01em]">Sign in</h1>
          <p className="mt-1 text-muted-foreground">To the Sentinel risk console.</p>

          <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
            <div className="space-y-1.5">
              <Label htmlFor="username">Username</Label>
              <Input
                id="username"
                autoComplete="username"
                autoFocus
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            {signIn.isError && (
              <p role="alert" className="text-[13px] text-decline">
                {signIn.error.message}
              </p>
            )}

            <Button type="submit" className="w-full" disabled={signIn.isPending || !username || !password}>
              {signIn.isPending && <LoaderCircle className="animate-spin" aria-hidden="true" />}
              Sign in
            </Button>
          </form>

          {import.meta.env.DEV && (
            <p className="mt-5 text-[13px] text-muted-foreground">
              Local demo accounts: <span className="font-mono">analyst</span> /{" "}
              <span className="font-mono">sentinel-analyst</span> and <span className="font-mono">admin</span> /{" "}
              <span className="font-mono">sentinel-admin</span>.
            </p>
          )}
        </div>
        <p className="text-[12.5px] text-muted-foreground">Team Runtime Error, UCS553 Enterprise Web Applications, TIET</p>
      </div>

      <aside className="hidden flex-col justify-end gap-8 bg-hero p-12 text-hero-foreground lg:flex">
        <p className="max-w-[22ch] font-display text-[40px] leading-[1.08] font-semibold tracking-[-0.015em]">
          Approve, step up or decline in milliseconds, with the reasons on record.
        </p>
        <div>
          <TapeIllustration />
          <p className="mt-4 max-w-[52ch] text-[13px] text-hero-foreground/70">
            Each bar is one payment, its height the amount. The red run in the middle is a card-testing attack: one
            stolen card tried again and again, declined from the sixth attempt.
          </p>
        </div>
      </aside>
    </div>
  );
}
