package com.runtimeerror.sentinel.console;

import java.util.List;

/** One page of search results. nextCursor is null on the last page. */
public record DecisionPage(List<DecisionSummary> items, String nextCursor) {
}
