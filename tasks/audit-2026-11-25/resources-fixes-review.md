# Updater Status Layout Fix: Freeze Checkpoint

## Lane Closed: Scoped Verification

Main subsequently reported all eight UpdateStatusLayoutInstrumentedTest cases PASS on API34, using saved phase-three app APK e25c957e... and test APK 9f1c1919... (hash prefixes supplied by main; not expanded or independently rehashed here). RES-AUD-001 is verified within this tested scope. Main owns the central status update; this lane did not edit central files.

Read the complete retained 43-line geometry log at app/build/reports/audit-2026-11-25/phase-three-updater-geometry.log (SHA-256 5ecdbd3c41e7fd2d0ad8d38c86b93b30ad031633946ff40fd68fb27318de5585). Its 42 geometry entries include all enum states at both font scales, byte/completion displays, recreation/reset and the two measured pre-patch controls. In DOWNLOAD_FAILED, the pre-patch normal-font message y560..706 overlaps action y581..707; fixed action y748..874 follows the same message. At large font, pre-patch message y587..924 overlaps action y608..751; fixed action y966..1109 follows the message. These are actual retained Android measurements, not the earlier synthetic host solver dimensions. GONE views can retain old rectangles and are not treated as visible overlaps.

Both frozen source hashes below remain unchanged. Main reported 711 JVM cases with 3 skips and 0 failures, and lint with 37 warnings and 0 errors. The broader 96-case Android batch had two unrelated fixture failures; this is not a whole-batch or whole-repository green claim. No new build, adb command or app edit was performed by this lane.

Main also read and viewed both asset contact sheets and accepted the asset report. All assigned resource/XML/translation, updater-fix and asset/configuration work is complete. Lane closed; no further follow-up scope is assumed.

The original freeze record below is retained as historical evidence. Its pending-compilation/runtime statements are superseded only by this scoped validation addendum; its viewport, locale, physical-device and end-to-end installer limits remain.

## Ready for Main

**APP SOURCE FROZEN: 2026-11-25 11:19:10 UTC.** No further app edits by this lane after this checkpoint. The new test class for the next consolidated build is:

`org.adaway.ui.update.UpdateStatusLayoutInstrumentedTest`

Eight AndroidJUnit4 test methods are authored. They are NOT compiled or executed here. Main's saved phase-2 app (30f86e8...) and test (671f74...) APKs predate this change and do not prove it. Main reported 87 phase-2 cases (11 fixture failures being corrected) and seven live VPN passes; those are external context, not this lane's verification.

## Frozen Files

| Path | SHA-256 |
| --- | --- |
| app/src/main/res/layout/update_actity.xml | b82385e8a82af5dffc76c17de2f33838aa499c2af06a0d56f863776cad83ad4d |
| app/src/androidTest/java/org/adaway/ui/update/UpdateStatusLayoutInstrumentedTest.java | 365da467cbd522e5c7b9fdd41719240c28aa394b2033e5afe5bad7a3cfc76895 |

Pre-patch layout SHA-256: `181a6fb461d9a172bd64062668998318ef0c7092e0644c98af9239bf35bb8b97`.

The app index listing (`git ls-files --stage -- app`) SHA-256 was
`c458e4ed10c5a5b7ba42c940d2a0fbc40e848798e1267ae9f13911f2b939496a`
both before edits and at freeze. The layout is unstaged and the new test untracked. No stage, commit, build, Gradle or adb command was run. Other lanes made concurrent unstaged app changes; this lane neither changed nor reverted those files.

## Narrow Production Change

Only three XML attributes changed:
- Retry/update button top follows `progressTextView`, not the header.
- Status width is `0dp` between its existing 16dp side margins, allowing real wrapping.
- Status text stays centered within that bounded width.

The existing header -> progress -> status -> action -> changelog sequence now reserves status height. The action's existing INVISIBLE/GONE behavior is unchanged. No new IDs, containers, resources, controller paths or redesign were added.

## Test Wiring and Measurement

Reuses `UpdateRetryUiInstrumentedTest`'s passive model fixture and reflective `UpdateModel.setInstallState` publication. The production LiveData -> UpdateViewModel -> UpdateActivity observer updates the views; tests do not call `renderProgress`, substitute a fake renderer, or assign status strings/visibility directly.

The real Activity's production binding is reinflated through a cloned inflater with an isolated `Configuration` context. It uses the manifest's actual `Theme.AdAway.NoActionBar.Red`, fontScale 1.0 or 2.0, and a controlled portrait 360x800dp viewport. Locale/direction are inherited, not globally changed. Only the binding/inflation context is replaced; existing observers remain live, and the production listener-binding method is reused. This is isolated configured-view measurement inside the real Activity, not a device-wide font-setting or screenshot claim.

The tests explicitly measure/layout the actual Android view tree and inspect:
- complete TextView Layout line consumption, line widths, ellipsis count and height including padding;
- positive rectangles contained in the measured viewport;
- status after visible progress, retry action after complete status, and changelog after both;
- positive changelog viewport, actual rendered state text and control visibility;
- retained real state before reinflating the recreated Activity;
- unchanged application font configuration at teardown.

No network/download/installer work is allowed by the RecordingModel overrides. Existing passive-root fixture reset conventions are preserved. This is controlled state publication, not end-to-end network/install verification.

## Eight Test Methods

| Method | Coverage |
| --- | --- |
| everyInstallStateHasSeparatedGeometryAtNormalFont | All 11 InstallState values, plus reset to IDLE; font 1.0 |
| everyInstallStateHasSeparatedGeometryAtLargeFont | Same complete enum matrix; font 2.0 |
| byteProgressTransitionsHaveSeparatedGeometryAtNormalFont | Real postProgress guarded publisher at 0/50/100 percent; compatibility completion; VERIFYING -> INSTALLER_OPENED; font 1.0 |
| byteProgressTransitionsHaveSeparatedGeometryAtLargeFont | Same byte/completion/installation transition coverage; font 2.0 |
| retryGeometrySurvivesRecreationAtNormalFont | Retained INSTALLER_OPENED state, remeasurement and IDLE reset; font 1.0 |
| retryGeometrySurvivesRecreationAtLargeFont | Same recreation/reset path; font 2.0 |
| prePatchRetryConstraintsFailMeasuredSeparationAtNormalFont | Restore the exact three pre-fix XML attributes; assert actual DOWNLOAD_FAILED rectangles intersect and the separation assertion rejects them; font 1.0 |
| prePatchRetryConstraintsFailMeasuredSeparationAtLargeFont | Same measured negative control; font 2.0 |

InstallState matrix: IDLE; DOWNLOADING; VERIFYING; PERMISSION_REQUIRED; INSTALLER_OPENED; DOWNLOAD_FAILED; VERIFICATION_FAILED; INSTALL_FAILED; METADATA_EXPIRED; CHECKING; CHECK_FAILED. Seven retry states and three active states are included, not just the originally reported error.

The compatibility CompleteDownloadStatus case follows existing `UpdateActivityDownloadStatusInstrumentedTest` via the public progress LiveData. It is not represented as a production installer-success path; actual installer handoff remains INSTALLER_OPENED and explicitly unconfirmed.

## Executed Local Evidence

- Read the staged pre-patch XML with `git show :app/src/main/res/layout/update_actity.xml` and the unstaged fixed XML; no index write.
- Parsed both with the JDK DOM parser and fed their actual top-anchor/margin relationships to the cached AndroidX ConstraintLayout core 1.1.0 solver.
- **21 expected-red baseline overlaps**: seven retry states x three synthetic message heights.
- **45 green patched solver layouts**: 11 install states + three byte samples + compatibility completion, each at heights 24, 104 and 208.
- Example baseline/fixed vertical coordinates:
  - height 24: baseline message [208,232], action [216,280]; fixed action [248,312], changelog starts 328.
  - height 104: baseline message [208,312], action [216,280]; fixed action [328,392], changelog starts 408.
  - height 208: baseline message [208,416], action [216,280]; fixed action [432,496], changelog starts 512.
- The solver uses supplied dimensions (header 200, action 64, synthetic message heights) to isolate constraint behavior. These are NOT Android text/font measurements. The authored Android negative controls provide the actual measured red baseline gate for main.
- JDK 21 `JavacTask.parse` accepted the complete new Java source with no syntax errors. No analyze/type-check, Android compilation, APK build or test execution is claimed.
- Scoped `git diff --check` passed. Sidecar CSV/hash checks are separate from runtime proof.
- First JShell local-engine attempt could not load the solver class at runtime and provided no proof; rerun with its standard execution engine passed with `RESOURCE_FIXES_PROBE_PASS red=21 green=45 Java_parse_only=true`.

## Remaining Gate

RES-AUD-001 is **implemented-awaiting-android**, not verified-fixed. Main must compile the next APK pair, run all eight new tests, retain their logged measured rectangles (tag `UpdateStatusLayoutTest`) and review failures without weakening assertions. These tests cover the stated portrait viewport; they do not claim landscape, every locale, all short windows, keyboard/insets, screen-reader or physical-device proof.

Central files and the original resources-audit sidecars are untouched. The original audit's layout hash is now stale by design; import the new two-row resources-fixes-files.csv only after reviewing this exact frozen snapshot. No implicit closure of any other audit finding.

## Reproduce the Host-Only Probe

Run the following Java snippets through JDK21 jshell with the cached
`androidx.constraintlayout:constraintlayout-core:1.1.0` JAR on `--class-path`.
No Android build or device is involved. The baseline must still be the old staged layout; once main stages the fix, use the recorded pre-fix revision instead.

```java
import java.nio.file.*;
import java.nio.charset.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import androidx.constraintlayout.core.widgets.*;
import androidx.constraintlayout.core.widgets.ConstraintAnchor.Type;
import javax.tools.*;
import com.sun.source.util.*;

Document xml(String value) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
}
Map<String, Element> entries(Document doc) {
    var result = new HashMap<String, Element>();
    var nodes = doc.getDocumentElement().getChildNodes();
    for (int i=0;i<nodes.getLength();i++) {
        if (nodes.item(i) instanceof Element e && e.hasAttribute("android:id")) {
            result.put(e.getAttribute("android:id").replace("@+id/",""),e);
        }
    }
    return result;
}
Map<String, ConstraintWidget> solve(Document doc, String state, int messageHeight) {
    var es=entries(doc);
    var root=new ConstraintWidgetContainer(360,800);
    root.setOptimizationLevel(0);
    var ws=new HashMap<String,ConstraintWidget>();
    ws.put("headerTextView", new ConstraintWidget(328,200));
    ws.put("downloadProgressBar",new ConstraintWidget(296,6));
    ws.put("progressTextView",new ConstraintWidget(328,state.equals("IDLE")?0:messageHeight));
    ws.put("update_button",new ConstraintWidget(180,64));
    ws.put("changelogScrollView",new ConstraintWidget(360,0));
    boolean active=Set.of("DOWNLOADING","VERIFYING","CHECKING","BYTE_0","BYTE_50","BYTE_100","BYTE_COMPLETE").contains(state);
    ws.get("downloadProgressBar").setVisibility(active?ConstraintWidget.VISIBLE:ConstraintWidget.GONE);
    ws.get("update_button").setVisibility(state.equals("IDLE")?ConstraintWidget.GONE:active?ConstraintWidget.INVISIBLE:ConstraintWidget.VISIBLE);
    for(var item:ws.entrySet()) {
        var id=item.getKey();var w=item.getValue();root.add(w);
        w.connect(Type.LEFT,root,Type.LEFT,0);w.connect(Type.RIGHT,root,Type.RIGHT,0);
        if(id.equals("headerTextView")) {w.connect(Type.TOP,root,Type.TOP,0);continue;}
        var e=es.get(id);
        var target=e.getAttribute("app:layout_constraintTop_toBottomOf").replace("@+id/","").replace("@id/","");
        int margin=Integer.parseInt(e.getAttribute("android:layout_marginTop").replace("dp",""));
        w.connect(Type.TOP,ws.get(target),Type.BOTTOM,margin);
    }
    var scroll=ws.get("changelogScrollView");
    scroll.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT);
    scroll.connect(Type.BOTTOM,root,Type.BOTTOM,16);
    root.layout();
    return ws;
}
void check(boolean result,String message) {if(!result)throw new AssertionError(message);}
String geometry(Map<String,ConstraintWidget> ws) {
    var m=ws.get("progressTextView");var b=ws.get("update_button");var s=ws.get("changelogScrollView");
    return "message=["+m.getTop()+","+m.getBottom()+"] button=["+b.getTop()+","+b.getBottom()+"] changelog=["+s.getTop()+","+s.getBottom()+"]";
}
{
    var file="app/src/main/res/layout/update_actity.xml";
    var process=new ProcessBuilder("git","show",":"+file).start();
    var oldText=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
    check(process.waitFor()==0,"Cannot read untouched staged baseline");
    var old=xml(oldText);var fixed=xml(Files.readString(Path.of(file)));
    var oldEntries=entries(old);var fixedEntries=entries(fixed);
    check(oldEntries.get("update_button").getAttribute("app:layout_constraintTop_toBottomOf").equals("@+id/headerTextView"),"Wrong baseline anchor");
    check(oldEntries.get("progressTextView").getAttribute("android:layout_width").equals("wrap_content"),"Wrong baseline width");
    check(fixedEntries.get("update_button").getAttribute("app:layout_constraintTop_toBottomOf").equals("@+id/progressTextView"),"Missing corrected anchor");
    check(fixedEntries.get("progressTextView").getAttribute("android:layout_width").equals("0dp"),"Missing bounded text width");
    var states=List.of("IDLE","DOWNLOADING","VERIFYING","PERMISSION_REQUIRED","INSTALLER_OPENED","DOWNLOAD_FAILED","VERIFICATION_FAILED","INSTALL_FAILED","METADATA_EXPIRED","CHECKING","CHECK_FAILED","BYTE_0","BYTE_50","BYTE_100","BYTE_COMPLETE");
    int red=0,green=0;
    for(int textHeight:new int[]{24,104,208}) for(var state:states) {
        var before=solve(old,state,textHeight);var after=solve(fixed,state,textHeight);
        var b=before.get("update_button");var t=before.get("progressTextView");
        if(b.getVisibility()==ConstraintWidget.VISIBLE) {
            check(b.getTop()<t.getBottom()&&t.getTop()<b.getBottom(),"Baseline must reproduce overlap "+state);
            red++;
        }
        var ab=after.get("update_button");var at=after.get("progressTextView");var ap=after.get("downloadProgressBar");var sc=after.get("changelogScrollView");
        check(sc.getHeight()>0,"No changelog viewport "+state);
        check(sc.getTop()>=at.getBottom(),"Changelog/status overlap "+state);
        if(ab.getVisibility()==ConstraintWidget.VISIBLE)check(ab.getTop()>=at.getBottom(),"Button/status overlap "+state);
        if(ap.getVisibility()==ConstraintWidget.VISIBLE)check(at.getTop()>=ap.getBottom(),"Progress/status overlap "+state);
        green++;
        if(state.equals("DOWNLOAD_FAILED"))System.out.println("RED baseline height="+textHeight+" "+geometry(before)+"; GREEN patched "+geometry(after));
    }
    var compiler=ToolProvider.getSystemJavaCompiler();
    var diagnostics=new DiagnosticCollector<JavaFileObject>();
    try(var manager=compiler.getStandardFileManager(diagnostics,null,StandardCharsets.UTF_8)) {
        var sources=manager.getJavaFileObjects("app/src/androidTest/java/org/adaway/ui/update/UpdateStatusLayoutInstrumentedTest.java");
        var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,sources);
        task.parse().iterator().next();
        check(diagnostics.getDiagnostics().stream().noneMatch(d->d.getKind()==Diagnostic.Kind.ERROR),"Java syntax error "+diagnostics.getDiagnostics());
    }
    System.out.println("RESOURCE_FIXES_PROBE_PASS red="+red+" green="+green+" Java_parse_only=true");
}
/exit
```
