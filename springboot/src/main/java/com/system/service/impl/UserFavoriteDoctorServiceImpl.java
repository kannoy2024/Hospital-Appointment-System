package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.system.common.BusinessException;
import com.system.dto_vo.vo.DoctorVO;
import com.system.dto_vo.vo.UserFavoriteDoctorVO;
import com.system.entity.Doctor;
import com.system.entity.UserFavoriteDoctor;
import com.system.mapper.DoctorMapper;
import com.system.mapper.UserFavoriteDoctorMapper;
import com.system.security.SecurityUtils;
import com.system.service.DoctorService;
import com.system.service.UserFavoriteDoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

//用户收藏医生实现类

@Service
@RequiredArgsConstructor
public class UserFavoriteDoctorServiceImpl implements UserFavoriteDoctorService {

    private final UserFavoriteDoctorMapper userFavoriteDoctorMapper;
    private final DoctorMapper doctorMapper;
    private final DoctorService doctorService;

    @Override
    public List<UserFavoriteDoctorVO> listMine() {
        //取出当前登录用户id
        Long currentUserId = currentUserId();

        //取出list列表
        List<UserFavoriteDoctor> userFavoriteDoctors = userFavoriteDoctorMapper.selectList(new LambdaQueryWrapper<UserFavoriteDoctor>()
                .eq(UserFavoriteDoctor::getUserId, currentUserId)
                .orderByDesc(UserFavoriteDoctor::getCreateTime));

        //转换
        return userFavoriteDoctors.stream()
                .map(this::toVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    //收藏医生
    @Override
    @Transactional
    public UserFavoriteDoctorVO add(Long doctorId) {
        Long currentUserId = currentUserId();
        //检查将要收藏的医生的状态
        requireActiveDoctor(doctorId);

        //防止连续收藏
        UserFavoriteDoctor userFavoriteDoctor = selectMine(currentUserId, doctorId);
        if (userFavoriteDoctor != null) {
            return toVO(userFavoriteDoctor);
        }

        UserFavoriteDoctor favorite = new UserFavoriteDoctor();
        favorite.setDoctorId(doctorId);
        favorite.setUserId(currentUserId);
        try {
            userFavoriteDoctorMapper.insert(favorite);
        }catch (DuplicateKeyException e) {
            return toVO(selectMine(currentUserId, doctorId));
        }
        return toVO(favorite);

    }


    //取消收藏
    @Override
    @Transactional
    public void remove(Long doctorId) {
    //检查将要取消的医生id是否是当前登录用户所收藏过的
        UserFavoriteDoctor userFavoriteDoctor = selectMine(currentUserId(), doctorId);
        if (userFavoriteDoctor == null) {
            return;
        }
        userFavoriteDoctorMapper.deleteById(userFavoriteDoctor.getId());

    }



    //检查是否收藏过
    @Override
    public boolean isFavorited(Long doctorId) {
        return selectMine(currentUserId(), doctorId) != null;
    }

    //私有方法


    /**
     * 取当前登录用户 ID。
     *
     * @return 当前登录用户ID
     */
    private Long currentUserId() {
      return SecurityUtils.getCurrentUserEntity().getId();
    }



    /** 查当前用户对某医生的有效收藏（@TableLogic 自动只查 deleted=0），没有返回 null */
    private UserFavoriteDoctor selectMine(Long userId, Long doctorId) {
        return userFavoriteDoctorMapper.selectOne(new LambdaQueryWrapper<UserFavoriteDoctor>()
                .eq(UserFavoriteDoctor::getUserId, userId)
                .eq(UserFavoriteDoctor::getDoctorId, doctorId)
                .orderByDesc(UserFavoriteDoctor::getId)
                .last("limit 1"));                      // ★ 用 selectOne，不是 selectById
    }


    /** 医生必须存在且在岗 */
    private void requireActiveDoctor(Long doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null || doctor.getStatus() == null || doctor.getStatus() != 1) {
            throw new BusinessException("医生不存在或已停诊");
        }
    }

    /**
     * 收藏记录 → VO，拼装完整医生信息（科室名/评分/接诊量都在里面）；
     * 医生已被删除或停诊时返回 null，由调用方过滤掉。
     */
    private UserFavoriteDoctorVO toVO(UserFavoriteDoctor favorite) {
        if (favorite == null) {
            return null;
        }
        Doctor doctor = doctorMapper.selectById(favorite.getDoctorId());
        if (doctor == null || doctor.getStatus() == null || doctor.getStatus() != 1) {
            return null;                                // 停诊/被删的医生不再展示
        }

        DoctorVO doctorVO = doctorService.getById(favorite.getDoctorId());

        UserFavoriteDoctorVO vo = UserFavoriteDoctorVO.fromEntity(favorite);
        vo.setDoctor(doctorVO);
        return vo;
    }

}
