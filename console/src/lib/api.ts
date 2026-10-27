/**
 * Every call from the console to the backend goes through api().
 *
 * Two jobs besides fetch():
 *  - CSRF: the backend sets a cookie called XSRF-TOKEN. Any request that changes
 *    something (POST, PUT, DELETE) must echo it back in the X-XSRF-TOKEN header. A
 *    malicious page on another site cannot read our cookies, so it cannot forge that header.
 *  - Errors: the backend answers errors as Problem Details JSON ({ title, detail, ... }).
 *    api() turns them into an ApiError with a message a person can act on.
 */

export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  /** Every problem the server listed, for example each mistake in a rule set. Often empty. */
  readonly problems: string[];

  constructor(status: number, title: string, detail: string, problems: string[] = []) {
    super(detail);
    this.status = status;
    this.title = title;
    this.problems = problems;
  }
}

/** Options for sending a JSON body: api(path, json("PUT", { ... })). */
export function json(method: "POST" | "PUT", body: unknown): RequestInit {
  return { method, body: JSON.stringify(body), headers: { "Content-Type": "application/json" } };
}

function readCookie(name: string): string | null {
  const prefix = name + "=";
  const found = document.cookie.split("; ").find((part) => part.startsWith(prefix));
  return found ? decodeURIComponent(found.slice(prefix.length)) : null;
}

/** Any GET makes the backend set the CSRF cookie; /api/auth/me is the cheapest one. */
async function ensureCsrfCookie() {
  if (!readCookie("XSRF-TOKEN")) {
    await fetch("/api/auth/me", { credentials: "same-origin" });
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  if (method !== "GET" && method !== "HEAD") {
    await ensureCsrfCookie();
    const token = readCookie("XSRF-TOKEN");
    if (token) headers.set("X-XSRF-TOKEN", token);
  }

  let response: Response;
  try {
    response = await fetch(path, { ...init, method, headers, credentials: "same-origin" });
  } catch {
    throw new ApiError(0, "No connection", "The console could not reach the server. Check your connection and try again.");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let body: unknown = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    // Not JSON. In development this usually means the Vite proxy could not reach Spring Boot.
    throw new ApiError(
      response.status,
      "Backend not responding",
      "The Sentinel backend did not answer. Is it running on port 8080?",
    );
  }

  if (!response.ok) {
    const problem = (body ?? {}) as { title?: string; detail?: string; problems?: unknown };
    const problems = Array.isArray(problem.problems)
      ? problem.problems.filter((p): p is string => typeof p === "string")
      : [];
    throw new ApiError(response.status, problem.title ?? response.statusText, problem.detail ?? "The request failed.", problems);
  }
  return body as T;
}
