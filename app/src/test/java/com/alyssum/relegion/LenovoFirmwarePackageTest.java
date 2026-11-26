package com.alyssum.relegion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LenovoFirmwarePackageTest {
    private static final String PACKAGE = "com.lenovo.topaz_prc_wifi_LenovoTabPenPlus-CD1";
    private static final String MANIFEST =
            "{\"manifest\":{\"application\":{\"bin_file\":\"firmware.bin\",\"dat_file\":\"firmware.dat\"}}}";
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test public void diagnosticsDistinguishMissingFieldsFromDownloadUriPolicy() {
        var missing = new LenovoFirmwareClient.Update("V2.30", 0, PACKAGE, "", "", null);
        assertEquals(List.of(LenovoFirmwarePackage.DownloadIssue.SIZE_INVALID,
                LenovoFirmwarePackage.DownloadIssue.FILENAME_MISSING,
                LenovoFirmwarePackage.DownloadIssue.SHA256_MISSING,
                LenovoFirmwarePackage.DownloadIssue.URL_INVALID), LenovoFirmwarePackage.downloadIssues(missing));

        var rejected = new LenovoFirmwareClient.Update("V2.30", 1, PACKAGE, "x.zip", "a".repeat(64),
                URI.create("http://other.example:8080/files/img.zip?token=private"));
        assertEquals(List.of(LenovoFirmwarePackage.DownloadIssue.URL_SCHEME,
                LenovoFirmwarePackage.DownloadIssue.URL_HOST, LenovoFirmwarePackage.DownloadIssue.URL_PORT,
                LenovoFirmwarePackage.DownloadIssue.URL_PATH), LenovoFirmwarePackage.downloadIssues(rejected));
        assertTrue(LenovoFirmwarePackage.downloadIssues(update(260711, "a".repeat(64), "V2.30", PACKAGE)).isEmpty());
    }

    @Test public void rejectsLocalNamesAndIpLiterals() {
        for (String host : List.of("localhost", "firmware.local", "service.home.arpa", "127.0.0.1", "[::1]")) {
            var update = new LenovoFirmwareClient.Update("V2.30", 1, PACKAGE, "x.zip", "a".repeat(64),
                    URI.create("https://" + host + "/package/img.zip"));
            assertEquals(List.of(LenovoFirmwarePackage.DownloadIssue.URL_HOST),
                    LenovoFirmwarePackage.downloadIssues(update));
        }
    }

    @Test public void onlyBindsKnownCd1RouteToItsOfficialPackage() {
        var ids = new LenovoFirmwareClient.DeviceIds(0x17ef, 0x617f);
        var cd1 = LenovoFirmwareClient.fromGatt("test", "PICASSO-CD1", "V1.11", "Lenovo Tab Pen Plus", ids, "TB710FU", "prc");
        var otherHardware = LenovoFirmwareClient.fromGatt("test", "PICASSO", "V1.11", "Lenovo Tab Pen Plus", ids, "TB710FU", "prc");
        String hash = "a".repeat(64);
        assertTrue(LenovoFirmwarePackage.matches(cd1, update(1, hash, "V2.30", PACKAGE)));
        assertFalse(LenovoFirmwarePackage.matches(cd1, update(1, "", "V2.30", PACKAGE)));
        assertFalse(LenovoFirmwarePackage.matches(otherHardware, update(1, hash, "V2.30", PACKAGE)));
        assertFalse(LenovoFirmwarePackage.matches(cd1, update(1, hash, "V2.30", PACKAGE.replace("-CD1", ""))));
        assertFalse(LenovoFirmwarePackage.matches(cd1, update(1, hash, "V1.09", PACKAGE)));
    }

    @Test public void validatesLengthAndSha256BeforeDfu() throws Exception {
        File archive = archive(MANIFEST);
        byte[] original = Files.readAllBytes(archive.toPath());
        var update = update(original.length, sha256(original).toUpperCase(java.util.Locale.ROOT), "V2.30", PACKAGE);
        LenovoFirmwarePackage.verify(archive, update);
        byte[] shortened = java.util.Arrays.copyOf(original, original.length - 1);
        Files.write(archive.toPath(), shortened);
        assertThrows(LenovoFirmwarePackage.InvalidPackage.class, () -> LenovoFirmwarePackage.verify(archive, update));
        original[4] ^= 1;
        Files.write(archive.toPath(), original);
        assertThrows(LenovoFirmwarePackage.InvalidPackage.class, () -> LenovoFirmwarePackage.verify(archive, update));
    }

    @Test public void rejectsFirmwareWithWrongDfuArchiveLayoutEvenWhenHashMatches() throws Exception {
        for (String manifest : new String[] {MANIFEST.replace("firmware.dat", "../firmware.dat"),
                MANIFEST.replace("application", "bootloader")}) {
            File archive = archive(manifest);
            byte[] bytes = Files.readAllBytes(archive.toPath());
            var update = update(bytes.length, sha256(bytes), "V2.30", PACKAGE);
            assertThrows(LenovoFirmwarePackage.InvalidPackage.class, () -> LenovoFirmwarePackage.verify(archive, update));
        }
    }

    private File archive(String manifest) throws Exception {
        File file = folder.newFile();
        try (var zip = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            entry(zip, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            entry(zip, "firmware.bin", new byte[] { 1, 2, 3 });
            entry(zip, "firmware.dat", new byte[] { 4, 5 });
        }
        return file;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static LenovoFirmwareClient.Update update(long size, String hash, String version, String name) {
        return new LenovoFirmwareClient.Update(version, size, name, "Picasso_CD1.zip", hash,
                URI.create("https://ota-cdn.lenovo.com/package/test.zip"));
    }
}
