package com.simxe.vireon.web.internal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;

/** 内部协议判定，不是消费者 SPI，也不负责选择或改写响应表示。 */
public final class VireonResponsePolicy {

    private VireonResponsePolicy() {}

    public static boolean bodyForbidden(String method, int status) {
        return "HEAD".equalsIgnoreCase(method)
                || (status >= 100 && status < 200)
                || status == 204 || status == 205 || status == 304;
    }

    public static boolean isJson(MediaType type) {
        return type != null && "application".equalsIgnoreCase(type.getType())
                && ("json".equalsIgnoreCase(type.getSubtype())
                        || type.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json"));
    }

    public static boolean accepts(List<String> headerValues, MediaType candidate) {
        if (candidate == null || !candidate.isConcrete()) {
            return false;
        }
        if (headerValues == null || headerValues.isEmpty() || headerValues.stream().allMatch(String::isBlank)) {
            return true;
        }
        try {
            Specificity best = null;
            double quality = 0;
            for (MediaType range : MediaType.parseMediaTypes(headerValues)) {
                double q = range.getQualityValue();
                if (!Double.isFinite(q) || q < 0 || q > 1) {
                    return false;
                }
                if (!range.includes(candidate) || !parametersMatch(range, candidate)) {
                    continue;
                }
                Specificity specificity = new Specificity(
                        range.isWildcardType() ? 0 : 1,
                        "*".equals(range.getSubtype()) ? 0 : range.isWildcardSubtype() ? 1 : 2,
                        (int) range.getParameters().keySet().stream().filter(key -> !"q".equalsIgnoreCase(key)).count());
                int comparison = best == null ? 1 : specificity.compareTo(best);
                if (comparison > 0) {
                    best = specificity;
                    quality = q;
                } else if (comparison == 0) {
                    // 同等具体度的冲突采用保守拒绝，与字段顺序无关。
                    quality = Math.min(quality, q);
                }
            }
            return best != null && quality > 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean parametersMatch(MediaType range, MediaType candidate) {
        for (Map.Entry<String, String> entry : range.getParameters().entrySet()) {
            String name = entry.getKey();
            if ("q".equalsIgnoreCase(name)) {
                continue;
            }
            if ("charset".equalsIgnoreCase(name)) {
                var actual = candidate.getCharset();
                if (actual == null && isJson(candidate)) {
                    actual = StandardCharsets.UTF_8;
                }
                if (!range.getCharset().equals(actual)) {
                    return false;
                }
            } else if (!unquote(entry.getValue()).equals(unquote(candidate.getParameter(name)))) {
                return false;
            }
        }
        return true;
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return value;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < value.length() - 1; i++) {
            char character = value.charAt(i);
            if (character == '\\' && i < value.length() - 2) {
                character = value.charAt(++i);
            }
            result.append(character);
        }
        return result.toString();
    }

    private record Specificity(int type, int subtype, int parameters) implements Comparable<Specificity> {
        @Override
        public int compareTo(Specificity other) {
            int comparison = Integer.compare(type, other.type);
            if (comparison == 0) {
                comparison = Integer.compare(subtype, other.subtype);
            }
            return comparison == 0 ? Integer.compare(parameters, other.parameters) : comparison;
        }
    }
}
