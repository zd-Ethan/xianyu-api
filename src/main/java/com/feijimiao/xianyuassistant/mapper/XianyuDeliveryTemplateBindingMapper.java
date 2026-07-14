package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuDeliveryTemplateBinding;
import com.feijimiao.xianyuassistant.entity.bo.DeliveryTemplateBindingBO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface XianyuDeliveryTemplateBindingMapper extends BaseMapper<XianyuDeliveryTemplateBinding> {

    @Select("SELECT b.*, t.name AS template_name, t.enabled AS template_enabled " +
            "FROM xianyu_delivery_template_binding b " +
            "LEFT JOIN xianyu_delivery_template t ON t.id = b.template_id " +
            "WHERE b.xianyu_account_id = #{accountId} AND b.xy_goods_id = #{xyGoodsId} " +
            "ORDER BY b.id DESC")
    List<DeliveryTemplateBindingBO> selectByGoods(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId);

    @Select("SELECT b.*, t.name AS template_name, t.enabled AS template_enabled " +
            "FROM xianyu_delivery_template_binding b " +
            "LEFT JOIN xianyu_delivery_template t ON t.id = b.template_id " +
            "WHERE b.xianyu_account_id = #{accountId} AND b.xy_goods_id = #{xyGoodsId} " +
            "AND b.enabled = 1 " +
            "ORDER BY b.id DESC LIMIT 1")
    DeliveryTemplateBindingBO selectActiveByGoods(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId);

    @Select("SELECT * FROM xianyu_delivery_template_binding " +
            "WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} " +
            "LIMIT 1")
    XianyuDeliveryTemplateBinding selectByGoodsKey(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId);

    @Select("SELECT COUNT(1) FROM xianyu_delivery_template_binding WHERE template_id = #{templateId} AND enabled = 1")
    int countEnabledByTemplateId(@Param("templateId") Long templateId);

    @Delete("DELETE FROM xianyu_delivery_template_binding WHERE template_id = #{templateId}")
    int deleteByTemplateId(@Param("templateId") Long templateId);
}
