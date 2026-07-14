package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 从数据库获取商品列表请求DTO
 */
@Data
public class ItemListFromDbReqDTO {
    
    /**
     * 只显示可配置商品
     * true=显示在售和审核中(status=0/3), false=显示全部
     * 默认true
     */
    private Boolean onlyOnSale = true;

    /**
     * 商品状态筛选
     * -1=已删除 0=在售 1=已下架 2=已售出 3=审核中
     */
    private Integer status;
    
    /**
     * 闲鱼账号ID（可选）
     */
    private Long xianyuAccountId;
    
    /**
     * 页码，从1开始
     * 默认1
     */
    private Integer pageNum = 1;
    
    /**
     * 每页数量
     * 默认20
     */
    private Integer pageSize = 20;
}
