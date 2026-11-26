package com.alyssum.relegion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public class LenovoFirmwareClientTest {
    private static final byte[] PNP_ID = {2, (byte) 0xef, 0x17, 0x7f, 0x61, 0, 1};
    private static final LenovoFirmwareClient.DeviceIds IDS = LenovoFirmwareClient.DeviceIds.fromPnpId(PNP_ID);
    private static final String MODEL = "LenovoTabPenPlus_PICASSO-CD1_0x617F_0x17EF_TB710FU_prc";
    private static final LenovoFirmwareClient.Pen PEN = LenovoFirmwareClient.fromGatt(
            "TEST+1&2", "PICASSO-CD1", "PICASSO V1.05", "Lenovo Tab Pen Plus", IDS, "TB710FU",
            LenovoFirmwareClient.resolveRegion(null, "TB710FU", "TB710FU_CN_OPEN_USER_BUILD"));
    private static final String CONFIG = """
            {"update_packages":[],"update_packages_data":[
              {"data_package_name":"LenovoTabPenPlus_Test","version_code":107,"ignored":"config only"}
            ]}
            """;
    private static final String MANIFEST = """
            <firmwareupdate>
              <packageappdatas>
                <datanum>1</datanum>
                <packageappdata>
                  <datapackagename>LenovoTabPenPlus_Test</datapackagename>
                  <versioncode>107</versioncode><versionname>1.07</versionname>
                  <size>120000</size><filename>test-package.zip</filename>
                  <sha256>aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa</sha256>
                  <downloadurl>https://ota-cdn.lenovo.com/package/test.zip</downloadurl>
                </packageappdata>
              </packageappdatas>
            </firmwareupdate>
            """;

    @Test public void realModelAndGattVersionSupplyTheCheck() {
        assertNotNull(PEN);
        assertEquals(MODEL, PEN.model());
        assertEquals("V1.05", PEN.version());
        assertEquals("LenovoTabPenPlus", PEN.packageNamePart());
        assertEquals("V1.05", LenovoFirmwareClient.firmwareVersion("V1.05"));
        assertEquals("V1.05", LenovoFirmwareClient.firmwareVersion("PICASSO     V1.05"));
        assertNull(LenovoFirmwareClient.fromGatt("", "PICASSO", "PICASSO V1.05", "Lenovo Tab Pen Plus", IDS, "TB710FU", "PRC"));
        assertNull(LenovoFirmwareClient.fromGatt("TEST", "", "PICASSO V1.05", "Lenovo Tab Pen Plus", IDS, "TB710FU", "PRC"));
        assertNull(LenovoFirmwareClient.fromGatt("TEST", "PICASSO", "unknown", "Lenovo Tab Pen Plus", IDS, "TB710FU", "PRC"));
    }

    @Test public void requiresTheActualPenPnpIdForOta() {
        assertEquals(0x17ef, IDS.vendor());
        assertEquals(0x617f, IDS.product());
        assertNull(LenovoFirmwareClient.DeviceIds.fromPnpId(new byte[]{2, 0, 0, 1, 2}));
        for (var ids : new LenovoFirmwareClient.DeviceIds[]{null, new LenovoFirmwareClient.DeviceIds(1, 0x617f)}) {
            assertNull(LenovoFirmwareClient.fromGatt(
                    "TEST", "PICASSO", "V1.05", "Lenovo Tab Pen Plus", ids, "TB710FU", "PRC"));
        }
    }

    @Test public void matchesTheCaseSensitiveZuiRegion() {
        assertEquals("prc", LenovoFirmwareClient.resolveRegion("PRC", "TB710FU", ""));
        assertEquals("row", LenovoFirmwareClient.resolveRegion("row", "TB710FU", "TB710FU_CN_OPEN_USER_BUILD"));
        assertEquals("unknown", LenovoFirmwareClient.resolveRegion(null, "TB710FU", "TB710FU_ROW_OPEN_USER_BUILD"));
        assertEquals("unknown", LenovoFirmwareClient.resolveRegion(null, "TB323FU", "TB323FU_CN_OPEN_USER_BUILD"));
    }

    @Test public void followsConfigAndManifestEndpointsWithoutDownloading() throws Exception {
        List<String> called = new ArrayList<>();
        var update = LenovoFirmwareClient.check(PEN, (path, query, body) -> {
            called.add(path);
            assertEquals("ChecksumType=sha256&locale=en&curfirmwarever=TB-styluspen_USR_S000001_20211018_V1"
                    + "&deviceid=TEST%2B1%262&devicemodel=" + MODEL + "&action=querynewfirmwar", query);
            if (path.equals("/engine/config")) {
                assertNull(body);
                return CONFIG;
            }
            assertEquals("/engine/upgrade", path);
            var data = new JSONObject(body).getJSONArray("update_packages_data").getJSONObject(0);
            assertEquals("LenovoTabPenPlus_Test", data.getString("data_package_name"));
            assertEquals(107, data.getInt("version_code"));
            assertFalse(data.has("ignored"));
            return MANIFEST;
        });
        assertEquals(List.of("/engine/config", "/engine/upgrade"), called);
        assertTrue(update.available());
        assertEquals("V1.07", update.version());
        assertEquals(120000, update.sizeBytes());
        assertEquals("LenovoTabPenPlus_Test", update.packageName());
        assertEquals(64, update.sha256().length());
    }

    @Test public void bindsManifestToConfigPackageAndVersion() {
        for (String manifest : List.of(
                MANIFEST.replace("LenovoTabPenPlus_Test", "LenovoTabPenPlus_Other"),
                MANIFEST.replace(">107<", ">109<"),
                MANIFEST.replace(">1.07<", ">1.99<"))) {
            assertThrows(LenovoFirmwareClient.PackageMismatch.class, () -> LenovoFirmwareClient.check(
                    PEN, (path, query, body) -> body == null ? CONFIG : manifest));
        }
    }

    @Test public void checksVersionEvenWithoutVerifiableDownloadMetadata() throws Exception {
        for (String manifest : List.of(
                MANIFEST.replaceAll("<sha256>[^<]*</sha256>", ""),
                MANIFEST.replace("https://ota-cdn.lenovo.com", "http://ota-cdn.lenovo.com"),
                MANIFEST.replace("ota-cdn.lenovo.com", "ota-cdn.lenovo.com.example.test"))) {
            var update = LenovoFirmwareClient.check(PEN, (path, query, body) -> body == null ? CONFIG : manifest);
            assertEquals("V1.07", update.version());
            assertFalse(LenovoFirmwarePackage.hasVerifiedDownloadMetadata(update));
        }
    }

    @Test public void acceptsAlternateHttpsCdnFromTheMatchingLenovoManifest() throws Exception {
        String manifest = MANIFEST.replace("ota-cdn.lenovo.com", "downloads.vendor-cloud.net");
        var update = LenovoFirmwareClient.check(PEN, (path, query, body) -> body == null ? CONFIG : manifest);
        assertEquals("downloads.vendor-cloud.net", update.downloadUri().getHost());
        assertTrue(LenovoFirmwarePackage.hasVerifiedDownloadMetadata(update));
    }

    @Test public void queriesOtherLenovoPensAndPreservesStockPaladinRoutes() {
        var lenovo = new LenovoFirmwareClient.DeviceIds(0x17ef, 0x612b);
        var nec = new LenovoFirmwareClient.DeviceIds(0x0409, 0x068d);
        assertEquals("PALADIN_PRC_WIFI", LenovoFirmwareClient.fromGatt("test", "PALADIN", "V1.04",
                "Lenovo Precision Pen", lenovo, "Lenovo TB-Q706F", "prc").model());
        assertEquals("ONYX_ROW_WIFI", LenovoFirmwareClient.fromGatt("test", "PALADIN", "V1.04",
                "Lenovo Precision Pen", lenovo, "TB132FU", "row").model());
        assertEquals("PALADIN_NEC", LenovoFirmwareClient.fromGatt("test", "PALADIN", "V1.04",
                "デジタルペン2", nec, "LAVIE Tab T12 12QHD1", "unknown").model());
        var alternate = LenovoFirmwareClient.fromGatt("test", "PARKER-V3", "V1.12",
                "Lenovo Tab Pen Pro", new LenovoFirmwareClient.DeviceIds(0x17ef, 0x622e), "TB710FU", "prc");
        assertEquals("LenovoTabPenPro_PARKER-V3_0x622E_0x17EF_TB710FU_prc", alternate.model());
        assertTrue(new LenovoFirmwareClient.DeviceIds(0x17ef, 0x6262).supportsQuery());
    }

    @Test public void skipsManifestForCurrentVersionOrEmptyConfig() throws Exception {
        for (String config : List.of(CONFIG.replace("107", "105"), "{\"update_packages_data\":[]}")) {
            var result = LenovoFirmwareClient.check(PEN, (path, query, body) -> {
                assertEquals("/engine/config", path);
                return config;
            });
            assertFalse(result.available());
        }
    }

    @Test public void rejectsPackageFromDifferentPenBeforeManifestRequest() {
        assertThrows(LenovoFirmwareClient.PackageMismatch.class,
                () -> LenovoFirmwareClient.check(PEN, (path, query, body) -> {
                    if (!path.equals("/engine/config")) fail("Incompatible package reached the manifest endpoint");
                    return CONFIG.replace("LenovoTabPenPlus_Test", "LenovoTabPenPro_Test");
                }));
    }

    @Test public void rejectsMalformedAndExternalEntityManifests() {
        for (String manifest : List.of("<html/>", "<!DOCTYPE firmwareupdate SYSTEM 'https://example.invalid/test'>" + MANIFEST)) {
            assertThrows(IOException.class, () -> LenovoFirmwareClient.check(
                    PEN, (path, query, body) -> body == null ? CONFIG : manifest));
        }
    }

    @Test public void distinguishesAnXmlServerErrorFromInvalidJson() {
        String reply = "<Error><Code>FUS-0112</Code><Message>Invalid device model</Message></Error>";
        var error = assertThrows(LenovoFirmwareClient.Failure.class,
                () -> LenovoFirmwareClient.check(PEN, (path, query, body) -> reply));
        assertEquals(LenovoFirmwareClient.Stage.CONFIG, error.stage);
        assertEquals(LenovoFirmwareClient.Reason.SERVER, error.reason);
        assertEquals("FUS-0112", error.detail);
        assertFalse(error.getMessage().contains(PEN.serial()));

        var manifest = assertThrows(LenovoFirmwareClient.Failure.class, () -> LenovoFirmwareClient.check(
                PEN, (path, query, body) -> body == null ? CONFIG : reply));
        assertEquals(LenovoFirmwareClient.Stage.MANIFEST, manifest.stage);
        assertEquals(LenovoFirmwareClient.Reason.SERVER, manifest.reason);
    }

    @Test public void reportsTheRequestStageWithoutLeakingTransportMessages() {
        var dns = assertThrows(LenovoFirmwareClient.Failure.class,
                () -> LenovoFirmwareClient.check(PEN, (path, query, body) -> {
                    throw new UnknownHostException(query);
                }));
        assertEquals(LenovoFirmwareClient.Stage.CONFIG, dns.stage);
        assertEquals(LenovoFirmwareClient.Reason.DNS, dns.reason);
        assertFalse(dns.getMessage().contains("deviceid"));

        var timeout = assertThrows(LenovoFirmwareClient.Failure.class,
                () -> LenovoFirmwareClient.check(PEN, (path, query, body) -> {
                    if (body == null) return CONFIG;
                    throw new SocketTimeoutException(query);
                }));
        assertEquals(LenovoFirmwareClient.Stage.MANIFEST, timeout.stage);
        assertEquals(LenovoFirmwareClient.Reason.TIMEOUT, timeout.reason);
    }
}
