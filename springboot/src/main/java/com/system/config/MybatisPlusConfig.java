package com.system.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.system.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    /*
    * 分页插件
    *
    * */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /*自动填充create和update字段*/
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

                Long currentUserId = tryGetCurrentUserId();
                if(currentUserId != null) {
                    strictInsertFill(metaObject, "createUser", Long.class, currentUserId);
                    strictInsertFill(metaObject, "updateUser", Long.class, currentUserId);
                }

            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

                Long currentUserId = tryGetCurrentUserId();
                if(currentUserId != null) {
                    strictInsertFill(metaObject, "updateUser", Long.class, currentUserId);
                }
            }

            private Long tryGetCurrentUserId(){
                try{
                    return SecurityUtils.getCurrentUserEntity().getId();
                }catch (Exception e){
                    return  null;
                    //未登录，不填充
                }
            }
        };
    }
}
