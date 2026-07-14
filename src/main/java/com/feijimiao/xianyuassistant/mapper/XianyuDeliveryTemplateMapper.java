package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuDeliveryTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface XianyuDeliveryTemplateMapper extends BaseMapper<XianyuDeliveryTemplate> {

    @Select("""
            SELECT id, xianyu_account_id, name, description, enabled, delivery_mode,
                   auto_delivery_content, kami_config_ids, kami_delivery_template,
                   auto_delivery_image_url, auto_confirm_shipment, multi_quantity_delivery,
                   create_time, update_time
            FROM xianyu_delivery_template
            WHERE #{xianyuAccountId} IS NULL OR xianyu_account_id IS NULL OR xianyu_account_id = #{xianyuAccountId}
            ORDER BY update_time DESC, id DESC
            """)
    List<XianyuDeliveryTemplate> selectAvailable(@Param("xianyuAccountId") Long xianyuAccountId);

    @Select("SELECT COUNT(1) FROM xianyu_delivery_template_binding WHERE template_id = #{templateId} AND enabled = 1")
    int countEnabledBindings(@Param("templateId") Long templateId);
}
