package nl.wapnet.teamswrapper;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OriginPolicy {
    private static final Set<String> TEAMS_HOSTS =
            Set.of("teams.cloud.microsoft", "teams.microsoft.com", "teams.live.com");
    // Teams chat, channel and meeting links (/l/...) and short meeting links (/meet/...).
    private static final Pattern TEAMS_LINK =
            Pattern.compile("https://([^/?#:@]+)(?::443)?/(?:l|meet)/.*", Pattern.CASE_INSENSITIVE);
    // Microsoft sign-in may open in a new window; it must stay in-app to keep the session.
    private static final Set<String> SIGN_IN_HOSTS = Set.of(
            "login.microsoftonline.com", "login.microsoft.com", "login.live.com", "login.windows.net");
    private static final Set<String> EXTERNAL_SCHEMES = Set.of("http", "https", "mailto", "tel");
    private static final Set<String> SETTINGS_LINKS =
            Set.of("ms-settings:privacy-webcam", "ms-settings:privacy-microphone");

    /** Only Teams' own HTTPS origins may use the camera and microphone. */
    static boolean mayCapture(String origin) {
        try {
            URI uri = new URI(origin);
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && TEAMS_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (Exception e) {
            return false;
        }
    }

    /** New windows on Teams or Microsoft sign-in open in the app; everything else does not. */
    static boolean opensInApp(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(uri.getScheme()) && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (TEAMS_HOSTS.contains(host) || SIGN_IN_HOSTS.contains(host));
        } catch (Exception e) {
            return false;
        }
    }

    /** HTTPS SharePoint/OneDrive for Business host (tenant.sharepoint.com, tenant-my.sharepoint.com). */
    static boolean isSharePoint(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(uri.getScheme()) && uri.getRawUserInfo() == null
                    && host.endsWith(".sharepoint.com");
        } catch (Exception e) {
            return false;
        }
    }

    /** Schemes safe to hand to Android (browser, mail, dialer). intent:, file:, javascript: etc. are not. */
    static boolean opensExternally(String url) {
        try {
            String scheme = new URI(url).getScheme();
            return scheme != null && EXTERNAL_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }

    /** Origin rules for scripts and bridges that must only run on Teams origins. */
    static Set<String> teamsOrigins() {
        Set<String> origins = new java.util.HashSet<>();
        for (String host : TEAMS_HOSTS) {
            origins.add("https://" + host);
        }
        return origins;
    }

    /**
     * Turns a link from another app into the HTTPS Teams URL to load, or null if it is not a
     * Teams link. Accepts https Teams links, msteams://host/l/... and legacy msteams:/l/...
     */
    static String deepLink(String link) {
        if (link == null || link.isEmpty() || link.contains("\\") || !link.equals(link.strip())
                || link.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        String url = link;
        if (link.regionMatches(true, 0, "msteams:", 0, 8)) {
            String rest = link.substring(8);
            url = rest.startsWith("//") ? "https:" + rest
                    : rest.startsWith("/") ? "https://teams.microsoft.com" + rest
                    : null;
        }
        if (url == null) {
            return null;
        }
        Matcher m = TEAMS_LINK.matcher(url);
        return m.matches() && TEAMS_HOSTS.contains(m.group(1).toLowerCase(Locale.ROOT)) ? url : null;
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
        String chat = "https://teams.microsoft.com/l/chat/0/0?users=alice@example.com";
        assert chat.equals(deepLink(chat));
        String meeting = "https://teams.microsoft.com/l/meetup-join/19%3ameeting_x%40thread.v2/0?context=%7b%7d";
        assert meeting.equals(deepLink(meeting));
        assert deepLink("https://teams.live.com/meet/9312345?p=abc") != null;
        assert deepLink("https://TEAMS.cloud.microsoft:443/l/chat/0/0") != null;
        assert "https://teams.microsoft.com/l/chat/0/0".equals(deepLink("msteams://teams.microsoft.com/l/chat/0/0"));
        assert "https://teams.microsoft.com/l/meetup-join/x".equals(deepLink("msteams:/l/meetup-join/x"));
        assert "https://teams.microsoft.com/l/x".equals(deepLink("MSTEAMS:/l/x"));
        assert deepLink("http://teams.microsoft.com/l/chat/0/0") == null;
        assert deepLink("https://teams.microsoft.com/") == null;
        assert deepLink("https://teams.microsoft.com/v2/") == null;
        assert deepLink("https://teams.microsoft.com.evil.example/l/chat") == null;
        assert deepLink("https://teams.microsoft.com@evil.example/l/chat") == null;
        assert deepLink("https://evil.example/l/chat?x=teams.microsoft.com") == null;
        assert deepLink("https://evil.example\\@teams.microsoft.com/l/chat") == null;
        assert deepLink("https://teams.microsoft.com:8443/l/chat") == null;
        assert deepLink("msteams://evil.example/l/chat") == null;
        assert deepLink("msteams:evil") == null;
        assert deepLink("javascript:alert(1)") == null;
        assert deepLink("https://teams.microsoft.com/l/chat x") == null;
        assert deepLink(null) == null;
        assert opensInApp("https://teams.microsoft.com/v2/");
        assert opensInApp("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?x=1");
        assert opensInApp("https://LOGIN.live.com/");
        assert !opensInApp("https://example.com/");
        assert !opensInApp("https://teams.microsoft.com.evil.example/");
        assert !opensInApp("https://evil.example@teams.microsoft.com/");
        assert !opensInApp("http://teams.microsoft.com/");
        assert !opensInApp("https://teams.microsoft.com:8443/");
        assert !opensInApp("https://contoso.sharepoint.com/file.docx");
        assert !opensInApp("not a uri");
        assert isSharePoint("https://enableyourworld-my.sharepoint.com/personal/x/_layouts/15/download.aspx?UniqueId=1");
        assert isSharePoint("https://Contoso.SharePoint.com/sites/a");
        assert !isSharePoint("http://contoso.sharepoint.com/");
        assert !isSharePoint("https://sharepoint.com.evil.example/");
        assert !isSharePoint("https://evilsharepoint.com/");
        assert !isSharePoint("https://contoso.sharepoint.com@evil.example/");
        assert !isSharePoint("not a uri");
        assert opensExternally("https://example.com/page");
        assert opensExternally("http://example.com/");
        assert opensExternally("mailto:alice@example.com");
        assert opensExternally("TEL:+31201234567");
        assert !opensExternally("intent://scan/#Intent;scheme=zxing;end");
        assert !opensExternally("javascript:alert(1)");
        assert !opensExternally("file:///data/x");
        assert !opensExternally("content://x/y");
        assert !opensExternally("data:text/html,x");
        assert !opensExternally("ms-settings:network");
        assert !opensExternally("not a uri");
        for (String origin : teamsOrigins()) {
            assert mayCapture(origin);
        }
    }

    private OriginPolicy() {}
}
