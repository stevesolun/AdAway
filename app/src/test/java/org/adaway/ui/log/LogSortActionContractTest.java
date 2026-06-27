/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.log;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LogSortActionContractTest {
    @Test
    public void sortMenuActionRoutesToViewModelToggleAndResortsCurrentRows()
            throws Exception {
        String menu = readRepoFile("app/src/main/res/menu/log_menu.xml");
        String activity = readRepoFile("app/src/main/java/org/adaway/ui/log/LogActivity.java");
        String viewModel = readRepoFile("app/src/main/java/org/adaway/ui/log/LogViewModel.java");

        assertTrue("DNS log menu must expose sort through the stable sort action id.",
                menu.contains("android:id=\"@+id/sort\"") &&
                        menu.contains("android:title=\"@string/tcpdump_menu_sort\""));
        assertTrue("DNS log Activity must inflate the menu that contains the sort action.",
                activity.contains("menuInflater.inflate(R.menu.log_menu, menu);"));

        int sortBranch = activity.indexOf("if (item.getItemId() == R.id.sort)");
        int toggleCall = activity.indexOf("this.mViewModel.toggleSort();", sortBranch);
        int handledReturn = activity.indexOf("return true;", toggleCall);
        assertTrue("Sort menu selection must call the ViewModel sort toggle and consume the event.",
                sortBranch >= 0 && sortBranch < toggleCall && toggleCall < handledReturn);

        int toggleMethod = viewModel.indexOf("public void toggleSort()");
        int toggleToAlphabetical = viewModel.indexOf("LogEntrySort.ALPHABETICAL", toggleMethod);
        int toggleToTopLevelDomain = viewModel.indexOf("LogEntrySort.TOP_LEVEL_DOMAIN", toggleMethod);
        int sortMethodCall = viewModel.indexOf(");", toggleToTopLevelDomain);
        assertTrue("Sort toggle must alternate between alphabetical and top-level-domain ordering.",
                toggleMethod >= 0 &&
                        toggleMethod < toggleToAlphabetical &&
                        toggleToAlphabetical < toggleToTopLevelDomain &&
                        toggleToTopLevelDomain < sortMethodCall);

        int sortMethod = viewModel.indexOf("private void sortDnsRequests(LogEntrySort sort)");
        int copyEntries = viewModel.indexOf("new ArrayList<>(entries)", sortMethod);
        int applyComparator = viewModel.indexOf("sortedEntries.sort(this.sort.comparator())",
                copyEntries);
        int postSorted = viewModel.indexOf("this.logEntries.postValue(sortedEntries)",
                applyComparator);
        assertTrue("Sort action must re-sort the current rows and publish the sorted list.",
                sortMethod >= 0 &&
                        sortMethod < copyEntries &&
                        copyEntries < applyComparator &&
                        applyComparator < postSorted);
    }

    private static String readRepoFile(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolveRepoFile(relativePath)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path direct = cwd.resolve(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = cwd.getParent();
        while (parent != null) {
            Path candidate = parent.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            parent = parent.getParent();
        }
        return direct;
    }
}
