package art.pilipala.api.admin.infra;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code pilipala.admin.*} 的配置。
 *
 * @param bootstrapUsernames 初始管理员用户名列表（去空白小写比较）：命中者在注册或登录时自动升为 admin
 */
@ConfigurationProperties(prefix = "pilipala.admin")
public record AdminProperties(Set<String> bootstrapUsernames) {
    public AdminProperties {
        bootstrapUsernames = bootstrapUsernames == null
                ? Set.of()
                : bootstrapUsernames.stream()
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    /** 该用户名是否在引导名单里。 */
    public boolean isBootstrapAdmin(String username) {
        return username != null && bootstrapUsernames.contains(username.trim().toLowerCase(Locale.ROOT));
    }
}
