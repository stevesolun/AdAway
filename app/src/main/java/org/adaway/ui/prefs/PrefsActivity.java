package org.adaway.ui.prefs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;

/**
 * This activity is the preferences activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    static final String EXTRA_INITIAL_FRAGMENT = "org.adaway.extra.INITIAL_PREFS_FRAGMENT";
    static final String INITIAL_FRAGMENT_BACKUP_RESTORE = "backup_restore";
    static final String PREFERENCE_NOT_FOUND = "preference not found";

    @NonNull
    public static Intent createBackupRestoreIntent(@NonNull Context context) {
        return new Intent(context, PrefsActivity.class)
                .putExtra(EXTRA_INITIAL_FRAGMENT, INITIAL_FRAGMENT_BACKUP_RESTORE);
    }

    static void setAppBarTitle(PreferenceFragmentCompat fragment, @StringRes int title) {
        FragmentActivity activity = fragment.getActivity();
        if (!(activity instanceof PrefsActivity)) {
            return;
        }
        ActionBar supportActionBar = ((PrefsActivity) activity).getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setTitle(title);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, createInitialFragment())
                    .commit();
        }
        /*
         * Configure actionbar.
         */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onSupportNavigateUp();
        }
        return true;
    }

    @NonNull
    private Fragment createInitialFragment() {
        String initialFragment = getIntent().getStringExtra(EXTRA_INITIAL_FRAGMENT);
        if (INITIAL_FRAGMENT_BACKUP_RESTORE.equals(initialFragment)) {
            return new PrefsBackupRestoreFragment();
        }
        return new PrefsMainFragment();
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new fragment
        String fragmentClassName = pref.getFragment();
        if (fragmentClassName == null) {
            return false;
        }
        Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                fragmentClassName
        );
        Bundle args = pref.getExtras();
        fragment.setArguments(args);
        // See https://developer.android.com/guide/topics/ui/settings/organize-your-settings#java
        //noinspection deprecation
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.animator.adaway_fragment_open_enter,
                        R.animator.adaway_fragment_open_exit,
                        R.animator.adaway_fragment_close_enter,
                        R.animator.adaway_fragment_close_exit
                )
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }
}
