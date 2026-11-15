package com.aigc.video.api.dto;

/** 触发 AI 生成时的可选提示词与模型 */
public record GenerateRequest(String prompt, String model) {
}
