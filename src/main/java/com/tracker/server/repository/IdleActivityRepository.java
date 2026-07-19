//package com.tracker.server.repository;
//
//import java.util.List;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import com.tracker.server.entity.IdleActivity;
//
//@Repository
//public interface IdleActivityRepository
//        extends JpaRepository<IdleActivity, Long> {
//	 List<IdleActivity> findByDeviceId(Long deviceId);
//	 
//	 @Query("""
//		       SELECT COALESCE(
//		           SUM(i.idleSeconds),
//		           0
//		       )
//		       FROM IdleActivity i
//		       WHERE i.user.id = :userId
//		       """)
//		Long getTotalIdleTime(
//		        @Param("userId")
//		        Long userId);
//}



package com.tracker.server.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tracker.server.entity.IdleActivity;

@Repository
public interface IdleActivityRepository extends JpaRepository<IdleActivity, Long>, JpaSpecificationExecutor<IdleActivity> {
    // existing methods..
    List<IdleActivity>
    findByDeviceIdOrderByIdDesc(
            Long deviceId);

//    List<IdleActivity>
//    findByUser_IdOrderByIdDesc(
//            Long userId);

    @Query("""
           SELECT COALESCE(
               SUM(i.idleSeconds),
               0
           )
           FROM IdleActivity i
           WHERE i.user.id = :userId
           """)
    Long getTotalIdleTime(
            @Param("userId")
            Long userId);
    
    
    
    List<IdleActivity> findByDeviceIdAndStatus(
            Long deviceId,
            String status);
}