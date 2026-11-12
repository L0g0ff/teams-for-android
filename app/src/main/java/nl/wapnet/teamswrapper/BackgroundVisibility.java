package nl.wapnet.teamswrapper;

import androidx.webkit.JavaScriptReplyProxy;

/** Reports the Activity state to Teams through the origin-restricted bridge. */
final class BackgroundVisibility {
    static final String BRIDGE = "TeamsAndroidLifecycle";

    private boolean backgrounded;
    private JavaScriptReplyProxy page;

    void connected(String message, boolean mainFrame, JavaScriptReplyProxy reply) {
        if (!mainFrame || !"ready".equals(message)) {
            return;
        }
        page = reply;
        reply.postMessage(String.valueOf(backgrounded));
    }

    void setBackgrounded(boolean value) {
        backgrounded = value;
        if (page != null) {
            page.postMessage(String.valueOf(value));
        }
    }
}
