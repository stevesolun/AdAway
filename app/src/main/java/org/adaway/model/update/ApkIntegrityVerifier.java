package org.adaway.model.update;

import static android.content.pm.PackageManager.GET_SIGNATURES;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class ApkIntegrityVerifier {
    private static final String SHA_256 = "SHA-256";

    private ApkIntegrityVerifier() {
    }

    static boolean matchesSha256(
            @NonNull Context context, @NonNull Uri apkUri, @NonNull String expectedSha256) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(apkUri)) {
            if (inputStream == null) {
                return false;
            }
            String actual = sha256Hex(inputStream);
            return actual.equals(normalizeSha256(expectedSha256));
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    static boolean matchesSigningCertificateSha256(
            @NonNull Context context,
            @NonNull Uri apkUri,
            @NonNull String expectedSigningCertificateSha256) {
        File apkCopy = null;
        try {
            apkCopy = File.createTempFile("adaway-update-", ".apk", context.getCacheDir());
            try (InputStream inputStream = context.getContentResolver().openInputStream(apkUri);
                OutputStream outputStream = new FileOutputStream(apkCopy)) {
                if (inputStream == null) {
                    return false;
                }
                copy(inputStream, outputStream);
            }
            Set<String> archiveDigests = getArchiveSigningCertificateSha256s(context, apkCopy);
            Set<String> installedDigests = getInstalledSigningCertificateSha256s(context);
            return hasExpectedAndInstalledSigningCertificate(
                    archiveDigests,
                    installedDigests,
                    expectedSigningCertificateSha256);
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        } finally {
            if (apkCopy != null && !apkCopy.delete()) {
                apkCopy.deleteOnExit();
            }
        }
    }

    @NonNull
    static String sha256Hex(@NonNull InputStream inputStream)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(SHA_256);
        byte[] buffer = new byte[8192];
        try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            while (digestInputStream.read(buffer) != -1) {
                // DigestInputStream updates the digest while bytes are read.
            }
        }
        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }

    static boolean hasExpectedAndInstalledSigningCertificate(
            @NonNull Set<String> archiveDigests,
            @NonNull Set<String> installedDigests,
            @NonNull String expectedSigningCertificateSha256) {
        if (!archiveDigests.contains(normalizeSha256(expectedSigningCertificateSha256))) {
            return false;
        }
        Set<String> matches = new HashSet<>(archiveDigests);
        matches.retainAll(installedDigests);
        return !matches.isEmpty();
    }

    private static void copy(@NonNull InputStream inputStream, @NonNull OutputStream outputStream)
            throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
    }

    @SuppressLint("PackageManagerGetSignatures")
    private static Set<String> getArchiveSigningCertificateSha256s(
            @NonNull Context context,
            @NonNull File apkFile) throws NoSuchAlgorithmException {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageArchiveInfo(
                apkFile.getAbsolutePath(),
                SDK_INT >= P ? GET_SIGNING_CERTIFICATES : GET_SIGNATURES);
        if (packageInfo == null || !context.getPackageName().equals(packageInfo.packageName)) {
            return new HashSet<>();
        }

        return getSignatureSha256s(packageInfo);
    }

    @SuppressLint("PackageManagerGetSignatures")
    private static Set<String> getInstalledSigningCertificateSha256s(@NonNull Context context)
            throws NoSuchAlgorithmException {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    context.getPackageName(),
                    SDK_INT >= P ? GET_SIGNING_CERTIFICATES : GET_SIGNATURES);
            return getSignatureSha256s(packageInfo);
        } catch (PackageManager.NameNotFoundException e) {
            return new HashSet<>();
        }
    }

    private static Set<String> getSignatureSha256s(@NonNull PackageInfo packageInfo)
            throws NoSuchAlgorithmException {
        Signature[] signatures;
        if (SDK_INT >= P) {
            if (packageInfo.signingInfo == null) {
                return new HashSet<>();
            }
            if (packageInfo.signingInfo.hasMultipleSigners()) {
                signatures = packageInfo.signingInfo.getApkContentsSigners();
            } else {
                signatures = packageInfo.signingInfo.getSigningCertificateHistory();
            }
        } else {
            signatures = packageInfo.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            return new HashSet<>();
        }

        MessageDigest digest = MessageDigest.getInstance(SHA_256);
        Set<String> digests = new HashSet<>();
        for (Signature signature : signatures) {
            digest.reset();
            digests.add(sha256Hex(digest.digest(signature.toByteArray())));
        }
        return digests;
    }

    @NonNull
    private static String normalizeSha256(@NonNull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static String sha256Hex(byte[] hash) {
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }
}
