import { Link } from "react-router";
import { EmptyState } from "@/components/States";
import { buttonVariants } from "@/components/ui/button";

export function NotFoundPage() {
  return (
    <EmptyState
      title="This page does not exist"
      action={
        // A link styled as a button. Putting a <button> inside an <a> is invalid HTML: it makes two
        // focus stops for one action, and screen readers announce it twice.
        <Link to="/" className={buttonVariants({ variant: "outline" })}>
          Go to the console
        </Link>
      }
    >
      Check the address, or use the sections on the left.
    </EmptyState>
  );
}
