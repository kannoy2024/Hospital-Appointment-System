package com.system.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 中文姓名转拼音首字母（演示用字表，新增医生姓名含未收录汉字时需补充）。
 */
public final class PinyinInitials {

    private static final Map<Character, Character> CHAR_MAP = new HashMap<>();

    static {
        put("张", 'Z'); put("明", 'M'); put("华", 'H'); put("李", 'L'); put("晓", 'X'); put("芳", 'F');
        put("周", 'Z'); put("德", 'D'); put("王", 'W'); put("建", 'J'); put("国", 'G'); put("赵", 'Z');
        put("志", 'Z'); put("强", 'Q'); put("马", 'M'); put("小", 'X'); put("军", 'J'); put("陈", 'C');
        put("美", 'M'); put("玲", 'L'); put("林", 'L'); put("燕", 'Y'); put("何", 'H'); put("俊", 'J');
        put("杰", 'J'); put("刘", 'L'); put("伟", 'W'); put("郑", 'Z'); put("黄", 'H'); put("文", 'W');
        put("静", 'J'); put("吴", 'W'); put("徐", 'X'); put("丽", 'L'); put("杨", 'Y'); put("帆", 'F');
        put("冯", 'F'); put("磊", 'L'); put("曹", 'C'); put("颖", 'Y'); put("谢", 'X'); put("邓", 'D');
        put("韩", 'H'); put("雪", 'X'); put("罗", 'L'); put("敏", 'M'); put("梁", 'L'); put("宋", 'S');
        put("娜", 'N'); put("彭", 'P'); put("刚", 'G'); put("董", 'D'); put("袁", 'Y'); put("莉", 'L');
        put("蒋", 'J'); put("涛", 'T'); put("潘", 'P'); put("虹", 'H'); put("杜", 'D'); put("程", 'C');
        put("远", 'Y'); put("傅", 'F'); put("沈", 'S'); put("浩", 'H'); put("海", 'H'); put("余", 'Y');
        put("婷", 'T'); put("谭", 'T'); put("孙", 'S'); put("琳", 'L');
    }

    private PinyinInitials() {
    }

    public static String from(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("医生姓名不能为空");
        }
        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            Character initial = CHAR_MAP.get(c);
            if (initial == null) {
                throw new BusinessException("姓名包含未支持的汉字：" + c + "，请联系管理员补充拼音映射");
            }
            sb.append(initial);
        }
        return sb.toString();
    }

    private static void put(String ch, char initial) {
        CHAR_MAP.put(ch.charAt(0), initial);
    }
}
