package nl.wapnet.teamswrapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UserAgent {
    private static final Pattern CHROME = Pattern.compile("Chrome/([0-9.]+)");

    static String desktop(String webViewUserAgent) {
        Matcher chrome = CHROME.matcher(webViewUserAgent);
        if (!chrome.find()) {
            return webViewUserAgent;
        }
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/" + chrome.group(1) + " Safari/537.36";
    }

    public static void main(String[] args) {
        String mobile = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Version/4.0 Chrome/142.0.7444.48 Mobile Safari/537.36";
        String desktop = desktop(mobile);
        assert desktop.contains("Chrome/142.0.7444.48");
        assert desktop.contains("X11; Linux x86_64");
        assert !desktop.contains("Android");
        assert !desktop.contains("Mobile");
    }

    private UserAgent() {}
}
