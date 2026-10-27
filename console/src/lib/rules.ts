import type { ConditionNode, Operator, Outcome, RuleDocument, RuleMode, RuleSetActivation, RuleSetStatus } from "./types";
import { featureKind, featureLabel, formatFeatureValue } from "./features";
import { MODE_LABEL, OPERATOR_SYMBOL, OUTCOME } from "./outcomes";

export const STATUS_LABEL: Record<RuleSetStatus, string> = {
  DRAFT: "Draft",
  PENDING: "Waiting for approval",
  PUBLISHED: "Published",
  REJECTED: "Rejected",
  DISCARDED: "Discarded",
};

export const ACTIVATION_LABEL: Record<RuleSetActivation["kind"], string> = {
  SEED: "Set up by the database migration",
  PUBLISH: "Published",
  ROLLBACK: "Made live again (rollback)",
};

/**
 * A condition in words, with readable labels and amounts:
 *   "Card attempts this minute > 5 and Payment amount > ₹20,000"
 * The technical form (card.txnCount.1m > 5) comes from the backend as conditionText.
 */
export function describeCondition(node: ConditionNode): string {
  switch (node.type) {
    case "compare": {
      if (featureKind(node.feature) === "flag" && (node.op === "EQ" || node.op === "NEQ")) {
        const yes = (node.value === 1) === (node.op === "EQ");
        return `${featureLabel(node.feature)}: ${yes ? "yes" : "no"}`;
      }
      const value = Number.isFinite(node.value) ? formatFeatureValue(node.feature, node.value) : "?";
      return `${featureLabel(node.feature)} ${OPERATOR_SYMBOL[node.op]} ${value}`;
    }
    case "all":
      return node.parts.map(grouped).join(" and ");
    case "any":
      return node.parts.map(grouped).join(" or ");
    case "not":
      return `not (${describeCondition(node.inner)})`;
  }
}

function grouped(node: ConditionNode): string {
  return node.type === "all" || node.type === "any" ? `(${describeCondition(node)})` : describeCondition(node);
}

// ---- editing ------------------------------------------------------------------------------

/** A rule in the shape the server stores and accepts: a RuleDocument without its display text. */
export type RuleShape = Omit<RuleDocument, "conditionText">;

/** One comparison row in the builder. The value is kept as typed, in rupees for amounts. */
export interface EditableCondition {
  key: string;
  feature: string;
  op: Operator;
  value: string;
}

/**
 * A rule while it is being edited. Rules whose condition is a single comparison or "all of
 * these comparisons" are fully editable. Rules that use any/not keep their condition as it is
 * (conditions is null), and only their other fields can be changed.
 */
export interface EditableRule {
  key: string;
  code: string;
  description: string;
  mode: RuleMode;
  proposes: Outcome;
  reasonCode: string;
  conditions: EditableCondition[] | null;
  original: ConditionNode;
}

export const OPERATOR_WORDS: Record<Operator, string> = {
  GT: "is more than",
  GTE: "is at least",
  LT: "is less than",
  LTE: "is at most",
  EQ: "is",
  NEQ: "is not",
};

let lastKey = 0;
/** React needs a stable key for every row, even before it has a code. */
export function newKey(): string {
  lastKey += 1;
  return `k${lastKey}`;
}

export function valueToInput(feature: string, value: number): string {
  return featureKind(feature) === "money" ? String(value / 100) : String(value);
}

/** What the person typed, as the stored number (paise for amounts), or NaN if it is not one. */
export function inputToValue(feature: string, input: string): number {
  const text = input.trim().replaceAll(",", "");
  if (text === "") return Number.NaN;
  const n = Number(text);
  if (!Number.isFinite(n)) return Number.NaN;
  return featureKind(feature) === "money" ? Math.round(n * 100) : n;
}

export function toEditable(rule: RuleShape): EditableRule {
  const c = rule.condition;
  const flat =
    c.type === "compare" ? [c] : c.type === "all" && c.parts.every((p) => p.type === "compare") ? c.parts : null;
  return {
    key: newKey(),
    code: rule.code,
    description: rule.description,
    mode: rule.mode,
    proposes: rule.proposes,
    reasonCode: rule.reasonCode,
    original: rule.condition,
    conditions:
      flat === null
        ? null
        : flat.map((p) => {
            const compare = p as Extract<ConditionNode, { type: "compare" }>;
            return { key: newKey(), feature: compare.feature, op: compare.op, value: valueToInput(compare.feature, compare.value) };
          }),
  };
}

export function fromEditable(rule: EditableRule): RuleShape {
  let condition: ConditionNode = rule.original;
  if (rule.conditions !== null) {
    const parts: ConditionNode[] = rule.conditions.map((c) => ({
      type: "compare",
      feature: c.feature,
      op: c.op,
      value: inputToValue(c.feature, c.value),
    }));
    condition = parts.length === 1 ? parts[0] : { type: "all", parts };
  }
  return {
    code: rule.code.trim(),
    description: rule.description.trim(),
    mode: rule.mode,
    proposes: rule.proposes,
    reasonCode: rule.reasonCode.trim(),
    condition,
  };
}

/** A new rule starts in shadow: it watches real traffic and changes nothing until someone makes it active. */
export function newRule(existing: EditableRule[]): EditableRule {
  let n = existing.length + 1;
  while (existing.some((r) => r.code === `NEW_RULE_${n}`)) n += 1;
  return {
    key: newKey(),
    code: `NEW_RULE_${n}`,
    description: "",
    mode: "SHADOW",
    proposes: "STEP_UP",
    reasonCode: `NEW_RULE_${n}`,
    conditions: [{ key: newKey(), feature: "txn.amountMinor", op: "GT", value: "" }],
    original: { type: "all", parts: [] },
  };
}

const UPPER_SNAKE = /^[A-Z][A-Z0-9_]*$/;

/**
 * The checks the builder can make as you type, keyed by rule. The server checks everything
 * again on save (and knows more, such as which features exist), so this is about speed, not trust.
 */
export function checkRules(rules: EditableRule[]): Map<string, string[]> {
  const problems = new Map<string, string[]>();
  const codes = new Map<string, number>();
  rules.forEach((r) => codes.set(r.code.trim(), (codes.get(r.code.trim()) ?? 0) + 1));

  for (const rule of rules) {
    const list: string[] = [];
    const code = rule.code.trim();
    if (!UPPER_SNAKE.test(code)) list.push("Code: capital letters, digits and underscores, starting with a letter (like VEL_CARD_1M).");
    else if ((codes.get(code) ?? 0) > 1) list.push(`Code: another rule is also called ${code}.`);
    if (!UPPER_SNAKE.test(rule.reasonCode.trim())) list.push("Reason code: capital letters, digits and underscores (like CARD_VELOCITY_1M).");
    if (rule.description.trim() === "") list.push("Description: say in plain words what this rule catches.");
    if (rule.conditions !== null) {
      if (rule.conditions.length === 0) list.push("Add at least one condition.");
      rule.conditions.forEach((c, i) => {
        const value = inputToValue(c.feature, c.value);
        const kind = featureKind(c.feature);
        if (Number.isNaN(value)) list.push(`Condition ${i + 1}: enter a number.`);
        else if (kind === "count" && !Number.isInteger(value)) list.push(`Condition ${i + 1}: counts are whole numbers.`);
        else if (kind === "money" && value < 0) list.push(`Condition ${i + 1}: an amount cannot be negative.`);
      });
    }
    if (list.length > 0) problems.set(rule.key, list);
  }
  return problems;
}

// ---- comparing two versions -------------------------------------------------------------

export type ChangedField = "mode" | "proposes" | "reasonCode" | "description" | "condition";

export interface RuleChange {
  code: string;
  kind: "added" | "removed" | "changed";
  rule: RuleShape;
  fields: { field: ChangedField; before: string; after: string }[];
}

export function sameCondition(a: ConditionNode, b: ConditionNode): boolean {
  if (a.type !== b.type) return false;
  switch (a.type) {
    case "compare": {
      const other = b as typeof a;
      return a.feature === other.feature && a.op === other.op && a.value === other.value;
    }
    case "all":
    case "any": {
      const other = b as typeof a;
      return a.parts.length === other.parts.length && a.parts.every((p, i) => sameCondition(p, other.parts[i]));
    }
    case "not":
      return sameCondition(a.inner, (b as typeof a).inner);
  }
}

/** What changes going from one list of rules to another, matched by code. */
export function diffRules(before: RuleShape[], after: RuleShape[]): { changes: RuleChange[]; unchanged: number } {
  const changes: RuleChange[] = [];
  let unchanged = 0;
  const old = new Map(before.map((r) => [r.code, r]));
  const now = new Set(after.map((r) => r.code));

  for (const rule of after) {
    const was = old.get(rule.code);
    if (!was) {
      changes.push({ code: rule.code, kind: "added", rule, fields: [] });
      continue;
    }
    const fields: RuleChange["fields"] = [];
    if (was.mode !== rule.mode) fields.push({ field: "mode", before: MODE_LABEL[was.mode], after: MODE_LABEL[rule.mode] });
    if (was.proposes !== rule.proposes)
      fields.push({ field: "proposes", before: OUTCOME[was.proposes].verb, after: OUTCOME[rule.proposes].verb });
    if (!sameCondition(was.condition, rule.condition))
      fields.push({ field: "condition", before: describeCondition(was.condition), after: describeCondition(rule.condition) });
    if (was.reasonCode !== rule.reasonCode) fields.push({ field: "reasonCode", before: was.reasonCode, after: rule.reasonCode });
    if (was.description !== rule.description) fields.push({ field: "description", before: was.description, after: rule.description });
    if (fields.length > 0) changes.push({ code: rule.code, kind: "changed", rule, fields });
    else unchanged += 1;
  }
  for (const rule of before) {
    if (!now.has(rule.code)) changes.push({ code: rule.code, kind: "removed", rule, fields: [] });
  }
  return { changes, unchanged };
}
