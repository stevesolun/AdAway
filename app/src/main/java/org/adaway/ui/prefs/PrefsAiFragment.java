package org.adaway.ui.prefs;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.model.ai.FilterListSuggester;
import org.adaway.model.ai.LlmProvider;
import org.adaway.model.ai.SecureApiKeyStore;
import org.adaway.util.AppExecutors;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Locale;

import timber.log.Timber;

/**
 * Preferences fragment for AI / LLM configuration.
 *
 * <p>Allows the user to:
 * <ul>
 *   <li>Select the AI provider (Claude, Gemini, OpenAI)</li>
 *   <li>Select the model variant</li>
 *   <li>Set API keys per provider (stored encrypted via {@link SecureApiKeyStore})</li>
 * </ul>
 */
public class PrefsAiFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_ai);
        bindProviderPref();
        bindApiKeyPrefs();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_ai_title);
    }

    @Override
    public void onResume() {
        super.onResume();
        PrefsActivity.setAppBarTitle(this, R.string.pref_ai_title);
        refreshApiKeySummaries();
    }

    // -------------------------------------------------------------------------
    // Provider + model binding
    // -------------------------------------------------------------------------

    private void bindProviderPref() {
        ListPreference providerPref = findPreference("pref_ai_provider");
        ListPreference modelPref = findPreference("pref_ai_model");
        if (providerPref == null || modelPref == null) return;

        // Restore saved provider
        LlmProvider currentProvider = FilterListSuggester.getSelectedProvider(requireContext());
        providerPref.setValue(String.valueOf(currentProvider.ordinal()));
        updateModelEntries(modelPref, currentProvider);

        // Restore saved model index for this provider
        int savedModelIndex = FilterListSuggester.getSelectedModelIndex(requireContext(),
                currentProvider);
        modelPref.setValue(String.valueOf(savedModelIndex));

        providerPref.setOnPreferenceChangeListener((preference, newValue) -> {
            int ordinal = Integer.parseInt((String) newValue);
            LlmProvider provider = LlmProvider.fromOrdinal(ordinal);
            FilterListSuggester.setSelectedProvider(requireContext(), provider);
            updateModelEntries(modelPref, provider);
            // Restore the per-provider saved model index (not forced to 0)
            int savedIndex = FilterListSuggester.getSelectedModelIndex(requireContext(), provider);
            modelPref.setValue(String.valueOf(savedIndex));
            return true;
        });

        modelPref.setOnPreferenceChangeListener((preference, newValue) -> {
            int index = Integer.parseInt((String) newValue);
            LlmProvider provider = FilterListSuggester.getSelectedProvider(requireContext());
            FilterListSuggester.setSelectedModelIndex(requireContext(), provider, index);
            return true;
        });
    }

    private void updateModelEntries(ListPreference modelPref, LlmProvider provider) {
        // Use the effective list (dynamically fetched if available, hardcoded otherwise)
        String[] displayNames = FilterListSuggester.getEffectiveModelDisplayNames(
                requireContext(), provider);
        String[] values = new String[displayNames.length];
        for (int i = 0; i < values.length; i++) values[i] = String.valueOf(i);
        modelPref.setEntries(displayNames);
        modelPref.setEntryValues(values);
    }

    // -------------------------------------------------------------------------
    // API key binding
    // -------------------------------------------------------------------------

    private void bindApiKeyPrefs() {
        for (LlmProvider provider : LlmProvider.values()) {
            String prefKey = "pref_ai_key_" + provider.name().toLowerCase(Locale.ROOT);
            Preference pref = findPreference(prefKey);
            if (pref == null) continue;

            pref.setOnPreferenceClickListener(preference -> {
                showApiKeyDialog(provider);
                return true;
            });
        }
    }

    private void refreshApiKeySummaries() {
        for (LlmProvider provider : LlmProvider.values()) {
            String prefKey = "pref_ai_key_" + provider.name().toLowerCase(Locale.ROOT);
            Preference pref = findPreference(prefKey);
            if (pref == null) continue;

            AppExecutors.getInstance().diskIO().execute(() -> {
                boolean hasKey = false;
                try {
                    SecureApiKeyStore store = SecureApiKeyStore.getInstance(requireContext());
                    hasKey = store.hasApiKey(provider.getApiKeyName());
                } catch (GeneralSecurityException | IOException e) {
                    Timber.w(e, "Failed to check API key for %s", provider);
                }
                final boolean finalHasKey = hasKey;
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (!isAdded() || pref == null) return;
                    pref.setSummary(finalHasKey
                            ? getString(R.string.pref_ai_key_set)
                            : getString(R.string.pref_ai_key_not_set));
                });
            });
        }
    }

    /** Placeholder shown in the EditText when a key already exists (never the actual key). */
    private static final String KEY_PLACEHOLDER = "••••••••••••••••";

    private void showApiKeyDialog(LlmProvider provider) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(getString(R.string.pref_ai_key_dialog_hint));

        // Pre-fill with a visual placeholder so the user knows a key is stored.
        // We deliberately do NOT put the actual key into the field.
        AppExecutors.getInstance().diskIO().execute(() -> {
            boolean hasKey = false;
            try {
                SecureApiKeyStore store = SecureApiKeyStore.getInstance(requireContext());
                hasKey = store.hasApiKey(provider.getApiKeyName());
            } catch (GeneralSecurityException | IOException e) {
                Timber.w(e, "Failed to check API key for %s", provider);
            }
            final boolean finalHasKey = hasKey;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (!isAdded()) return;
                if (finalHasKey) {
                    input.setText(KEY_PLACEHOLDER);
                    input.selectAll(); // select all so first keystroke replaces placeholder
                }
            });
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.pref_ai_key_dialog_title, provider.getDisplayName()))
                .setMessage(R.string.pref_ai_key_dialog_message)
                .setView(input)
                .setPositiveButton(R.string.button_save, (dialog, which) -> {
                    String typed = input.getText().toString().trim();
                    // If the user left the placeholder unchanged, treat as "keep existing key"
                    if (KEY_PLACEHOLDER.equals(typed)) return;
                    saveApiKey(provider, typed.isEmpty() ? null : typed);
                })
                .setNeutralButton(R.string.pref_ai_key_clear, (dialog, which) ->
                        saveApiKey(provider, null))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void saveApiKey(LlmProvider provider, String key) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                SecureApiKeyStore store = SecureApiKeyStore.getInstance(requireContext());
                store.putApiKey(provider.getApiKeyName(), key);

                // When a key is saved (not cleared), fetch available models from the API
                // so the model picker shows live options instead of only the hardcoded list.
                if (key != null) {
                    FilterListSuggester.fetchAndCacheModels(requireContext(), provider);
                }

                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (!isAdded()) return;
                    refreshApiKeySummaries();
                    // Refresh model picker with newly fetched model list
                    ListPreference modelPref = findPreference("pref_ai_model");
                    if (modelPref != null
                            && FilterListSuggester.getSelectedProvider(requireContext())
                            == provider) {
                        updateModelEntries(modelPref, provider);
                    }
                    int msg = (key == null)
                            ? R.string.pref_ai_key_cleared
                            : R.string.pref_ai_key_saved;
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                });
            } catch (GeneralSecurityException | IOException e) {
                Timber.e(e, "Failed to save API key for %s", provider);
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            R.string.pref_ai_key_save_error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
