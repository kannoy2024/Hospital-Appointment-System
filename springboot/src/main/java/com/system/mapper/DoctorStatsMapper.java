package com.system.mapper;

import com.system.dto_vo.vo.DoctorMetricRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DoctorStatsMapper {

    @Select("SELECT doctor_id AS doctorId, ROUND(AVG(rating), 1) AS avgRating, COUNT(*) AS reviewCount " +
            "FROM appointment_review GROUP BY doctor_id")
    List<DoctorMetricRow> selectReviewStats();

    @Select("SELECT s.doctor_id AS doctorId, COUNT(*) AS consultCount " +
            "FROM appointment a " +
            "INNER JOIN schedule s ON a.schedule_id = s.id " +
            "WHERE a.status = 'completed' " +
            "GROUP BY s.doctor_id")
    List<DoctorMetricRow> selectConsultCounts();
}
