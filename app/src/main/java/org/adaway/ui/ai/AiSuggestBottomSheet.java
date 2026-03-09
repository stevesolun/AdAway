package org.adaway.ui.ai;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.R;
import org.adaway.databinding.BottomSheetAiSuggestBinding;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.ai.FilterListSuggester;
import org.adaway.model.ai.LlmProvider;
import org.adaway.model.ai.LlmSuggestion;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterListCategory;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * BottomSheet that lets users describe their privacy intent in natural language
 * and receive AI-powered filter list recommendations.
 *
 * <p>Flow:
 * <ol>
 *   <li>User types a query ("block ads, protect privacy, keep WhatsApp working")</li>
 *   <li>Tap Ask → LLM call on networkIO thread</li>
 *   <li>Results shown as checkable category chips</li>
 *   <li>Tap Apply → insert selected filter lists into DB</li>
 * </ol>
 */
public class AiSuggestBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "AiSuggestBottomSheet";
    private static final String ARG_PREFILL_QUERY = "prefill_query";

    /**
     * Creates a new instance with an optional pre-filled query.
     * Pass the user's typed text when launching from the Home screen AI box.
     *
     * @param prefillQuery text to pre-fill in the query field, or {@code null} for empty
     */
    public static AiSuggestBottomSheet newInstance(@Nullable String prefillQuery) {
        AiSuggestBottomSheet sheet = new AiSuggestBottomSheet();
        if (prefillQuery != null && !prefillQuery.isEmpty()) {
            Bundle args = new Bundle();
            args.putString(ARG_PREFILL_QUERY, prefillQuery);
            sheet.setArguments(args);
        }
        return sheet;
    }

    private BottomSheetAiSuggestBinding binding;
    private final FilterListSuggester suggester = new FilterListSuggester();

    /** Currently displayed suggestion (set after LLM responds). */
    private LlmSuggestion currentSuggestion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        this.binding = BottomSheetAiSuggestBinding.inflate(inflater, container, false);
        return this.binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Show active provider · model in header
        LlmProvider provider = FilterListSuggester.getSelectedProvider(requireContext());
        int modelIndex = FilterListSuggester.getSelectedModelIndex(requireContext());
        String providerShort = provider.getDisplayName()
                .replaceAll("\\s*\\(.*\\)", "").trim(); // "Claude (Anthropic)" → "Claude"
        String modelShort = provider.getModelDisplayNames()[
                Math.min(modelIndex, provider.getModelDisplayNames().length - 1)]
                .replaceAll("\\s*\\(.*\\)", "").trim(); // "Sonnet 4.6 (balanced)" → "Sonnet 4.6"
        this.binding.aiProviderLabel.setText(
                getString(R.string.ai_provider_model_label, providerShort, modelShort));

        // Pre-fill query if launched from Home screen AI box
        Bundle args = getArguments();
        if (args != null) {
            String prefill = args.getString(ARG_PREFILL_QUERY);
            if (prefill != null && !prefill.isEmpty()) {
                this.binding.aiQueryEditText.setText(prefill);
                this.binding.aiQueryEditText.setSelection(prefill.length());
            }
        }

        // Ask button
        this.binding.aiAskButton.setOnClickListener(v -> onAskClicked());

        // Allow "Done" action on keyboard to trigger ask
        this.binding.aiQueryEditText.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onAskClicked();
                return true;
            }
            return false;
        });

        // Apply button
        this.binding.aiApplyButton.setOnClickListener(v -> onApplyClicked());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void onAskClicked() {
        String query = binding.aiQueryEditText.getText() == null ? ""
                : binding.aiQueryEditText.getText().toString().trim();
        if (query.isEmpty()) {
            binding.aiQueryInputLayout.setError(getString(R.string.ai_suggest_query_empty));
            return;
        }
        binding.aiQueryInputLayout.setError(null);

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.aiQueryEditText.getWindowToken(), 0);

        // Check API key configured
        if (!FilterListSuggester.hasApiKey(requireContext())) {
            showError(getString(R.string.ai_suggest_no_api_key));
            return;
        }

        setLoadingState(true);

        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                LlmSuggestion suggestion = suggester.suggest(appContext, query);
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (binding == null) return;
                    setLoadingState(false);
                    showSuggestion(suggestion);
                });
            } catch (Exception e) {
                Timber.e(e, "LLM suggestion failed");
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (binding == null) return;
                    setLoadingState(false);
                    showError(e.getMessage() != null ? e.getMessage()
                            : getString(R.string.ai_suggest_error_generic));
                });
            }
        });
    }

    private void onApplyClicked() {
        if (currentSuggestion == null) return;

        // Collect checked categories from chip group
        List<FilterListCategory> selectedCategories = new ArrayList<>();
        for (int i = 0; i < binding.aiCategoryChipGroup.getChildCount(); i++) {
            View child = binding.aiCategoryChipGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.isChecked()) {
                    selectedCategories.add((FilterListCategory) chip.getTag());
                }
            }
        }

        if (selectedCategories.isEmpty()) {
            Snackbar.make(binding.getRoot(),
                    R.string.ai_suggest_no_categories_selected, Snackbar.LENGTH_SHORT).show();
            return;
        }

        binding.aiApplyButton.setEnabled(false);
        final Context appContext = requireContext().getApplicationContext();

        AppExecutors.getInstance().diskIO().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(appContext);
            int added = 0;
            for (FilterListCategory category : selectedCategories) {
                List<FilterListCatalog.CatalogEntry> entries =
                        FilterListCatalog.getByCategory(category);
                for (FilterListCatalog.CatalogEntry entry : entries) {
                    if (!db.hostsSourceDao().getByUrl(entry.url).isPresent()) {
                        HostsSource source = entry.toHostsSource();
                        source.setEnabled(true);
                        db.hostsSourceDao().insert(source);
                        added++;
                    }
                }
            }

            final int finalAdded = added;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (binding == null) return;
                if (finalAdded > 0) {
                    Snackbar.make(binding.getRoot(),
                            getResources().getQuantityString(
                                    R.plurals.ai_suggest_applied, finalAdded, finalAdded),
                            Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(binding.getRoot(),
                            R.string.ai_suggest_already_subscribed,
                            Snackbar.LENGTH_SHORT).show();
                }
                dismiss();
            });
        });
    }

    // -------------------------------------------------------------------------
    // UI state helpers
    // -------------------------------------------------------------------------

    private void setLoadingState(boolean loading) {
        if (binding == null) return;
        binding.aiProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.aiAskButton.setEnabled(!loading);
        binding.aiQueryEditText.setEnabled(!loading);
        if (loading) {
            binding.aiResultsLayout.setVisibility(View.GONE);
            binding.aiErrorText.setVisibility(View.GONE);
        }
    }

    private void showSuggestion(@NonNull LlmSuggestion suggestion) {
        if (binding == null) return;
        currentSuggestion = suggestion;

        // Reasoning text
        binding.aiReasoningText.setText(suggestion.reasoning);

        // Category chips
        binding.aiCategoryChipGroup.removeAllViews();
        for (FilterListCategory category : suggestion.categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(getCategoryDisplayName(category));
            chip.setCheckable(true);
            chip.setChecked(true);
            chip.setTag(category);
            if (category.mayBreakServices()) {
                chip.setChipBackgroundColorResource(
                        com.google.android.material.R.color.design_default_color_error);
            }
            chip.setOnCheckedChangeListener((c, checked) -> updateSummary(suggestion.categories));
            binding.aiCategoryChipGroup.addView(chip);
        }

        updateSummary(suggestion.categories);
        binding.aiResultsLayout.setVisibility(View.VISIBLE);
        binding.aiErrorText.setVisibility(View.GONE);
    }

    private void updateSummary(List<FilterListCategory> allCategories) {
        if (binding == null) return;

        // Count total entries across selected categories
        int total = 0;
        for (int i = 0; i < binding.aiCategoryChipGroup.getChildCount(); i++) {
            View child = binding.aiCategoryChipGroup.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                FilterListCategory cat = (FilterListCategory) child.getTag();
                total += FilterListCatalog.getByCategory(cat).size();
            }
        }
        binding.aiSummaryText.setText(
                getResources().getQuantityString(R.plurals.ai_suggest_summary, total, total));
    }

    private void showError(String message) {
        if (binding == null) return;
        binding.aiErrorText.setText(message);
        binding.aiErrorText.setVisibility(View.VISIBLE);
        binding.aiResultsLayout.setVisibility(View.GONE);
    }

    private String getCategoryDisplayName(FilterListCategory category) {
        try {
            return getString(category.getLabelResId());
        } catch (Exception e) {
            return category.name();
        }
    }
}
