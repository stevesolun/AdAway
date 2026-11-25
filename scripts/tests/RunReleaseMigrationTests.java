import org.adaway.security.ReleaseWorkflowRegressionTest;
import org.adaway.security.SecurityHardeningTest;
import org.adaway.scripts.ReleaseReadinessScriptTest;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

/** Standalone entry point restricted to the authorized release test migration. */
public final class RunReleaseMigrationTests {
    private static int tests;
    private static int failures;

    public static void main(String[] args) {
        for (String name : new String[]{
                "atk34_releaseArtifactVerifierChecksManifestAndChecksums",
                "atk34_releaseSmokeRequiresReleaseApkOnRealDevice",
                "atk34_releaseSmokeVerifyOnlyUsesHighestParsedBuildToolsVersion",
                "atk34_releaseSmokeReportRecordsPhysicalLaunchWithoutSerialLeak"}) {
            check(name, Request.method(SecurityHardeningTest.class, name));
        }
        for (String name : new String[]{
                "releaseSmokePrefersTestedToolsOverNewerInstalledAndPath",
                "releaseSmokeRefusesMissingTestedSignerWithoutFallback",
                "releaseSmokeRejectsWrongCertificateWithPinnedTools"}) {
            check(name, Request.method(ReleaseWorkflowRegressionTest.class, name));
        }
        check("ReleaseReadinessScriptTest (all cases)", Request.aClass(ReleaseReadinessScriptTest.class));
        System.out.println("Release migration: " + tests + " tests, " + failures + " failures/skips");
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static void check(String label, Request request) {
        Result result = new JUnitCore().run(request);
        tests += result.getRunCount();
        int failed = result.getFailureCount() + result.getIgnoreCount() +
                result.getAssumptionFailureCount();
        failures += failed;
        System.out.println((failed == 0 ? "PASS " : "FAIL ") + label +
                " (" + result.getRunCount() + " tests)");
        result.getFailures().forEach(failure -> System.err.println(failure.getTrace()));
    }
}
