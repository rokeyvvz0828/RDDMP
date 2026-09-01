package com.ccb.architecture.change.persistence;

import com.ccb.architecture.change.model.SubsystemNumberReleaseReason;
import com.ccb.architecture.change.model.SubsystemNumberRequest;
import com.ccb.architecture.change.model.SubsystemNumberReservation;

import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * 编号保留的持久化边界。
 *
 * <p>实现必须在真实数据库事务内工作：先锁定 namespace，再在同一锁范围内依次尝试候选 ordinal，
 * 调用 {@code candidateAvailable} 过滤已发布 code 或其他永久占用项；不能在首个冲突时直接失败。
 * 同一 application/line/kind 必须幂等返回既有保留，并在获得 namespace 锁后再次校验，避免并发重复分配。</p>
 */
public interface SubsystemNumberReservationStore {

    Optional<SubsystemNumberReservation> findByApplicationLine(SubsystemNumberRequest request);

    SubsystemNumberReservation reserve(SubsystemNumberRequest request, IntPredicate candidateAvailable);

    /** 只接受 REJECTED/CANCELLED；实现将未发布保留放回所属 namespace 的回收池。 */
    void release(SubsystemNumberReservation reservation, SubsystemNumberReleaseReason reason);

    /** APPROVED 后消费活动保留，且绝不放回回收池。 */
    void consume(SubsystemNumberReservation reservation);

    /** 检查所有 tenant 的已发布逻辑/物理主记录，防止候选 code 与历史 code 冲突。 */
    boolean isPublishedCodeOccupied(String code);
}
