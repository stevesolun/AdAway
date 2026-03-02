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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.adaway.R;

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
        viewModel = new ViewModelProvider(this).get(DomainCheckerViewModel.class);

        TextInputEditText domainEditText = view.findViewById(R.id.domainEditText);
        MaterialButton checkButton = view.findViewById(R.id.checkButton);
        MaterialCardView resultCard = view.findViewById(R.id.resultCard);
        TextView statusBadge = view.findViewById(R.id.statusBadge);
        TextView domainCheckedTextView = view.findViewById(R.id.domainCheckedTextView);
        TextView sourcesLabel = view.findViewById(R.id.sourcesLabel);
        LinearLayout sourcesContainer = view.findViewById(R.id.sourcesContainer);
        MaterialButton unblockButton = view.findViewById(R.id.unblockButton);
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
            domainCheckedTextView.setText(result.domain);

            // Reset all conditional views
            sourcesLabel.setVisibility(View.GONE);
            sourcesContainer.setVisibility(View.GONE);
            unblockButton.setVisibility(View.GONE);
            alreadyAllowedLabel.setVisibility(View.GONE);
            notBlockedLabel.setVisibility(View.GONE);
            sourcesContainer.removeAllViews();

            if (result.blocked) {
                statusBadge.setText("\uD83D\uDD34 BLOCKED");
                statusBadge.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                sourcesLabel.setVisibility(View.VISIBLE);
                sourcesContainer.setVisibility(View.VISIBLE);
                for (String src : result.blockingSources) {
                    TextView tv = new TextView(requireContext());
                    tv.setText("\u2022 " + src);
                    tv.setTextSize(13f);
                    tv.setPadding(0, 4, 0, 4);
                    sourcesContainer.addView(tv);
                }
                if (result.userAllowed) {
                    alreadyAllowedLabel.setVisibility(View.VISIBLE);
                } else {
                    unblockButton.setVisibility(View.VISIBLE);
                    unblockButton.setOnClickListener(v -> {
                        viewModel.unblockDomain(result.domain);
                        Snackbar.make(view, "Added " + result.domain + " to allow list", Snackbar.LENGTH_SHORT).show();
                    });
                }
            } else {
                statusBadge.setText("\uD83D\uDFE2 NOT BLOCKED");
                statusBadge.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
                notBlockedLabel.setVisibility(View.VISIBLE);
            }
        });
    }
}
