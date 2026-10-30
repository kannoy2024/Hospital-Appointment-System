package com.system.common;

public class MessageConstants {
    //医生性别
    public static final int MALE = 1;
    public static final int FEMALE = 2;
    public static final String GYNECOLOGY_DEPT_NAME = "妇科";



    //医生用户名前缀
    public static final String USERNAME_PREFIX = "doctor";


    //异常提示管理
    public static final String ACCOUNT_LOCKED = "账号已被锁定！";
    public static final String SQL_LOGIN_ERROR = "MySQL 账号或密码错误！";
    public static final String INSUFFICIENT_PERMISSIONS = "账号权限不足！";
    public static final String ACCOUNT_PASSWORD_ERROR= "账号或密码错误！";
    public static final String UNKNOWN_ERROR= "未知错误";
    public static final String WX_LOGIN_FAILED="微信小程序登录失败！";
    //数据库连接失败提示
    public static final String DB_ERROR_HINT = "数据库连接失败，请检查 MySQL 是否已启动，并修改 application.yml 中的数据库账号密码";

    //调试打印
    public static final String DEBUG_LOGGING="调试打印日志";
}
