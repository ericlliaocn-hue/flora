package art.pilipala.api.settings.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import art.pilipala.api.settings.domain.AuthRuntimeKeys;
import art.pilipala.api.settings.infra.AppRuntimeSettingMapper;
import art.pilipala.api.settings.infra.AppRuntimeSettingMapper.AppRuntimeSettingRow;
import org.springframework.stereotype.Service;

@Service
public class RuntimeSettingService {

    private final AppRuntimeSettingMapper mapper;

    public RuntimeSettingService(AppRuntimeSettingMapper mapper) {
        this.mapper = mapper;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        AppRuntimeSettingRow row = mapper.findByKey(key);
        if (row == null || row.value() == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(row.value().trim());
    }

    /**
     * 读字符串配置。缺行或 value 为 null 时回退默认值；空字符串是合法存值（例如隐藏角标）。
     */
    public String getString(String key, String defaultValue) {
        AppRuntimeSettingRow row = mapper.findByKey(key);
        if (row == null || row.value() == null) {
            return defaultValue;
        }
        return row.value();
    }

    public Map<String, String> listAll() {
        List<AppRuntimeSettingRow> rows = mapper.findAll();
        Map<String, String> out = new LinkedHashMap<>();
        for (AppRuntimeSettingRow row : rows) {
            out.put(row.key(), row.value());
        }
        return out;
    }

    public Map<String, String> patch(Map<String, String> updates) {
        Map<String, String> changed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            if (!AuthRuntimeKeys.KNOWN_KEYS.contains(key)) {
                continue;
            }
            String normalized = normalizeBoolean(entry.getValue());
            mapper.upsert(key, normalized);
            changed.put(key, normalized);
        }
        return changed;
    }

    private static String normalizeBoolean(String raw) {
        if (raw == null) {
            return "false";
        }
        return "true".equalsIgnoreCase(raw.trim()) ? "true" : "false";
    }
}
