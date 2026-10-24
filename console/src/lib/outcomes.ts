import type { Outcome, RuleMode, RuleResult, Operator } from "./types";

/** Words and colours for each outcome. The colour classes map to the tokens in index.css. */
export const OUTCOME: Record<
  Outcome,
  { label: string; verb: string; text: string; listText: string; bg: string; border: string }
> = {
  // In lists an approval keeps its green dot but plain text: approvals are the normal case,
  // so step-ups and declines are the only coloured words on the screen.
  APPROVE: { label: "Approved", verb: "Approve", text: "text-approve", listText: "text-foreground", bg: "bg-approve", border: "border-approve" },
  STEP_UP: { label: "Step-up", verb: "Step up", text: "text-stepup", listText: "text-stepup", bg: "bg-stepup", border: "border-stepup" },
  DECLINE: { label: "Declined", verb: "Decline", text: "text-decline", listText: "text-decline", bg: "bg-decline", border: "border-decline" },
};

export const OUTCOMES: Outcome[] = ["APPROVE", "STEP_UP", "DECLINE"];

export const MODE_LABEL: Record<RuleMode, string> = {
  ACTIVE: "Active",
  SHADOW: "Shadow",
  OFF: "Off",
};

export const RESULT_LABEL: Record<RuleResult, string> = {
  FIRED: "Fired",
  NOT_FIRED: "Did not fire",
  ABSTAINED: "Could not run",
};

export const OPERATOR_SYMBOL: Record<Operator, string> = {
  GT: ">",
  GTE: "≥",
  LT: "<",
  LTE: "≤",
  EQ: "=",
  NEQ: "≠",
};
