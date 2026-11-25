#!/usr/bin/env ruby
# Standalone checks only: temporary output, cached dependencies, no Gradle/device/build writes.
require 'fileutils'
require 'open3'
require 'rexml/document'
require 'tmpdir'

REPO = File.expand_path('../..', __dir__)
JAVA = ENV.fetch('JAVA_HOME', '/opt/homebrew/opt/openjdk@21')
SDK = ENV.fetch('ANDROID_HOME', File.expand_path('~/.local/android-sdk'))
CACHE = File.expand_path('~/.gradle/caches')
AUDIT_BASELINE = 'd51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77'
abort 'No arguments supported' unless ARGV.empty?

production = %w[
  ui/adware/AdwareFragment.java ui/adware/AdwareLiveData.java ui/adware/AdwareViewModel.java
  ui/adware/AdwareInstall.java ui/log/LogAdapter.java ui/log/LogEntry.java
  ui/log/LogViewCallback.java db/entity/ListType.java util/AppExecutors.java
].map { |p| "app/src/main/java/org/adaway/#{p}" }
host_tests = %w[
  ui/adware/AdwareRefreshTest.java ui/adware/AdwarePermissionContractTest.java
  ui/adware/AdwareLiveDataMatcherTest.java ui/adware/AdwareUninstallIntentContractTest.java
  ui/log/LogActionDescriptionTest.java ui/domainchecker/DomainCheckerTest.java
].map { |p| "app/src/test/java/org/adaway/#{p}" }
android_tests = %w[
  ui/adware/AdwareRefreshInstrumentedTest.java ui/log/LogAdapterActionsInstrumentedTest.java
].map { |p| "app/src/androidTest/java/org/adaway/#{p}" }
test_names = host_tests.map { |p| p.sub('app/src/test/java/', '').sub('.java', '').tr('/', '.') }

android_jar = File.join(SDK, 'platforms/android-36/android.jar')
image = Dir[File.join(CACHE, '9.5.1/transforms/*/transformed/output/jdkImage')].first
app_classes = File.join(REPO, 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes')
test_classes = File.join(REPO, 'app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes')
r_jar = File.join(REPO, 'app/build/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar')
[android_jar, image, app_classes, test_classes, r_jar].each do |p|
  abort "Missing cached compile input: #{p.inspect}" unless p && File.exist?(p)
end
dependencies = Dir[File.join(CACHE, '9.5.1/transforms/*/transformed/**/classes.jar')].sort +
               Dir[File.join(CACHE, 'modules-2/files-2.1/**/*.jar')].sort
classpath = [android_jar, app_classes, test_classes, *dependencies].join(File::PATH_SEPARATOR)

def run!(*command)
  abort "Command failed: #{command.first}" unless system(*command)
end

Dir.chdir(REPO) do
  Dir.mktmpdir('adaway-support-fixes-') do |tmp|
    write = lambda do |path, contents|
      destination = File.join(tmp, path)
      FileUtils.mkdir_p(File.dirname(destination))
      File.write(destination, contents)
      destination
    end

    # New string IDs have not been linked by main yet. Check every symbol against real
    # cached R fields or current XML, then provide temporary IDs for API/host compilation.
    resources = Hash.new { |h, k| h[k] = [] }
    (production + host_tests + android_tests).each do |path|
      File.read(path).scan(/\bR\.(\w+)\.(\w+)/) { |type, name| resources[type] << name }
    end
    strings = REXML::Document.new(File.read('app/src/main/res/values/strings_log.xml'))
    new_strings = %w[log_remove_blocked_entry log_remove_allowed_entry log_remove_redirected_entry]
    new_strings.each do |name|
      matches = strings.get_elements('resources/string').select { |e| e.attributes['name'] == name }
      abort "Missing/duplicate/empty string #{name}" unless matches.size == 1 && !matches[0].text.to_s.empty?
    end
    next_id = 0x7f010000
    r_body = resources.map do |type, names|
      existing, status = Open3.capture2(File.join(JAVA, 'bin/javap'), '-classpath', r_jar,
                                       "org.adaway.R$#{type}")
      abort "Unable to inspect cached R.#{type}" unless status.success?
      fields = names.uniq.map do |name|
        unless existing.match?(/\bint #{Regexp.escape(name)};/) ||
               (type == 'string' && new_strings.include?(name))
          abort "Unknown resource: R.#{type}.#{name}"
        end
        next_id += 1
        "public static final int #{name} = #{next_id};"
      end.join("\n")
      "public static final class #{type} {\n#{fields}\n}"
    end.join("\n")
    r_source = write.call('symbols/org/adaway/R.java', "package org.adaway; public final class R {\n#{r_body}\n}\n")

    copied_production = production.map { |p| write.call("source/#{p}", File.read(p)) }
    harness = write.call('SupportTestRunner.java', <<~JAVA)
      import org.junit.runner.JUnitCore;
      import org.junit.runner.Result;
      import org.junit.runner.notification.Failure;
      public final class SupportTestRunner {
        public static void main(String[] names) throws Exception {
          Class<?>[] classes = new Class<?>[names.length];
          for (int i = 0; i < names.length; i++) classes[i] = Class.forName(names[i]);
          Result result = new JUnitCore().run(classes);
          java.util.Set<String> actual = new java.util.TreeSet<>();
          for (Failure failure : result.getFailures()) {
            actual.add(failure.getDescription().getMethodName());
            System.out.println(failure.getTrace());
          }
          String wanted = System.getProperty("adaway.expectedFailures", "");
          java.util.Set<String> expected = new java.util.TreeSet<>();
          if (!wanted.isEmpty()) expected.addAll(java.util.Arrays.asList(wanted.split(",")));
          System.out.println("Executed=" + result.getRunCount() + " failures="
              + result.getFailureCount() + " skipped=" + result.getIgnoreCount()
              + (wanted.isEmpty() ? "" : " (negative control; expected " + expected + ")"));
          if (!actual.equals(expected) || result.getFailureCount() != expected.size()) System.exit(1);
        }
      }
    JAVA
    host_output = File.join(tmp, 'host-classes')
    api_output = File.join(tmp, 'android-api-classes')
    FileUtils.mkdir_p([host_output, api_output])
    puts 'HOST: compiling real source against cached dependencies; temporary checked R IDs only.'
    run!(File.join(JAVA, 'bin/javac'), '--release', '17', '-proc:none', '-cp', classpath,
         '-d', host_output, r_source, harness, *copied_production, *host_tests)
    runtime = [host_output, classpath].join(File::PATH_SEPARATOR)
    run!(File.join(JAVA, 'bin/java'), '-cp', runtime, 'SupportTestRunner', *test_names)

    puts 'ANDROID API: type-checking production plus host and instrumentation tests; not executing Android.'
    run!(File.join(JAVA, 'bin/javac'), '-source', '17', '-target', '17', '--system', image,
         '-proc:none', '-cp', classpath, '-d', api_output, r_source, *copied_production,
         *host_tests, *android_tests)

    puts 'NEGATIVE CONTROL: baseline XML must fail the permission and initial-label assertions.'
    baseline = File.join(tmp, 'baseline')
    %w[app/src/main/AndroidManifest.xml app/src/debug/AndroidManifest.xml
       app/src/directRelease/AndroidManifest.xml app/src/main/res/layout/log_entry.xml
       app/build.gradle].each do |p|
      text, status = Open3.capture2('git', 'show', "#{AUDIT_BASELINE}:#{p}")
      abort "Cannot read baseline #{p}" unless status.success?
      write.call("baseline/#{p}", text)
    end
    expected = %w[mainManifestRequestsOnlyNormalUninstallPermissionWithoutSdkCap
                  initialLayoutDescriptionsAgreeWithAllowAndRedirectActions].join(',')
    run!(File.join(JAVA, 'bin/java'), "-Dadaway.repo=#{baseline}",
         "-Dadaway.expectedFailures=#{expected}", '-cp', runtime, 'SupportTestRunner',
         'org.adaway.ui.adware.AdwarePermissionContractTest',
         'org.adaway.ui.log.LogActionDescriptionTest')

    puts 'NEGATIVE CONTROL: removing only the generation check must fail three stale-result tests.'
    data_path = copied_production.find { |p| p.end_with?('/AdwareLiveData.java') }
    data = File.read(data_path)
    guard = 'if (generation == this.scanGeneration)'
    abort 'Generation guard missing/ambiguous' unless data.scan(guard).size == 1
    File.write(data_path, data.sub(guard, 'if (true)'))
    run!(File.join(JAVA, 'bin/javac'), '--release', '17', '-proc:none', '-cp', runtime,
         '-d', host_output, data_path)
    expected = %w[olderWorkerCannotReplaceNewerCompletedScan
                  newRequestInvalidatesOldMainDeliveryBeforeNewScanCompletes
                  delayedOldMainDeliveryCannotOverwritePublishedNewResult].join(',')
    run!(File.join(JAVA, 'bin/java'), "-Dadaway.expectedFailures=#{expected}", '-cp', runtime,
         'SupportTestRunner', 'org.adaway.ui.adware.AdwareRefreshTest')
    puts 'PASS: host regressions, Android API compilation, and both negative controls.'
    puts 'No resource linking, Gradle, emulator, adb, system uninstall, or Android runtime execution.'
  end
end
