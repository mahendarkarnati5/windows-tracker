//package com.tracker.server.repository;
//
//import java.util.List;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.stereotype.Repository;
//
//import com.tracker.server.entity.ActiveWindowActivity;
//
//@Repository
//public interface ActiveWindowActivityRepository
//        extends JpaRepository<ActiveWindowActivity, Long> {
//	List<ActiveWindowActivity> findByDeviceId(Long deviceId);
//}

package com.tracker.server.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tracker.server.entity.ActiveWindowActivity;

import jakarta.transaction.Transactional;

@Repository
public interface ActiveWindowActivityRepository extends JpaRepository<ActiveWindowActivity, Long>, JpaSpecificationExecutor<ActiveWindowActivity> {
    
    List<ActiveWindowActivity>
    findByDeviceIdOrderByIdDesc(
            Long deviceId);

//    List<ActiveWindowActivity>
//    findByUser_IdOrderByIdDesc(
//            Long userId);
    
    Optional<ActiveWindowActivity> findByOfflineId(String offlineId);
    List<ActiveWindowActivity> findByDeviceIdAndStatus(
            Long deviceId,
            String status);
    
    Optional<ActiveWindowActivity>
    findFirstByDeviceIdAndWindowTitleAndStatusOrderByStartTimeDesc(
            Long deviceId,
            String windowTitle,
            String status);
    @Modifying
    @Transactional
    @Query("""
    update ActiveWindowActivity a
    set a.status='CLOSED',
        a.endTime=:end
    where a.device.id=:deviceId
    and a.status='RUNNING'
    """)
    void closeRunning(@Param("deviceId") Long deviceId,
                      @Param("end") LocalDateTime end);
}