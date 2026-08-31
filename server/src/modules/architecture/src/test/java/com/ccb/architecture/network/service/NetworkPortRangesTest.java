package com.ccb.architecture.network.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkPortRangesTest {
    @Test
    void 合并端口区间并判断完全覆盖() {
        NetworkPortRanges covering = NetworkPortRanges.parse("443, 8443-8445,8446");

        assertThat(covering.containsAll(NetworkPortRanges.parse("443,8444-8446"))).isTrue();
        assertThat(covering.containsAll(NetworkPortRanges.parse("443,8444-8447"))).isFalse();
    }

    @Test
    void 非法端口直接拒绝解析() {
        assertThatThrownBy(() -> NetworkPortRanges.parse("0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NetworkPortRanges.parse("65536")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NetworkPortRanges.parse("9000-8000")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NetworkPortRanges.parse("443,tcp")).isInstanceOf(IllegalArgumentException.class);
    }
}
