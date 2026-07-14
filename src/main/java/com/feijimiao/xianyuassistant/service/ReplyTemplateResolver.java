package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuKeywordReplyContent;
import com.feijimiao.xianyuassistant.entity.XianyuKeywordReplyRule;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplate;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplateKeywordContent;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplateKeywordRule;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveKeywordRuleBO;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveReplyConfigBO;
import com.feijimiao.xianyuassistant.entity.bo.KeywordReplyRuleBO;
import com.feijimiao.xianyuassistant.entity.bo.ReplyTemplateBindingBO;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuKeywordReplyContentMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuKeywordReplyRuleMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuReplyTemplateBindingMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuReplyTemplateKeywordContentMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuReplyTemplateKeywordRuleMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuReplyTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReplyTemplateResolver {

    private static final String SOURCE_LOCAL = "local";
    private static final String SOURCE_TEMPLATE = "template";
    private static final String LOCAL_SOURCE_NAME = "商品单独配置";
    private static final String DEFAULT_SOURCE_NAME = "默认配置";
    private static final int DEFAULT_DELAY_SECONDS = 15;
    private static final int DEFAULT_INTERVENTION_MINUTES = 10;

    @Autowired
    private XianyuKeywordReplyRuleMapper localRuleMapper;

    @Autowired
    private XianyuKeywordReplyContentMapper localContentMapper;

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;

    @Autowired
    private XianyuReplyTemplateBindingMapper bindingMapper;

    @Autowired
    private XianyuReplyTemplateMapper templateMapper;

    @Autowired
    private XianyuReplyTemplateKeywordRuleMapper templateRuleMapper;

    @Autowired
    private XianyuReplyTemplateKeywordContentMapper templateContentMapper;

    public EffectiveReplyConfigBO getEffectiveConfig(Long accountId, String xyGoodsId) {
        EffectiveReplyConfigBO config = defaultEffectiveConfig(accountId, xyGoodsId);
        if (accountId == null || xyGoodsId == null || xyGoodsId.trim().isEmpty()) {
            return config;
        }

        XianyuGoodsConfig localConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        if (localConfig != null) {
            applyLocalConfig(config, localConfig);
        }

        try {
            XianyuGoodsAutoDeliveryConfig deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
            if (deliveryConfig != null && deliveryConfig.getRagDelaySeconds() != null && deliveryConfig.getRagDelaySeconds() > 0) {
                config.setRagDelaySeconds(normalizeDelaySeconds(deliveryConfig.getRagDelaySeconds()));
                config.setRagDelaySourceName(LOCAL_SOURCE_NAME);
            }
        } catch (Exception ignored) {
            // Reply delay lives in the delivery config table historically. Missing rows simply fall back to templates/defaults.
        }

        List<ReplyTemplateBindingBO> bindings = bindingMapper.selectByGoods(accountId, xyGoodsId);
        if (bindings != null) {
            for (ReplyTemplateBindingBO binding : bindings) {
                if (!Integer.valueOf(1).equals(binding.getEnabled()) || !Integer.valueOf(1).equals(binding.getTemplateEnabled())) {
                    continue;
                }
                XianyuReplyTemplate template = templateMapper.selectById(binding.getTemplateId());
                if (template == null || !Integer.valueOf(1).equals(template.getEnabled())) {
                    continue;
                }
                applyTemplateConfig(config, template, binding.getTemplateName());
            }
        }

        return config;
    }

    public List<EffectiveKeywordRuleBO> getEffectiveRules(Long accountId, String xyGoodsId, boolean includeOverridden) {
        if (accountId == null || xyGoodsId == null || xyGoodsId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<EffectiveKeywordRuleBO> candidates = new ArrayList<>();
        candidates.addAll(loadLocalRules(accountId, xyGoodsId));
        candidates.addAll(loadTemplateRules(accountId, xyGoodsId));

        Map<String, EffectiveKeywordRuleBO> winnerByKey = new LinkedHashMap<>();
        List<EffectiveKeywordRuleBO> result = new ArrayList<>();
        for (EffectiveKeywordRuleBO rule : candidates) {
            String key = buildConflictKey(rule.getKeyword(), rule.getMatchMode(), rule.getIsFallback());
            rule.setConflictKey(key);
            EffectiveKeywordRuleBO winner = winnerByKey.get(key);
            if (winner == null) {
                rule.setOverridden(false);
                winnerByKey.put(key, rule);
                result.add(rule);
            } else {
                rule.setOverridden(true);
                rule.setOverriddenBy(winner.getSourceName());
                if (includeOverridden) {
                    result.add(rule);
                }
            }
        }
        return result;
    }

    public List<KeywordReplyRuleBO> matchKeyword(Long accountId, String xyGoodsId, String message) {
        if (message == null || message.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String msg = message.trim();
        List<EffectiveKeywordRuleBO> rules = getEffectiveRules(accountId, xyGoodsId, false);
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<EffectiveKeywordRuleBO> exactMatches = rules.stream()
                .filter(rule -> !isFallback(rule))
                .filter(rule -> Integer.valueOf(2).equals(rule.getMatchMode()))
                .filter(rule -> normalize(rule.getKeyword()).equals(normalize(msg)))
                .collect(Collectors.toList());

        List<EffectiveKeywordRuleBO> fuzzyMatches = rules.stream()
                .filter(rule -> !isFallback(rule))
                .filter(rule -> !Integer.valueOf(2).equals(rule.getMatchMode()))
                .filter(rule -> {
                    String keyword = normalize(rule.getKeyword());
                    return !keyword.isEmpty() && normalize(msg).contains(keyword);
                })
                .collect(Collectors.toList());

        List<EffectiveKeywordRuleBO> matched = new ArrayList<>();
        matched.addAll(exactMatches);
        matched.addAll(fuzzyMatches);
        if (!matched.isEmpty()) {
            return matched.stream().map(this::toKeywordRuleBO).collect(Collectors.toList());
        }

        return rules.stream()
                .filter(this::isFallback)
                .filter(rule -> rule.getContents() != null && !rule.getContents().isEmpty())
                .findFirst()
                .map(rule -> Collections.singletonList(toKeywordRuleBO(rule)))
                .orElse(Collections.emptyList());
    }

    private EffectiveReplyConfigBO defaultEffectiveConfig(Long accountId, String xyGoodsId) {
        EffectiveReplyConfigBO config = new EffectiveReplyConfigBO();
        config.setXianyuAccountId(accountId);
        config.setXyGoodsId(xyGoodsId);
        config.setXianyuAutoReplyOn(0);
        config.setXianyuAutoReplyContextOn(1);
        config.setXianyuKeywordReplyOn(0);
        config.setHumanInterventionOn(0);
        config.setHumanInterventionMinutes(DEFAULT_INTERVENTION_MINUTES);
        config.setFirstReplyOn(0);
        config.setFirstReplySkipManualOn(0);
        config.setFirstReplyText("");
        config.setFirstReplyImageUrl("");
        config.setFixedMaterial("");
        config.setRagDelaySeconds(DEFAULT_DELAY_SECONDS);
        config.setRagDelaySourceName(DEFAULT_SOURCE_NAME);
        return config;
    }

    private void applyLocalConfig(EffectiveReplyConfigBO target, XianyuGoodsConfig local) {
        if (isOn(local.getXianyuAutoReplyOn())) {
            target.setXianyuAutoReplyOn(1);
            target.setXianyuAutoReplyContextOn(local.getXianyuAutoReplyContextOn() == null ? 1 : normalizeSwitch(local.getXianyuAutoReplyContextOn()));
            target.setAiReplySourceName(LOCAL_SOURCE_NAME);
        }
        if (isOn(local.getXianyuKeywordReplyOn())) {
            target.setXianyuKeywordReplyOn(1);
            target.setKeywordReplySourceName(LOCAL_SOURCE_NAME);
        }
        if (isOn(local.getHumanInterventionOn())) {
            target.setHumanInterventionOn(1);
            target.setHumanInterventionMinutes(normalizeMinutes(local.getHumanInterventionMinutes()));
            target.setHumanInterventionSourceName(LOCAL_SOURCE_NAME);
        }
        if (isOn(local.getFirstReplyOn()) && (hasText(local.getFirstReplyText()) || hasText(local.getFirstReplyImageUrl()))) {
            target.setFirstReplyOn(1);
            target.setFirstReplySkipManualOn(normalizeSwitch(local.getFirstReplySkipManualOn()));
            target.setFirstReplyText(trimToEmpty(local.getFirstReplyText()));
            target.setFirstReplyImageUrl(trimToEmpty(local.getFirstReplyImageUrl()));
            target.setFirstReplySourceName(LOCAL_SOURCE_NAME);
        }
        if (hasText(local.getFixedMaterial())) {
            target.setFixedMaterial(local.getFixedMaterial().trim());
            target.setFixedMaterialSourceName(LOCAL_SOURCE_NAME);
        }
    }

    private void applyTemplateConfig(EffectiveReplyConfigBO target, XianyuReplyTemplate template, String sourceName) {
        String name = hasText(sourceName) ? sourceName : "模板 " + template.getId();
        if (!isOn(target.getFirstReplyOn())
                && isOn(template.getFirstReplyOn())
                && (hasText(template.getFirstReplyText()) || hasText(template.getFirstReplyImageUrl()))) {
            target.setFirstReplyOn(1);
            target.setFirstReplySkipManualOn(normalizeSwitch(template.getFirstReplySkipManualOn()));
            target.setFirstReplyText(trimToEmpty(template.getFirstReplyText()));
            target.setFirstReplyImageUrl(trimToEmpty(template.getFirstReplyImageUrl()));
            target.setFirstReplySourceName(name);
        }
        if (!isOn(target.getXianyuAutoReplyOn()) && isOn(template.getXianyuAutoReplyOn())) {
            target.setXianyuAutoReplyOn(1);
            target.setXianyuAutoReplyContextOn(template.getXianyuAutoReplyContextOn() == null ? 1 : normalizeSwitch(template.getXianyuAutoReplyContextOn()));
            target.setAiReplySourceName(name);
        }
        if (!isOn(target.getXianyuKeywordReplyOn()) && templateKeywordEnabled(template)) {
            target.setXianyuKeywordReplyOn(1);
            target.setKeywordReplySourceName(name);
        }
        if (!isOn(target.getHumanInterventionOn()) && isOn(template.getHumanInterventionOn())) {
            target.setHumanInterventionOn(1);
            target.setHumanInterventionMinutes(normalizeMinutes(template.getHumanInterventionMinutes()));
            target.setHumanInterventionSourceName(name);
        }
        if (!hasText(target.getFixedMaterial()) && hasText(template.getFixedMaterial())) {
            target.setFixedMaterial(template.getFixedMaterial().trim());
            target.setFixedMaterialSourceName(name);
        }
        if (DEFAULT_SOURCE_NAME.equals(target.getRagDelaySourceName())
                && template.getRagDelaySeconds() != null
                && template.getRagDelaySeconds() > 0) {
            target.setRagDelaySeconds(normalizeDelaySeconds(template.getRagDelaySeconds()));
            target.setRagDelaySourceName(name);
        }
    }

    private boolean templateKeywordEnabled(XianyuReplyTemplate template) {
        if (template == null || !isOn(template.getXianyuKeywordReplyOn())) {
            return false;
        }
        List<XianyuReplyTemplateKeywordRule> rules = templateRuleMapper.selectByTemplateId(template.getId());
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (XianyuReplyTemplateKeywordRule rule : rules) {
            List<XianyuReplyTemplateKeywordContent> contents = templateContentMapper.selectByTemplateRuleId(rule.getId());
            if (contents != null && contents.stream().anyMatch(content -> hasText(content.getReplyText()) || hasText(content.getReplyImageUrl()))) {
                return true;
            }
        }
        return false;
    }

    private List<EffectiveKeywordRuleBO> loadLocalRules(Long accountId, String xyGoodsId) {
        XianyuGoodsConfig localConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        if (localConfig == null || !isOn(localConfig.getXianyuKeywordReplyOn())) {
            return Collections.emptyList();
        }

        List<XianyuKeywordReplyRule> rules = localRuleMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<EffectiveKeywordRuleBO> result = new ArrayList<>();
        for (XianyuKeywordReplyRule rule : rules) {
            List<XianyuKeywordReplyContent> contents = localContentMapper.selectByRuleId(rule.getId());
            EffectiveKeywordRuleBO bo = new EffectiveKeywordRuleBO();
            bo.setId(rule.getId());
            bo.setSourceRuleId(rule.getId());
            bo.setSourceType(SOURCE_LOCAL);
            bo.setSourceName(LOCAL_SOURCE_NAME);
            bo.setSourceOrder(0);
            bo.setXianyuAccountId(accountId);
            bo.setXyGoodsId(xyGoodsId);
            bo.setKeyword(rule.getKeyword());
            bo.setMatchMode(rule.getMatchMode());
            bo.setIsFallback(rule.getIsFallback());
            bo.setContents(toLocalContentBO(contents));
            result.add(bo);
        }
        return result;
    }

    private List<EffectiveKeywordRuleBO> loadTemplateRules(Long accountId, String xyGoodsId) {
        List<ReplyTemplateBindingBO> bindings = bindingMapper.selectByGoods(accountId, xyGoodsId);
        if (bindings == null || bindings.isEmpty()) {
            return Collections.emptyList();
        }

        List<EffectiveKeywordRuleBO> result = new ArrayList<>();
        for (ReplyTemplateBindingBO binding : bindings) {
            if (!Integer.valueOf(1).equals(binding.getEnabled()) || !Integer.valueOf(1).equals(binding.getTemplateEnabled())) {
                continue;
            }
            XianyuReplyTemplate template = templateMapper.selectById(binding.getTemplateId());
            if (template == null || !isOn(template.getXianyuKeywordReplyOn())) {
                continue;
            }
            List<XianyuReplyTemplateKeywordRule> rules = templateRuleMapper.selectByTemplateId(binding.getTemplateId());
            if (rules == null || rules.isEmpty()) {
                continue;
            }
            for (XianyuReplyTemplateKeywordRule rule : rules) {
                List<XianyuReplyTemplateKeywordContent> contents = templateContentMapper.selectByTemplateRuleId(rule.getId());
                EffectiveKeywordRuleBO bo = new EffectiveKeywordRuleBO();
                bo.setId(rule.getId());
                bo.setSourceRuleId(rule.getId());
                bo.setTemplateId(binding.getTemplateId());
                bo.setSourceType(SOURCE_TEMPLATE);
                bo.setSourceName(binding.getTemplateName());
                bo.setSourceOrder(binding.getSortOrder());
                bo.setXianyuAccountId(accountId);
                bo.setXyGoodsId(xyGoodsId);
                bo.setKeyword(rule.getKeyword());
                bo.setMatchMode(rule.getMatchMode());
                bo.setIsFallback(rule.getIsFallback());
                bo.setContents(toTemplateContentBO(contents));
                result.add(bo);
            }
        }
        return result;
    }

    private List<KeywordReplyRuleBO.KeywordReplyContentBO> toLocalContentBO(List<XianyuKeywordReplyContent> contents) {
        if (contents == null) {
            return Collections.emptyList();
        }
        return contents.stream().map(content -> {
            KeywordReplyRuleBO.KeywordReplyContentBO bo = new KeywordReplyRuleBO.KeywordReplyContentBO();
            bo.setId(content.getId());
            bo.setRuleId(content.getRuleId());
            bo.setReplyText(content.getReplyText());
            bo.setReplyImageUrl(content.getReplyImageUrl());
            return bo;
        }).collect(Collectors.toList());
    }

    private List<KeywordReplyRuleBO.KeywordReplyContentBO> toTemplateContentBO(List<XianyuReplyTemplateKeywordContent> contents) {
        if (contents == null) {
            return Collections.emptyList();
        }
        return contents.stream().map(content -> {
            KeywordReplyRuleBO.KeywordReplyContentBO bo = new KeywordReplyRuleBO.KeywordReplyContentBO();
            bo.setId(content.getId());
            bo.setRuleId(content.getTemplateRuleId());
            bo.setReplyText(content.getReplyText());
            bo.setReplyImageUrl(content.getReplyImageUrl());
            return bo;
        }).collect(Collectors.toList());
    }

    private KeywordReplyRuleBO toKeywordRuleBO(EffectiveKeywordRuleBO rule) {
        KeywordReplyRuleBO bo = new KeywordReplyRuleBO();
        bo.setId(rule.getSourceRuleId());
        bo.setXianyuAccountId(rule.getXianyuAccountId());
        bo.setXyGoodsId(rule.getXyGoodsId());
        bo.setKeyword(rule.getKeyword());
        bo.setMatchMode(rule.getMatchMode());
        bo.setIsFallback(rule.getIsFallback());
        bo.setContents(rule.getContents() != null ? rule.getContents() : Collections.emptyList());
        return bo;
    }

    private boolean isFallback(EffectiveKeywordRuleBO rule) {
        return Integer.valueOf(1).equals(rule.getIsFallback());
    }

    private String buildConflictKey(String keyword, Integer matchMode, Integer isFallback) {
        if (Integer.valueOf(1).equals(isFallback)) {
            return "__fallback__";
        }
        return normalize(keyword) + "#" + (matchMode != null ? matchMode : 1);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isOn(Integer value) {
        return value != null && value == 1;
    }

    private int normalizeSwitch(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private int normalizeMinutes(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_INTERVENTION_MINUTES;
        }
        return Math.max(1, Math.min(120, value));
    }

    private int normalizeDelaySeconds(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_DELAY_SECONDS;
        }
        return Math.max(2, Math.min(120, value));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
