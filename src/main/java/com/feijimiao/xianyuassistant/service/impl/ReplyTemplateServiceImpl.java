package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuKeywordReplyContent;
import com.feijimiao.xianyuassistant.entity.XianyuKeywordReplyRule;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplate;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplateBinding;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplateKeywordContent;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplateKeywordRule;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveKeywordRuleBO;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveReplyConfigBO;
import com.feijimiao.xianyuassistant.entity.bo.ReplyTemplateBO;
import com.feijimiao.xianyuassistant.entity.bo.ReplyTemplateBindingBO;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuKeywordReplyContentMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuKeywordReplyRuleMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuReplyTemplateBindingMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuReplyTemplateKeywordContentMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuReplyTemplateKeywordRuleMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuReplyTemplateMapper;
import com.feijimiao.xianyuassistant.service.ReplyTemplateResolver;
import com.feijimiao.xianyuassistant.service.ReplyTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReplyTemplateServiceImpl implements ReplyTemplateService {

    @Autowired
    private XianyuReplyTemplateMapper templateMapper;

    @Autowired
    private XianyuReplyTemplateKeywordRuleMapper templateRuleMapper;

    @Autowired
    private XianyuReplyTemplateKeywordContentMapper templateContentMapper;

    @Autowired
    private XianyuReplyTemplateBindingMapper bindingMapper;

    @Autowired
    private XianyuKeywordReplyRuleMapper localRuleMapper;

    @Autowired
    private XianyuKeywordReplyContentMapper localContentMapper;

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;

    @Autowired
    private ReplyTemplateResolver resolver;

    @Override
    public List<ReplyTemplateBO> list(Long accountId) {
        List<XianyuReplyTemplate> templates = templateMapper.selectAvailable(accountId);
        if (templates == null) {
            return Collections.emptyList();
        }
        return templates.stream().map(template -> toTemplateBO(template, false)).collect(Collectors.toList());
    }

    @Override
    public ReplyTemplateBO detail(Long templateId) {
        XianyuReplyTemplate template = requireTemplate(templateId);
        return toTemplateBO(template, true);
    }

    @Override
    public ReplyTemplateBO create(Long accountId, String name, String description) {
        String cleanName = cleanRequired(name, "模板名称不能为空");
        XianyuReplyTemplate template = new XianyuReplyTemplate();
        template.setXianyuAccountId(accountId);
        template.setName(cleanName);
        template.setDescription(clean(description));
        template.setEnabled(1);
        template.setXianyuKeywordReplyOn(0);
        templateMapper.insert(template);
        return detail(template.getId());
    }

    @Override
    public void update(Long templateId, String name, String description, Integer enabled) {
        XianyuReplyTemplate template = requireTemplate(templateId);
        if (name != null) {
            template.setName(cleanRequired(name, "模板名称不能为空"));
        }
        if (description != null) {
            template.setDescription(clean(description));
        }
        if (enabled != null) {
            template.setEnabled(enabled == 1 ? 1 : 0);
        }
        templateMapper.updateById(template);
    }

    @Override
    public void updateConfig(
            Long templateId,
            Integer firstReplyOn,
            Integer firstReplySkipManualOn,
            String firstReplyText,
            String firstReplyImageUrl,
            Integer xianyuAutoReplyOn,
            Integer xianyuAutoReplyContextOn,
            Integer xianyuKeywordReplyOn,
            Integer humanInterventionOn,
            Integer humanInterventionMinutes,
            Integer ragDelaySeconds,
            String fixedMaterial) {
        XianyuReplyTemplate template = requireTemplate(templateId);
        if (firstReplyOn != null) {
            template.setFirstReplyOn(normalizeSwitch(firstReplyOn));
        }
        if (firstReplySkipManualOn != null) {
            template.setFirstReplySkipManualOn(normalizeSwitch(firstReplySkipManualOn));
        }
        if (firstReplyText != null) {
            template.setFirstReplyText(clean(firstReplyText));
        }
        if (firstReplyImageUrl != null) {
            template.setFirstReplyImageUrl(clean(firstReplyImageUrl));
        }
        if (xianyuAutoReplyOn != null) {
            template.setXianyuAutoReplyOn(normalizeSwitch(xianyuAutoReplyOn));
        }
        if (xianyuAutoReplyContextOn != null) {
            template.setXianyuAutoReplyContextOn(normalizeSwitch(xianyuAutoReplyContextOn));
        }
        if (xianyuKeywordReplyOn != null) {
            template.setXianyuKeywordReplyOn(normalizeSwitch(xianyuKeywordReplyOn));
        }
        if (humanInterventionOn != null) {
            template.setHumanInterventionOn(normalizeSwitch(humanInterventionOn));
        }
        if (humanInterventionMinutes != null) {
            template.setHumanInterventionMinutes(Math.max(1, Math.min(120, humanInterventionMinutes)));
        }
        if (ragDelaySeconds != null) {
            template.setRagDelaySeconds(ragDelaySeconds > 0 ? Math.max(2, Math.min(120, ragDelaySeconds)) : null);
        }
        if (fixedMaterial != null) {
            template.setFixedMaterial(clean(fixedMaterial));
        }
        templateMapper.updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long templateId) {
        XianyuReplyTemplate template = requireTemplate(templateId);
        List<XianyuReplyTemplateKeywordRule> rules = templateRuleMapper.selectByTemplateId(template.getId());
        if (rules != null) {
            for (XianyuReplyTemplateKeywordRule rule : rules) {
                templateContentMapper.deleteByTemplateRuleId(rule.getId());
            }
        }
        templateRuleMapper.deleteByTemplateId(template.getId());
        bindingMapper.deleteByTemplateId(template.getId());
        templateMapper.deleteById(template.getId());
    }

    @Override
    public ReplyTemplateBO.TemplateKeywordRuleBO addRule(Long templateId, String keyword, Integer matchMode) {
        requireTemplate(templateId);
        String cleanKeyword = cleanRequired(keyword, "关键词不能为空");
        Integer cleanMatchMode = matchMode != null && matchMode == 2 ? 2 : 1;
        XianyuReplyTemplateKeywordRule existing = templateRuleMapper.selectByKeyword(templateId, cleanKeyword, cleanMatchMode);
        if (existing != null) {
            return toRuleBO(existing, templateContentMapper.selectByTemplateRuleId(existing.getId()));
        }

        XianyuReplyTemplateKeywordRule rule = new XianyuReplyTemplateKeywordRule();
        rule.setTemplateId(templateId);
        rule.setKeyword(cleanKeyword);
        rule.setMatchMode(cleanMatchMode);
        rule.setIsFallback(0);
        templateRuleMapper.insert(rule);
        return toRuleBO(rule, Collections.emptyList());
    }

    @Override
    public ReplyTemplateBO.TemplateKeywordRuleBO ensureFallbackRule(Long templateId) {
        requireTemplate(templateId);
        XianyuReplyTemplateKeywordRule existing = templateRuleMapper.selectFallback(templateId);
        if (existing != null) {
            return toRuleBO(existing, templateContentMapper.selectByTemplateRuleId(existing.getId()));
        }

        XianyuReplyTemplateKeywordRule rule = new XianyuReplyTemplateKeywordRule();
        rule.setTemplateId(templateId);
        rule.setKeyword("__fallback__");
        rule.setMatchMode(1);
        rule.setIsFallback(1);
        templateRuleMapper.insert(rule);
        return toRuleBO(rule, Collections.emptyList());
    }

    @Override
    public void updateRule(Long ruleId, String keyword, Integer matchMode) {
        XianyuReplyTemplateKeywordRule rule = requireRule(ruleId);
        if (!Integer.valueOf(1).equals(rule.getIsFallback())) {
            if (keyword != null) {
                rule.setKeyword(cleanRequired(keyword, "关键词不能为空"));
            }
            if (matchMode != null) {
                rule.setMatchMode(matchMode == 2 ? 2 : 1);
            }
        }
        templateRuleMapper.updateById(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long ruleId) {
        requireRule(ruleId);
        templateContentMapper.deleteByTemplateRuleId(ruleId);
        templateRuleMapper.deleteById(ruleId);
    }

    @Override
    public ReplyTemplateBO.TemplateKeywordContentBO addContent(Long ruleId, String replyText, String replyImageUrl) {
        requireRule(ruleId);
        XianyuReplyTemplateKeywordContent content = new XianyuReplyTemplateKeywordContent();
        content.setTemplateRuleId(ruleId);
        content.setReplyText(clean(replyText));
        content.setReplyImageUrl(clean(replyImageUrl));
        templateContentMapper.insert(content);
        return toContentBO(content);
    }

    @Override
    public void updateContent(Long contentId, String replyText, String replyImageUrl) {
        XianyuReplyTemplateKeywordContent content = templateContentMapper.selectById(contentId);
        if (content == null) {
            throw new RuntimeException("回复内容不存在");
        }
        content.setReplyText(clean(replyText));
        content.setReplyImageUrl(clean(replyImageUrl));
        templateContentMapper.updateById(content);
    }

    @Override
    public void deleteContent(Long contentId) {
        templateContentMapper.deleteById(contentId);
    }

    @Override
    public List<ReplyTemplateBindingBO> listBindings(Long accountId, String xyGoodsId) {
        return bindingMapper.selectByGoods(accountId, xyGoodsId);
    }

    @Override
    public ReplyTemplateBindingBO bind(Long accountId, String xyGoodsId, Long templateId) {
        requireTemplate(templateId);
        if (accountId == null || xyGoodsId == null || xyGoodsId.trim().isEmpty()) {
            throw new RuntimeException("商品信息不能为空");
        }
        XianyuReplyTemplateBinding existing = bindingMapper.selectByGoodsAndTemplate(accountId, xyGoodsId, templateId);
        if (existing == null) {
            XianyuReplyTemplateBinding binding = new XianyuReplyTemplateBinding();
            binding.setXianyuAccountId(accountId);
            binding.setXyGoodsId(xyGoodsId);
            binding.setTemplateId(templateId);
            binding.setSortOrder(bindingMapper.maxSortOrder(accountId, xyGoodsId) + 10);
            binding.setEnabled(1);
            bindingMapper.insert(binding);
        } else if (!Integer.valueOf(1).equals(existing.getEnabled())) {
            existing.setEnabled(1);
            bindingMapper.updateById(existing);
        }
        return findBinding(accountId, xyGoodsId, templateId);
    }

    @Override
    public void updateBinding(Long bindingId, Integer enabled, Integer sortOrder) {
        XianyuReplyTemplateBinding binding = bindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new RuntimeException("模板绑定不存在");
        }
        if (enabled != null) {
            binding.setEnabled(enabled == 1 ? 1 : 0);
        }
        if (sortOrder != null) {
            binding.setSortOrder(sortOrder);
        }
        bindingMapper.updateById(binding);
    }

    @Override
    public void unbind(Long bindingId) {
        bindingMapper.deleteById(bindingId);
    }

    @Override
    public List<EffectiveKeywordRuleBO> effectiveRules(Long accountId, String xyGoodsId) {
        return resolver.getEffectiveRules(accountId, xyGoodsId, true);
    }

    @Override
    public EffectiveReplyConfigBO effectiveConfig(Long accountId, String xyGoodsId) {
        return resolver.getEffectiveConfig(accountId, xyGoodsId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchBind(Long accountId, List<String> xyGoodsIds, Long templateId) {
        requireTemplate(templateId);
        if (accountId == null || xyGoodsIds == null || xyGoodsIds.isEmpty()) {
            throw new RuntimeException("商品信息不能为空");
        }
        int changed = 0;
        Set<String> uniqueGoodsIds = xyGoodsIds.stream()
                .map(this::clean)
                .filter(value -> value != null && !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String xyGoodsId : uniqueGoodsIds) {
            XianyuReplyTemplateBinding existing = bindingMapper.selectByGoodsAndTemplate(accountId, xyGoodsId, templateId);
            bind(accountId, xyGoodsId, templateId);
            if (existing == null || !Integer.valueOf(1).equals(existing.getEnabled())) {
                changed++;
            }
        }
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReplyTemplateBO createFromGoods(Long accountId, String xyGoodsId, String name, String description) {
        ReplyTemplateBO template = create(accountId, name, description);
        XianyuReplyTemplate templateEntity = requireTemplate(template.getId());
        XianyuGoodsConfig localConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        if (localConfig != null) {
            templateEntity.setFirstReplyOn(localConfig.getFirstReplyOn());
            templateEntity.setFirstReplySkipManualOn(localConfig.getFirstReplySkipManualOn());
            templateEntity.setFirstReplyText(localConfig.getFirstReplyText());
            templateEntity.setFirstReplyImageUrl(localConfig.getFirstReplyImageUrl());
            templateEntity.setXianyuAutoReplyOn(localConfig.getXianyuAutoReplyOn());
            templateEntity.setXianyuAutoReplyContextOn(localConfig.getXianyuAutoReplyContextOn());
            templateEntity.setXianyuKeywordReplyOn(localConfig.getXianyuKeywordReplyOn());
            templateEntity.setHumanInterventionOn(localConfig.getHumanInterventionOn());
            templateEntity.setHumanInterventionMinutes(localConfig.getHumanInterventionMinutes());
            templateEntity.setFixedMaterial(localConfig.getFixedMaterial());
        }
        XianyuGoodsAutoDeliveryConfig deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
        if (deliveryConfig != null) {
            templateEntity.setRagDelaySeconds(deliveryConfig.getRagDelaySeconds());
        }
        templateMapper.updateById(templateEntity);

        List<XianyuKeywordReplyRule> localRules = localRuleMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        if (localRules != null) {
            for (XianyuKeywordReplyRule localRule : localRules) {
                XianyuReplyTemplateKeywordRule templateRule = new XianyuReplyTemplateKeywordRule();
                templateRule.setTemplateId(template.getId());
                templateRule.setKeyword(localRule.getKeyword());
                templateRule.setMatchMode(localRule.getMatchMode());
                templateRule.setIsFallback(localRule.getIsFallback());
                templateRuleMapper.insert(templateRule);

                List<XianyuKeywordReplyContent> localContents = localContentMapper.selectByRuleId(localRule.getId());
                if (localContents != null) {
                    for (XianyuKeywordReplyContent localContent : localContents) {
                        XianyuReplyTemplateKeywordContent templateContent = new XianyuReplyTemplateKeywordContent();
                        templateContent.setTemplateRuleId(templateRule.getId());
                        templateContent.setReplyText(localContent.getReplyText());
                        templateContent.setReplyImageUrl(localContent.getReplyImageUrl());
                        templateContentMapper.insert(templateContent);
                    }
                }
            }
        }
        return detail(template.getId());
    }

    private ReplyTemplateBindingBO findBinding(Long accountId, String xyGoodsId, Long templateId) {
        return bindingMapper.selectByGoods(accountId, xyGoodsId).stream()
                .filter(binding -> templateId.equals(binding.getTemplateId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("模板绑定不存在"));
    }

    private XianyuReplyTemplate requireTemplate(Long templateId) {
        XianyuReplyTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new RuntimeException("回复模板不存在");
        }
        return template;
    }

    private XianyuReplyTemplateKeywordRule requireRule(Long ruleId) {
        XianyuReplyTemplateKeywordRule rule = templateRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new RuntimeException("模板关键词规则不存在");
        }
        return rule;
    }

    private ReplyTemplateBO toTemplateBO(XianyuReplyTemplate template, boolean includeRules) {
        ReplyTemplateBO bo = new ReplyTemplateBO();
        bo.setId(template.getId());
        bo.setXianyuAccountId(template.getXianyuAccountId());
        bo.setName(template.getName());
        bo.setDescription(template.getDescription());
        bo.setEnabled(template.getEnabled());
        bo.setCreateTime(template.getCreateTime());
        bo.setUpdateTime(template.getUpdateTime());
        bo.setBindingCount(templateMapper.countBindings(template.getId()));
        bo.setFirstReplyOn(defaultZero(template.getFirstReplyOn()));
        bo.setFirstReplySkipManualOn(defaultZero(template.getFirstReplySkipManualOn()));
        bo.setFirstReplyText(nullToEmpty(template.getFirstReplyText()));
        bo.setFirstReplyImageUrl(nullToEmpty(template.getFirstReplyImageUrl()));
        bo.setXianyuAutoReplyOn(defaultZero(template.getXianyuAutoReplyOn()));
        bo.setXianyuAutoReplyContextOn(template.getXianyuAutoReplyContextOn() == null ? 1 : template.getXianyuAutoReplyContextOn());
        bo.setXianyuKeywordReplyOn(defaultZero(template.getXianyuKeywordReplyOn()));
        bo.setHumanInterventionOn(defaultZero(template.getHumanInterventionOn()));
        bo.setHumanInterventionMinutes(template.getHumanInterventionMinutes() == null ? 10 : template.getHumanInterventionMinutes());
        bo.setRagDelaySeconds(template.getRagDelaySeconds());
        bo.setFixedMaterial(nullToEmpty(template.getFixedMaterial()));
        if (includeRules) {
            List<XianyuReplyTemplateKeywordRule> rules = templateRuleMapper.selectByTemplateId(template.getId());
            bo.setRules(rules == null ? Collections.emptyList() : rules.stream()
                    .map(rule -> toRuleBO(rule, templateContentMapper.selectByTemplateRuleId(rule.getId())))
                    .collect(Collectors.toList()));
        } else {
            bo.setRules(Collections.emptyList());
        }
        return bo;
    }

    private ReplyTemplateBO.TemplateKeywordRuleBO toRuleBO(
            XianyuReplyTemplateKeywordRule rule,
            List<XianyuReplyTemplateKeywordContent> contents) {
        ReplyTemplateBO.TemplateKeywordRuleBO bo = new ReplyTemplateBO.TemplateKeywordRuleBO();
        bo.setId(rule.getId());
        bo.setTemplateId(rule.getTemplateId());
        bo.setKeyword(rule.getKeyword());
        bo.setMatchMode(rule.getMatchMode());
        bo.setIsFallback(rule.getIsFallback());
        bo.setContents(contents == null ? Collections.emptyList() : contents.stream()
                .map(this::toContentBO)
                .collect(Collectors.toList()));
        return bo;
    }

    private ReplyTemplateBO.TemplateKeywordContentBO toContentBO(XianyuReplyTemplateKeywordContent content) {
        ReplyTemplateBO.TemplateKeywordContentBO bo = new ReplyTemplateBO.TemplateKeywordContentBO();
        bo.setId(content.getId());
        bo.setTemplateRuleId(content.getTemplateRuleId());
        bo.setReplyText(content.getReplyText());
        bo.setReplyImageUrl(content.getReplyImageUrl());
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

    private Integer normalizeSwitch(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
