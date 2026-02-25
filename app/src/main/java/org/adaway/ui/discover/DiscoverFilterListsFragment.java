package org.adaway.ui.discover;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.databinding.FragmentDiscoverFilterlistsBinding;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.adaway.ui.source.SourceEditActivity;
import org.adaway.util.AppExecutors;
import org.adaway.ui.hosts.FilterListsSubscribeAllWorker;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fragment for browsing and subscribing to FilterLists.com directory.
 *
 * Ported from FilterListsImportActivity. Phase 2 adds tag chip + language spinner filtering.
 */
public class DiscoverFilterListsFragment extends Fragment {

    private FragmentDiscoverFilterlistsBinding binding;

    private static final String PREFS = "filterlists_cache";
    private static final String KEY_LISTS_JSON = "listsJson";
    private static final String KEY_SYNTAXES_JSON = "syntaxesJson";
    private static final String KEY_CACHED_AT = "cachedAt";
    private static final String KEY_TAGS_JSON = "tagsJson";
    private static final String KEY_LANGUAGES_JSON = "languagesJson";
    private static final String KEY_TAGS_CACHED_AT = "tagsCachedAt";
    private static final String KEY_URL_PREFIX = "listUrl_";
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1000L; // 24h

    private final List<FilterListsDirectoryApi.ListSummary> all = new ArrayList<>();
    private final List<FilterListsDirectoryApi.ListSummary> filtered = new ArrayList<>();
    private Map<Integer, String> syntaxNames;
    private final Map<Integer, String> resolvedUrlCache = new HashMap<>();
    private final Set<String> existingUrls = new HashSet<>();
    private HostsSourceDao hostsSourceDao;

    // Tag/language filter state
    private int selectedTagId = 0;
    private int selectedLanguageId = 0;
    private List<FilterListsDirectoryApi.Language> loadedLanguages = new ArrayList<>();

    private Adapter adapter;

    private Integer currentWorkingId = null;
    private String currentWorkingName = null;
    private int currentDone = 0;
    private int currentTotal = 0;

    private int monotonicLastDone = -1;
    private int monotonicLastTotal = -1;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean sourcesUiRefreshScheduled = false;
    private final Runnable sourcesUiRefresh = () -> {
        sourcesUiRefreshScheduled = false;
        if (this.binding == null) return;
        if (adapter != null) adapter.notifyDataSetChanged();
    };

    private long lastAdapterRefreshMs = 0L;

    private Snackbar activeSnackbar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        this.binding = FragmentDiscoverFilterlistsBinding.inflate(inflater, container, false);
        return this.binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        hostsSourceDao = AppDatabase.getInstance(requireContext()).hostsSourceDao();

        binding.filterlistsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Adapter();
        binding.filterlistsRecyclerView.setAdapter(adapter);

        binding.filterlistsSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.filterlistsSubscribeAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                subscribeAll();
            } else {
                unsubscribeAll();
            }
        });

        // Observe live DB changes to update subscribed state
        hostsSourceDao.loadAll().observe(getViewLifecycleOwner(), sources -> {
            if (this.binding == null) return;
            final List<HostsSource> snapshot = sources != null ? new ArrayList<>(sources) : null;
            AppExecutors.getInstance().diskIO().execute(() -> {
                Set<String> urls = new HashSet<>();
                if (snapshot != null) {
                    for (HostsSource s : snapshot) {
                        urls.add(s.getUrl());
                    }
                }
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    existingUrls.clear();
                    existingUrls.addAll(urls);
                    requestAdapterRefreshThrottled(1000);
                });
            });
        });

        // Observe background subscribe-all job
        WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME)
                .observe(getViewLifecycleOwner(), infos -> {
                    if (this.binding == null) return;
                    WorkInfo info = pickBestWorkInfo(infos);
                    boolean running = info != null && (info.getState() == WorkInfo.State.RUNNING
                            || info.getState() == WorkInfo.State.ENQUEUED);

                    if (running && info != null) {
                        int done = info.getProgress().getInt(FilterListsSubscribeAllWorker.PROGRESS_DONE, 0);
                        int total = info.getProgress().getInt(FilterListsSubscribeAllWorker.PROGRESS_TOTAL, 0);
                        String currentName = info.getProgress()
                                .getString(FilterListsSubscribeAllWorker.PROGRESS_CURRENT_NAME);

                        if (total != monotonicLastTotal) {
                            monotonicLastTotal = total;
                            monotonicLastDone = -1;
                        }
                        if (done < monotonicLastDone) {
                            return;
                        }
                        monotonicLastDone = done;
                        currentDone = done;
                        currentTotal = total;
                        currentWorkingName = currentName;

                        binding.filterlistsSubscribeAllStatus.setVisibility(View.VISIBLE);
                        String line;
                        if (total <= 0) {
                            line = "Preparing\u2026";
                        } else {
                            int percent = (int) Math.floor(done * 100.0 / total);
                            line = done + "/" + total + " (" + percent + "%)";
                        }
                        if (currentName != null && !currentName.isEmpty())
                            line += " \u2022 " + currentName;
                        binding.filterlistsSubscribeAllStatus.setText(line);
                    } else {
                        monotonicLastDone = -1;
                        monotonicLastTotal = -1;
                        currentDone = 0;
                        currentTotal = 0;
                        currentWorkingId = null;
                        currentWorkingName = null;
                        binding.filterlistsSubscribeAllStatus.setVisibility(View.GONE);
                    }

                    binding.filterlistsSubscribeAllSwitch.setEnabled(!running);
                    requestAdapterRefreshThrottled(running ? 1000 : 0);
                });

        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeSnackbar != null) {
            activeSnackbar.dismiss();
            activeSnackbar = null;
        }
        this.binding = null;
    }

    private void requestAdapterRefreshThrottled(long minIntervalMs) {
        long now = android.os.SystemClock.elapsedRealtime();
        long waitMs = Math.max(0L, (lastAdapterRefreshMs + minIntervalMs) - now);
        mainHandler.removeCallbacks(sourcesUiRefresh);
        sourcesUiRefreshScheduled = true;
        mainHandler.postDelayed(() -> {
            lastAdapterRefreshMs = android.os.SystemClock.elapsedRealtime();
            sourcesUiRefresh.run();
        }, waitMs);
    }

    private void load() {
        if (binding == null) return;
        binding.filterlistsProgress.setVisibility(View.VISIBLE);
        binding.filterlistsSubscribeAllSwitch.setEnabled(false);

        // Capture application context on main thread — safe to use from any thread thereafter.
        final Context appContext = requireContext().getApplicationContext();

        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                AdAwayApplication app = (AdAwayApplication) appContext;
                FilterListsDirectoryApi api = new FilterListsDirectoryApi(app.getSourceModel().getHttpClientForUi());

                // Load existing sources
                List<HostsSource> existing = hostsSourceDao.getAll();
                Set<String> existingUrlsLocal = new HashSet<>();
                for (HostsSource s : existing) {
                    existingUrlsLocal.add(s.getUrl());
                }

                SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String cachedListsJson = prefs.getString(KEY_LISTS_JSON, null);
                String cachedSyntaxesJson = prefs.getString(KEY_SYNTAXES_JSON, null);
                String cachedTagsJson = prefs.getString(KEY_TAGS_JSON, null);
                String cachedLanguagesJson = prefs.getString(KEY_LANGUAGES_JSON, null);
                long cachedAt = prefs.getLong(KEY_CACHED_AT, 0L);
                long tagsCachedAt = prefs.getLong(KEY_TAGS_CACHED_AT, 0L);
                long now = System.currentTimeMillis();
                boolean listsCacheFresh = cachedListsJson != null && cachedSyntaxesJson != null
                        && (now - cachedAt) < CACHE_TTL_MS;
                boolean tagsCacheFresh = cachedTagsJson != null && cachedLanguagesJson != null
                        && (now - tagsCachedAt) < CACHE_TTL_MS;

                // Show cached lists immediately if available
                if (cachedListsJson != null && cachedSyntaxesJson != null) {
                    try {
                        Map<Integer, String> cachedSyntaxNames = FilterListsDirectoryApi
                                .parseSyntaxNamesJson(cachedSyntaxesJson);
                        List<FilterListsDirectoryApi.ListSummary> cachedLists = FilterListsDirectoryApi
                                .parseListsJson(cachedListsJson);
                        List<FilterListsDirectoryApi.Tag> cachedTags = cachedTagsJson != null
                                ? FilterListsDirectoryApi.parseTagsJson(cachedTagsJson)
                                : new ArrayList<>();
                        List<FilterListsDirectoryApi.Language> cachedLanguages = cachedLanguagesJson != null
                                ? FilterListsDirectoryApi.parseLanguagesJson(cachedLanguagesJson)
                                : new ArrayList<>();
                        AppExecutors.getInstance().mainThread().execute(() -> {
                            if (this.binding == null) return;
                            this.syntaxNames = cachedSyntaxNames;
                            this.existingUrls.clear();
                            this.existingUrls.addAll(existingUrlsLocal);
                            all.clear();
                            all.addAll(cachedLists);
                            populateTagChips(cachedTags);
                            populateLanguageSpinner(cachedLanguages);
                            filter();
                            binding.filterlistsProgress.setVisibility(View.GONE);
                            binding.filterlistsSubscribeAllSwitch.setEnabled(true);
                        });
                    } catch (IOException ignored) {
                        // fall through to network
                    }
                }

                if (listsCacheFresh && tagsCacheFresh) {
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        if (this.binding == null) return;
                        binding.filterlistsProgress.setVisibility(View.GONE);
                        binding.filterlistsSubscribeAllSwitch.setEnabled(true);
                    });
                    return;
                }

                // Refresh from network
                String listsJson = api.getListsJson();
                String syntaxesJson = api.getSyntaxesJson();
                String tagsJson = api.getTagsJson();
                String languagesJson = api.getLanguagesJson();

                Map<Integer, String> syntaxNamesNet = FilterListsDirectoryApi.parseSyntaxNamesJson(syntaxesJson);
                List<FilterListsDirectoryApi.ListSummary> lists = FilterListsDirectoryApi.parseListsJson(listsJson);
                List<FilterListsDirectoryApi.Tag> tags = FilterListsDirectoryApi.parseTagsJson(tagsJson);
                List<FilterListsDirectoryApi.Language> languages = FilterListsDirectoryApi.parseLanguagesJson(languagesJson);

                prefs.edit()
                        .putString(KEY_LISTS_JSON, listsJson)
                        .putString(KEY_SYNTAXES_JSON, syntaxesJson)
                        .putLong(KEY_CACHED_AT, now)
                        .putString(KEY_TAGS_JSON, tagsJson)
                        .putString(KEY_LANGUAGES_JSON, languagesJson)
                        .putLong(KEY_TAGS_CACHED_AT, now)
                        .apply();

                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    this.syntaxNames = syntaxNamesNet;
                    this.existingUrls.clear();
                    this.existingUrls.addAll(existingUrlsLocal);
                    all.clear();
                    all.addAll(lists);
                    populateTagChips(tags);
                    populateLanguageSpinner(languages);
                    filter();
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    binding.filterlistsSubscribeAllSwitch.setEnabled(true);
                });
            } catch (IOException e) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    binding.filterlistsSubscribeAllSwitch.setEnabled(false);
                    showSnackbar("Failed to load FilterLists.com");
                });
            }
        });
    }

    private void populateTagChips(@NonNull List<FilterListsDirectoryApi.Tag> tags) {
        if (binding == null) return;
        ChipGroup chipGroup = binding.tagChipGroup;
        chipGroup.removeAllViews();

        // "All" chip
        Chip allChip = new Chip(requireContext());
        allChip.setText("All");
        allChip.setCheckable(true);
        allChip.setChecked(selectedTagId == 0);
        allChip.setTag(0);
        allChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedTagId = 0;
                filter();
            }
        });
        chipGroup.addView(allChip);

        for (FilterListsDirectoryApi.Tag tag : tags) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag.name);
            chip.setCheckable(true);
            chip.setChecked(selectedTagId == tag.id);
            chip.setTag(tag.id);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedTagId = tag.id;
                    filter();
                }
            });
            chipGroup.addView(chip);
        }
    }

    private void populateLanguageSpinner(@NonNull List<FilterListsDirectoryApi.Language> languages) {
        if (binding == null) return;
        loadedLanguages = new ArrayList<>(languages);

        List<String> items = new ArrayList<>();
        items.add("All Languages");
        for (FilterListsDirectoryApi.Language lang : languages) {
            items.add(lang.name);
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, items);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.languageSpinner.setAdapter(spinnerAdapter);

        binding.languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedLanguageId = 0;
                } else {
                    selectedLanguageId = loadedLanguages.get(position - 1).id;
                }
                filter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedLanguageId = 0;
            }
        });
    }

    private void filter() {
        if (binding == null) return;
        String q = binding.filterlistsSearchEditText.getText() != null
                ? binding.filterlistsSearchEditText.getText().toString().toLowerCase(Locale.ROOT).trim()
                : "";
        filtered.clear();
        for (FilterListsDirectoryApi.ListSummary s : all) {
            if (!q.isEmpty()) {
                String name = s.name != null ? s.name.toLowerCase(Locale.ROOT) : "";
                String desc = s.description != null ? s.description.toLowerCase(Locale.ROOT) : "";
                if (!name.contains(q) && !desc.contains(q)) continue;
            }
            if (selectedTagId != 0 && !hasId(s.tagIds, selectedTagId)) continue;
            if (selectedLanguageId != 0 && !hasId(s.languageIds, selectedLanguageId)) continue;
            filtered.add(s);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private static boolean hasId(@Nullable int[] ids, int want) {
        if (ids == null) return false;
        for (int id : ids) if (id == want) return true;
        return false;
    }

    @Nullable
    private String getCachedUrlForId(int id) {
        String url = resolvedUrlCache.get(id);
        if (url != null) return url;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        url = prefs.getString(KEY_URL_PREFIX + id, null);
        if (url != null) {
            resolvedUrlCache.put(id, url);
        }
        return url;
    }

    private void onPick(FilterListsDirectoryApi.ListSummary summary) {
        if (binding == null) return;
        binding.filterlistsProgress.setVisibility(View.VISIBLE);
        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                AdAwayApplication app = (AdAwayApplication) appContext;
                FilterListsDirectoryApi api = new FilterListsDirectoryApi(app.getSourceModel().getHttpClientForUi());
                FilterListsDirectoryApi.ListDetails details = api.getListDetails(summary.id);
                String url = details.pickBestDownloadUrl();
                if (url != null) {
                    resolvedUrlCache.put(summary.id, url);
                }
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    if (url == null) {
                        showSnackbar("No direct hosts URL found for this entry. Use its homepage.");
                        return;
                    }
                    Intent intent = new Intent(requireContext(), SourceEditActivity.class);
                    intent.putExtra(SourceEditActivity.EXTRA_INITIAL_LABEL, details.name);
                    intent.putExtra(SourceEditActivity.EXTRA_INITIAL_URL, url);
                    intent.putExtra(SourceEditActivity.EXTRA_INITIAL_ALLOW, false);
                    intent.putExtra(SourceEditActivity.EXTRA_INITIAL_REDIRECT, false);
                    startActivity(intent);
                });
            } catch (IOException e) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    showSnackbar("Failed to resolve list URL");
                });
            }
        });
    }

    private void setSubscribed(FilterListsDirectoryApi.ListSummary summary, boolean subscribed) {
        if (binding == null) return;
        binding.filterlistsProgress.setVisibility(View.VISIBLE);
        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                String url = resolvedUrlCache.get(summary.id);
                if (url == null) {
                    AdAwayApplication app = (AdAwayApplication) appContext;
                    FilterListsDirectoryApi api = new FilterListsDirectoryApi(
                            app.getSourceModel().getHttpClientForUi());
                    FilterListsDirectoryApi.ListDetails details = api.getListDetails(summary.id);
                    url = details.pickBestDownloadUrl();
                    if (url != null) {
                        resolvedUrlCache.put(summary.id, url);
                    }
                }
                if (url == null) {
                    String msg = "No direct download URL for this list";
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        if (this.binding == null) return;
                        binding.filterlistsProgress.setVisibility(View.GONE);
                        showSnackbar(msg);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    });
                    return;
                }

                if (subscribed) {
                    HostsSource src = new HostsSource();
                    src.setLabel(summary.name != null ? summary.name : url);
                    src.setUrl(url);
                    src.setEnabled(true);
                    src.setAllowEnabled(false);
                    src.setRedirectEnabled(false);
                    hostsSourceDao.insert(src);
                    existingUrls.add(url);
                } else {
                    HostsSource existing = hostsSourceDao.getByUrl(url).orElse(null);
                    if (existing != null) {
                        hostsSourceDao.delete(existing);
                    }
                    existingUrls.remove(url);
                }

                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    if (adapter != null) adapter.notifyDataSetChanged();
                    showSnackbar(subscribed ? "Subscribed" : "Unsubscribed");
                });
            } catch (IOException e) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    binding.filterlistsProgress.setVisibility(View.GONE);
                    showSnackbar("Failed to update subscription");
                    if (adapter != null) adapter.notifyDataSetChanged();
                });
            }
        });
    }

    private void subscribeAll() {
        if (binding == null) return;
        binding.filterlistsSubscribeAllStatus.setVisibility(View.VISIBLE);
        binding.filterlistsSubscribeAllStatus.setText("Preparing\u2026");
        binding.filterlistsSubscribeAllSwitch.setEnabled(false);
        currentDone = 0;
        currentTotal = 0;
        currentWorkingId = null;
        currentWorkingName = "Preparing\u2026";
        if (adapter != null) adapter.notifyDataSetChanged();

        try {
            binding.filterlistsSearchEditText.clearFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(binding.filterlistsSearchEditText.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FilterListsSubscribeAllWorker.class).build();
        WorkManager wm = WorkManager.getInstance(requireContext());
        wm.enqueueUniqueWork(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);

        showSnackbar("Running in background\u2026");
    }

    private void unsubscribeAll() {
        if (binding == null) return;
        binding.filterlistsSubscribeAllSwitch.setEnabled(false);
        AppExecutors.getInstance().diskIO().execute(() -> {
            hostsSourceDao.deleteAll();
            existingUrls.clear();
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (this.binding == null) return;
                binding.filterlistsSubscribeAllSwitch.setEnabled(true);
                if (adapter != null) adapter.notifyDataSetChanged();
                showSnackbar("Unsubscribed from all lists");
            });
        });
    }

    private void showSnackbar(String message) {
        if (binding == null) return;
        activeSnackbar = Snackbar.make(binding.filterlistsRecyclerView, message, Snackbar.LENGTH_LONG);
        activeSnackbar.show();
    }

    @Nullable
    private static WorkInfo pickBestWorkInfo(@Nullable List<WorkInfo> infos) {
        if (infos == null || infos.isEmpty()) return null;
        for (WorkInfo i : infos) {
            if (i.getState() == WorkInfo.State.RUNNING) return i;
        }
        for (WorkInfo i : infos) {
            if (i.getState() == WorkInfo.State.ENQUEUED) return i;
        }
        return infos.get(infos.size() - 1);
    }

    private String formatSyntax(int[] ids) {
        if (ids == null || ids.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(", ");
            String name = syntaxNames != null ? syntaxNames.get(ids[i]) : null;
            sb.append(name != null ? name : ("Syntax " + ids[i]));
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------------------------

    private class Adapter extends RecyclerView.Adapter<RowVH> {
        @NonNull
        @Override
        public RowVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.filterlists_import_item, parent, false);
            return new RowVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RowVH holder, int position) {
            FilterListsDirectoryApi.ListSummary s = filtered.get(position);
            String cachedUrl = getCachedUrlForId(s.id);
            boolean isSubscribed = cachedUrl != null && existingUrls.contains(cachedUrl);

            holder.switchView.setOnCheckedChangeListener(null);
            holder.switchView.setChecked(isSubscribed);
            holder.switchView.setOnCheckedChangeListener((buttonView, checked) -> setSubscribed(s, checked));

            holder.name.setText(s.name);
            holder.syntax.setText(formatSyntax(s.syntaxIds));

            boolean isCurrent = currentWorkingId != null && currentWorkingId == s.id;
            if (isCurrent) {
                holder.status.setVisibility(View.VISIBLE);
                holder.status.setText("Processing\u2026");
            } else if (isSubscribed) {
                holder.status.setVisibility(View.VISIBLE);
                holder.status.setText("Subscribed");
            } else {
                holder.status.setVisibility(View.GONE);
            }

            holder.desc.setText(s.description != null ? s.description : "");
            holder.itemView.setOnClickListener(v -> onPick(s));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }
    }

    static class RowVH extends RecyclerView.ViewHolder {
        final MaterialSwitch switchView;
        final TextView name;
        final TextView syntax;
        final TextView status;
        final TextView desc;

        RowVH(@NonNull View itemView) {
            super(itemView);
            switchView = itemView.findViewById(R.id.filterlistsItemSwitch);
            name = itemView.findViewById(R.id.filterlistsItemName);
            syntax = itemView.findViewById(R.id.filterlistsItemSyntax);
            status = itemView.findViewById(R.id.filterlistsItemStatus);
            desc = itemView.findViewById(R.id.filterlistsItemDesc);
        }
    }
}
