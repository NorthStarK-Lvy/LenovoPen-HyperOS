package com.alyssum.relegion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/** Verifies the stock PICASSO-CD1 package without extracting or editing it. */
final class LenovoFirmwarePackage {
    private static final String OTA_MODEL = "LenovoTabPenPlus_PICASSO-CD1_0x617F_0x17EF_TB710FU_prc";
    private static final String PACKAGE = "com.lenovo.topaz_prc_wifi_LenovoTabPenPlus-CD1";
    private static final int PACKAGE_LIMIT = 16 * 1024 * 1024;
    private static final int MANIFEST_LIMIT = 64 * 1024;
    private static final Set<String> LOCAL_SUFFIXES =
            Set.of("arpa", "example", "internal", "invalid", "local", "localhost", "onion", "test");

    static final class InvalidPackage extends IOException {
        InvalidPackage(String message) { super(message); }
    }

    enum DownloadIssue {
        SIZE_INVALID("清单未给出有效的文件大小"),
        SIZE_LIMIT("文件大小超过 16 MiB"),
        FILENAME_MISSING("清单未给出固件文件名"),
        FILENAME_NOT_ZIP("固件文件名没有 .zip 后缀"),
        SHA256_MISSING("清单未给出 SHA-256"),
        SHA256_FORMAT("清单的 SHA-256 格式异常"),
        URL_INVALID("清单未给出可解析的下载地址"),
        URL_SCHEME("下载地址未使用 HTTPS"),
        URL_HOST("下载地址缺少有效的公网域名"),
        URL_PORT("下载地址使用了不允许的端口"),
        URL_PATH("下载地址的路径不在允许范围内"),
        URL_COMPONENTS("下载地址包含凭据或片段");

        final String message;
        DownloadIssue(String message) { this.message = message; }
    }

    private LenovoFirmwarePackage() {}

    static boolean matches(LenovoFirmwareClient.Pen pen, LenovoFirmwareClient.Update update) {
        return pen != null && update != null && update.available()
                && OTA_MODEL.equals(pen.model()) && PACKAGE.equals(update.packageName())
                && hasVerifiedDownloadMetadata(update)
                && LenovoFirmwareClient.compareVersions(update.version(), pen.version()) > 0;
    }

    static boolean hasVerifiedDownloadMetadata(LenovoFirmwareClient.Update update) {
        return downloadIssues(update).isEmpty();
    }

    static List<DownloadIssue> downloadIssues(LenovoFirmwareClient.Update update) {
        List<DownloadIssue> issues = new ArrayList<>();
        if (update.sizeBytes() <= 0) issues.add(DownloadIssue.SIZE_INVALID);
        else if (update.sizeBytes() > PACKAGE_LIMIT) issues.add(DownloadIssue.SIZE_LIMIT);
        if (update.filename().isBlank()) issues.add(DownloadIssue.FILENAME_MISSING);
        else if (!update.filename().endsWith(".zip")) issues.add(DownloadIssue.FILENAME_NOT_ZIP);
        if (update.sha256().isBlank()) issues.add(DownloadIssue.SHA256_MISSING);
        else if (!update.sha256().matches("[0-9a-fA-F]{64}")) issues.add(DownloadIssue.SHA256_FORMAT);

        var uri = update.downloadUri();
        if (uri == null || uri.getScheme() == null) {
            issues.add(DownloadIssue.URL_INVALID);
        } else {
            if (!"https".equalsIgnoreCase(uri.getScheme())) issues.add(DownloadIssue.URL_SCHEME);
            if (!isDownloadHostname(uri.getHost())) issues.add(DownloadIssue.URL_HOST);
            if (uri.getPort() != -1 && uri.getPort() != 443) issues.add(DownloadIssue.URL_PORT);
            if (uri.getPath() == null || !uri.getPath().startsWith("/package/")) issues.add(DownloadIssue.URL_PATH);
            if (uri.getUserInfo() != null || uri.getFragment() != null) issues.add(DownloadIssue.URL_COMPONENTS);
        }
        return issues;
    }

    private static boolean isDownloadHostname(String host) {
        if (host == null) return false;
        // The matching Lenovo HTTPS manifest supplies the CDN and the expected SHA-256.
        String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        int separator = name.lastIndexOf('.');
        if (separator <= 0) return false;
        String suffix = name.substring(separator + 1).toLowerCase(Locale.ROOT);
        return suffix.matches("[a-z]{2,63}") && !LOCAL_SUFFIXES.contains(suffix);
    }

    static File download(File cache, LenovoFirmwareClient.Update update, IntConsumer progress) throws IOException {
        if (!hasVerifiedDownloadMetadata(update)) throw new InvalidPackage("固件下载信息不完整");
        File partial = File.createTempFile("pen-ota-", ".zip", cache);
        boolean finished = false;
        try {
            var connection = (HttpsURLConnection) update.downloadUri().toURL().openConnection();
            try {
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(15_000);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (connection.getResponseCode() != 200) throw new IOException("Lenovo CDN returned a non-200 reply");
                try (var input = connection.getInputStream(); var output = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int lastPercent = -1;
                    for (int n; (n = input.read(buffer)) != -1;) {
                        total += n;
                        if (total > update.sizeBytes()) throw new InvalidPackage("固件大小与联想清单不符");
                        output.write(buffer, 0, n);
                        int percent = (int) (total * 100 / update.sizeBytes());
                        if (percent != lastPercent) progress.accept(lastPercent = percent);
                    }
                }
            } finally {
                connection.disconnect();
            }
            verify(partial, update);
            finished = true;
            return partial;
        } finally {
            if (!finished) Files.deleteIfExists(partial.toPath());
        }
    }

    static void verify(File file, LenovoFirmwareClient.Update update) throws IOException {
        if (!file.isFile() || file.length() != update.sizeBytes() || file.length() > PACKAGE_LIMIT) {
            throw new InvalidPackage("固件大小与联想清单不符");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unexpected) {
            throw new IllegalStateException("SHA-256 unavailable", unexpected);
        }
        try (var input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        var hex = new StringBuilder(64);
        for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        if (!hex.toString().equalsIgnoreCase(update.sha256())) {
            throw new InvalidPackage("固件 SHA-256 与联想清单不符");
        }
        verifyArchive(file);
    }

    private static void verifyArchive(File file) throws IOException {
        try (var archive = new ZipFile(file)) {
            ZipEntry entry = checkedEntry(archive, "manifest.json", MANIFEST_LIMIT);
            try (var input = archive.getInputStream(entry)) {
                byte[] metadata = input.readNBytes(MANIFEST_LIMIT + 1);
                if (metadata.length > MANIFEST_LIMIT) throw new InvalidPackage("DFU 清单过大");
                JSONObject manifest = new JSONObject(new String(metadata, StandardCharsets.UTF_8))
                        .getJSONObject("manifest");
                if (manifest.length() != 1 || !manifest.has("application")) {
                    throw new InvalidPackage("固件包与已验证的应用固件类型不符");
                }
                JSONObject application = manifest.getJSONObject("application");
                String image = application.getString("bin_file");
                String init = application.getString("dat_file");
                if (!image.matches("[A-Za-z0-9._-]+\\.bin") || !init.matches("[A-Za-z0-9._-]+\\.dat")) {
                    throw new InvalidPackage("DFU 清单包含无效文件名");
                }
                checkedEntry(archive, image, PACKAGE_LIMIT);
                checkedEntry(archive, init, MANIFEST_LIMIT);
            }
        } catch (JSONException | java.util.zip.ZipException invalid) {
            throw new InvalidPackage("无法读取联想 DFU 固件包");
        }
    }

    private static ZipEntry checkedEntry(ZipFile archive, String name, int limit) throws InvalidPackage {
        ZipEntry entry = archive.getEntry(name);
        if (entry == null || entry.isDirectory() || entry.getSize() <= 0 || entry.getSize() > limit
                || archive.stream().filter(item -> item.getName().equals(name)).count() != 1) {
            throw new InvalidPackage("DFU 包缺少有效的清单、固件或初始化文件");
        }
        return entry;
    }
}
