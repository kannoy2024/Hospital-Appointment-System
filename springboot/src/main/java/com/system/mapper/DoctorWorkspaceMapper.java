package com.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface DoctorWorkspaceMapper {

    @Select("""
            SELECT COUNT(*)
            FROM appointment a
            INNER JOIN schedule s ON a.schedule_id = s.id
            WHERE s.doctor_id = #{doctorId}
              AND s.schedule_date = #{today}
              AND a.status NOT IN ('cancelled', 'unpaid')
            """)
    Long countTodayAppointments(@Param("doctorId") Long doctorId, @Param("today") LocalDate today);

    @Select("""
            SELECT COUNT(*)
            FROM appointment a
            INNER JOIN schedule s ON a.schedule_id = s.id
            WHERE s.doctor_id = #{doctorId}
              AND s.schedule_date = #{today}
              AND a.status IN ('pending', 'confirmed', 'checked_in', 'in_consultation', 'pending_rx_pay')
            """)
    Long countTodayPending(@Param("doctorId") Long doctorId, @Param("today") LocalDate today);

    @Select("""
            SELECT COUNT(*)
            FROM appointment a
            INNER JOIN schedule s ON a.schedule_id = s.id
            WHERE s.doctor_id = #{doctorId}
              AND a.status = 'completed'
            """)
    Long countCompleted(@Param("doctorId") Long doctorId);
}
