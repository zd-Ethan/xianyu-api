package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

@Data
@TableName("xianyu_sales_order")
public class XianyuSalesOrder {
    private Long id;
    private Long xianyuAccountId;
    private String orderId;
    private String xyGoodsId;
    private String platformStatus;
    private String rawStatus;
    private String paidAt;
    private String paidDate;
    private Integer quantity;
    private Long grossAmountCents;
    private Integer refundedQuantity;
    private Long refundedAmountCents;
    private String lastSyncedAt;
    private String createTime;
    private String updateTime;

    // 以下标记仅用于区分“平台未返回字段”和“平台明确返回零值”。
    @TableField(exist = false)
    private boolean platformStatusProvided = true;
    @TableField(exist = false)
    private boolean quantityProvided = true;
    @TableField(exist = false)
    private boolean grossAmountProvided = true;
    @TableField(exist = false)
    private boolean refundedQuantityProvided = true;
    @TableField(exist = false)
    private boolean refundedAmountProvided = true;
    @TableField(exist = false)
    private String dataQualityError;
}
