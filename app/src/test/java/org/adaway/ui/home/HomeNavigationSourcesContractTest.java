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
                        wrapperLayout.contains("@+id/hosts_sources_toolbar"));
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
    public void terminalUpdateProgressRestoresCountersAndDisablesControls() throws Exception {
        String homeFragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeFragment.java");

        assertTrue("Stopped update progress must reset import counter guards before reattaching " +
                        "host counters.",
                homeFragment.contains("if (progress.isStopped) {") &&
                        homeFragment.contains("resetImportCounterGuards();") &&
                        homeFragment.contains("attachHostCounterObservers();") &&
                        homeFragment.contains("refreshHostCountersOnce();"));
        assertTrue("Idle progress must reset the import counter guard so host-count LiveData " +
                        "can update the blocked counter again.",
                homeFragment.contains("private void resetImportCounterGuards()") &&
                        homeFragment.contains("this.homeViewModel.setCachedInitialBlockedCount(-1)"));
        assertTrue("Terminal complete/stopped text must be announced for accessibility.",
                homeFragment.contains("progress.isComplete || progress.isStopped") &&
                        homeFragment.contains("announceForAccessibility(progressText)"));
        assertTrue("Pause and stop controls must be disabled after complete terminal progress.",
                homeFragment.contains("&& !progress.isComplete") &&
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
                "app/src/main/res/mipmap-hdpi/icon.png",
                "app/src/main/res/mipmap-xhdpi/icon.png",
                "app/src/main/res/mipmap-xxhdpi/icon.png",
                "app/src/main/res/mipmap-xxxhdpi/icon.png",
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

    private static String readRepoFile(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolveRepoFile(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
        return repo.resolve(relativePath);
    }
}
