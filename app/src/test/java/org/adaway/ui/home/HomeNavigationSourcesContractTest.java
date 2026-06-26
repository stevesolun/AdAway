package org.adaway.ui.home;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HomeNavigationSourcesContractTest {
    @Test
    public void bottomNavigationExposesFirstClassSourcesTab() throws Exception {
        String bottomNav = readRepoFile("app/src/main/res/menu/bottom_nav_menu.xml");
        String strings = readRepoFile("app/src/main/res/values/strings.xml");
        String homeActivity = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeActivity.java");

        assertTrue("Bottom navigation must include a Sources tab.",
                bottomNav.contains("@+id/nav_sources"));
        assertTrue("Sources tab must have a user-visible label.",
                bottomNav.contains("@string/nav_sources") &&
                        strings.contains("<string name=\"nav_sources\">Sources</string>"));
        assertTrue("HomeActivity must route the Sources tab to the wrapper fragment.",
                homeActivity.contains("import org.adaway.ui.hosts.HostsSourcesTabFragment;") &&
                        homeActivity.contains("menuItemId == R.id.nav_sources") &&
                        homeActivity.contains("fragment = new HostsSourcesTabFragment()"));
        assertTrue("HomeActivity navigateTo docs must include the Sources tab.",
                homeActivity.contains("R.id.nav_sources"));
    }

    @Test
    public void homeLaunchShellStartsOnHomeAndKeepsLatestDeepIntent() throws Exception {
        String manifest = readRepoFile("app/src/main/AndroidManifest.xml");
        String bottomNav = readRepoFile("app/src/main/res/menu/bottom_nav_menu.xml");
        String homeNav = readRepoFile("app/src/main/res/layout/activity_home_nav.xml");
        String homeActivity = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeActivity.java");

        assertTrue("HomeActivity must be the exported launcher shell.",
                manifest.contains("android:name=\".ui.home.HomeActivity\"") &&
                        manifest.contains("android:exported=\"true\"") &&
                        manifest.contains("android.intent.action.MAIN") &&
                        manifest.contains("android.intent.category.LAUNCHER"));
        assertTrue("Home launch shell must host the bottom navigation menu.",
                homeNav.contains("android:id=\"@+id/nav_fragment_container\"") &&
                        homeNav.contains("android:id=\"@+id/bottom_navigation\"") &&
                        homeNav.contains("app:menu=\"@menu/bottom_nav_menu\""));
        assertTrue("Bottom navigation must expose Home, Discover, Sources, and More.",
                bottomNav.contains("@+id/nav_home") &&
                        bottomNav.contains("@+id/nav_discover") &&
                        bottomNav.contains("@+id/nav_sources") &&
                        bottomNav.contains("@+id/nav_more"));
        assertTrue("Fresh launches must default to the Home tab unless Discover is requested.",
                homeActivity.contains("int startTab = getIntent().getBooleanExtra(" +
                        "EXTRA_NAV_DISCOVER, false)") &&
                        homeActivity.contains("? R.id.nav_discover : R.id.nav_home") &&
                        homeActivity.contains("showTab(startTab);"));
        assertTrue("Tab routing must keep all four first-class destinations reachable.",
                homeActivity.contains("fragment = new HomeFragment()") &&
                        homeActivity.contains("fragment = new DiscoverFragment()") &&
                        homeActivity.contains("fragment = new HostsSourcesTabFragment()") &&
                        homeActivity.contains("fragment = new MoreFragment()"));

        int onNewIntent = homeActivity.indexOf("protected void onNewIntent(Intent intent)");
        int setIntent = homeActivity.indexOf("setIntent(intent)", onNewIntent);
        int readExtra = homeActivity.indexOf(
                "intent.getBooleanExtra(EXTRA_NAV_DISCOVER, false)", onNewIntent);
        assertTrue("singleTop launch intents must replace stale Activity intent before routing.",
                onNewIntent >= 0 && setIntent >= 0 && setIntent < readExtra);
    }

    @Test
    public void homeNavContentStaysAboveBottomNavigation() throws Exception {
        String homeNav = readRepoFile("app/src/main/res/layout/activity_home_nav.xml");

        assertTrue("Home nav shell must constrain tab content above bottom navigation.",
                homeNav.contains("androidx.constraintlayout.widget.ConstraintLayout") &&
                        homeNav.contains("android:id=\"@+id/nav_fragment_container\"") &&
                        homeNav.contains(
                                "app:layout_constraintBottom_toTopOf=\"@id/bottom_navigation\"") &&
                        homeNav.contains(
                                "app:layout_constraintBottom_toBottomOf=\"parent\""));
        assertFalse("Home nav shell must not let content draw under bottom navigation.",
                homeNav.contains("app:layout_dodgeInsetEdges=\"bottom\"") ||
                        homeNav.contains("app:layout_insetEdge=\"bottom\""));
    }

    @Test
    public void sourcesTabOwnsToolbarAndDelegatesMenuActions() throws Exception {
        String wrapper = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/HostsSourcesTabFragment.java");
        String wrapperLayout = readRepoFile(
                "app/src/main/res/layout/fragment_hosts_sources_tab.xml");
        String sourcesFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/HostsSourcesFragment.java");

        assertTrue("Sources tab wrapper must host the existing sources fragment as a child.",
                wrapper.contains("new HostsSourcesFragment()") &&
                        wrapper.contains("R.id.hosts_sources_child_container"));
        assertTrue("Sources tab wrapper must own a toolbar with the existing actions menu.",
                wrapper.contains("R.menu.hosts_sources_menu") &&
                        wrapper.contains("setOnMenuItemClickListener") &&
                        wrapperLayout.contains("com.google.android.material.appbar.MaterialToolbar") &&
                        wrapperLayout.contains("@+id/hosts_sources_toolbar") &&
                        wrapperLayout.contains("app:title=\"@string/nav_sources\"") &&
                        wrapperLayout.contains("app:titleTextColor=\"@color/ui_text_primary\""));
        assertTrue("Toolbar actions must delegate to the child fragment menu handler.",
                wrapper.contains("handleMenuItem(item)"));
        assertTrue("HostsSourcesFragment must expose a shared menu handler.",
                sourcesFragment.contains("boolean handleMenuItem(@NonNull MenuItem item)") &&
                        sourcesFragment.contains("handleMenuItem(item)"));
    }

    @Test
    public void homeAndMoreSourceEntrypointsNavigateToSourcesTab() throws Exception {
        String homeFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeFragment.java");
        String moreFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/more/MoreFragment.java");

        assertTrue("Home source card must navigate to the Sources tab.",
                homeFragment.contains("navigateTo(R.id.nav_sources)"));
        assertTrue("More filter sources row must navigate to the Sources tab.",
                moreFragment.contains("navigateTo(R.id.nav_sources)"));
        assertFalse("Home source card must not launch HostsSourcesActivity from the nav shell.",
                homeFragment.contains("new Intent(requireContext(), HostsSourcesActivity.class)"));
        assertFalse("More filter sources row must not launch HostsSourcesActivity from the nav shell.",
                moreFragment.contains("new Intent(requireContext(), HostsSourcesActivity.class)"));
    }

    @Test
    public void moreBackupRestoreDeepLinksToBackupPreferences() throws Exception {
        String moreFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/more/MoreFragment.java");
        String prefsActivity = readRepoFile(
                "app/src/main/java/org/adaway/ui/prefs/PrefsActivity.java");
        String mainPreferences = readRepoFile("app/src/main/res/xml/preferences_main.xml");

        assertTrue("More Backup & Restore row must use the dedicated deep-link intent.",
                moreFragment.contains("moreRowBackup.setOnClickListener") &&
                        moreFragment.contains("PrefsActivity.createBackupRestoreIntent"));
        assertTrue("Normal Preferences row must still open the main preferences screen.",
                moreFragment.contains("moreRowPreferences.setOnClickListener") &&
                        moreFragment.contains("new Intent(requireContext(), PrefsActivity.class)"));
        assertTrue("PrefsActivity must expose a stable Backup & Restore deep-link extra.",
                prefsActivity.contains("EXTRA_INITIAL_FRAGMENT") &&
                        prefsActivity.contains("INITIAL_FRAGMENT_BACKUP_RESTORE") &&
                        prefsActivity.contains("createBackupRestoreIntent"));
        assertTrue("PrefsActivity must create the backup fragment directly for the deep link.",
                prefsActivity.contains("createInitialFragment()") &&
                        prefsActivity.contains("return new PrefsBackupRestoreFragment()") &&
                        prefsActivity.contains("return new PrefsMainFragment()"));
        assertTrue("Main preferences must still expose Backup & Restore for settings users.",
                mainPreferences.contains("PrefsBackupRestoreFragment"));
    }

    @Test
    public void terminalUpdateProgressRestoresCountersAndDisablesControls() throws Exception {
        String homeFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeFragment.java");

        assertTrue("Terminal update progress must reset import counter guards before reattaching " +
                        "host counters.",
                homeFragment.contains("boolean isStopped = progress.phase == " +
                        "FilterOperationState.Phase.STOPPED") &&
                        homeFragment.contains("if (isStopped || isComplete) {") &&
                        homeFragment.contains("resetImportCounterGuards();") &&
                        homeFragment.contains("attachHostCounterObservers();") &&
                        homeFragment.contains("refreshHostCountersOnce();"));
        assertTrue("Idle progress must reset the import counter guard so host-count LiveData " +
                        "can update the blocked counter again.",
                homeFragment.contains("private void resetImportCounterGuards()") &&
                        homeFragment.contains("this.homeViewModel.setCachedInitialBlockedCount(-1)"));
        assertTrue("Terminal complete/stopped text must be announced for accessibility.",
                homeFragment.contains("boolean isComplete = progress.phase == " +
                        "FilterOperationState.Phase.COMPLETE") &&
                        homeFragment.contains("if (isComplete || isStopped)") &&
                        homeFragment.contains("announceForAccessibility(progressText)"));
        assertTrue("Pause and stop controls must be disabled after complete terminal progress.",
                homeFragment.contains("&& !isComplete") &&
                homeFragment.contains("pauseResumeButton.setEnabled(controlsEnabled)") &&
                        homeFragment.contains("stopButton.setEnabled(controlsEnabled)"));
    }

    @Test
    public void homeUpdateProgressIsSummaryFirst() throws Exception {
        String homeLayout = readRepoFile("app/src/main/res/layout/home_content.xml");
        String homeFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeFragment.java");

        assertTrue("Home update progress must keep the overall summary visible.",
                homeLayout.contains("android:id=\"@+id/overallProgressFrame\"") &&
                        homeLayout.contains("android:id=\"@+id/overallProgressText\""));
        assertTrue("Home update progress must keep explicit pause and stop controls.",
                homeLayout.contains("android:id=\"@+id/pauseResumeButton\"") &&
                        homeLayout.contains("android:id=\"@+id/stopButton\""));
        assertTrue("Home update progress must hide noisy phase rows in the primary path.",
                homeFragment.contains("downloadPhaseLabel.setVisibility(View.GONE)") &&
                        homeFragment.contains("downloadProgressBar.setVisibility(View.GONE)") &&
                        homeFragment.contains("downloadPhasePercent.setVisibility(View.GONE)") &&
                        homeFragment.contains("parsePhaseLabel.setVisibility(View.GONE)") &&
                        homeFragment.contains("parseProgressBar.setVisibility(View.GONE)") &&
                        homeFragment.contains("parsePhasePercent.setVisibility(View.GONE)"));
    }

    @Test
    public void homeUpdateProgressUsesSharedFilterOperationState() throws Exception {
        String homeViewModel = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeViewModel.java");
        String homeFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeFragment.java");

        assertTrue("HomeViewModel must expose the shared filter operation state.",
                homeViewModel.contains("LiveData<FilterOperationState> filterOperationState") &&
                        homeViewModel.contains("this.sourceModel.getFilterOperationState()") &&
                        homeViewModel.contains("getFilterOperationState()"));
        assertTrue("HomeFragment must bind the shared filter operation state for update UI.",
                homeFragment.contains("bindFilterOperationState()") &&
                        homeFragment.contains("getFilterOperationState().observe") &&
                        homeFragment.contains("FilterOperationState.Phase.FINALIZE") &&
                        homeFragment.contains("FilterOperationState.Phase.COMPLETE"));
        assertFalse("HomeFragment must not render progress directly from legacy " +
                        "MultiPhaseProgress.",
                homeFragment.contains("getMultiPhaseProgress().observe") ||
                        homeFragment.contains("SourceModel.MultiPhaseProgress") ||
                        homeFragment.contains("bindMultiPhaseProgress()"));
    }

    @Test
    public void homeUpdateActionsAreClearSemanticButtons() throws Exception {
        String homeLayout = readRepoFile("app/src/main/res/layout/home_content.xml");
        String homeFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeFragment.java");
        String homeViewModel = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeViewModel.java");
        String strings = readRepoFile("app/src/main/res/values/strings_home.xml");

        assertTrue("Home check action must be a semantic button with a tooltip.",
                homeLayout.contains("<ImageButton\n                " +
                        "android:id=\"@+id/checkForUpdateImageView\"") &&
                        homeLayout.contains(
                                "android:tooltipText=\"@string/check_hosts_update_description\""));
        assertTrue("Home update/apply action must be a semantic button with a tooltip.",
                homeLayout.contains("<ImageButton\n                " +
                        "android:id=\"@+id/updateImageView\"") &&
                        homeLayout.contains(
                                "android:tooltipText=\"@string/update_hosts_description\""));
        assertTrue("Home update action labels must distinguish checking from applying protection.",
                strings.contains("<string name=\"check_hosts_update_description\">" +
                        "Check sources for updates</string>") &&
                        strings.contains("<string name=\"update_hosts_description\">" +
                                "Update and apply protection</string>"));
        assertTrue("Home buttons must keep the existing source update/apply wiring.",
                homeFragment.contains("checkForUpdateImageView") &&
                        homeFragment.contains(".setOnClickListener(v -> " +
                                "this.homeViewModel.update())") &&
                        homeFragment.contains("updateImageView") &&
                        homeFragment.contains(".setOnClickListener(v -> " +
                                "this.homeViewModel.sync())"));
        assertTrue("HomeViewModel sync must apply only after a completed source update.",
                homeViewModel.contains("public void sync()") &&
                        homeViewModel.contains(
                                "if (this.sourceModel.checkAndRetrieveHostsSources())") &&
                        homeViewModel.contains("this.adBlockModel.apply();"));
    }

    @Test
    public void homeAndDiscoverDoNotExposeAiPrompting() throws Exception {
        String homeLayout = readRepoFile("app/src/main/res/layout/home_content.xml");
        String discoverLayout = readRepoFile("app/src/main/res/layout/fragment_discover.xml");
        String discoverFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java");

        assertFalse("Home must not keep hidden AI prompting controls.",
                homeLayout.contains("aiBoxCard") ||
                        homeLayout.contains("homeAi") ||
                        homeLayout.contains("ai_suggest"));
        assertFalse("Discover must not expose an AI chip.",
                discoverLayout.contains("chipDiscoverAskAi") ||
                        discoverLayout.contains("discover_ask_ai") ||
                        discoverFragment.contains("AiSuggestBottomSheet"));
        assertTrue("Discover must keep curated presets as the simple entry point.",
                discoverLayout.contains("chipDiscoverSafe") &&
                        discoverLayout.contains("chipDiscoverBalanced") &&
                        discoverLayout.contains("chipDiscoverAggressive"));
    }

    @Test
    public void appBrandingKeepsAdAwayBirdLogo() throws Exception {
        String[] logoFiles = {
                "app/src/main/res/drawable/icon_foreground_red.xml",
                "app/src/main/res/drawable/icon_foreground_white.xml",
                "app/src/main/res/drawable/icon_monochrome.xml",
                "app/src/main/res/drawable/logo.xml"
        };

        for (String logoFile : logoFiles) {
            String logo = readRepoFile(logoFile);
            assertTrue("Logo asset must keep the AdAway bird path: " + logoFile,
                    logo.contains("M721,394.9c-5.7") ||
                            logo.contains("M9.9063,1.2188"));
            assertFalse("Logo asset must not use the shield/block replacement: " + logoFile,
                    logo.contains("M512,112L784,216L752,512"));
        }
    }

    @Test
    public void launcherBrandingKeepsBirdIconFallbacks() throws Exception {
        String manifest = readRepoFile("app/src/main/AndroidManifest.xml");
        String icon = readRepoFile("app/src/main/res/mipmap-anydpi/icon.xml");
        String roundIcon = readRepoFile("app/src/main/res/mipmap-anydpi/icon_round.xml");

        assertTrue("Launcher icon must use the AdAway bird foreground.",
                manifest.contains("android:icon=\"@mipmap/icon\"") &&
                        icon.contains("@drawable/icon_foreground_red"));
        assertTrue("Round launcher icon must use the AdAway bird foreground.",
                manifest.contains("android:roundIcon=\"@mipmap/icon_round\"") &&
                        roundIcon.contains("@drawable/icon_foreground_red"));

        String[] fallbackIconFiles = {
                "app/src/main/res/mipmap-mdpi/icon.png",
                "app/src/main/res/mipmap-mdpi/icon_foreground.png",
                "app/src/main/res/mipmap-hdpi/icon.png",
                "app/src/main/res/mipmap-hdpi/icon_foreground.png",
                "app/src/main/res/mipmap-xhdpi/icon.png",
                "app/src/main/res/mipmap-xhdpi/icon_foreground.png",
                "app/src/main/res/mipmap-xxhdpi/icon.png",
                "app/src/main/res/mipmap-xxhdpi/icon_foreground.png",
                "app/src/main/res/mipmap-xxxhdpi/icon.png",
                "app/src/main/res/mipmap-xxxhdpi/icon_foreground.png",
                "app/src/main/res/mipmap-mdpi/icon_round.png",
                "app/src/main/res/mipmap-hdpi/icon_round.png",
                "app/src/main/res/mipmap-xhdpi/icon_round.png",
                "app/src/main/res/mipmap-xxhdpi/icon_round.png",
                "app/src/main/res/mipmap-xxxhdpi/icon_round.png"
        };
        for (String fallbackIconFile : fallbackIconFiles) {
            Path iconPath = resolveRepoFile(fallbackIconFile);
            assertTrue("Launcher fallback icon must exist: " + fallbackIconFile,
                    Files.exists(iconPath));
            assertTrue("Launcher fallback icon must not be empty: " + fallbackIconFile,
                    Files.size(iconPath) > 0);
        }
    }

    @Test
    public void licenseInventoryMatchesRestoredBirdBranding() throws Exception {
        String thirdPartyLicenses = readRepoFile("THIRD_PARTY_LICENSES.md");

        assertTrue("Packaged logo inventory must describe the restored AdAway bird assets.",
                thirdPartyLicenses.contains("AdAway bird"));
        assertTrue("Packaged logo inventory must include density fallback launcher icons.",
                thirdPartyLicenses.contains("Launcher density fallback icons") &&
                        thirdPartyLicenses.contains("app/src/main/res/mipmap-*/icon*.png"));
        assertFalse("Packaged logo inventory must not describe a different shield logo.",
                thirdPartyLicenses.contains("geometric DNS shield"));
    }

    @Test
    public void homeProtectionFabDoesNotFloatOverStatusCards() throws Exception {
        String fragmentHome = readRepoFile("app/src/main/res/layout/fragment_home.xml");
        String homeLayout = readRepoFile("app/src/main/res/layout/home_content.xml");
        String homeFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeFragment.java");

        assertFalse("Home must not keep a floating protection button over scrollable cards.",
                fragmentHome.contains("android:id=\"@+id/fab\"") ||
                        fragmentHome.contains("FloatingActionButton") ||
                        homeFragment.contains("binding.fab"));
        assertFalse("Home protection control must not be bottom-floating over status cards.",
                fragmentHome.contains("android:layout_gravity=\"bottom|end\"") ||
                        fragmentHome.contains("android:layout_marginBottom=\"104dp\""));
        assertTrue("The bird remains as Home's brand signal.",
                homeLayout.contains("android:id=\"@+id/logoImageView\"") &&
                        homeLayout.contains("app:srcCompat=\"@drawable/icon_foreground_red\""));
    }

    @Test
    public void homeHeroShowsExplicitProtectionState() throws Exception {
        String homeLayout = readRepoFile("app/src/main/res/layout/home_content.xml");
        String homeFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeFragment.java");
        String strings = readRepoFile("app/src/main/res/values/strings_home.xml");

        assertTrue("Home hero must include a primary protection status line.",
                homeLayout.contains("android:id=\"@+id/protectionStatusTextView\"") &&
                        homeLayout.contains("@string/home_protection_status_inactive"));
        assertTrue("Home status strings must name both protection states clearly.",
                strings.contains("home_protection_status_active") &&
                        strings.contains("home_protection_status_inactive"));
        assertTrue("Home must render active and inactive states from isAdBlocked().",
                homeFragment.contains("R.string.home_protection_status_active") &&
                        homeFragment.contains("R.string.home_protection_status_inactive") &&
                        homeFragment.contains("protectionStatusTextView.setText"));
        assertFalse("Home must not ignore the adBlocked value in notifyAdBlocked.",
                homeFragment.contains("private void notifyAdBlocked(boolean adBlocked) {\n" +
                        "        if (this.binding == null) return;\n" +
                        "        this.binding.content.headerFrameLayout.setBackgroundColor"));
    }

    @Test
    public void homeDoesNotCrowdBottomNavWithDecorativeCreditLine() throws Exception {
        String homeLayout = readRepoFile("app/src/main/res/layout/home_content.xml");
        String preferences = readRepoFile("app/src/main/res/xml/preferences_main.xml");
        String strings = readRepoFile("app/src/main/res/values/strings.xml");

        assertFalse("Home must not spend first-screen space on decorative attribution near " +
                        "bottom navigation.",
                homeLayout.contains("android:id=\"@+id/creditTextView\"") ||
                        homeLayout.contains("@string/credit_line_home"));
        assertTrue("Attribution must remain reachable from About/More instead of Home.",
                preferences.contains("app:key=\"pref_about\"") &&
                        preferences.contains("@string/credit_line_home") &&
                        strings.contains("<string name=\"credit_line_home\">"));
    }

    @Test
    public void uxMatrixCoversDomainCheckerWorkflow() throws Exception {
        String uxMatrix = readRepoFile(
                "app/src/androidTest/java/org/adaway/ui/UxDeviceMatrixTest.java");

        assertTrue("UX matrix must enter and capture the actual domain checker workflow.",
                uxMatrix.contains("captureDomainChecker") &&
                        uxMatrix.contains("R.id.domainCheckerCard") &&
                        uxMatrix.contains("\"domain_checker\""));
    }

    @Test
    public void uxMatrixAssertsHomeBirdLogoIsVisible() throws Exception {
        String uxMatrix = readRepoFile(
                "app/src/androidTest/java/org/adaway/ui/UxDeviceMatrixTest.java");

        assertTrue("UX matrix must assert Home renders the AdAway bird logo before screenshot.",
                uxMatrix.contains("assertHomeBirdLogoVisible") &&
                        uxMatrix.contains("R.id.logoImageView") &&
                        uxMatrix.contains("Home bird logo must be visible"));
    }

    @Test
    public void uxMatrixCapturesFirstClassSourcesTab() throws Exception {
        String uxMatrix = readRepoFile(
                "app/src/androidTest/java/org/adaway/ui/UxDeviceMatrixTest.java");

        assertTrue("UX matrix must screenshot Sources through the bottom-nav shell users see.",
                uxMatrix.contains("navigateAndCapture(scenario, R.id.nav_sources, \"sources\")"));
        assertFalse("UX matrix must not use the legacy standalone Sources activity as the " +
                        "primary Sources screenshot.",
                uxMatrix.contains("HostsSourcesActivity.class, \"sources\"") ||
                        uxMatrix.contains("import org.adaway.ui.hosts.HostsSourcesActivity;"));
    }

    @Test
    public void uxMatrixCoversCustomRulesAndKeepsFabClearance() throws Exception {
        String uxMatrix = readRepoFile(
                "app/src/androidTest/java/org/adaway/ui/UxDeviceMatrixTest.java");
        String listsLayout = readRepoFile("app/src/main/res/layout/lists_fragment.xml");
        String hostsListsLayout = readRepoFile(
                "app/src/main/res/layout/hosts_lists_fragment.xml");

        assertTrue("UX matrix must screenshot the reachable Custom Rules surface.",
                uxMatrix.contains("captureActivity(ListsActivity.class, \"custom_rules\")"));
        assertTrue("Custom Rules list must leave scroll clearance for the bottom FAB.",
                hostsListsLayout.contains("android:clipToPadding=\"false\"") &&
                        hostsListsLayout.contains(
                                "android:contentDescription=\"@string/lists_title\"") &&
                        hostsListsLayout.contains(
                                "android:paddingBottom=\"@dimen/fab_list_bottom_clearance\"") &&
                        hostsListsLayout.contains(
                                "android:paddingEnd=\"@dimen/fab_list_side_clearance\""));
        assertTrue("Custom Rules shell must use RTL-safe constraints and FAB anchoring.",
                listsLayout.contains("app:layout_anchorGravity=\"bottom|end\"") &&
                        listsLayout.contains("app:layout_constraintStart_toStartOf=\"parent\"") &&
                        listsLayout.contains("app:layout_constraintEnd_toEndOf=\"parent\""));
        assertFalse("Custom Rules shell must not keep legacy left/right constraints.",
                listsLayout.contains("layout_constraintLeft") ||
                        listsLayout.contains("layout_constraintRight") ||
                        listsLayout.contains("bottom|right|end"));
    }

    @Test
    public void uxMatrixDoesNotInheritBackgroundWorkers() throws Exception {
        String uxMatrix = readRepoFile(
                "app/src/androidTest/java/org/adaway/ui/UxDeviceMatrixTest.java");
        String testState = readRepoFile(
                "app/src/androidTest/java/org/adaway/testing/InstrumentedTestState.java");

        assertTrue("UX matrix must reset app state before passive screenshots.",
                uxMatrix.contains("InstrumentedTestState.resetForPassiveRootUi") &&
                        uxMatrix.contains("InstrumentedTestState.resetWorkManager"));
        assertTrue("Passive UI tests must disable update preferences before launch.",
                testState.contains("pref_update_check_app_daily_key") &&
                        testState.contains("pref_update_check_hosts_daily_key") &&
                        testState.contains("pref_automatic_update_daily_key") &&
                        testState.contains("FilterSetStore.setGlobalSchedule") &&
                        testState.contains("SourceUpdateService.disable") &&
                        testState.contains("ApkUpdateService.disable") &&
                        testState.contains("FilterSetUpdateService.disable") &&
                        testState.contains("cancelAllWork()") &&
                        testState.contains("pruneWork()"));
        assertTrue("Passive UI WorkManager reset must tolerate slow emulator cleanup.",
                testState.contains("WORK_MANAGER_RESET_TIMEOUT_SECONDS") &&
                        testState.contains("WORK_MANAGER_RESET_TIMEOUT_SECONDS = 30") &&
                        testState.contains(
                        "WORK_MANAGER_RESET_TIMEOUT_SECONDS, TimeUnit.SECONDS"));
    }

    @Test
    public void homeHonorsStartupAppUpdatePreference() throws Exception {
        String homeActivity = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeActivity.java");
        String preferenceHelper = readRepoFile(
                "app/src/main/java/org/adaway/helper/PreferenceHelper.java");
        String preferences = readRepoFile("app/src/main/res/xml/preferences_update.xml");

        assertTrue("Update preferences must expose the app startup update switch.",
                preferences.contains("@string/pref_update_check_app_startup_key"));
        assertTrue("PreferenceHelper must expose the app startup update switch.",
                preferenceHelper.contains("getUpdateCheckAppStartup"));
        assertTrue("HomeActivity must honor the startup app update preference on fresh launch.",
                homeActivity.contains("checkAppUpdateAtStartup(savedInstanceState)") &&
                        homeActivity.contains("PreferenceHelper.getUpdateCheckAppStartup(this)") &&
                        homeActivity.contains("new ViewModelProvider(this).get(" +
                                "HomeViewModel.class)") &&
                        homeActivity.contains("checkForAppUpdate()"));
        assertTrue("Startup update checks must not rerun after Activity state restoration.",
                homeActivity.contains("if (savedInstanceState != null)") &&
                        homeActivity.indexOf("if (savedInstanceState != null)")
                                < homeActivity.indexOf("checkForAppUpdate()"));
    }

    @Test
    public void globalScheduleDoesNotUpdateAllSourcesOnFreshLaunch() throws Exception {
        String store = readRepoFile("app/src/main/java/org/adaway/ui/hosts/FilterSetStore.java");
        String worker = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/FilterSetUpdateWorker.java");

        assertTrue("Global schedule defaults must seed last-run instead of leaving it zero.",
                store.contains("putLong(KEY_GLOBAL_LAST_RUN, now)"));
        assertTrue("Enabling the global schedule must start at the next slot, not now.",
                store.contains("editor.putLong(KEY_GLOBAL_LAST_RUN, System.currentTimeMillis())"));
        assertTrue("The worker must repair old zero-last-run globals without full source update.",
                worker.contains("if (last <= 0L)") &&
                        worker.contains("FilterSetStore.setGlobalLastRun(context, now)") &&
                        worker.contains("} else if (FilterSetStore.isDueByWallClock"));
    }

    @Test
    public void sourcesRowsStaySimpleAndLargeFontSafe() throws Exception {
        String sourceItem = readRepoFile("app/src/main/res/layout/filter_source_item.xml");
        String categoryHeader = readRepoFile("app/src/main/res/layout/filter_category_header.xml");
        String sourcesLayout = readRepoFile("app/src/main/res/layout/hosts_sources_fragment.xml");

        assertTrue("Source rows should read as simple list items, not heavy nested cards.",
                sourceItem.contains("android:id=\"@+id/sourceTextColumn\"") &&
                        sourceItem.contains("app:cardCornerRadius=\"8dp\"") &&
                        sourceItem.contains("app:cardElevation=\"0dp\"") &&
                        sourceItem.contains("app:strokeWidth=\"0dp\""));
        assertTrue("Large-font source text must get a stable text column before row actions.",
                sourceItem.contains("app:layout_constraintStart_toEndOf=\"@id/sourceSwitch\"") &&
                        sourceItem.contains("app:layout_constraintEnd_toEndOf=\"parent\"") &&
                        sourceItem.contains("app:layout_constraintTop_toBottomOf=\"@id/sourceTextColumn\"") &&
                        sourceItem.contains("android:maxLines=\"2\"") &&
                        sourceItem.contains("android:id=\"@+id/hostCountBadge\""));
        assertFalse("Source text must not be squeezed against an optional host-count badge.",
                sourceItem.contains("app:layout_constraintEnd_toStartOf=\"@id/hostCountBadge\""));
        assertTrue("Category headers should use the same restrained 8dp card radius.",
                categoryHeader.contains("app:cardCornerRadius=\"8dp\""));
        assertTrue("Sources FAB and preview item must match the categorized list users see.",
                sourcesLayout.contains("app:layout_anchorGravity=\"bottom|end\"") &&
                        sourcesLayout.contains("tools:listitem=\"@layout/filter_source_item\""));
        assertFalse("Sources layout must not keep legacy right gravity for the FAB.",
                sourcesLayout.contains("bottom|right|end"));
    }

    @Test
    public void domainCheckerDoesNotAutoscrollPastItsTitle() throws Exception {
        String domainCheckerLayout = readRepoFile(
                "app/src/main/res/layout/fragment_domain_checker.xml");
        String domainCheckerFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/domainchecker/DomainCheckerFragment.java");

        assertFalse("Domain checker must not hardcode a status-bar sized top padding.",
                domainCheckerLayout.contains("android:paddingTop=\"80dp\""));
        assertFalse("Domain checker should not need root-focus XML workarounds.",
                domainCheckerLayout.contains("android:focusableInTouchMode=\"true\"") ||
                        domainCheckerLayout.contains("android:descendantFocusability="));
        assertTrue("Domain checker should use platform system-bar insets for the top title.",
                domainCheckerFragment.contains("ViewCompat.setOnApplyWindowInsetsListener") &&
                        domainCheckerFragment.contains("WindowInsetsCompat.Type.systemBars()"));
        assertFalse("Domain checker should not request root focus to mask layout insets.",
                domainCheckerFragment.contains("view.setFocusableInTouchMode(true);") ||
                        domainCheckerFragment.contains("view.requestFocus();"));
    }

    @Test
    public void uxMatrixDomainCheckerCaptureDoesNotDoubleWait() throws Exception {
        String uxMatrix = readRepoFile(
                "app/src/androidTest/java/org/adaway/ui/UxDeviceMatrixTest.java");
        int start = uxMatrix.indexOf("private void captureDomainChecker");
        int end = uxMatrix.indexOf("private <T extends Activity> void captureActivity", start);
        String captureDomainChecker = uxMatrix.substring(start, end);

        assertFalse("Domain checker capture should rely on captureAfterIdle for idle waiting.",
                captureDomainChecker.contains("SystemClock.sleep"));
        assertTrue("Domain checker capture must still assert it opens at the top.",
                captureDomainChecker.contains("getScrollY() == 0"));
    }

    @Test
    public void homeStatLabelsUseSingleLineAutosizingForLargeFonts() throws Exception {
        String homeLayout = readRepoFile("app/src/main/res/layout/home_content.xml");

        assertTrue("Blocked stat label must use the full card width and autosize.",
                statLabelUsesAutosize(homeLayout, "blockedHostTextView"));
        assertTrue("Allowed stat label must use the full card width and autosize.",
                statLabelUsesAutosize(homeLayout, "allowedHostTextView"));
        assertTrue("Redirect stat label must use the full card width and autosize.",
                statLabelUsesAutosize(homeLayout, "redirectHostTextView"));
    }

    @Test
    public void uxMatrixAuditsHorizontalTextOverflow() throws Exception {
        String uxMatrix = readRepoFile(
                "app/src/androidTest/java/org/adaway/ui/UxDeviceMatrixTest.java");

        assertTrue("UX matrix must fail on visible horizontal text overflow.",
                uxMatrix.contains("horizontally clipped text") &&
                        uxMatrix.contains("getLineRight") &&
                        uxMatrix.contains("availableTextWidth"));
    }

    @Test
    public void homeRefreshesLeakStatusAfterVpnProtectionStarts() throws Exception {
        String homeFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeFragment.java");
        int notifyStart = homeFragment.indexOf("private void notifyAdBlocked(boolean adBlocked)");
        int notifyEnd = homeFragment.indexOf("private void notifyError", notifyStart);
        String notifyAdBlocked = homeFragment.substring(notifyStart, notifyEnd);

        assertTrue("Home must resample leak status after VPN transitions from starting to running.",
                homeFragment.contains("VPN_LEAK_STATUS_REFRESH_DELAY_MS") &&
                        notifyAdBlocked.contains("renderLeakStatus();") &&
                        notifyAdBlocked.contains("postDelayed(this::renderLeakStatus") &&
                        notifyAdBlocked.contains("PreferenceHelper.getAdBlockMethod") &&
                        notifyAdBlocked.contains("== VPN"));
    }

    private static boolean statLabelUsesAutosize(String layout, String id) {
        int start = layout.indexOf("android:id=\"@+id/" + id + "\"");
        if (start < 0) {
            return false;
        }
        int end = layout.indexOf("/>", start);
        if (end < 0) {
            return false;
        }
        String block = layout.substring(start, end);
        return block.contains("android:layout_width=\"match_parent\"") &&
                block.contains("android:gravity=\"center\"") &&
                block.contains("android:maxLines=\"1\"") &&
                block.contains("app:autoSizeTextType=\"uniform\"") &&
                block.contains("app:autoSizeMinTextSize=\"10sp\"") &&
                block.contains("app:autoSizeMaxTextSize=\"12sp\"");
    }

    private static String readRepoFile(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolveRepoFile(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
        return repo.resolve(relativePath);
    }
}
