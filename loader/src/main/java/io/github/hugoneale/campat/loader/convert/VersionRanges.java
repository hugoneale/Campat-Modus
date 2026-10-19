package io.github.hugoneale.campat.loader.convert;

/** Minimal Maven version range matching, enough for mods.toml ranges such as {@code [1.20,1.21)}. */
public final class VersionRanges {
    private VersionRanges() {}

    public static boolean contains(String spec, String version) {
        spec = spec.strip();
        if (spec.isEmpty() || spec.equals("*")) return true;
        if (spec.charAt(0) != '[' && spec.charAt(0) != '(') return compare(spec, version) <= 0; // "1.20" means >= 1.20 (soft)
        int i = 0;
        while (i < spec.length()) {
            char open = spec.charAt(i);
            int close = indexOfAny(spec, i + 1);
            if (close < 0) return false;
            String body = spec.substring(i + 1, close);
            char end = spec.charAt(close);
            if (matches(open, body, end, version)) return true;
            i = close + 1;
            while (i < spec.length() && (spec.charAt(i) == ',' || spec.charAt(i) == ' ')) i++;
        }
        return false;
    }

    private static int indexOfAny(String s, int from) {
        for (int i = from; i < s.length(); i++) if (s.charAt(i) == ']' || s.charAt(i) == ')') return i;
        return -1;
    }

    private static boolean matches(char open, String body, char end, String v) {
        int comma = body.indexOf(',');
        if (comma < 0) return compare(body.strip(), v) == 0;
        String lo = body.substring(0, comma).strip(), hi = body.substring(comma + 1).strip();
        if (!lo.isEmpty()) {
            int c = compare(lo, v);
            if (open == '[' ? c > 0 : c >= 0) return false;
        }
        if (!hi.isEmpty()) {
            int c = compare(v, hi);
            if (end == ']' ? c > 0 : c >= 0) return false;
        }
        return true;
    }

    /** numeric, dot separated comparison; trailing qualifiers are ignored */
    static int compare(String a, String b) {
        String[] x = a.split("[.\\-+]"), y = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        int n = 0, i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) n = n * 10 + (s.charAt(i++) - '0');
        return n;
    }
}
