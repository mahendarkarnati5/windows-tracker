//package com.tracker.server.repository;
//
//import java.util.List;
//import java.util.Optional;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import com.tracker.server.entity.DeviceSession;
//
//public interface DeviceSessionRepository
//extends JpaRepository<DeviceSession, Long> {
//
//Optional<DeviceSession>
//findTopByDeviceIdAndStatusOrderByIdDesc(
//    Long deviceId,
//    String status);
//
//List<DeviceSession>
//findByDeviceId(Long deviceId);
//
////List<DeviceSession>
////findByDeviceIdOrderByIdDesc(
////        Long deviceId);
//
//List<DeviceSession>
//findByDeviceUserIdOrderByIdDesc(Long userId);
//
////List<DeviceSession>
////findByUserIdOrderByIdDesc(
////        Long userId);
//}
//
//


package com.tracker.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.tracker.server.entity.DeviceSession;

@Repository
public interface DeviceSessionRepository extends JpaRepository<DeviceSession, Long>, JpaSpecificationExecutor<DeviceSession> {
//    List<DeviceSession> findByDeviceId(Long deviceId);
    List<DeviceSession> findByUserId(Long userId);

    Optional<DeviceSession>
    findTopByDeviceIdAndStatusOrderByIdDesc(
            Long deviceId,
            String status);

    List<DeviceSession>
    findByDeviceId(
            Long deviceId);

    List<DeviceSession>
    findByDeviceIdOrderByIdDesc(
            Long deviceId);

    List<DeviceSession>
    findByUserIdOrderByIdDesc(
            Long userId);
    
    List<DeviceSession> findByStatus(String status);
    List<DeviceSession> findByDeviceIdAndStatus(Long deviceId,String status);
}
