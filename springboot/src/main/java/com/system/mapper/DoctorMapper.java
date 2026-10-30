package com.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.system.entity.Doctor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DoctorMapper extends BaseMapper<Doctor> {

    @Select("SELECT DISTINCT d.id, d.department_id, d.name, d.title, d.gender, d.avatar, d.intro, d.status, d.create_time " +
            "FROM doctor d INNER JOIN schedule s ON s.doctor_id = d.id " +
            "WHERE d.status = 1 AND s.status = 1 AND s.schedule_date >= CURDATE() AND d.deleted = 0 " +
            "AND s.booked_count < s.total_quota " +
            "AND (#{departmentId} IS NULL OR d.department_id = #{departmentId}) " +
            "ORDER BY d.id")
    List<Doctor> selectActiveWithAvailableSlots(@Param("departmentId") Long departmentId);
}
