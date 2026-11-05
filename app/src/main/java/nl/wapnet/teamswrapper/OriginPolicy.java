package nl.wapnet.teamswrapper;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

final class OriginPolicy {
    private static final Set<String> MEDIA_HOSTS =
            Set.of("teams.cloud.microsoft", "teams.microsoft.com", "teams.live.com");
    private static final Set<String> SETTINGS_LINKS =
            Set.of("ms-settings:privacy-webcam", "ms-settings:privacy-microphone");

    /** Only Teams' own HTTPS origins may use the camera and microphone. */
    static boolean mayCapture(String origin) {
        try {
            URI uri = new URI(origin);
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && MEDIA_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (Exception e) {
            return false;
        }
    }

    /** Origin rules for scripts that must only run on Teams' media origins. */
    static Set<String> mediaOrigins() {
        Set<String> origins = new java.util.HashSet<>();
        for (String host : MEDIA_HOSTS) {
            origins.add("https://" + host);
        }
        return origins;
    }

    static boolean isWindowsSettingsLink(String url) {
        return url.toLowerCase(Locale.ROOT).startsWith("ms-settings:");
    }

    /** Windows privacy links Teams shows for camera or microphone; opened as this app's Android settings. */
    static boolean isMediaSettingsLink(String url) {
        return SETTINGS_LINKS.contains(url.toLowerCase(Locale.ROOT));
    }

    public static void main(String[] args) {
        assert mayCapture("https://teams.cloud.microsoft/");
        assert mayCapture("https://teams.microsoft.com");
        assert mayCapture("https://TEAMS.microsoft.com:443/");
        assert !mayCapture("http://teams.microsoft.com/");
        assert !mayCapture("https://teams.microsoft.com.evil.example/");
        assert !mayCapture("https://evil.example/teams.microsoft.com");
        assert !mayCapture("https://teams.microsoft.com:8443/");
        assert !mayCapture("https://login.microsoftonline.com/");
        assert !mayCapture("not a uri");
        assert isMediaSettingsLink("ms-settings:privacy-webcam");
        assert isMediaSettingsLink("MS-SETTINGS:privacy-microphone");
        assert !isMediaSettingsLink("ms-settings:network");
        assert isWindowsSettingsLink("ms-settings:network");
        assert !isWindowsSettingsLink("https://teams.cloud.microsoft/");
        for (String origin : mediaOrigins()) {
            assert mayCapture(origin);
        }
    }

    private OriginPolicy() {}
}
