package org.adaway.ui.hosts;

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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.adaway.ui.source.SourceEditActivity;
import org.adaway.util.AppExecutors;

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
 * Import filter lists from FilterLists.com directory.
 *
 * Only "hosts" compatible lists are shown (Syntax id 1: Hosts localhost IPv4).
 * API docs: https://api.filterlists.com/index.html
 */
public class FilterListsImportActivity extends AppCompatActivity {
    // We now show ALL filter lists, not just hosts.

    private EditText searchEditText;
    private RecyclerView recyclerView;
    private View progress;
    private MaterialButton subscribeAllButton;
    private TextView subscribeAllStatus;

    private static final String PREFS = "filterlists_cache";
    private static final String KEY_LISTS_JSON = "listsJson";
    private static final String KEY_SYNTAXES_JSON = "syntaxesJson";
    private static final String KEY_CACHED_AT = "cachedAt";
    private static final String KEY_URL_PREFIX = "listUrl_";
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1000L; // 24h

    private final List<FilterListsDirectoryApi.ListSummary> all = new ArrayList<>();
    private final List<FilterListsDirectoryApi.ListSummary> filtered = new ArrayList<>();
    private Map<Integer, String> syntaxNames;
    private final Map<Integer, String> resolvedUrlCache = new HashMap<>();
    private final Set<String> existingUrls = new HashSet<>();
    private HostsSourceDao hostsSourceDao;

    private Adapter adapter;

    private Integer currentWorkingId = null;
    private String currentWorkingName = null;
    private int currentDone = 0;
    private int currentTotal = 0;

    // Avoid UI jank/ANR when many sources are inserted quickly (subscribe-all).
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean sourcesUiRefreshScheduled = false;
    private final Runnable sourcesUiRefresh = () -> {
        sourcesUiRefreshScheduled = false;
        if (adapter != null) adapter.notifyDataSetChanged();
    };

    private long lastAdapterRefreshMs = 0L;
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.filterlists_import_activity);

        hostsSourceDao = AppDatabase.getInstance(this).hostsSourceDao();

        MaterialToolbar toolbar = findViewById(R.id.filterlistsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.filterlistsProgress);
        searchEditText = findViewById(R.id.filterlistsSearchEditText);
        subscribeAllButton = findViewById(R.id.filterlistsSubscribeAllButton);
        subscribeAllStatus = findViewById(R.id.filterlistsSubscribeAllStatus);
        recyclerView = findViewById(R.id.filterlistsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new Adapter();
        recyclerView.setAdapter(adapter);

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        subscribeAllButton.setOnClickListener(v -> subscribeAll());

        // Live update of subscribed state while background job is running.
        hostsSourceDao.loadAll().observe(this, sources -> {
            // This callback runs on the main thread. Building a large HashSet + refreshing the adapter
            // too often can cause ANRs when thousands of sources are inserted quickly.
            final List<HostsSource> snapshot = sources != null ? new ArrayList<>(sources) : null;
            AppExecutors.getInstance().diskIO().execute(() -> {
                Set<String> urls = new HashSet<>();
                if (snapshot != null) {
                    for (HostsSource s : snapshot) {
                        urls.add(s.getUrl());
                    }
                }
                AppExecutors.getInstance().mainThread().execute(() -> {
                    existingUrls.clear();
                    existingUrls.addAll(urls);
                    // Refresh at most once per second while subscribe-all is hammering the DB.
                    requestAdapterRefreshThrottled(1000);
                });
            });
        });

        // Observe background subscribe-all job to show in-app progress UI.
        WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME)
                .observe(this, infos -> {
                    WorkInfo info = pickBestWorkInfo(infos);
                    boolean running = info != null && (info.getState() == WorkInfo.State.RUNNING || info.getState() == WorkInfo.State.ENQUEUED);

                    if (running && info != null) {
                        int done = info.getProgress().getInt(FilterListsSubscribeAllWorker.PROGRESS_DONE, 0);
                        int total = info.getProgress().getInt(FilterListsSubscribeAllWorker.PROGRESS_TOTAL, 0);
                        int currentId = info.getProgress().getInt(FilterListsSubscribeAllWorker.PROGRESS_CURRENT_ID, -1);
                        String currentName = info.getProgress().getString(FilterListsSubscribeAllWorker.PROGRESS_CURRENT_NAME);

                        currentDone = done;
                        currentTotal = total;
                        currentWorkingId = currentId >= 0 ? currentId : null;
                        currentWorkingName = currentName;

                        subscribeAllStatus.setVisibility(View.VISIBLE);
                        String line;
                        if (total <= 0) {
                            line = "Preparing…";
                        } else {
                            int percent = (int) Math.floor(done * 100.0 / total);
                            line = done + "/" + total + " (" + percent + "%)";
                        }
                        if (currentName != null && !currentName.isEmpty()) line += " • " + currentName;
                        subscribeAllStatus.setText(line);
                    } else {
                        currentDone = 0;
                        currentTotal = 0;
                        currentWorkingId = null;
                        currentWorkingName = null;
                        subscribeAllStatus.setVisibility(View.GONE);
                    }

                    subscribeAllButton.setEnabled(!running);
                    // Throttle full-list refresh (it's expensive for very large lists).
                    requestAdapterRefreshThrottled(running ? 1000 : 0);
                });

        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        subscribeAllButton.setEnabled(false);
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                AdAwayApplication app = (AdAwayApplication) getApplicationContext();
                FilterListsDirectoryApi api = new FilterListsDirectoryApi(app.getSourceModel().getHttpClientForUi());

                // Load existing sources (for checkbox state)
                List<HostsSource> existing = hostsSourceDao.getAll();
                Set<String> existingUrls = new HashSet<>();
                for (HostsSource s : existing) {
                    existingUrls.add(s.getUrl());
                }

                // Try local cache first to avoid slow network on every open.
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                String cachedListsJson = prefs.getString(KEY_LISTS_JSON, null);
                String cachedSyntaxesJson = prefs.getString(KEY_SYNTAXES_JSON, null);
                long cachedAt = prefs.getLong(KEY_CACHED_AT, 0L);
                long now = System.currentTimeMillis();
                boolean cacheFresh = cachedListsJson != null && cachedSyntaxesJson != null && (now - cachedAt) < CACHE_TTL_MS;

                if (cachedListsJson != null && cachedSyntaxesJson != null) {
                    try {
                        Map<Integer, String> cachedSyntaxNames = FilterListsDirectoryApi.parseSyntaxNamesJson(cachedSyntaxesJson);
                        List<FilterListsDirectoryApi.ListSummary> cachedLists = FilterListsDirectoryApi.parseListsJson(cachedListsJson);
                        AppExecutors.getInstance().mainThread().execute(() -> {
                            this.syntaxNames = cachedSyntaxNames;
                            this.existingUrls.clear();
                            this.existingUrls.addAll(existingUrls);
                            all.clear();
                            all.addAll(cachedLists);
                            filter();
                        });
                    } catch (IOException ignored) {
                        // fall through to network fetch
                    }
                }

                if (cacheFresh) {
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        progress.setVisibility(View.GONE);
                        subscribeAllButton.setEnabled(true);
                    });
                    return;
                }

                // Refresh from network (and update cache)
                String listsJson = api.getListsJson();
                String syntaxesJson = api.getSyntaxesJson();
                Map<Integer, String> syntaxNames = FilterListsDirectoryApi.parseSyntaxNamesJson(syntaxesJson);
                List<FilterListsDirectoryApi.ListSummary> lists = FilterListsDirectoryApi.parseListsJson(listsJson);

                prefs.edit()
                        .putString(KEY_LISTS_JSON, listsJson)
                        .putString(KEY_SYNTAXES_JSON, syntaxesJson)
                        .putLong(KEY_CACHED_AT, now)
                        .apply();

                AppExecutors.getInstance().mainThread().execute(() -> {
                    this.syntaxNames = syntaxNames;
                    this.existingUrls.clear();
                    this.existingUrls.addAll(existingUrls);
                    all.clear();
                    all.addAll(lists);
                    filter();
                    progress.setVisibility(View.GONE);
                    subscribeAllButton.setEnabled(true);
                });
            } catch (IOException e) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    progress.setVisibility(View.GONE);
                    subscribeAllButton.setEnabled(false);
                    Snackbar.make(recyclerView, "Failed to load FilterLists.com", Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void subscribeAll() {
        // Immediate UI feedback (even before WorkManager delivers progress).
        subscribeAllStatus.setVisibility(View.VISIBLE);
        subscribeAllStatus.setText("Preparing…");
        subscribeAllButton.setEnabled(false);
        currentDone = 0;
        currentTotal = 0;
        currentWorkingId = null;
        currentWorkingName = "Preparing…";
        adapter.notifyDataSetChanged();

        // Hide keyboard so the user can see the status line.
        try {
            searchEditText.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
            // best-effort
        }

        // Run in background with notification progress; user can leave immediately.
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FilterListsSubscribeAllWorker.class).build();
        WorkManager wm = WorkManager.getInstance(this);
        // Always start a fresh run when user taps the button.
        wm.enqueueUniqueWork(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);

        Snackbar.make(recyclerView, "We’ll notify you when finished. You can go back now.", Snackbar.LENGTH_LONG).show();
    }

    @Nullable
    private static WorkInfo pickBestWorkInfo(@Nullable List<WorkInfo> infos) {
        if (infos == null || infos.isEmpty()) return null;
        // Prefer RUNNING, then ENQUEUED, otherwise the last one (most recent in practice).
        for (WorkInfo i : infos) {
            if (i.getState() == WorkInfo.State.RUNNING) return i;
        }
        for (WorkInfo i : infos) {
            if (i.getState() == WorkInfo.State.ENQUEUED) return i;
        }
        return infos.get(infos.size() - 1);
    }

    @Nullable
    private String getCachedUrlForId(int id) {
        String url = resolvedUrlCache.get(id);
        if (url != null) return url;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        url = prefs.getString(KEY_URL_PREFIX + id, null);
        if (url != null) {
            resolvedUrlCache.put(id, url);
        }
        return url;
    }

    private void filter() {
        String q = searchEditText.getText() != null ? searchEditText.getText().toString().toLowerCase(Locale.ROOT).trim() : "";
        filtered.clear();
        for (FilterListsDirectoryApi.ListSummary s : all) {
            if (q.isEmpty()) {
                filtered.add(s);
            } else {
                String name = s.name != null ? s.name.toLowerCase(Locale.ROOT) : "";
                String desc = s.description != null ? s.description.toLowerCase(Locale.ROOT) : "";
                if (name.contains(q) || desc.contains(q)) {
                    filtered.add(s);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void onPick(FilterListsDirectoryApi.ListSummary summary) {
        progress.setVisibility(View.VISIBLE);
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                AdAwayApplication app = (AdAwayApplication) getApplicationContext();
                FilterListsDirectoryApi api = new FilterListsDirectoryApi(app.getSourceModel().getHttpClientForUi());
                FilterListsDirectoryApi.ListDetails details = api.getListDetails(summary.id);
                String url = details.pickBestDownloadUrl();
                if (url != null) {
                    resolvedUrlCache.put(summary.id, url);
                }
                AppExecutors.getInstance().mainThread().execute(() -> {
                    progress.setVisibility(View.GONE);
                    if (url == null) {
                        Snackbar.make(recyclerView, "No direct hosts URL found for this entry. Use its homepage.", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    Intent intent = new Intent(FilterListsImportActivity.this, SourceEditActivity.class);
                    intent.putExtra(SourceEditActivity.EXTRA_INITIAL_LABEL, details.name);
                    intent.putExtra(SourceEditActivity.EXTRA_INITIAL_URL, url);
                    intent.putExtra(SourceEditActivity.EXTRA_INITIAL_ALLOW, false);
                    intent.putExtra(SourceEditActivity.EXTRA_INITIAL_REDIRECT, false);
                    startActivity(intent);
                });
            } catch (IOException e) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    progress.setVisibility(View.GONE);
                    Snackbar.make(recyclerView, "Failed to resolve list URL", Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setSubscribed(FilterListsDirectoryApi.ListSummary summary, boolean subscribed) {
        // Disable list interactions while we resolve/add/remove
        progress.setVisibility(View.VISIBLE);
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String url = resolvedUrlCache.get(summary.id);
                if (url == null) {
                    AdAwayApplication app = (AdAwayApplication) getApplicationContext();
                    FilterListsDirectoryApi api = new FilterListsDirectoryApi(app.getSourceModel().getHttpClientForUi());
                    FilterListsDirectoryApi.ListDetails details = api.getListDetails(summary.id);
                    url = details.pickBestDownloadUrl();
                    if (url != null) {
                        resolvedUrlCache.put(summary.id, url);
                    }
                }
                if (url == null) {
                    String msg = "No direct download URL for this list";
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        progress.setVisibility(View.GONE);
                        Snackbar.make(recyclerView, msg, Snackbar.LENGTH_LONG).show();
                        adapter.notifyDataSetChanged();
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
                    progress.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                    Snackbar.make(
                            recyclerView,
                            subscribed ? "Subscribed" : "Unsubscribed",
                            Snackbar.LENGTH_SHORT
                    ).show();
                });
            } catch (IOException e) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    progress.setVisibility(View.GONE);
                    Snackbar.make(recyclerView, "Failed to update subscription", Snackbar.LENGTH_LONG).show();
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    private static boolean hasSyntax(int[] ids, int want) {
        if (ids == null) return false;
        for (int id : ids) {
            if (id == want) return true;
        }
        return false;
    }

    private class Adapter extends RecyclerView.Adapter<RowVH> {
        @NonNull
        @Override
        public RowVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.filterlists_import_item, parent, false);
            return new RowVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RowVH holder, int position) {
            FilterListsDirectoryApi.ListSummary s = filtered.get(position);
            String cachedUrl = getCachedUrlForId(s.id);
            boolean isSubscribed = cachedUrl != null && existingUrls.contains(cachedUrl);

            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(isSubscribed);
            holder.checkbox.setOnCheckedChangeListener((buttonView, checked) -> setSubscribed(s, checked));

            holder.name.setText(s.name);
            holder.syntax.setText(formatSyntax(s.syntaxIds));

            // Per-filter progress: mark the one currently being processed.
            boolean isCurrent = currentWorkingId != null && currentWorkingId == s.id;
            if (isCurrent) {
                holder.status.setVisibility(View.VISIBLE);
                holder.status.setText("Processing…");
            } else if (isSubscribed) {
                holder.status.setVisibility(View.VISIBLE);
                holder.status.setText("Subscribed");
            } else {
                holder.status.setVisibility(View.GONE);
            }

            holder.desc.setText(s.description != null ? s.description : "");
            holder.itemView.setOnClickListener(v -> onPick(s)); // tap row to edit/inspect URL in Source editor
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }
    }

    static class RowVH extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView name;
        final TextView syntax;
        final TextView status;
        final TextView desc;

        RowVH(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.filterlistsItemCheckbox);
            name = itemView.findViewById(R.id.filterlistsItemName);
            syntax = itemView.findViewById(R.id.filterlistsItemSyntax);
            status = itemView.findViewById(R.id.filterlistsItemStatus);
            desc = itemView.findViewById(R.id.filterlistsItemDesc);
        }
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
}
