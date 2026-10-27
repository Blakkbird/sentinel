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

// ---- rule sets (Sprint 2) ---------------------------------------------------------------

export type RuleSetStatus = "DRAFT" | "PENDING" | "PUBLISHED" | "REJECTED" | "DISCARDED";

/** A rule's condition as stored: a small tree. The same shape as RuleSetJson on the backend. */
export type ConditionNode =
  | { type: "compare"; feature: string; op: Operator; value: number }
  | { type: "all"; parts: ConditionNode[] }
  | { type: "any"; parts: ConditionNode[] }
  | { type: "not"; inner: ConditionNode };

/** One version in a list. "live" means it is deciding payments right now. */
export interface RuleSetSummary {
  version: number;
  status: RuleSetStatus;
  title: string;
  basedOn: number | null;
  ruleCount: number;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  submittedAt: string | null;
  reviewedByName: string | null;
  reviewedAt: string | null;
  live: boolean;
}

export interface RuleSetListing {
  liveVersion: number;
  previousVersion: number | null;
  items: RuleSetSummary[];
}

/** A rule as stored, plus its condition as one line of text (card.txnCount.1m > 5). */
export interface RuleDocument {
  code: string;
  description: string;
  mode: RuleMode;
  proposes: Outcome;
  reasonCode: string;
  condition: ConditionNode;
  conditionText: string;
}

/** "From this moment, this version was live." */
export interface RuleSetActivation {
  id: number;
  version: number;
  kind: "SEED" | "PUBLISH" | "ROLLBACK";
  activatedBy: string;
  activatedByName: string;
  activatedAt: string;
}

/** Something a person can do to a version. The server says which are allowed, and why not. */
export type RuleSetAction = "EDIT" | "DISCARD";

export interface Permission {
  action: RuleSetAction;
  allowed: boolean;
  reason: string | null;
}

/** A fact rules can use, from the feature catalogue. */
export interface FeatureSpec {
  name: string;
  description: string;
}

export interface RuleSetDetail {
  version: number;
  status: RuleSetStatus;
  title: string;
  basedOn: number | null;
  revision: number;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedByName: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  live: boolean;
  liveVersion: number;
  rules: RuleDocument[];
  activations: RuleSetActivation[];
  permissions: Permission[];
}

/** What the Rule Lab found. Outcome strips have one letter per decision, oldest first: A, S or D. */
export interface ReplayReport {
  version: number;
  liveVersion: number;
  decisions: number;
  from: string | null;
  to: string | null;
  liveOutcomes: Record<Outcome, number>;
  versionOutcomes: Record<Outcome, number>;
  changed: number;
  moves: { from: Outcome; to: Outcome; count: number }[];
  rules: {
    code: string;
    mode: RuleMode;
    proposes: Outcome;
    liveFired: number | null;
    versionFired: number | null;
    versionAbstained: number;
  }[];
  examples: {
    transactionId: string;
    decidedAt: string;
    cardId: string;
    amountMinor: number;
    currency: string;
    before: Outcome;
    after: Outcome;
    reasonCodes: string[];
  }[];
  liveStrip: string;
  versionStrip: string;
  check: { checked: number; matched: number; unknownVersion: number };
}
