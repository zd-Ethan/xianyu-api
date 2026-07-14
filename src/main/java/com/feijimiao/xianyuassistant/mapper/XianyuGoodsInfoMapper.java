package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 闲鱼商品信息Mapper
 */
@Mapper
public interface XianyuGoodsInfoMapper extends BaseMapper<XianyuGoodsInfo> {

    @Select("SELECT " +
            "COUNT(*) AS itemCount, " +
            "SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS sellingItemCount, " +
            "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS offShelfItemCount, " +
            "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS soldItemCount " +
            "FROM xianyu_goods")
    Map<String, Object> selectDashboardGoodsStats();
}
