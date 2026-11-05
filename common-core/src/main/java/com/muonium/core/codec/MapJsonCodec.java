package com.muonium.core.codec;

import com.muonium.core.json.Json;
import com.muonium.core.spi.Ports;

import java.util.Map;

/** {@link Ports.JsonCodec} 的纯 JVM 默认实现。平台可替换为自身的实现。 */
public final class MapJsonCodec implements Ports.JsonCodec {

    @Override
    public String stringify(Map<String, Object> value) {
        return Json.stringify(value);
    }

    @Override
    public Map<String, Object> parse(String text) {
        return Json.parseObject(text);
    }
}
