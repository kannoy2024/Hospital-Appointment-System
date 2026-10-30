package com.system.service.impl;


import com.system.common.BusinessException;
import com.system.common.ResilientRedisClient;
import com.system.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.system.common.RedisConstants.SYSTEM_STATUS_PREFIX;

/**
 * 系统状态服务实现：负责系统运行状态的读写（正常/维护中）。
 */
@Service
@RequiredArgsConstructor
public class SystemServiceImpl implements SystemService {

    private final ResilientRedisClient resilientRedisClient;

    /**
     * 写入系统运行状态到 Redis。
     *
     * @param status 状态值（1-正常，0-维护中）
     */
    @Override
    public void setStatus(Integer status) {
        resilientRedisClient.set(SYSTEM_STATUS_PREFIX,status.toString());
    }

    /**
     * 读取系统运行状态的中文描述，状态缺失或非法时抛出业务异常。
     *
     * @return 状态描述（正常/系统维护中）
     */
    @Override
    public String getStatus() {
        String status = resilientRedisClient.get(SYSTEM_STATUS_PREFIX);
        if (status == null) {
            throw new BusinessException("系统状态获取失败！");
        }
        if(status.equals("1")){
            status="正常";
        }else if(status.equals("0")){
            status="系统维护中";
        }else {
            throw new BusinessException("系统状态获取失败！");
        }
        return status;
    }
}
