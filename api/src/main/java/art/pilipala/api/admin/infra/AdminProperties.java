package art.pilipala.api.admin.infra;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code pilipala.admin.*} 配置。WP17 起支持 bootstrap-phones / bootstrap-emails；
 * bootstrap-usernames 保留一期双读。
 */
@ConfigurationProperties(prefix = "pilipala.admin")
public record AdminProperties(
        Set<String> bootstrapUsernames,
        Set<String> bootstrapPhones,
        Set<String> bootstrapEmails) {

    public AdminProperties {
        bootstrapUsernames = normalizeSet(bootstrapUsernames, true);
        bootstrapPhones = normalizePhones(bootstrapPhones);
        bootstrapEmails = normalizeSet(bootstrapEmails, true);
    }

    public boolean isBootstrapAdminUsername(String username) {
        return username != null && bootstrapUsernames.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isBootstrapAdminPhone(String phoneE164) {
        return phoneE164 != null && bootstrapPhones.contains(phoneE164.trim());
    }

    public boolean isBootstrapAdminEmail(String email) {
        return email != null && bootstrapEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> normalizeSet(Set<String> raw, boolean lower) {
        if (raw == null) {
            return Set.of();
        }
        return raw.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> lower ? s.toLowerCase(Locale.ROOT) : s)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> normalizePhones(Set<String> raw) {
        if (raw == null) {
            return Set.of();
        }
        return raw.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(phone -> phone.startsWith("+") ? phone : "+86" + phone.replaceFirst("^86", ""))
                .collect(Collectors.toUnmodifiableSet());
    }
}
