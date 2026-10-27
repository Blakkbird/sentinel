import { Plus, Trash2, X } from "lucide-react";
import type { FeatureSpec, Operator, Outcome, RuleMode } from "@/lib/types";
import {
  describeCondition,
  newKey,
  newRule,
  OPERATOR_WORDS,
  type EditableCondition,
  type EditableRule,
} from "@/lib/rules";
import { featureKind, featureLabel } from "@/lib/features";
import { MODE_LABEL, OUTCOME } from "@/lib/outcomes";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/select";
import { Segmented } from "@/components/ui/segmented";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

const MODES: RuleMode[] = ["ACTIVE", "SHADOW", "OFF"];
const PROPOSALS: Outcome[] = ["STEP_UP", "DECLINE"];
const OPERATORS: Operator[] = ["GT", "GTE", "LT", "LTE", "EQ", "NEQ"];

/**
 * The rule builder. Each rule reads like a sentence: when all of these are true, then step up
 * or decline, with this reason code. Amounts are typed in rupees and stored in paise.
 */
export function RuleBuilder({
  rules,
  onChange,
  features,
  problems,
  serverProblems,
}: {
  rules: EditableRule[];
  onChange: (rules: EditableRule[]) => void;
  features: FeatureSpec[];
  /** The builder's own checks, by rule key. */
  problems: Map<string, string[]>;
  /** What the server said on the last save, matched to rules by their code. */
  serverProblems: string[];
}) {
  const change = (key: string, patch: Partial<EditableRule>) =>
    onChange(rules.map((rule) => (rule.key === key ? { ...rule, ...patch } : rule)));

  return (
    <div className="space-y-4">
      {rules.map((rule) => (
        <RuleEditor
          key={rule.key}
          rule={rule}
          features={features}
          problems={[...(problems.get(rule.key) ?? []), ...serverProblems.filter((p) => mentions(p, rule.code))]}
          onChange={(patch) => change(rule.key, patch)}
          onRemove={() => onChange(rules.filter((r) => r.key !== rule.key))}
        />
      ))}
      <Button variant="outline" onClick={() => onChange([...rules, newRule(rules)])}>
        <Plus aria-hidden="true" />
        Add a rule
      </Button>
    </div>
  );
}

/** Server problems name a rule as "VEL_CARD_1M: ..." or "rules[2] (VEL_CARD_1M).mode: ...". */
function mentions(problem: string, code: string) {
  const trimmed = code.trim();
  return trimmed !== "" && (problem.startsWith(`${trimmed}:`) || problem.includes(`(${trimmed})`));
}

function RuleEditor({
  rule,
  features,
  problems,
  onChange,
  onRemove,
}: {
  rule: EditableRule;
  features: FeatureSpec[];
  problems: string[];
  onChange: (patch: Partial<EditableRule>) => void;
  onRemove: () => void;
}) {
  const id = rule.key;
  const conditions = rule.conditions;

  return (
    <section aria-label={`Rule ${rule.code || "without a code"}`} className="relative rounded-md border bg-card py-4 pr-4 pl-5">
      {/* The left edge carries the rule's meaning, as on the decision page: the colour of what
          it asks for when active, dashed violet when it only watches (shadow), grey when off. */}
      <span
        aria-hidden="true"
        className={cn(
          "absolute inset-y-0 left-0 w-[3px] rounded-l-md",
          rule.mode === "ACTIVE" ? OUTCOME[rule.proposes].bg : rule.mode === "OFF" ? "bg-border" : "",
        )}
        style={
          rule.mode === "SHADOW"
            ? { backgroundImage: "repeating-linear-gradient(to bottom, var(--shadow) 0 6px, transparent 6px 10px)" }
            : undefined
        }
      />

      <div className="flex flex-wrap items-end gap-x-4 gap-y-3">
        <div className="space-y-1">
          <Label htmlFor={`${id}-code`}>Code</Label>
          <Input
            id={`${id}-code`}
            value={rule.code}
            onChange={(e) => onChange({ code: e.target.value.toUpperCase() })}
            className="w-[250px] font-mono text-[13px]"
            spellCheck={false}
            autoComplete="off"
          />
        </div>
        <div className="space-y-1">
          <span className="block text-[13px] font-medium">Mode</span>
          <Segmented
            label="Mode"
            value={rule.mode}
            onChange={(mode) => onChange({ mode })}
            options={MODES.map((m) => ({ value: m, label: MODE_LABEL[m] }))}
          />
        </div>
        <Button variant="ghost" size="sm" className="ml-auto text-muted-foreground" onClick={onRemove}>
          <Trash2 aria-hidden="true" />
          Remove rule
        </Button>
      </div>

      <div className="mt-3 max-w-[760px] space-y-1">
        <Label htmlFor={`${id}-description`}>What it catches</Label>
        <Textarea
          id={`${id}-description`}
          rows={2}
          value={rule.description}
          onChange={(e) => onChange({ description: e.target.value })}
          placeholder="In plain words, for whoever reads a decision this rule made"
        />
      </div>

      <fieldset className="mt-4 space-y-2">
        <legend className="mb-1 text-[13px] font-medium">When all of these are true</legend>
        {conditions === null ? (
          <p className="max-w-[760px] rounded-md border border-dashed px-3 py-2 text-[13px]">
            <span className="text-muted-foreground">
              This rule combines conditions with “any” or “not”, which the builder cannot edit yet. Its condition stays as
              it is:{" "}
            </span>
            {describeCondition(rule.original)}
          </p>
        ) : (
          <>
            {conditions.map((condition, i) => (
              <ConditionRow
                key={condition.key}
                index={i}
                condition={condition}
                features={features}
                onChange={(patch) =>
                  onChange({ conditions: conditions.map((c) => (c.key === condition.key ? { ...c, ...patch } : c)) })
                }
                onRemove={
                  conditions.length > 1
                    ? () => onChange({ conditions: conditions.filter((c) => c.key !== condition.key) })
                    : undefined
                }
              />
            ))}
            <Button
              variant="ghost"
              size="sm"
              onClick={() =>
                onChange({ conditions: [...conditions, { key: newKey(), feature: "card.txnCount.1m", op: "GT", value: "" }] })
              }
            >
              <Plus aria-hidden="true" />
              Add a condition
            </Button>
          </>
        )}
      </fieldset>

      <div className="mt-4 flex flex-wrap items-end gap-x-4 gap-y-3">
        <div className="space-y-1">
          <span className="block text-[13px] font-medium">Then</span>
          <Segmented
            label="What the rule asks for when it fires"
            value={rule.proposes}
            onChange={(proposes) => onChange({ proposes })}
            options={PROPOSALS.map((o) => ({ value: o, label: OUTCOME[o].verb }))}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor={`${id}-reason`}>Reason code sent to the payment system</Label>
          <Input
            id={`${id}-reason`}
            value={rule.reasonCode}
            onChange={(e) => onChange({ reasonCode: e.target.value.toUpperCase() })}
            className="w-[270px] font-mono text-[13px]"
            spellCheck={false}
            autoComplete="off"
          />
        </div>
      </div>

      {problems.length > 0 && (
        <ul role="alert" className="mt-3 space-y-0.5 text-[13px] text-decline">
          {problems.map((p) => (
            <li key={p}>{p}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

function ConditionRow({
  index,
  condition,
  features,
  onChange,
  onRemove,
}: {
  index: number;
  condition: EditableCondition;
  features: FeatureSpec[];
  onChange: (patch: Partial<EditableCondition>) => void;
  onRemove?: () => void;
}) {
  const kind = featureKind(condition.feature);
  const known = features.some((f) => f.name === condition.feature);
  const operators: Operator[] = kind === "flag" ? ["EQ", "NEQ"] : OPERATORS;
  const n = index + 1;

  function pickFeature(feature: string) {
    const next = featureKind(feature);
    onChange({
      feature,
      op: next === "flag" ? "EQ" : condition.op === "EQ" || condition.op === "NEQ" ? "GT" : condition.op,
      // A rupee amount typed for one fact means nothing for a count, so the value is cleared
      // when the kind of fact changes.
      value: next === "flag" ? "1" : next === kind ? condition.value : "",
    });
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <NativeSelect
        aria-label={`Condition ${n}: fact`}
        value={condition.feature}
        onChange={(e) => pickFeature(e.target.value)}
        className="w-[290px] max-w-full"
      >
        {!known && <option value={condition.feature}>{condition.feature}</option>}
        {features.map((f) => (
          <option key={f.name} value={f.name} title={f.description}>
            {featureLabel(f.name)}
          </option>
        ))}
      </NativeSelect>

      <NativeSelect
        aria-label={`Condition ${n}: comparison`}
        value={condition.op}
        onChange={(e) => onChange({ op: e.target.value as Operator })}
        className="w-[150px]"
      >
        {operators.map((op) => (
          <option key={op} value={op}>
            {OPERATOR_WORDS[op]}
          </option>
        ))}
      </NativeSelect>

      {kind === "flag" ? (
        <NativeSelect
          aria-label={`Condition ${n}: value`}
          value={condition.value}
          onChange={(e) => onChange({ value: e.target.value })}
          className="w-[100px]"
        >
          <option value="1">Yes</option>
          <option value="0">No</option>
        </NativeSelect>
      ) : (
        <div className="relative w-[150px]">
          {kind === "money" && (
            <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground" aria-hidden="true">
              ₹
            </span>
          )}
          <Input
            aria-label={`Condition ${n}: value${kind === "money" ? " in rupees" : ""}`}
            inputMode="decimal"
            value={condition.value}
            onChange={(e) => onChange({ value: e.target.value })}
            className={cn("tabular", kind === "money" && "pl-7")}
            placeholder={kind === "money" ? "Rupees" : "Number"}
          />
        </div>
      )}

      {onRemove && (
        <Button variant="ghost" size="icon" className="size-8" onClick={onRemove} title={`Remove condition ${n}`}>
          <X aria-hidden="true" />
          <span className="sr-only">Remove condition {n}</span>
        </Button>
      )}
    </div>
  );
}
