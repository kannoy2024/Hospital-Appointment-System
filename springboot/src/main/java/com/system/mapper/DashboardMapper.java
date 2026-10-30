package com.system.mapper;

import com.system.dto_vo.vo.ChartItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;


//轮播图有关的查询内容，
@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM appointment " +
            "WHERE create_time >= CURDATE() AND create_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY) " +
            "AND status IN ('pending', 'confirmed', 'completed')")
    Long countTodayAppointments();

    @Select("SELECT COUNT(*) FROM appointment")
    Long countTotalAppointments();

    @Select("SELECT COUNT(*) FROM doctor WHERE status = 1 AND deleted = 0")
    Long countActiveDoctors();

    @Select("SELECT COUNT(*) FROM sys_user WHERE role = 'USER' AND deleted = 0")
    Long countPatients();

    @Select("""
            SELECT DATE(create_time) AS day, COUNT(*) AS cnt
            FROM appointment
            WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE(create_time)
            ORDER BY day
            """)
    List<Map<String, Object>> selectAppointmentTrendRaw();

    @Select("SELECT status AS name, COUNT(*) AS value FROM appointment GROUP BY status")
    List<ChartItemVO> selectStatusDistribution();

    @Select("SELECT dept.name AS name, COUNT(*) AS value " +
            "FROM appointment a " +
            "INNER JOIN schedule s ON a.schedule_id = s.id " +
            "INNER JOIN doctor d ON s.doctor_id = d.id AND d.deleted = 0 " +
            "INNER JOIN department dept ON d.department_id = dept.id " +
            "GROUP BY dept.id, dept.name " +
            "ORDER BY value DESC " +
            "LIMIT 8")
    List<ChartItemVO> selectDepartmentTop();

    @Select("SELECT CAST(rating AS CHAR) AS name, COUNT(*) AS value " +
            "FROM appointment_review GROUP BY rating ORDER BY rating")
    List<ChartItemVO> selectRatingDistribution();
}
