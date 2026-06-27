package org.adaway.ui.domainchecker;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.adaway.R;
import org.adaway.ui.domainchecker.DomainCheckResult.Status;

public class DomainCheckerFragment extends Fragment {

    private DomainCheckerViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_domain_checker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyTopInsets(view);
        viewModel = new ViewModelProvider(this).get(DomainCheckerViewModel.class);

        TextInputEditText domainEditText = view.findViewById(R.id.domainEditText);
        MaterialButton checkButton = view.findViewById(R.id.checkButton);
        MaterialCardView resultCard = view.findViewById(R.id.resultCard);
        TextView statusBadge = view.findViewById(R.id.statusBadge);
        TextView domainCheckedTextView = view.findViewById(R.id.domainCheckedTextView);
        TextView sourcesLabel = view.findViewById(R.id.sourcesLabel);
        LinearLayout sourcesContainer = view.findViewById(R.id.sourcesContainer);
        MaterialButton unblockButton = view.findViewById(R.id.unblockButton);
        MaterialButton removeAllowButton = view.findViewById(R.id.removeAllowButton);
        MaterialButton blockButton = view.findViewById(R.id.blockButton);
        TextView alreadyAllowedLabel = view.findViewById(R.id.alreadyAllowedLabel);
        TextView notBlockedLabel = view.findViewById(R.id.notBlockedLabel);

        checkButton.setOnClickListener(v -> {
            String input = domainEditText.getText() != null ? domainEditText.getText().toString() : "";
            viewModel.checkDomain(input);
            // Hide keyboard
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        });

        domainEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                checkButton.performClick();
                return true;
            }
            return false;
        });

        viewModel.loading.observe(getViewLifecycleOwner(), loading ->
                checkButton.setEnabled(!Boolean.TRUE.equals(loading)));

        viewModel.checkResult.observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            resultCard.setVisibility(View.VISIBLE);
            domainCheckedTextView.setText(getString(
                    R.string.domain_checker_checked_domain, result.domain));

            // Reset all conditional views
            sourcesLabel.setVisibility(View.GONE);
            sourcesContainer.setVisibility(View.GONE);
            unblockButton.setVisibility(View.GONE);
            removeAllowButton.setVisibility(View.GONE);
            blockButton.setVisibility(View.GONE);
            alreadyAllowedLabel.setVisibility(View.GONE);
            notBlockedLabel.setVisibility(View.GONE);
            sourcesContainer.removeAllViews();

            statusBadge.setText(statusText(result.status));
            statusBadge.setTextColor(getResources().getColor(statusColor(result.status), null));

            if (result.status == Status.BLOCKED || result.status == Status.REDIRECTED) {
                sourcesLabel.setVisibility(View.VISIBLE);
                sourcesContainer.setVisibility(View.VISIBLE);

                int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

                for (DomainCheckResult.BlockingSource src : result.blockingSources) {
                    LinearLayout row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(0, dp8, 0, dp8);

                    TextView tv = new TextView(requireContext());
                    tv.setLayoutParams(new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    int sourceText = src.isUserRule
                            ? R.string.domain_checker_user_source_format
                            : R.string.domain_checker_filter_source_format;
                    tv.setText(getString(sourceText, src.name));
                    tv.setTextSize(13f);
                    row.addView(tv);

                    if (src.isUserRule) {
                        MaterialButton del = new MaterialButton(requireContext(),
                                null, com.google.android.material.R.attr.borderlessButtonStyle);
                        del.setText(R.string.domain_checker_delete_rule);
                        del.setTextSize(11f);
                        del.setOnClickListener(v2 -> {
                            viewModel.deleteRule(src.itemId, result.domain);
                            Snackbar.make(view, R.string.domain_checker_rule_deleted, Snackbar.LENGTH_SHORT).show();
                        });
                        row.addView(del);
                    }
                    sourcesContainer.addView(row);
                }

                if (result.userAllowed) {
                    alreadyAllowedLabel.setVisibility(View.VISIBLE);
                    removeAllowButton.setVisibility(View.VISIBLE);
                    removeAllowButton.setOnClickListener(v -> {
                        viewModel.removeUserAllowRule(result.domain);
                        Snackbar.make(view, R.string.domain_checker_allow_removed, Snackbar.LENGTH_SHORT).show();
                    });
                } else {
                    unblockButton.setVisibility(View.VISIBLE);
                    unblockButton.setOnClickListener(v -> {
                        viewModel.unblockDomain(result.domain);
                        Snackbar.make(view, getString(R.string.domain_checker_allowed_snack, result.domain),
                                Snackbar.LENGTH_SHORT).show();
                    });
                }
            } else if (result.status == Status.ALLOWED && result.userAllowed) {
                alreadyAllowedLabel.setVisibility(View.VISIBLE);
                removeAllowButton.setVisibility(View.VISIBLE);
                removeAllowButton.setOnClickListener(v -> {
                    viewModel.removeUserAllowRule(result.domain);
                    Snackbar.make(view, R.string.domain_checker_allow_removed, Snackbar.LENGTH_SHORT).show();
                });
            } else {
                notBlockedLabel.setVisibility(View.VISIBLE);
                blockButton.setVisibility(View.VISIBLE);
                blockButton.setOnClickListener(v -> {
                    viewModel.blockDomain(result.domain);
                    Snackbar.make(view, getString(R.string.domain_checker_blocked_snack, result.domain),
                            Snackbar.LENGTH_SHORT).show();
                });
            }
        });
    }

    private static int statusText(Status status) {
        if (status == Status.BLOCKED) {
            return R.string.domain_checker_status_blocked;
        }
        if (status == Status.ALLOWED) {
            return R.string.allowed_hosts_label;
        }
        if (status == Status.REDIRECTED) {
            return R.string.redirect_hosts_label;
        }
        return R.string.domain_checker_status_unknown;
    }

    private static int statusColor(Status status) {
        if (status == Status.BLOCKED) {
            return android.R.color.holo_red_dark;
        }
        if (status == Status.REDIRECTED) {
            return R.color.redirected;
        }
        if (status == Status.ALLOWED) {
            return android.R.color.holo_green_dark;
        }
        return android.R.color.darker_gray;
    }

    private static void applyTopInsets(@NonNull View view) {
        int initialLeft = view.getPaddingLeft();
        int initialTop = view.getPaddingTop();
        int initialRight = view.getPaddingRight();
        int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(initialLeft, initialTop + systemBars.top, initialRight, initialBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
