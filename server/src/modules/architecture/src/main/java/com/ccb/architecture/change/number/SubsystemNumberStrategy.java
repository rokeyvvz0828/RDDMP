package com.ccb.architecture.change.number;

import com.ccb.architecture.change.model.SubsystemNumberReleaseReason;
import com.ccb.architecture.change.model.SubsystemNumberRequest;
import com.ccb.architecture.change.model.SubsystemNumberReservation;

/** 面向业务服务的编号策略接口，不绑定 HTTP、Spring 或具体数据库。 */
public interface SubsystemNumberStrategy {
    SubsystemNumberReservation reserve(SubsystemNumberRequest request);

    void release(SubsystemNumberReservation reservation, SubsystemNumberReleaseReason reason);

    void consume(SubsystemNumberReservation reservation);
}
