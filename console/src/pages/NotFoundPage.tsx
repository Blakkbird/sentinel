import { Link } from "react-router";
import { EmptyState } from "@/components/States";
import { Button } from "@/components/ui/button";

export function NotFoundPage() {
  return (
    <EmptyState
      title="This page does not exist"
      action={
        <Link to="/">
          <Button variant="outline">Go to the console</Button>
        </Link>
      }
    >
      Check the address, or use the sections on the left.
    </EmptyState>
  );
}
