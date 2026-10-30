package com.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.system.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /** 乐观锁占号：仅当已预约数小于总号源时加 1，返回 0 表示已满。 */
    @Update("UPDATE schedule SET booked_count = booked_count + 1, update_time = NOW() " +
            "WHERE id = #{id} AND status = 1 AND booked_count < total_quota")
    int tryIncrementBookedCount(@Param("id") Long id);

    /** 取消预约时释放号源，不低于 0。 */
    @Update("UPDATE schedule SET booked_count = GREATEST(booked_count - 1, 0), update_time = NOW() " +
            "WHERE id = #{id}")
    int decrementBookedCount(@Param("id") Long id);
}
