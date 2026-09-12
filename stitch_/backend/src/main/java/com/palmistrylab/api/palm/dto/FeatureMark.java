package com.palmistrylab.api.palm.dto;

/**
 * 印记及其判定来源：deterministic_hash（图哈希确定性兜底）；
 * 感知边车不做印记 CV 识别（TD §13.1 范围约定）。
 */
public record FeatureMark(
    String name,
    String source) {
}
