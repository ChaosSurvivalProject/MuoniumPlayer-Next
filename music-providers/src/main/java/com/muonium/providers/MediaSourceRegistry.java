package com.muonium.providers;

import com.muonium.core.spi.Ports;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 音乐源注册表：按名字选择来源（local / qq）。
 *
 * <p>工厂以 {@link Supplier} 延迟构造，避免未用到 QQ 音乐时也强制要求 Cookie。
 * 游戏内播放器不持有此注册表；它仅由本机导入器 / 桌面工具 / Fabric 集成在用户主动触发时调用。
 */
public final class MediaSourceRegistry {

    private final Map<String, Supplier<Ports.MediaSource>> factories = new LinkedHashMap<>();
    private String defaultName = "local";

    public MediaSourceRegistry register(String name, Supplier<Ports.MediaSource> factory) {
        factories.put(name, factory);
        return this;
    }

    public Ports.MediaSource get(String name) {
        Supplier<Ports.MediaSource> f = factories.get(name);
        if (f == null) throw new ProviderException("未知音乐源: " + name + "（可选: " + names() + "）");
        return f.get();
    }

    public String defaultName() { return defaultName; }

    public MediaSourceRegistry setDefault(String name) {
        if (!factories.containsKey(name)) throw new ProviderException("默认源不存在: " + name);
        this.defaultName = name;
        return this;
    }

    public Set<String> names() { return new LinkedHashSet<>(factories.keySet()); }

    public boolean contains(String name) { return factories.containsKey(name); }
}
