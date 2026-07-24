package com.feijimiao.xianyuassistant.entity.bo;

import lombok.Data;

@Data
public class SalesDailyBO {
    private String date;
    private Long quantity;
    private Long amountCents;
}
