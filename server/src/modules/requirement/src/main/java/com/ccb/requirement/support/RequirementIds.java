package com.ccb.requirement.support;

import java.util.concurrent.ThreadLocalRandom;

/** 业务主键生成器：时间戳 + 随机后缀，避免跨表主键冲突。 */
public final class RequirementIds {
    private RequirementIds() {
    }

    public static long next() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
