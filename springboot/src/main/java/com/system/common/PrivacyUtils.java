package com.system.common;


//在导出时用来过滤敏感信息
public final class PrivacyUtils {
    private PrivacyUtils() {
    }

    /**
     * 13988000000 → 139****0000；长度不足 7 位或为空则原样返回
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 110101199003071234 → 1101**********1234；空或短于 8 位原样返回
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }

        //18位->10个星，15位老身份证
        return idCard.substring(0, 4)
                + "*".repeat(idCard.length() - 8)
                + idCard.substring(idCard.length() - 4);
    }

}