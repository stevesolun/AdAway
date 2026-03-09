package org.adaway.ui.ai;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.R;
import org.adaway.databinding.BottomSheetAiSuggestBinding;
import org.adaway.model.ai.AiActionExecutor;
import org.adaway.model.ai.AiAgentAction;
import org.adaway.model.ai.AiAgentResponse;
import org.adaway.model.ai.FilterListSuggester;
import org.adaway.model.ai.LlmApiException;
import org.adaway.model.ai.LlmProvider;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * BottomSheet that lets users describe their privacy intent in natural language
 * and receive AI-powered filter management actions (subscribe, enable/disable categories,
 * block/allow domains, trigger updates).
 *
 * <p>Flow:
 * <ol>
 *   <li>User types a query ("block ads, protect privacy, keep WhatsApp working")</li>
 *   <li>Tap Ask → LLM agent call on networkIO thread</li>
 *   <li>Planned actions shown as text items in the actions container</li>
 *   <li>Tap Execute → actions run on diskIO thread, results shown inline</li>
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

    /** Currently displayed agent response (set after LLM responds). */
    private AiAgentResponse currentResponse;

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
        // ATK-15: modelIndex could be negative if SharedPreferences is corrupted; clamp to [0, len).
        String[] modelNames = provider.getModelDisplayNames();
        int safeIndex = Math.max(0, Math.min(modelIndex, modelNames.length - 1));
        String modelShort = modelNames[safeIndex]
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

        // Execute button
        this.binding.aiApplyButton.setOnClickListener(v -> onExecuteClicked());
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
                AiAgentResponse response = suggester.execute(appContext, query);
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (binding == null) return;
                    setLoadingState(false);
                    showResponse(response);
                });
            } catch (Exception e) {
                // ATK-19: log only class name + code, not raw message (may contain API response data)
                String errDetail = (e instanceof LlmApiException)
                        ? "HTTP " + ((LlmApiException) e).getHttpCode()
                        : e.getClass().getSimpleName();
                Timber.w("LLM agent call failed: %s", errDetail);
                // ATK-16: Only show user-friendly messages from LlmApiException; fall back to
                // generic string for all other exceptions to avoid leaking internal details.
                final String userMessage = (e instanceof LlmApiException && e.getMessage() != null)
                        ? e.getMessage()
                        : getString(R.string.ai_suggest_error_generic);
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (binding == null) return;
                    setLoadingState(false);
                    showError(userMessage);
                });
            }
        });
    }

    private void onExecuteClicked() {
        if (binding == null) return; // QA-13: guard against view destroyed before click handled
        if (currentResponse == null) return;
        if (currentResponse.actions.isEmpty()) {
            Snackbar.make(binding.getRoot(),
                    R.string.ai_suggest_no_actions, Snackbar.LENGTH_SHORT).show();
            return;
        }

        binding.aiApplyButton.setEnabled(false);
        final Context appContext = requireContext().getApplicationContext();
        final List<AiAgentAction> actionsSnapshot = new ArrayList<>(currentResponse.actions);

        AppExecutors.getInstance().diskIO().execute(() -> {
            AiActionExecutor executor = new AiActionExecutor(appContext);
            List<String> results = new ArrayList<>();
            for (AiAgentAction action : actionsSnapshot) {
                results.add(executor.execute(action));
            }

            final String resultText = joinResults(results);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (binding == null) return;
                binding.aiSummaryText.setText(resultText);
                binding.aiSummaryText.setVisibility(View.VISIBLE);
                // Keep Execute disabled — actions already run
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

    private void showResponse(@NonNull AiAgentResponse response) {
        if (binding == null) return;
        currentResponse = response;

        // Reasoning text
        binding.aiReasoningText.setText(response.reasoning);

        // Populate action items
        binding.aiActionsContainer.removeAllViews();
        if (response.actions.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText(R.string.ai_suggest_no_actions_available);
            emptyView.setTextSize(13);
            binding.aiActionsContainer.addView(emptyView);
        } else {
            for (AiAgentAction action : response.actions) {
                TextView tv = new TextView(requireContext());
                tv.setText("• " + describeAction(action));
                tv.setTextSize(13);
                tv.setPadding(0, 4, 0, 4);
                binding.aiActionsContainer.addView(tv);
            }
        }

        // Reset results area
        binding.aiSummaryText.setVisibility(View.GONE);
        binding.aiSummaryText.setText("");
        binding.aiApplyButton.setEnabled(!response.actions.isEmpty());

        binding.aiResultsLayout.setVisibility(View.VISIBLE);
        binding.aiErrorText.setVisibility(View.GONE);
    }

    private void showError(String message) {
        if (binding == null) return;
        binding.aiErrorText.setText(message);
        binding.aiErrorText.setVisibility(View.VISIBLE);
        binding.aiResultsLayout.setVisibility(View.GONE);
    }

    /** Returns a human-readable description of a planned action (shown before execution). */
    private String describeAction(@NonNull AiAgentAction action) {
        switch (action.type) {
            case SUBSCRIBE_CATEGORY:
                return getString(R.string.ai_action_subscribe_category, action.payload);
            case ENABLE_CATEGORY:
                return getString(R.string.ai_action_enable_category, action.payload);
            case DISABLE_CATEGORY:
                return getString(R.string.ai_action_disable_category, action.payload);
            case UPDATE_SOURCES:
                return getString(R.string.ai_action_update_sources);
            case CHECK_DOMAIN:
                return getString(R.string.ai_action_check_domain, action.payload);
            case ALLOW_DOMAIN:
                return getString(R.string.ai_action_allow_domain, action.payload);
            case BLOCK_DOMAIN:
                return getString(R.string.ai_action_block_domain, action.payload);
            default:
                return action.toString();
        }
    }

    private static String joinResults(@NonNull List<String> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append("• ").append(results.get(i));
        }
        return sb.toString();
    }
}
