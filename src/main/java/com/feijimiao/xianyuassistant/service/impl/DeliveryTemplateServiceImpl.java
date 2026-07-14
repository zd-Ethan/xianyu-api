package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuDeliveryTemplate;
import com.feijimiao.xianyuassistant.entity.XianyuDeliveryTemplateBinding;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.entity.bo.DeliveryTemplateBindingBO;
import com.feijimiao.xianyuassistant.entity.bo.DeliveryTemplateBO;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveDeliveryConfigBO;
import com.feijimiao.xianyuassistant.mapper.XianyuDeliveryTemplateBindingMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuDeliveryTemplateMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.service.AutoDeliveryService;
import com.feijimiao.xianyuassistant.service.DeliveryTemplateService;
import com.feijimiao.xianyuassistant.service.DeliveryTemplateResolver;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeliveryTemplateServiceImpl implements DeliveryTemplateService {

    @Autowired
    private XianyuDeliveryTemplateMapper templateMapper;

    @Autowired
    private XianyuDeliveryTemplateBindingMapper bindingMapper;

    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;

    @Autowired
    private AutoDeliveryService autoDeliveryService;

    @Autowired
    private DeliveryTemplateResolver deliveryTemplateResolver;

    @Override
    public List<DeliveryTemplateBO> list(Long accountId) {
        List<XianyuDeliveryTemplate> templates = templateMapper.selectAvailable(accountId);
        if (templates == null) {
            return Collections.emptyList();
        }
        return templates.stream().map(this::toBO).collect(Collectors.toList());
    }

    @Override
    public DeliveryTemplateBO detail(Long templateId) {
        return toBO(requireTemplate(templateId));
    }

    @Override
    public DeliveryTemplateBO create(Long accountId, String name, String description) {
        XianyuDeliveryTemplate template = new XianyuDeliveryTemplate();
        template.setXianyuAccountId(accountId);
        template.setName(cleanRequired(name, "发货模板名称不能为空"));
        template.setDescription(clean(description));
        template.setEnabled(1);
        template.setDeliveryMode(1);
        template.setAutoDeliveryContent("");
        template.setKamiConfigIds("");
        template.setKamiDeliveryTemplate("");
        template.setAutoDeliveryImageUrl("");
        template.setAutoConfirmShipment(0);
        template.setMultiQuantityDelivery(1);
        templateMapper.insert(template);
        return detail(template.getId());
    }

    @Override
    public void update(
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
            Integer multiQuantityDelivery) {
        XianyuDeliveryTemplate template = requireTemplate(templateId);
        if (name != null) {
            template.setName(cleanRequired(name, "发货模板名称不能为空"));
        }
        if (description != null) {
            template.setDescription(clean(description));
        }
        if (enabled != null) {
            template.setEnabled(enabled == 1 ? 1 : 0);
        }
        if (deliveryMode != null) {
            template.setDeliveryMode(deliveryMode == 2 ? 2 : 1);
        }
        if (autoDeliveryContent != null) {
            template.setAutoDeliveryContent(clean(autoDeliveryContent));
        }
        if (kamiConfigIds != null) {
            template.setKamiConfigIds(clean(kamiConfigIds));
        }
        if (kamiDeliveryTemplate != null) {
            template.setKamiDeliveryTemplate(clean(kamiDeliveryTemplate));
        }
        if (autoDeliveryImageUrl != null) {
            template.setAutoDeliveryImageUrl(clean(autoDeliveryImageUrl));
        }
        if (autoConfirmShipment != null) {
            template.setAutoConfirmShipment(autoConfirmShipment == 1 ? 1 : 0);
        }
        if (multiQuantityDelivery != null) {
            template.setMultiQuantityDelivery(multiQuantityDelivery == 0 ? 0 : 1);
        }
        validateTemplateConfig(template);
        templateMapper.updateById(template);
    }

    @Override
    public void delete(Long templateId) {
        requireTemplate(templateId);
        bindingMapper.deleteByTemplateId(templateId);
        templateMapper.deleteById(templateId);
    }

    @Override
    public DeliveryTemplateBO createFromGoods(Long accountId, String xyGoodsId, String name, String description) {
        XianyuGoodsAutoDeliveryConfig config = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
        if (config == null) {
            throw new RuntimeException("当前商品还没有自动发货配置");
        }
        DeliveryTemplateBO created = create(accountId, cleanRequired(name, "发货模板名称不能为空"), description);
        XianyuDeliveryTemplate template = requireTemplate(created.getId());
        template.setDeliveryMode(config.getDeliveryMode() == null ? 1 : config.getDeliveryMode());
        template.setAutoDeliveryContent(nullToEmpty(config.getAutoDeliveryContent()));
        template.setKamiConfigIds(nullToEmpty(config.getKamiConfigIds()));
        template.setKamiDeliveryTemplate(nullToEmpty(config.getKamiDeliveryTemplate()));
        template.setAutoDeliveryImageUrl(nullToEmpty(config.getAutoDeliveryImageUrl()));
        template.setAutoConfirmShipment(config.getAutoConfirmShipment() == null ? 0 : config.getAutoConfirmShipment());
        template.setMultiQuantityDelivery(config.getMultiQuantityDelivery() == null ? 1 : config.getMultiQuantityDelivery());
        validateTemplateConfig(template);
        templateMapper.updateById(template);
        return detail(template.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyToGoods(Long accountId, List<String> xyGoodsIds, Long templateId, Integer enableAutoDelivery) {
        XianyuDeliveryTemplate template = requireTemplate(templateId);
        if (!Integer.valueOf(1).equals(template.getEnabled())) {
            throw new RuntimeException("发货模板已停用");
        }
        validateTemplateConfig(template);
        if (accountId == null || xyGoodsIds == null || xyGoodsIds.isEmpty()) {
            throw new RuntimeException("请选择要套用的商品");
        }
        Set<String> uniqueGoodsIds = xyGoodsIds.stream()
                .map(this::clean)
                .filter(value -> value != null && !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int changed = 0;
        for (String xyGoodsId : uniqueGoodsIds) {
            bind(accountId, xyGoodsId, templateId, enableAutoDelivery);
            changed++;
        }
        return changed;
    }

    @Override
    public List<DeliveryTemplateBindingBO> bindings(Long accountId, String xyGoodsId) {
        if (accountId == null || !hasText(xyGoodsId)) {
            return Collections.emptyList();
        }
        List<DeliveryTemplateBindingBO> bindings = bindingMapper.selectByGoods(accountId, xyGoodsId);
        return bindings == null ? Collections.emptyList() : bindings;
    }

    @Override
    public DeliveryTemplateBindingBO bind(Long accountId, String xyGoodsId, Long templateId, Integer enableAutoDelivery) {
        if (accountId == null || !hasText(xyGoodsId)) {
            throw new RuntimeException("请选择要绑定的商品");
        }
        XianyuDeliveryTemplate template = requireTemplate(templateId);
        if (!Integer.valueOf(1).equals(template.getEnabled())) {
            throw new RuntimeException("发货模板已停用");
        }
        validateTemplateConfig(template);

        XianyuDeliveryTemplateBinding existing = bindingMapper.selectByGoodsKey(accountId, xyGoodsId);
        if (existing == null) {
            existing = new XianyuDeliveryTemplateBinding();
            existing.setXianyuAccountId(accountId);
            existing.setXyGoodsId(cleanRequired(xyGoodsId, "商品ID不能为空"));
            existing.setTemplateId(templateId);
            existing.setEnabled(1);
            bindingMapper.insert(existing);
        } else {
            existing.setTemplateId(templateId);
            existing.setEnabled(1);
            bindingMapper.updateById(existing);
        }
        if (enableAutoDelivery == null || enableAutoDelivery == 1) {
            enableAutoDelivery(accountId, xyGoodsId);
        }
        return bindingMapper.selectActiveByGoods(accountId, xyGoodsId);
    }

    @Override
    public void unbind(Long bindingId) {
        if (bindingId == null) {
            throw new RuntimeException("缺少绑定ID");
        }
        bindingMapper.deleteById(bindingId);
    }

    @Override
    public EffectiveDeliveryConfigBO effectiveConfig(Long accountId, String xyGoodsId, String skuId) {
        return deliveryTemplateResolver.getEffectiveConfig(accountId, xyGoodsId, skuId);
    }

    private void enableAutoDelivery(Long accountId, String xyGoodsId) {
        XianyuGoodsConfig config = autoDeliveryService.getGoodsConfig(accountId, xyGoodsId);
        if (config == null) {
            config = new XianyuGoodsConfig();
            config.setXianyuAccountId(accountId);
            config.setXyGoodsId(xyGoodsId);
            config.setXianyuAutoReplyOn(0);
            config.setXianyuAutoReplyContextOn(1);
            config.setXianyuKeywordReplyOn(0);
            config.setHumanInterventionOn(0);
            config.setHumanInterventionMinutes(10);
            config.setFirstReplyOn(0);
            config.setFirstReplySkipManualOn(0);
            config.setFirstReplyText("");
            config.setFirstReplyImageUrl("");
            config.setFixedMaterial("");
            String now = LocalDateTime.now().toString().replace('T', ' ');
            config.setCreateTime(now);
            config.setUpdateTime(now);
        }
        config.setXianyuAutoDeliveryOn(1);
        autoDeliveryService.saveOrUpdateGoodsConfig(config);
    }

    private void validateTemplateConfig(XianyuDeliveryTemplate template) {
        int mode = template.getDeliveryMode() == null ? 1 : template.getDeliveryMode();
        if (mode == 1 && !hasText(template.getAutoDeliveryContent())) {
            throw new RuntimeException("文本发货模板需要填写发货内容");
        }
        if (mode == 2 && !hasText(template.getKamiConfigIds())) {
            throw new RuntimeException("卡密发货模板需要绑定卡密仓库");
        }
    }

    private XianyuDeliveryTemplate requireTemplate(Long templateId) {
        XianyuDeliveryTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new RuntimeException("发货模板不存在");
        }
        return template;
    }

    private DeliveryTemplateBO toBO(XianyuDeliveryTemplate template) {
        DeliveryTemplateBO bo = new DeliveryTemplateBO();
        BeanUtils.copyProperties(template, bo);
        bo.setDeliveryMode(template.getDeliveryMode() == null ? 1 : template.getDeliveryMode());
        bo.setAutoDeliveryContent(nullToEmpty(template.getAutoDeliveryContent()));
        bo.setKamiConfigIds(nullToEmpty(template.getKamiConfigIds()));
        bo.setKamiDeliveryTemplate(nullToEmpty(template.getKamiDeliveryTemplate()));
        bo.setAutoDeliveryImageUrl(nullToEmpty(template.getAutoDeliveryImageUrl()));
        bo.setAutoConfirmShipment(template.getAutoConfirmShipment() == null ? 0 : template.getAutoConfirmShipment());
        bo.setMultiQuantityDelivery(template.getMultiQuantityDelivery() == null ? 1 : template.getMultiQuantityDelivery());
        bo.setBindingCount(templateMapper.countEnabledBindings(template.getId()));
        return bo;
    }

    private String cleanRequired(String value, String message) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.isEmpty()) {
            throw new RuntimeException(message);
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
