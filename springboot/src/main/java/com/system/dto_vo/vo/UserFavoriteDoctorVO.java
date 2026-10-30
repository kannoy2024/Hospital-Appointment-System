package com.system.dto_vo.vo;

import com.system.entity.UserFavoriteDoctor;
import lombok.Data;

@Data
public class UserFavoriteDoctorVO {

    private Long id;          // 收藏记录ID
    private Long doctorId;    // 医生ID
    private DoctorVO doctor;  // 医生信息

    public static UserFavoriteDoctorVO fromEntity(UserFavoriteDoctor e) {
        UserFavoriteDoctorVO vo = new UserFavoriteDoctorVO();
        vo.setId(e.getId());
        vo.setDoctorId(e.getDoctorId());
        return vo;
    }
}