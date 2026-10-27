import type { ConditionNode, RuleSetActivation, RuleSetStatus } from "./types";
import { featureKind, featureLabel, formatFeatureValue } from "./features";
import { OPERATOR_SYMBOL } from "./outcomes";

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
      return `${featureLabel(node.feature)} ${OPERATOR_SYMBOL[node.op]} ${formatFeatureValue(node.feature, node.value)}`;
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
