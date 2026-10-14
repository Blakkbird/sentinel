package com.runtimeerror.sentinel.engine;

/**
 * The comparisons a rule condition can make between a feature and a constant.
 *
 * Each constant carries its own implementation of apply(). That is an "enum with
 * behaviour": the compiler guarantees every operator has an implementation, and there
 * is no switch statement anywhere that could fall out of step when one is added.
 */
public enum Operator {
    GT(">") {
        @Override
        public boolean apply(double left, double right) {
            return left > right;
        }
    },
    GTE(">=") {
        @Override
        public boolean apply(double left, double right) {
            return left >= right;
        }
    },
    LT("<") {
        @Override
        public boolean apply(double left, double right) {
            return left < right;
        }
    },
    LTE("<=") {
        @Override
        public boolean apply(double left, double right) {
            return left <= right;
        }
    },
    // Exact equality on doubles is normally a mistake. It is safe here because every
    // feature we compare with EQ or NEQ is a whole number: a count, or a 0/1 flag.
    EQ("==") {
        @Override
        public boolean apply(double left, double right) {
            return left == right;
        }
    },
    NEQ("!=") {
        @Override
        public boolean apply(double left, double right) {
            return left != right;
        }
    };

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public abstract boolean apply(double left, double right);

    /** Used when rules are loaded from JSON in stage 3. */
    public static Operator fromSymbol(String symbol) {
        for (Operator op : values()) {
            if (op.symbol.equals(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("unknown operator: " + symbol);
    }
}
