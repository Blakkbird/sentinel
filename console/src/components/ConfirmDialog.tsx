import { useEffect, useRef, type ReactNode } from "react";
import { LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Asks before doing something that matters. Built on the browser's own <dialog> element:
 * showModal() traps keyboard focus inside it, Escape closes it, and the rest of the page
 * cannot be clicked meanwhile, all without a library.
 */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  onConfirm,
  onCancel,
  pending,
  confirmDisabled,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
  pending?: boolean;
  confirmDisabled?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onCancel={(event) => {
        event.preventDefault(); // Escape: let the page decide, so its state stays in step
        if (!pending) onCancel();
      }}
      aria-labelledby="confirm-title"
      className="m-auto w-[min(92vw,520px)] rounded-lg border bg-card p-0 text-foreground shadow-lg backdrop:bg-[#0e1525]/45"
    >
      <div className="space-y-3 px-5 pt-5 pb-4">
        <h2 id="confirm-title" className="text-[16px] font-semibold">
          {title}
        </h2>
        <div className="space-y-3 text-[14px]">{children}</div>
      </div>
      <div className="flex justify-end gap-2 border-t px-5 py-3">
        <Button variant="outline" onClick={onCancel} disabled={pending}>
          Cancel
        </Button>
        <Button onClick={onConfirm} disabled={pending || confirmDisabled}>
          {pending && <LoaderCircle className="animate-spin" aria-hidden="true" />}
          {confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
