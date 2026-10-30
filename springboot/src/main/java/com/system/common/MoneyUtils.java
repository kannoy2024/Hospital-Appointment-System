package com.system.common;


import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
public class MoneyUtils {


    /** 元 → 分（禁止 double 参与，走 BigDecimal 保证 0.1 不会变 10） */
    public static long yuanToFen(BigDecimal yuan) {
        if (yuan == null) {
            throw new BusinessException("金额不能为空");
        }
        return yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 分 → 元（仅用于展示/对账，业务运算一律用分） */
    public static BigDecimal fenToYuan(long fen) {
        return BigDecimal.valueOf(fen).movePointLeft(2);
    }

    /** "2000" → "20.00"（不带符号，供前端/文案） */
    public static String formatFen(long fen) {
        return BigDecimal.valueOf(fen).movePointLeft(2).toPlainString();
    }

    /** "2000" → "¥20.00"（供站内通知与日志） */
    public static String formatYuan(long fen) {
        return "¥" + formatFen(fen);
    }

    /** 只有正数才是合法支付金额 */
    public static boolean isValidAmount(long fen) {
        return fen > 0;
    }
}
