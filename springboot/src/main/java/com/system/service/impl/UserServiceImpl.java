package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.system.common.BusinessException;
import com.system.common.FileCategories;
import com.system.common.MessageConstants;
import com.system.common.PageResult;
import com.system.dto_vo.dto.*;
import com.system.dto_vo.vo.LoginResponse;
import com.system.dto_vo.vo.UserVO;
import com.system.dto_vo.vo.WxSessionResponse;
import com.system.entity.Doctor;
import com.system.entity.User;
import com.system.mapper.DoctorMapper;
import com.system.mapper.UserMapper;
import com.system.security.JwtTokenProvider;
import com.system.security.LoginUser;
import com.system.security.SecurityUtils;
import com.system.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.system.common.MessageConstants.ACCOUNT_LOCKED;
import static com.system.common.MessageConstants.ACCOUNT_PASSWORD_ERROR;


/**
 * 用户服务实现：负责登录认证（密码/微信）、注册、个人信息维护以及管理员对用户账号的增删改查。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    

    private final UserMapper userMapper;                         // 用户数据访问最后更新者的id
    private final PasswordEncoder passwordEncoder;               // 密码加密器
    private final AuthenticationManager authenticationManager;   // Spring Security 认证管理器
    private final JwtTokenProvider jwtTokenProvider;             // JWT 令牌生成器
    private final LogService logService;                         // 日志服务
    private final RoleService roleService;                       // 角色校验服务
    private final FileStorageService fileStorageService;         // 文件存储服务
    private final WeChatService weChatService;                   // 微信小程序服务
    private final DoctorMapper doctorMapper;                     // 医生数据访问


    /**
     * 账号密码登录：由 Spring Security 校验身份，成功后签发 JWT 并记录登录日志。
     *
     * @param request 登录请求（包含用户名和密码）
     * @return 登录响应（包含 JWT 令牌和用户信息）
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            // 1. 调用 Spring Security 认证管理器进行身份验证
            //    - 会触发 UserDetailsService.loadUserByUsername() 加载用户
            //    - 会使用 PasswordEncoder 验证密码
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),   // 用户名
                            request.getPassword()    // 明文密码
                    )
            );

            // 2. 验证通过，获取登录用户信息
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();

            // 3. 生成 JWT 令牌
            String token = jwtTokenProvider.generateToken(loginUser.getUsername());

            // 4. 记录登录成功日志
            logService.saveLoginLog(request.getUsername(), true, "登录成功");

            // 5. 返回令牌和用户信息
            return new LoginResponse(token, toUserVO(loginUser.getUser()));

        } catch (BadCredentialsException e) {
            // 用户名或密码错误
            logService.saveLoginLog(request.getUsername(), false, ACCOUNT_PASSWORD_ERROR+e);
            throw e;
        }catch (LockedException e){
            // 用户状态被锁定
            logService.saveLoginLog(request.getUsername(), false, ACCOUNT_LOCKED+e);
            throw e;
        } catch (Exception e) {
            // 其他异常（账号被禁用等）
            logService.saveLoginLog(request.getUsername(), false, e.getMessage());
            throw e;
        }
    }

    /**
     * 微信小程序登录：code 换 openid，首次自动注册，否则同步昵称头像，最后签发 JWT。
     *
     * @param request 微信登录请求（包含 code、昵称、头像）
     * @return 登录响应（包含 JWT 令牌和用户信息）
     */
    @Override
    public LoginResponse wxLogin(WxLoginRequest request) {

        // 1. 调用微信接口，用 code 换取 openid
        if (request.getCode() == null) {
            throw new BusinessException(MessageConstants.WX_LOGIN_FAILED);
        }
        WxSessionResponse session = weChatService.code2Session(request.getCode());
        String openid = session.getOpenid();

        // 2. 根据 openid 查询用户
        if (openid == null) {
            throw new BusinessException(MessageConstants.WX_LOGIN_FAILED);
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));

        // 3. 首次登录：自动注册,拼装用户信息
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setUsername(buildWxUsername(openid));              // 生成用户名：wx_xxx
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // 随机密码
            user.setGender(0);
            user.setNickname(StringUtils.hasText(request.getNickname()) ?
                    request.getNickname() : "微信用户");             // 默认昵称
            user.setAvatar(request.getAvatarUrl());                 // 微信头像
            user.setStatus(1);                                      // 启用
            user.setRole("USER");                                   // 普通用户
            userMapper.insert(user);
        } else {
            // 4. 已有账号：更新昵称和头像（如果发生了变化）
            boolean updated = false;
            if (StringUtils.hasText(request.getNickname()) && !request.getNickname().equals(user.getNickname())) {
                user.setNickname(request.getNickname());
                updated = true;
            }
            if (StringUtils.hasText(request.getAvatarUrl()) && !request.getAvatarUrl().equals(user.getAvatar())) {
                user.setAvatar(request.getAvatarUrl());
                updated = true;
            }
            if (updated) {
                userMapper.updateById(user);
            }
        }

        // 5. 检查账号状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用,请找管理员申述");
        }

        // 6. 生成 JWT 令牌
        String token = jwtTokenProvider.generateToken(user.getUsername());

        // 7. 记录登录日志
        logService.saveLoginLog(user.getUsername(), true, "微信登录成功");

        return new LoginResponse(token, toUserVO(user));
    }

    /**
     * 生成微信用户的用户名（wx_ + openid），超长时截断以适配字段长度。
     *
     * @param openid 微信 openid
     * @return 用户名
     */
    private String buildWxUsername(String openid) {
        String base = "wx_" + openid;
        if (base.length() <= 50) {
            return base;
        }
        return "wx_" + openid.substring(0, 46);
    }


    /**
     * 普通用户注册：校验用户名唯一后加密密码并落库（默认 USER 角色、启用状态）。
     *
     * @param request 注册请求
     * @return 注册成功的用户信息
     */
    @Override
    public UserVO register(RegisterRequest request) {
        // 1. 校验用户名唯一性
        checkUsernameUnique(request.getUsername(), null);

        // 2. 构建用户对象
        User user = new User();
        user.setUsername(request.getUsername());

        // 3. 加密密码（关键！）
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender()!=null ? request.getGender() : 0);
        user.setStatus(1);      // 启用
        user.setRole("USER");   // 普通用户角色

        // 4. 保存到数据库
        userMapper.insert(user);

        return toUserVO(user);
    }

    //  个人信息管理 

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前用户信息
     */
    @Override
    public UserVO getCurrentUser() {
        return toUserVO(SecurityUtils.getCurrentUserEntity());
    }

    /**
     * 更新当前用户的个人信息（昵称、邮箱、手机号、性别，用户名不可改）。
     *
     * @param request 个人信息更新请求
     * @return 更新后的用户信息
     */
    @Override
    public UserVO updateProfile(ProfileUpdateRequest request) {
        // 1. 获取当前用户
        User user = SecurityUtils.getCurrentUserEntity();

        // 2. 更新字段
        if(StringUtils.hasText(request.getNickname())){
            user.setNickname(request.getNickname());
        }
        if(StringUtils.hasText(request.getEmail())){
            user.setEmail(request.getEmail());
        }
        if(StringUtils.hasText(request.getPhone())){
            user.setPhone(request.getPhone());
        }
        if(request.getGender()!=null){
            user.setGender(request.getGender());
        }
        //记录操作人的信息
        user.setUpdateUser(SecurityUtils.getCurrentUserEntity().getId());

        // 3. 保存到数据库
        userMapper.updateById(user);

        // 4. 重新查询并返回最新数据
        return toUserVO(userMapper.selectById(user.getId()));
    }

    /**
     * 绑定头像：校验路径归属当前用户并更新头像，医生角色同步更新医生头像，最后清理旧文件。
     *
     * @param path 头像文件路径
     * @return 更新后的用户信息
     */
    @Override
    public UserVO bindAvatar(String path) {
        // 1. 获取当前用户
        User user = SecurityUtils.getCurrentUserEntity();

        // 2. 验证路径合法性：必须是当前用户上传的头像文件
        //    上传时文件名格式为：{userId}_{timestamp}.jpg
        String prefix = "/uploads/" + FileCategories.AVATAR  + "/" + user.getId() + "_";
        if (!path.startsWith(prefix)) {
            throw new BusinessException("只能绑定当前用户上传的头像文件");
        }

        // 3. 验证文件是否存在
        fileStorageService.resolveLocalPath(path);

        // 4. 保存旧路径（用于删除旧文件）
        String oldPath = user.getAvatar();

        // 5. 更新用户头像
        user.setAvatar(path);
        userMapper.updateById(user);

        // 6. 如果是医生，同步更新医生头像
        syncDoctorAvatar(user, path);

        // 7. 删除旧头像文件
        fileStorageService.deleteLocalFile(oldPath);

        // 8. 返回更新后的用户信息
        return toUserVO(userMapper.selectById(user.getId()));
    }

    /**
     * 若用户是医生，同步更新 Doctor 表中的头像保持一致。
     *
     * @param user 用户实体
     * @param path 新头像路径
     */
    private void syncDoctorAvatar(User user, String path) {
        // 只有医生角色才需要同步
        if (!"DOCTOR".equals(user.getRole()) || user.getDoctorId() == null) {
            return;
        }

        Doctor doctor = doctorMapper.selectById(user.getDoctorId());
        if (doctor == null) {
            return;
        }

        doctor.setAvatar(path);
        doctorMapper.updateById(doctor);
    }

    /**
     * 修改密码：校验两次新密码一致、新旧不同且旧密码正确后，加密保存新密码。
     *
     * @param request 修改密码请求（包含旧密码、新密码、确认密码）
     */
    @Override
    public void changePassword(ChangePasswordRequest request) {
        // 1. 校验新密码两次输入一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }

        // 2. 校验新旧密码不同
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        // 3. 获取当前用户
        User user = SecurityUtils.getCurrentUserEntity();

        // 4. 验证旧密码是否正确
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        // 5. 加密新密码并更新
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    //  管理员后台管理 

    /**
     * 分页查询用户列表（后台管理用），支持用户名/昵称/邮箱关键字搜索。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 搜索关键字（可选）
     * @return 分页结果
     */
    @Override
    public PageResult<UserVO> pageUsers(long current, long size, String keyword) {
        // 1. 构建查询条件，准备包装器
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 2. 关键字搜索（用户名、昵称、邮箱）
        if (StringUtils.hasText(keyword)) {//判断是否有实际内容
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getEmail, keyword));
        }

        // 3. 按创建时间降序排列
        wrapper.orderByDesc(User::getCreateTime);

        // 4. 执行分页查询
        Page<User> page = userMapper.selectPage(new Page<>(current, size), wrapper);

        // 5. Entity → VO 转换
        List<UserVO> records = page.getRecords().stream()
                .map(this::toUserVO)
                .collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 根据 ID 查询用户（后台管理用），不存在则抛出异常。
     *
     * @param id 用户ID
     * @return 用户信息
     */
    @Override
    public UserVO getUserById(Long id) {
        User user = getUserOrThrow(id);
        return toUserVO(user);
    }

    /**
     * 创建用户（管理员专用）：校验权限、用户名唯一、密码长度与角色合法性后加密落库。
     *
     * @param request 用户创建请求
     * @return 创建后的用户信息
     */
    @Override
    public UserVO createUser(UserSaveRequest request) {
        // 1. 校验管理员权限
        requireAdmin();

        // 2. 校验用户名唯一性
        checkUsernameUnique(request.getUsername(), null);

        // 3. 校验密码
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        if (request.getPassword().length() < 3) {
            throw new BusinessException("密码长度至少3个字符");
        }

        // 4. 不允许创建普通用户角色（只能微信注册）
        rejectUserRoleForAdminCreate(request.getRole());

        // 5. 构建用户对象
        User user = buildUserFromRequest(request);
        BeanUtils.copyProperties(request,user);

        // 6. 加密密码
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // 7. 保存到数据库
        userMapper.insert(user);

        return toUserVO(user);
    }

    /**
     * 更新用户（管理员专用）：校验权限与角色合法性，确保至少保留一名管理员后保存。
     *
     * @param request 用户更新请求
     * @return 更新后的用户信息
     */
    @Override
    public UserVO updateUser(UserSaveRequest request) {
        // 1. 校验管理员权限
        requireAdmin();

        // 2. 校验用户ID
        if (request.getId() == null) {
            throw new BusinessException("用户ID不能为空");
        }

        // 3. 查询用户（不存在则抛异常）
        User existing = getUserOrThrow(request.getId());

        // 4. 校验用户名唯一性（排除自身）
        checkUsernameUnique(request.getUsername(), request.getId());

        // 5. 更新基本信息
        existing.setUsername(request.getUsername());
        existing.setNickname(request.getNickname());
        existing.setEmail(request.getEmail());
        existing.setPhone(request.getPhone());
        existing.setGender(request.getGender()!=null ? request.getGender() : 0);
        existing.setStatus(request.getStatus());
        existing.setUpdateUser(getCurrentUser().getId());

        // 6. 校验角色合法性
        roleService.validateRoleCode(request.getRole());

        // 7. 确保至少保留一名管理员
        ensureAdminRemains(request.getId(), request.getRole());

        // 8. 不允许给非USER角色赋USER角色
        rejectUserRoleAssignment(existing, request.getRole());

        // 9. 更新角色
        existing.setRole(request.getRole());

        // 10. 如果传了新密码，更新密码
        if (StringUtils.hasText(request.getPassword()) && request.getPassword().length() < 3) {
            throw new BusinessException("密码长度至少3个字符");
        }
        if (StringUtils.hasText(request.getPassword())) {
            existing.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // 11. 保存到数据库
        userMapper.updateById(existing);

        return toUserVO(existing);
    }

    /**
     * 删除用户（管理员专用）：不能删除自己，且需保证至少保留一名管理员。
     *
     * @param id 用户ID
     */
    @Override
    public void deleteUser(Long id) {
        // 1. 校验管理员权限
        requireAdmin();

        // 2. 不能删除当前登录用户
        User current = SecurityUtils.getCurrentUserEntity();
        if (current.getId().equals(id)) {
            throw new BusinessException("不能删除当前登录用户");
        }

        // 3. 至少保留一名管理员
        ensureAdminRemainsOnDelete(id);

        // 4.检查该账号是否是医生角色。禁止在用户界面删除医生账号
        User target=getUserOrThrow(current.getId());
        if("DOCTOR".equals(target.getRole())){
            throw new BusinessException("医生账号请前往医生管理页删除");
        }

        // 5. 执行逻辑删除
        userMapper.deleteById(id);
    }

    /**
     * 批量删除用户，按ID去重后逐个调用删除逻辑。
     *
     * @param ids 用户ID列表
     */
    @Override
    public void deleteUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的用户");
        }

        // 去重后逐个删除（每个删除都包含完整的校验逻辑）
        for (Long id : ids.stream().distinct().toList()) {
            deleteUser(id);
        }
    }

    //====私有工具方法====

    /**
     * 将 User 实体转为 UserVO，医生无头像时从 Doctor 表补取。
     *
     * @param user 用户实体
     * @return 用户VO
     */
    private UserVO toUserVO(User user) {
        UserVO vo = UserVO.fromEntity(user);

        // 医生特殊处理：如果 User 表中没有头像，从 Doctor 表中取
        if (!StringUtils.hasText(vo.getAvatar())
                && "DOCTOR".equals(user.getRole())
                && user.getDoctorId() != null) {
            Doctor doctor = doctorMapper.selectById(user.getDoctorId());
            if (doctor != null && StringUtils.hasText(doctor.getAvatar())) {
                vo.setAvatar(doctor.getAvatar());
            }
        }

        return vo;
    }

    /**
     * 将 UserSaveRequest 转为 User 实体并校验角色合法性（不含密码与ID）。
     *
     * @param request 用户保存请求
     * @return 用户实体
     */
    private User buildUserFromRequest(UserSaveRequest request) {
        // 校验角色合法性
        roleService.validateRoleCode(request.getRole());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender()!=null ? request.getGender() : 0);
        user.setStatus(request.getStatus());
        user.setRole(request.getRole());
        return user;
    }

    /**
     * 根据ID查询用户，不存在则抛出异常。
     *
     * @param id 用户ID
     * @return 用户实体
     */
    private User getUserOrThrow(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /**
     * 校验用户名唯一性，编辑时通过 excludeId 排除自身。
     *
     * @param username 待检查的用户名
     * @param excludeId 排除的用户ID（编辑时传入）
     */
    private void checkUsernameUnique(String username, Long excludeId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(User::getId, excludeId);
        }
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }
    }

    /**
     * 校验当前用户是否为管理员，否则抛出 403 异常。
     */
    private void requireAdmin() {
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }

    /**
     * 统计当前管理员数量。
     *
     * @return 管理员总数
     */
    private long countActiveAdmins() {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));
    }

    /**
     * 角色变更时确保至少保留一名管理员，否则拒绝操作。
     *
     * @param userId 用户ID
     * @param newRole 新角色
     */
    private void ensureAdminRemains(Long userId, String newRole) {
        User user = getUserOrThrow(userId);
        if ("ADMIN".equals(user.getRole()) && !"ADMIN".equals(newRole) && countActiveAdmins() <= 1) {
            throw new BusinessException("至少保留一名管理员");
        }
    }

    /**
     * 删除用户时确保至少保留一名管理员。
     *
     * @param userId 要删除的用户ID
     */
    private void ensureAdminRemainsOnDelete(Long userId) {
        User user = getUserOrThrow(userId);
        if ("ADMIN".equals(user.getRole()) && countActiveAdmins() <= 1) {
            throw new BusinessException("至少保留一名管理员");
        }
    }

    /**
     * 管理员创建用户时禁止创建 USER 角色（普通用户仅支持微信注册）。
     *
     * @param role 要创建的角色
     */
    private void rejectUserRoleForAdminCreate(String role) {
        if ("USER".equals(role)) {
            throw new BusinessException("普通用户仅支持微信小程序注册，不可后台新增");
        }
    }

    /**
     * 禁止将非 USER 角色改为 USER 角色，避免误降级医生或管理员账号。
     *
     * @param existing 现有用户
     * @param newRole 新角色
     */
    private void rejectUserRoleAssignment(User existing, String newRole) {
        if ("USER".equals(newRole) && !"USER".equals(existing.getRole())) {
            throw new BusinessException("不可将账号设置为普通用户");
        }
    }
}