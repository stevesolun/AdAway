package org.adaway.model.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class BackupCapacityTest {
    @Test
    public void sourceAndRuleLimitsKeepTheirSecurityBoundaries() throws Exception {
        assertEquals(200, BackupFormat.MAX_SOURCES);
        assertEquals(50_000, BackupFormat.MAX_LIST_ITEMS);
        assertEquals(16 * 1024 * 1024, BackupFormat.MAX_CHARACTERS);
        BackupFormat.checkCounts(200, 50_000);
        assertThrows(JSONException.class, () -> BackupFormat.checkCounts(201, 0));
        assertThrows(JSONException.class, () -> BackupFormat.checkCounts(0, 50_001));
        assertThrows(JSONException.class,
                () -> BackupFormat.checkCounts(0, 3L * Integer.MAX_VALUE));
    }

    @Test
    public void ruleLimitIsSharedAcrossAllArrays() throws Exception {
        JSONObject backup = emptyBackup();
        JSONObject rule = new JSONObject().put("host", "capacity.example").put("enabled", true);
        for (int i = 0; i < 25_000; i++) {
            backup.getJSONArray("blocked").put(rule);
            backup.getJSONArray("allowed").put(rule);
        }
        assertEquals(50_000, BackupFormat.parseBackup(backup).hosts.size());
        backup.getJSONArray("redirected").put(rule);
        assertThrows(JSONException.class, () -> BackupFormat.serializeBackup(backup));
    }

    @Test
    public void serializedCharacterBoundaryIncludesFormatting() throws Exception {
        JSONObject backup = emptyBackup().put("padding", "");
        int overhead = backup.toString(4).length();
        char[] padding = new char[BackupFormat.MAX_CHARACTERS - overhead];
        Arrays.fill(padding, 'x');
        String value = new String(padding);
        backup.put("padding", value);
        assertEquals(BackupFormat.MAX_CHARACTERS,
                BackupFormat.serializeBackup(backup).length());
        backup.put("padding", value + "x");
        assertThrows(JSONException.class, () -> BackupFormat.serializeBackup(backup));
    }

    @Test
    public void exportRejectsRowsThatImportWouldReject() throws Exception {
        JSONObject backup = emptyBackup();
        backup.getJSONArray("blocked").put(new JSONObject()
                .put("host", "https://not-a-host.example/path").put("enabled", true));
        assertThrows(JSONException.class, () -> BackupFormat.serializeBackup(backup));
    }

    @Test
    public void reservedUserSourceIsRejectedBeforeReconciliation() throws Exception {
        JSONObject backup = emptyBackup();
        backup.getJSONArray("sources").put(new JSONObject()
                .put("label", "User source").put("url", "content://org.adaway/user/hosts")
                .put("enabled", false).put("allow", true).put("redirect", true));
        assertThrows(JSONException.class, () -> BackupFormat.parseBackup(backup));
    }

    private static JSONObject emptyBackup() throws JSONException {
        return new JSONObject().put("sources", new JSONArray()).put("blocked", new JSONArray())
                .put("allowed", new JSONArray()).put("redirected", new JSONArray());
    }
}
