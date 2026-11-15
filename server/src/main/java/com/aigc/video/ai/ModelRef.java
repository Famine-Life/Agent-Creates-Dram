package com.aigc.video.ai;

/** 模型复合标识 provider/model，如 ark/doubao-seed-evolving */
public record ModelRef(String provider, String model) {

    public static ModelRef parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        int slash = text.indexOf('/');
        if (slash <= 0 || slash == text.length() - 1) {
            throw new IllegalArgumentException("模型标识需为 provider/model 格式：" + value);
        }
        return new ModelRef(text.substring(0, slash), text.substring(slash + 1));
    }

    public String key() {
        return provider + "/" + model;
    }
}
