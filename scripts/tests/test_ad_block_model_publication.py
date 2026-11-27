"""Exercise the real application selector on a host JDK, without Android or a VPN."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


MAIN = Path(__file__).resolve().parents[2] / "app/src/main/java"
BIN = Path(os.environ["JAVA_HOME"]) / "bin" if "JAVA_HOME" in os.environ else None

# Only the selector and method enum are under test. Startup collaborators are
# compile-time stubs; the factory gate and VPN counter make ownership observable.
STUBS = {
    "android.content.Context": "public class Context {}",
    "android.content.SharedPreferences":
        "public interface SharedPreferences { boolean getBoolean(String k, boolean d); }",
    "android.app.Application": """public class Application extends android.content.Context {
        protected static final int MODE_PRIVATE = 0;
        public void onCreate() {}
        public android.content.SharedPreferences getSharedPreferences(String s, int m) { return null; }
    }""",
    "androidx.appcompat.app.AppCompatDelegate":
        "public class AppCompatDelegate { public static void setApplicationLocales(Object o) {} }",
    "androidx.core.os.LocaleListCompat": """public class LocaleListCompat {
        public static LocaleListCompat forLanguageTags(String s) { return null; }
        public static LocaleListCompat getEmptyLocaleList() { return null; }
    }""",
    "org.adaway.helper.NotificationHelper":
        "public class NotificationHelper { public static void createNotificationChannels(Object o) {} }",
    "org.adaway.helper.PreferenceHelper": """import org.adaway.model.adblocking.*;
        public class PreferenceHelper {
            public static volatile AdBlockMethod method = AdBlockMethod.VPN;
            public static AdBlockMethod getAdBlockMethod(android.content.Context c) { return method; }
        }""",
    "org.adaway.model.source.SourceOperationCoordinator": "public class SourceOperationCoordinator {}",
    "org.adaway.model.source.PendingSourceApply":
        "public class PendingSourceApply { public PendingSourceApply(Object o) {} }",
    "org.adaway.model.source.SourceModel":
        "public class SourceModel { public SourceModel(Object o) {} public void syncHostEntries() {} }",
    "org.adaway.model.source.SiteCompatibilityAllowlist": """public class SiteCompatibilityAllowlist {
        public static boolean ensureAllowlistSync(Object o) { return false; }
    }""",
    "org.adaway.model.update.UpdateModel": "public class UpdateModel { public UpdateModel(Object o) {} }",
    "org.adaway.ui.hosts.FilterSetUpdateService":
        "public class FilterSetUpdateService { public static void syncPreferences(Object o) {} }",
    "org.adaway.util.AppExecutors": """public class AppExecutors {
        public static AppExecutors getInstance() { return null; }
        public java.util.concurrent.Executor diskIO() { return null; }
    }""",
    "org.adaway.util.Constants":
        'public class Constants { public static final String PREFS_NAME="", PREFS_PENDING_SOURCE_APPLY=""; }',
    "org.adaway.util.log.ApplicationLog":
        "public class ApplicationLog { public static void init(Object o) {} }",
    "org.adaway.model.adblocking.AdBlockModel": """import org.adaway.model.vpn.VpnModel;
        public class AdBlockModel {
            public static final Object gate = new Object();
            public static int builds;
            private final AdBlockMethod method;
            protected AdBlockModel(AdBlockMethod method) { this.method = method; }
            public AdBlockMethod getMethod() { return method; }
            public static AdBlockModel build(android.content.Context c, AdBlockMethod method) {
                synchronized (gate) {
                    builds++;
                    return method == AdBlockMethod.VPN ? new VpnModel() : new AdBlockModel(method);
                }
            }
        }""",
    "org.adaway.model.vpn.VpnModel": """import org.adaway.model.adblocking.*;
        public class VpnModel extends AdBlockModel {
            public int invalidations;
            public VpnModel() { super(AdBlockMethod.VPN); }
            public void invalidateRulesCache() { invalidations++; }
        }""",
}

FIXTURE = """
import java.lang.management.*;
import java.util.concurrent.*;
import org.adaway.AdAwayApplication;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.*;
import org.adaway.model.vpn.VpnModel;
import static org.adaway.model.adblocking.AdBlockMethod.*;

public class PublicationFixture {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    // The first builder cannot publish while main owns the factory gate. A
    // contender must finish, reach that same gate (old code), or queue behind
    // the first caller (fixed code). The deadline only diagnoses a stuck test.
    static Thread startContender(FutureTask<?> task, Thread first) throws Exception {
        Thread worker = new Thread(task);
        worker.setDaemon(true);
        worker.start();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!task.isDone()) {
            ThreadInfo info = threads.getThreadInfo(worker.getId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED) {
                boolean atFactory = info.getLockOwnerId() == Thread.currentThread().getId()
                        && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(AdBlockModel.gate);
                if (atFactory || (first != null && info.getLockOwnerId() == first.getId())) return worker;
            }
            check(System.nanoTime() < deadline, "Contender never reached model selection");
            Thread.sleep(1);
        }
        return worker;
    }

    public static void main(String[] args) throws Exception {
        AdAwayApplication app = new AdAwayApplication();
        app.invalidateVpnRulesCache();
        check(AdBlockModel.builds == 0, "Invalidation initialized an unused model");
        if (!args[0].equals("cold")) {
            PreferenceHelper.method = ROOT;
            check(app.getAdBlockModel().getMethod() == ROOT, "Expected initial root model");
        }
        PreferenceHelper.method = VPN;
        boolean invalidate = args[0].equals("invalidate");
        int buildsBefore = AdBlockModel.builds;
        FutureTask<AdBlockModel> first = new FutureTask<>(app::getAdBlockModel);
        FutureTask<AdBlockModel> second = new FutureTask<>(() -> {
            if (!invalidate) return app.getAdBlockModel();
            app.invalidateVpnRulesCache();
            return null;
        });
        synchronized (AdBlockModel.gate) {
            Thread builder = startContender(first, null);
            check(!first.isDone(), "First caller did not reach the closed factory gate");
            startContender(second, builder);
        }
        VpnModel retained = (VpnModel) first.get(5, TimeUnit.SECONDS);
        AdBlockModel other = second.get(5, TimeUnit.SECONDS);
        check(AdBlockModel.builds == buildsBefore + 1, "Two callers built competing models");
        if (!invalidate) {
            check(other == retained, "Callers retained different models");
            app.invalidateVpnRulesCache();
        }
        check(retained == app.getAdBlockModel(), "Application replaced the retained VPN model");
        check(retained.invalidations == 1, "Invalidation missed the selected VPN model");

        PreferenceHelper.method = ROOT;
        check(app.getAdBlockModel().getMethod() == ROOT, "Method change did not replace VPN");
        app.invalidateVpnRulesCache();
        PreferenceHelper.method = VPN;
        VpnModel next = (VpnModel) app.getAdBlockModel();
        app.invalidateVpnRulesCache();
        check(next != retained && next.invalidations == 1 && retained.invalidations == 1,
              "Switching back must select and invalidate a fresh VPN model");
    }
}
"""


class AdBlockModelPublicationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(tmp.cleanup)
        cls.root = Path(tmp.name)
        sources = [MAIN / "org/adaway/AdAwayApplication.java",
                   MAIN / "org/adaway/model/adblocking/AdBlockMethod.java"]
        for name, body in STUBS.items():
            path = cls.root / (name.replace(".", "/") + ".java")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"package {name.rsplit('.', 1)[0]};\n{body}\n")
            sources.append(path)
        fixture = cls.root / "PublicationFixture.java"
        fixture.write_text(FIXTURE)
        subprocess.run([BIN / "javac" if BIN else "javac", "--release", "17",
                        "-d", cls.root, *sources, fixture], check=True)

    def run_fixture(self, mode):
        subprocess.run([BIN / "java" if BIN else "java", "-cp", self.root,
                        "PublicationFixture", mode], check=True, timeout=20)

    def test_concurrent_first_selection(self):
        self.run_fixture("cold")

    def test_concurrent_method_change(self):
        self.run_fixture("switch")

    def test_invalidation_during_method_change(self):
        self.run_fixture("invalidate")


if __name__ == "__main__":
    unittest.main()
