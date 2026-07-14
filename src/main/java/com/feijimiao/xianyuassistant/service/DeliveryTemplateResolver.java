package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuDeliveryTemplate;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.bo.DeliveryTemplateBindingBO;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveDeliveryConfigBO;
import com.feijimiao.xianyuassistant.mapper.XianyuDeliveryTemplateBindingMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuDeliveryTemplateMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeliveryTemplateResolver {

    private static final String SOURCE_TEMPLATE = "发货模板";
    private static final String SOURCE_LOCAL = "商品单独配置";
    private static final String SOURCE_SKU = "规格单独配置";
    private static final String SOURCE_DEFAULT = "默认配置";

    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;

    @Autowired
    private XianyuDeliveryTemplateBindingMapper bindingMapper;

    @Autowired
    private XianyuDeliveryTemplateMapper templateMapper;

    public EffectiveDeliveryConfigBO getEffectiveConfig(Long accountId, String xyGoodsId, String skuId) {
        EffectiveDeliveryConfigBO target = defaultConfig(accountId, xyGoodsId, skuId);
        if (accountId == null || !hasText(xyGoodsId)) {
            return target;
        }

        DeliveryTemplateBindingBO binding = bindingMapper.selectActiveByGoods(accountId, xyGoodsId);
        if (binding != null && Integer.valueOf(1).equals(binding.getTemplateEnabled())) {
            XianyuDeliveryTemplate template = templateMapper.selectById(binding.getTemplateId());
            if (template != null && Integer.valueOf(1).equals(template.getEnabled())) {
                applyTemplate(target, template, binding);
            }
        }

        XianyuGoodsAutoDeliveryConfig baseConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
        applyLocal(target, baseConfig, SOURCE_LOCAL);

        if (hasText(skuId)) {
            XianyuGoodsAutoDeliveryConfig skuConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdAndSkuId(accountId, xyGoodsId, skuId);
            applyLocal(target, skuConfig, SOURCE_SKU);
        }

        target.setHasEffectiveConfig(isUsable(target));
        return target;
    }

    public XianyuGoodsAutoDeliveryConfig getExecutionConfig(Long accountId, String xyGoodsId, String skuId) {
        EffectiveDeliveryConfigBO effective = getEffectiveConfig(accountId, xyGoodsId, skuId);
        if (effective == null || !Boolean.TRUE.equals(effective.getHasEffectiveConfig())) {
            return null;
        }
        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setId(effective.getId());
        config.setXianyuAccountId(effective.getXianyuAccountId());
        config.setXianyuGoodsId(effective.getXianyuGoodsId());
        config.setXyGoodsId(effective.getXyGoodsId());
        config.setDeliveryMode(effective.getDeliveryMode());
        config.setSkuId(effective.getSkuId());
        config.setSkuName(effective.getSkuName());
        config.setAutoDeliveryContent(nullToEmpty(effective.getAutoDeliveryContent()));
        config.setKamiConfigIds(nullToEmpty(effective.getKamiConfigIds()));
        config.setKamiDeliveryTemplate(nullToEmpty(effective.getKamiDeliveryTemplate()));
        config.setAutoDeliveryImageUrl(nullToEmpty(effective.getAutoDeliveryImageUrl()));
        config.setAutoConfirmShipment(effective.getAutoConfirmShipment());
        config.setMultiQuantityDelivery(effective.getMultiQuantityDelivery());
        config.setRagDelaySeconds(effective.getRagDelaySeconds());
        return config;
    }

    private EffectiveDeliveryConfigBO defaultConfig(Long accountId, String xyGoodsId, String skuId) {
        EffectiveDeliveryConfigBO config = new EffectiveDeliveryConfigBO();
        config.setXianyuAccountId(accountId);
        config.setXyGoodsId(xyGoodsId);
        config.setSkuId(skuId);
        config.setDeliveryMode(1);
        config.setAutoDeliveryContent("");
        config.setKamiConfigIds("");
        config.setKamiDeliveryTemplate("");
        config.setAutoDeliveryImageUrl("");
        config.setAutoConfirmShipment(0);
        config.setMultiQuantityDelivery(1);
        config.setRagDelaySeconds(15);
        config.setDeliveryModeSourceName(SOURCE_DEFAULT);
        config.setAutoConfirmSourceName(SOURCE_DEFAULT);
        config.setMultiQuantitySourceName(SOURCE_DEFAULT);
        config.setHasEffectiveConfig(false);
        return config;
    }

    private void applyTemplate(EffectiveDeliveryConfigBO target, XianyuDeliveryTemplate template, DeliveryTemplateBindingBO binding) {
        String sourceName = hasText(binding.getTemplateName()) ? binding.getTemplateName() : SOURCE_TEMPLATE;
        target.setTemplateId(template.getId());
        target.setTemplateName(sourceName);
        target.setTemplateEnabled(template.getEnabled());
        target.setXianyuAccountId(binding.getXianyuAccountId());
        target.setXyGoodsId(binding.getXyGoodsId());

        int mode = normalizeMode(template.getDeliveryMode());
        if (mode == 1 && hasText(template.getAutoDeliveryContent())) {
            target.setDeliveryMode(1);
            target.setAutoDeliveryContent(template.getAutoDeliveryContent().trim());
            target.setDeliveryModeSourceName(sourceName);
            target.setContentSourceName(sourceName);
        }
        if (mode == 2 && hasText(template.getKamiConfigIds())) {
            target.setDeliveryMode(2);
            target.setKamiConfigIds(template.getKamiConfigIds().trim());
            target.setKamiDeliveryTemplate(trimToEmpty(template.getKamiDeliveryTemplate()));
            target.setDeliveryModeSourceName(sourceName);
            target.setContentSourceName(sourceName);
        }
        if (hasText(template.getAutoDeliveryImageUrl())) {
            target.setAutoDeliveryImageUrl(template.getAutoDeliveryImageUrl().trim());
            target.setImageSourceName(sourceName);
        }
        if (template.getAutoConfirmShipment() != null) {
            target.setAutoConfirmShipment(template.getAutoConfirmShipment() == 1 ? 1 : 0);
            target.setAutoConfirmSourceName(sourceName);
        }
        if (template.getMultiQuantityDelivery() != null) {
            target.setMultiQuantityDelivery(template.getMultiQuantityDelivery() == 0 ? 0 : 1);
            target.setMultiQuantitySourceName(sourceName);
        }
    }

    private void applyLocal(EffectiveDeliveryConfigBO target, XianyuGoodsAutoDeliveryConfig local, String sourceName) {
        if (local == null) {
            return;
        }
        target.setId(local.getId());
        target.setXianyuAccountId(local.getXianyuAccountId());
        target.setXianyuGoodsId(local.getXianyuGoodsId());
        target.setXyGoodsId(local.getXyGoodsId());
        if (hasText(local.getSkuId())) {
            target.setSkuId(local.getSkuId());
            target.setSkuName(local.getSkuName());
        }
        if (local.getRagDelaySeconds() != null) {
            target.setRagDelaySeconds(local.getRagDelaySeconds());
        }

        int mode = normalizeMode(local.getDeliveryMode());
        if (mode == 1 && hasText(local.getAutoDeliveryContent())) {
            target.setDeliveryMode(1);
            target.setAutoDeliveryContent(local.getAutoDeliveryContent().trim());
            target.setDeliveryModeSourceName(sourceName);
            target.setContentSourceName(sourceName);
        }
        if (mode == 2 && hasText(local.getKamiConfigIds())) {
            target.setDeliveryMode(2);
            target.setKamiConfigIds(local.getKamiConfigIds().trim());
            target.setKamiDeliveryTemplate(trimToEmpty(local.getKamiDeliveryTemplate()));
            target.setDeliveryModeSourceName(sourceName);
            target.setContentSourceName(sourceName);
        }
        if (hasText(local.getAutoDeliveryImageUrl())) {
            target.setAutoDeliveryImageUrl(local.getAutoDeliveryImageUrl().trim());
            target.setImageSourceName(sourceName);
        }
        if (local.getAutoConfirmShipment() != null) {
            target.setAutoConfirmShipment(local.getAutoConfirmShipment() == 1 ? 1 : 0);
            target.setAutoConfirmSourceName(sourceName);
        }
        if (local.getMultiQuantityDelivery() != null) {
            target.setMultiQuantityDelivery(local.getMultiQuantityDelivery() == 0 ? 0 : 1);
            target.setMultiQuantitySourceName(sourceName);
        }
    }

    private boolean isUsable(EffectiveDeliveryConfigBO config) {
        int mode = normalizeMode(config.getDeliveryMode());
        if (mode == 1) {
            return hasText(config.getAutoDeliveryContent());
        }
        if (mode == 2) {
            return hasText(config.getKamiConfigIds());
        }
        return false;
    }

    private int normalizeMode(Integer mode) {
        return mode != null && mode == 2 ? 2 : 1;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
