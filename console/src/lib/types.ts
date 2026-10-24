// The shapes the backend sends. Field names match the Java records one to one.

export type Outcome = "APPROVE" | "STEP_UP" | "DECLINE";
export type RuleMode = "OFF" | "SHADOW" | "ACTIVE";
export type RuleResult = "FIRED" | "NOT_FIRED" | "ABSTAINED";
export type Operator = "GT" | "GTE" | "LT" | "LTE" | "EQ" | "NEQ";
export type Role = "ADMIN" | "ANALYST";

export interface CurrentUser {
  username: string;
  displayName: string;
  role: Role;
}

export interface RuleView {
  code: string;
  description: string;
  condition: string;
  proposes: Outcome;
  reasonCode: string;
  mode: RuleMode;
}

export interface RuleSetView {
  version: number;
  rules: RuleView[];
}

/** One row of the decision list and one event of the live feed. */
export interface DecisionSummary {
  decisionId: string;
  transactionId: string;
  cardId: string;
  deviceId: string | null;
  merchantId: string | null;
  amountMinor: number;
  currency: string;
  decidedAt: string;
  outcome: Outcome;
  shadowOutcome: Outcome;
  reasonCodes: string[];
  ruleSetVersion: number;
  degraded: boolean;
  computeMicros: number;
}

export interface DecisionPage {
  items: DecisionSummary[];
  nextCursor: string | null;
}

/** One comparison inside a rule, checked against the facts the rule saw. */
export interface Evidence {
  feature: string;
  op: Operator;
  threshold: number;
  actual: number | null;
  holds: boolean;
}

export interface Fact {
  name: string;
  value: number;
  description: string | null;
}

export interface RuleResultView {
  code: string;
  mode: RuleMode;
  result: RuleResult;
  proposes: Outcome;
  reasonCode: string;
  description: string | null;
  condition: string | null;
  definitionVersion: number | null;
  evidence: Evidence[];
}

export interface DecisionView {
  decisionId: string;
  transactionId: string;
  cardId: string;
  deviceId: string | null;
  merchantId: string | null;
  amountMinor: number;
  currency: string;
  occurredAt: string;
  decidedAt: string;
  outcome: Outcome;
  shadowOutcome: Outcome;
  reasonCodes: string[];
  ruleSetVersion: number;
  degraded: boolean;
  computeMicros: number;
  facts: Fact[];
  rules: RuleResultView[];
}
