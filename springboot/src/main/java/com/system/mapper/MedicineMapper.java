package com.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.system.entity.Medicine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MedicineMapper extends BaseMapper<Medicine> {

    /** 开方预扣库存：stock 不足时返回 0，由业务层回滚事务。 */
    @Update("UPDATE medicine SET stock = stock - #{qty}, update_time = NOW() " +
            "WHERE id = #{id} AND stock >= #{qty}")
    int tryDecrementStock(@Param("id") Long id, @Param("qty") int qty);

    /** 取消/重开处方时归还库存。 */
    @Update("UPDATE medicine SET stock = stock + #{qty}, update_time = NOW() WHERE id = #{id}")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
