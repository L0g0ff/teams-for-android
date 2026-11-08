package nl.wapnet.teamswrapper;

import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Download file names. Android's URLUtil.guessFileName ignores RFC 5987 filename*= (used by SharePoint). */
final class FileName {
    private static final Pattern EXTENDED =
            Pattern.compile("filename\\*\\s*=\\s*([\\w-]+)'[^']*'([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN =
            Pattern.compile("filename\\s*=\\s*(\"([^\"]*)\"|[^;]+)", Pattern.CASE_INSENSITIVE);

    /** File name from a Content-Disposition header, or null. Prefers filename*= over filename=. */
    static String fromContentDisposition(String header) {
        if (header == null) {
            return null;
        }
        String name = null;
        Matcher m = EXTENDED.matcher(header);
        if (m.find()) {
            try {
                // String charset overload: the Charset one needs API 33.
                name = URLDecoder.decode(m.group(2).trim().replace("+", "%2B"),
                        m.group(1).equalsIgnoreCase("UTF-8") ? "UTF-8" : "ISO-8859-1");
            } catch (IllegalArgumentException | java.io.UnsupportedEncodingException e) {
                name = null;
            }
        }
        if (name == null) {
            m = PLAIN.matcher(header);
            if (m.find()) {
                name = m.group(2) != null ? m.group(2) : m.group(1).trim();
            }
        }
        return safe(name);
    }

    /** Replaces the extension when it does not match the MIME type (e.g. download.aspx for a PDF). */
    static String withExtension(String name, String extension) {
        if (extension == null || extension.isEmpty()) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String current = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return current.equals(extension.toLowerCase(Locale.ROOT)) ? name : base + "." + extension;
    }

    /** Strips directories and control characters so a header cannot pick another folder. */
    private static String safe(String name) {
        if (name == null) {
            return null;
        }
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "").trim();
        return name.isEmpty() || name.equals(".") || name.equals("..") ? null : name;
    }

    public static void main(String[] args) {
        assert "Offerte 2026 é.pdf".equals(fromContentDisposition(
                "attachment; filename=\"Offerte 2026 e.pdf\"; filename*=UTF-8''Offerte%202026%20%C3%A9.pdf"));
        assert "a+b.pdf".equals(fromContentDisposition("attachment; filename*=UTF-8''a+b.pdf"));
        assert "report.pdf".equals(fromContentDisposition("attachment; filename=\"report.pdf\""));
        assert "report.pdf".equals(fromContentDisposition("inline; filename=report.pdf"));
        assert "passwd".equals(fromContentDisposition("attachment; filename=\"../../etc/passwd\""));
        assert "x.pdf".equals(fromContentDisposition("attachment; filename*=UTF-8''..%2F..%5Cx.pdf"));
        assert fromContentDisposition("attachment") == null;
        assert fromContentDisposition("attachment; filename=\"..\"") == null;
        assert fromContentDisposition(null) == null;
        assert "download.pdf".equals(withExtension("download.aspx", "pdf"));
        assert "report.PDF".equals(withExtension("report.PDF", "pdf"));
        assert "file.docx".equals(withExtension("file", "docx"));
        assert "download.aspx".equals(withExtension("download.aspx", null));
    }

    private FileName() {}
}
