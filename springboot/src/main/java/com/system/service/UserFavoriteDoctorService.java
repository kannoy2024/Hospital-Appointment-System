package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.dto.DoctorSaveRequest;
import com.system.dto_vo.vo.UserFavoriteDoctorVO;

import java.util.List;

public interface UserFavoriteDoctorService {

    /** 我的收藏医生列表（仅在职医生，按收藏时间倒序） */
    List<UserFavoriteDoctorVO> listMine();

    /** 收藏医生*/
    UserFavoriteDoctorVO add(Long doctorId);

    /** 取消收藏 */
    void remove(Long doctorId);

    /** 该医生当前用户是否已收藏（医生详情页点亮爱心用） */
    boolean isFavorited(Long doctorId);
}
