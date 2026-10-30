package com.system.dto_vo.vo;

import com.system.entity.User;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private Integer gender;
    private String avatar;
    private Long balanceFen;
    private Integer status;
    private String role;
    private Long doctorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;

    public static UserVO fromEntity(User user) {

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }


}
