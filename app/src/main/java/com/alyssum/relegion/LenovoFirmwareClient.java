package com.alyssum.relegion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Two-stage Lenovo OTA lookup. The manifest ties the package identity to its SHA-256. */
final class LenovoFirmwareClient {
    private static final String ORIGIN = "https://ota.lenovo.com";
    private static final int RESPONSE_LIMIT = 256 * 1024;
    private static final int TIMEOUT_MS = 15_000;

    record Pen(String serial, String model, String version, String packageNamePart, String hardware, DeviceIds ids) {}

    record DeviceIds(int vendor, int product) {
        static DeviceIds fromPnpId(byte[] value) {
            if (value.length != 7) return null;
            return new DeviceIds((value[1] & 0xff) | (value[2] & 0xff) << 8,
                    (value[3] & 0xff) | (value[4] & 0xff) << 8);
        }

        boolean supportsQuery() { return vendor == 0x17ef && product > 0 || vendor == 0x0409 && product == 0x068d; }
    }

    record Update(String version, long sizeBytes, String packageName, String filename, String sha256, URI downloadUri) {
        static final Update CURRENT = new Update("", 0, "", "", "", null);
        boolean available() { return !version.isEmpty(); }
    }

    @FunctionalInterface
    interface Transport {
        String request(String path, String query, String jsonBody) throws IOException;
    }

    static final class PackageMismatch extends IOException {}

    enum Stage { CONFIG, MANIFEST }

    enum Reason { DNS, CONNECTION, TIMEOUT, TLS, HTTP, SERVER, RESPONSE, IO }

    static final class Failure extends IOException {
        final Stage stage;
        final Reason reason;
        final String detail;

        Failure(Stage stage, Reason reason, String detail) {
            super("Lenovo OTA " + stage + ": " + reason + (detail.isEmpty() ? "" : " (" + detail + ")"));
            this.stage = stage;
            this.reason = reason;
            this.detail = detail;
        }
    }

    private static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status) { this.status = status; }
    }

    private LenovoFirmwareClient() {}

    static String resolveRegion(String configuredRegion, String tabletModel, String vendorBuild) {
        if (configuredRegion != null && !configuredRegion.isBlank() && !configuredRegion.equals("unknown")) {
            return "prc".equalsIgnoreCase(configuredRegion) ? "prc" : configuredRegion;
        }
        // The Lenovo OTA model key is case sensitive. The TB710FU CN vendor build uses ZUI's prc suffix.
        if ("TB710FU".equals(tabletModel) && vendorBuild.startsWith("TB710FU_CN_")) return "prc";
        return "unknown";
    }

    static Pen fromGatt(String serial, String hardware, String firmware, String bluetoothName,
                        DeviceIds ids, String tabletModel, String region) {
        String name = bluetoothName.replace(" ", "");
        String version = firmwareVersion(firmware);
        if (serial.isBlank() || hardware.isBlank() || name.isBlank() || version.isEmpty()
                || ids == null || !ids.supportsQuery() || tabletModel.isBlank() || region.isBlank()) return null;
        // The standard Lenovo OTA route uses the physical tablet identity, even under a port.
        String model = String.format(Locale.ROOT, "%s_%s_0x%04X_0x%04X_%s_%s",
                name, hardware, ids.product(), ids.vendor(), tabletModel.replace(" ", ""), region);
        if ("PALADIN".equals(hardware)) {
            String legacyModel = paladinRoute(bluetoothName, ids, tabletModel, region);
            if (!legacyModel.isEmpty()) model = legacyModel;
        }
        return new Pen(serial, model, version, name, hardware, ids);
    }

    /** The original Lenovo client still uses these keys for the first PALADIN hardware revision. */
    private static String paladinRoute(String name, DeviceIds ids, String tabletModel, String region) {
        if ("LAVIE Tab T12 12QHD1".equals(tabletModel) && "デジタルペン2".equals(name)
                && ids.vendor() == 0x0409 && ids.product() == 0x068d) return "PALADIN_NEC";
        if (!name.contains("Lenovo Precision Pen") || ids.vendor() != 0x17ef || ids.product() != 0x612b) return "";
        String market = region.toLowerCase(Locale.ROOT).contains("prc") ? "PRC" : "ROW";
        return switch (tabletModel) {
            case "Lenovo TB-Q706Z" -> "PALADIN_" + market + "_LTE";
            case "Lenovo TB-Q706F" -> "PALADIN_" + market + "_WIFI";
            case "TB132FU" -> "ONYX_" + market + "_WIFI";
            default -> "";
        };
    }

    static String firmwareVersion(String firmware) {
        for (String field : firmware.strip().split("\\s+")) {
            if (field.matches("[Vv]?[0-9]+(?:\\.[0-9]+)+")) {
                return "V" + field.replaceFirst("^[Vv]", "");
            }
        }
        return "";
    }

    static Update check(Pen pen) throws IOException {
        return check(pen, LenovoFirmwareClient::request);
    }

    static int compareVersions(String a, String b) {
        var left = a.substring(1).split("\\.");
        var right = b.substring(1).split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            BigInteger l = i < left.length ? new BigInteger(left[i]) : BigInteger.ZERO;
            BigInteger r = i < right.length ? new BigInteger(right[i]) : BigInteger.ZERO;
            int comparison = l.compareTo(r);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    static Update check(Pen pen, Transport transport) throws IOException {
        // The app sends a fixed protocol baseline, then compares against the GATT version locally.
        String query = "ChecksumType=sha256&locale=en&curfirmwarever=TB-styluspen_USR_S000001_20211018_V1"
                + "&deviceid=" + URLEncoder.encode(pen.serial(), StandardCharsets.UTF_8)
                + "&devicemodel=" + URLEncoder.encode(pen.model(), StandardCharsets.UTF_8)
                + "&action=querynewfirmwar";
        String response = fetch(transport, Stage.CONFIG, query, null);
        JSONObject body;
        String packageName;
        int versionCode;
        String expectedVersion;
        try {
            if (response.stripLeading().startsWith("<")) {
                rejectServerError(readXml(response), Stage.CONFIG);
                throw new IOException("Expected OTA JSON configuration");
            }
            JSONObject config = new JSONObject(response);
            JSONArray packages = config.getJSONArray("update_packages_data");
            if (packages.length() == 0) return Update.CURRENT;

            JSONObject candidate = packages.getJSONObject(packages.length() - 1);
            packageName = candidate.getString("data_package_name");
            if (!packageName.contains(pen.packageNamePart())) {
                throw new PackageMismatch();
            }
            versionCode = candidate.getInt("version_code");
            String code = Integer.toString(versionCode);
            if (!code.matches("[0-9]{2,}")) throw new IOException("Invalid OTA version code");
            expectedVersion = "V" + code.charAt(0) + "." + code.substring(1);
            if (compareVersions(pen.version(), expectedVersion) >= 0) return Update.CURRENT;

            body = new JSONObject();
            body.put("update_packages", config.getJSONArray("update_packages"));
            JSONArray packageData = new JSONArray();
            for (int i = 0; i < packages.length(); i++) {
                JSONObject entry = packages.getJSONObject(i);
                JSONObject data = new JSONObject();
                data.put("data_package_name", entry.getString("data_package_name"));
                data.put("version_code", entry.getInt("version_code"));
                packageData.put(data);
            }
            body.put("update_packages_data", packageData);
        } catch (Failure | PackageMismatch error) {
            throw error;
        } catch (JSONException | IOException error) {
            throw new Failure(Stage.CONFIG, Reason.RESPONSE, "");
        }
        String manifest = fetch(transport, Stage.MANIFEST, query, body.toString());
        try {
            return readManifest(manifest, packageName, versionCode, expectedVersion);
        } catch (Failure | PackageMismatch error) {
            throw error;
        } catch (IOException error) {
            throw new Failure(Stage.MANIFEST, Reason.RESPONSE, "");
        }
    }

    private static String fetch(Transport transport, Stage stage, String query, String body) throws Failure {
        try {
            return transport.request(stage == Stage.CONFIG ? "/engine/config" : "/engine/upgrade", query, body);
        } catch (HttpFailure error) {
            throw new Failure(stage, Reason.HTTP, Integer.toString(error.status));
        } catch (UnknownHostException error) {
            throw new Failure(stage, Reason.DNS, "");
        } catch (SocketTimeoutException error) {
            throw new Failure(stage, Reason.TIMEOUT, "");
        } catch (SSLException error) {
            throw new Failure(stage, Reason.TLS, "");
        } catch (ConnectException error) {
            throw new Failure(stage, Reason.CONNECTION, "");
        } catch (IOException error) {
            throw new Failure(stage, Reason.IO, error.getClass().getSimpleName());
        }
    }

    private static Element readXml(String xml) throws IOException {
        if (xml.contains("<!DOCTYPE")) throw new IOException("Unsupported OTA XML declaration");
        try {
            var parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            parser.setErrorHandler(new DefaultHandler());
            parser.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External OTA XML entities are forbidden");
            });
            return parser.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        } catch (ParserConfigurationException | SAXException error) {
            throw new IOException("Invalid OTA XML");
        }
    }

    private static void rejectServerError(Element root, Stage stage) throws Failure {
        if (!"Error".equals(root.getTagName())) return;
        Element element = child(root, "Code");
        String code = element == null ? "" : element.getTextContent().strip();
        throw new Failure(stage, Reason.SERVER, code.matches("FUS-[0-9]{4}") ? code : "");
    }

    private static Update readManifest(String xml, String packageName, int versionCode, String expectedVersion)
            throws IOException {
        Element root = readXml(xml);
        rejectServerError(root, Stage.MANIFEST);
        try {
            if (!"firmwareupdate".equals(root.getTagName())) throw new IOException("Invalid OTA XML root");
            Element collection = child(root, "packageappdatas");
            if (collection == null) throw new IOException("Missing OTA package list");
            Element count = child(collection, "datanum");
            if (child(collection, "packageappdata") == null
                    && count != null && "0".equals(count.getTextContent().strip())) {
                return Update.CURRENT;
            }
            Element entry = null;
            for (Node node = collection.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element item && "packageappdata".equals(item.getTagName())
                        && packageName.equals(text(item, "datapackagename"))
                        && Integer.toString(versionCode).equals(text(item, "versioncode"))) {
                    if (entry != null) throw new IOException("Duplicate firmware package entry");
                    entry = item;
                }
            }
            if (entry == null) throw new PackageMismatch();
            String version = text(entry, "versionname");
            if (!version.matches("V?[0-9]+(?:\\.[0-9]+)+")) throw new IOException("Invalid OTA package version");
            if (!version.startsWith("V")) version = "V" + version;
            if (compareVersions(version, expectedVersion) != 0) throw new PackageMismatch();
            return new Update(version, packageSize(text(entry, "size")), packageName, text(entry, "filename"),
                    text(entry, "sha256"), packageUri(text(entry, "downloadurl")));
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid Lenovo OTA manifest");
        }
    }

    private static long packageSize(String value) {
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    private static URI packageUri(String value) {
        try {
            return value.isEmpty() ? null : URI.create(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String text(Element element, String name) {
        Element value = child(element, name);
        return value == null ? "" : value.getTextContent().strip();
    }

    private static Element child(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) return element;
        }
        return null;
    }

    private static String request(String path, String query, String body) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(ORIGIN + path + "?" + query)
                .toURL().openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (var out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new HttpFailure(status);
            try (InputStream input = connection.getInputStream(); var output = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                for (int n; (n = input.read(chunk)) != -1;) {
                    if (output.size() + n > RESPONSE_LIMIT) throw new IOException("OTA response exceeds limit");
                    output.write(chunk, 0, n);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
