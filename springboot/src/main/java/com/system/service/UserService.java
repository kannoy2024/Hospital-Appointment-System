package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.dto.ChangePasswordRequest;
import com.system.dto_vo.dto.LoginRequest;
import com.system.dto_vo.vo.LoginResponse;
import com.system.dto_vo.dto.ProfileUpdateRequest;
import com.system.dto_vo.dto.RegisterRequest;
import com.system.dto_vo.dto.WxLoginRequest;
import com.system.dto_vo.dto.UserSaveRequest;
import com.system.dto_vo.vo.UserVO;

import java.util.List;

/**
 * 用户服务：账号登录注册、个人资料管理、管理员用户管理。
 */
public interface UserService {

    /** 用户名密码登录，返回 JWT 令牌和用户信息 */
    LoginResponse login(LoginRequest request);

    /** 微信小程序 code 换 openid 登录，新用户自动注册 */
    LoginResponse wxLogin(WxLoginRequest request);

    /** 注册新用户（用户名密码方式） */
    UserVO register(RegisterRequest request);

    /** 获取当前登录用户的信息 */
    UserVO getCurrentUser();

    /** 更新当前用户个人资料（昵称/邮箱/手机号） */
    UserVO updateProfile(ProfileUpdateRequest request);

    /** 绑定当前用户的头像 */
    UserVO bindAvatar(String path);

    /** 修改当前用户密码（校验旧密码） */
    void changePassword(ChangePasswordRequest request);

    /** 管理员分页查询用户列表，支持关键字搜索 */
    PageResult<UserVO> pageUsers(long current, long size, String keyword);

    /** 管理员查询单个用户详情 */
    UserVO getUserById(Long id);

    /** 管理员新增用户 */
    UserVO createUser(UserSaveRequest request);

    /** 管理员编辑用户 */
    UserVO updateUser(UserSaveRequest request);

    /** 管理员删除单个用户 */
    void deleteUser(Long id);

    /** 管理员批量删除用户 */
    void deleteUsers(List<Long> ids);
}
