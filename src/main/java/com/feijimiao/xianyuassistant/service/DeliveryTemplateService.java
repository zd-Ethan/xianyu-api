package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.bo.DeliveryTemplateBO;
import com.feijimiao.xianyuassistant.entity.bo.DeliveryTemplateBindingBO;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveDeliveryConfigBO;

import java.util.List;

public interface DeliveryTemplateService {

    List<DeliveryTemplateBO> list(Long accountId);

    DeliveryTemplateBO detail(Long templateId);

    DeliveryTemplateBO create(Long accountId, String name, String description);

    void update(
            Long templateId,
            String name,
            String description,
            Integer enabled,
            Integer deliveryMode,
            String autoDeliveryContent,
            String kamiConfigIds,
            String kamiDeliveryTemplate,
            String autoDeliveryImageUrl,
            Integer autoConfirmShipment,
            Integer multiQuantityDelivery);

    void delete(Long templateId);

    DeliveryTemplateBO createFromGoods(Long accountId, String xyGoodsId, String name, String description);

    int applyToGoods(Long accountId, List<String> xyGoodsIds, Long templateId, Integer enableAutoDelivery);

    List<DeliveryTemplateBindingBO> bindings(Long accountId, String xyGoodsId);

    DeliveryTemplateBindingBO bind(Long accountId, String xyGoodsId, Long templateId, Integer enableAutoDelivery);

    void unbind(Long bindingId);

    EffectiveDeliveryConfigBO effectiveConfig(Long accountId, String xyGoodsId, String skuId);
}
