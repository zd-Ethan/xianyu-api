package com.feijimiao.xianyuassistant.entity.bo;

import lombok.Data;

@Data
public class SalesSummaryBO {
    private Long totalQuantity;
    private Long todayQuantity;
    private Long last7DaysQuantity;
    private Long last30DaysQuantity;
    private Long monthQuantity;
    private Long totalAmountCents;
    private Long monthAmountCents;
}
