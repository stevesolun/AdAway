package org.adaway.model.ai;

import org.adaway.model.source.FilterListCategory;

import java.util.List;

/**
 * Result of an LLM-powered filter list suggestion.
 */
public class LlmSuggestion {

    /** Categories the LLM recommended enabling. */
    public final List<FilterListCategory> categories;

    /** Human-readable explanation from the LLM. */
    public final String reasoning;

    public LlmSuggestion(List<FilterListCategory> categories, String reasoning) {
        this.categories = categories;
        this.reasoning = reasoning;
    }
}
