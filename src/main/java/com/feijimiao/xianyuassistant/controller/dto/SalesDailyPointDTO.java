package com.feijimiao.xianyuassistant.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SalesDailyPointDTO {
    private String date;
    private long quantity;
    private long amountCents;
}
