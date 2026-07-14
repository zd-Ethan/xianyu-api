package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.bo.EffectiveKeywordRuleBO;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveReplyConfigBO;
import com.feijimiao.xianyuassistant.entity.bo.ReplyTemplateBO;
import com.feijimiao.xianyuassistant.entity.bo.ReplyTemplateBindingBO;

import java.util.List;

public interface ReplyTemplateService {

    List<ReplyTemplateBO> list(Long accountId);

    ReplyTemplateBO detail(Long templateId);

    ReplyTemplateBO create(Long accountId, String name, String description);

    void update(Long templateId, String name, String description, Integer enabled);

    void updateConfig(
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
            String fixedMaterial);

    void delete(Long templateId);

    ReplyTemplateBO.TemplateKeywordRuleBO addRule(Long templateId, String keyword, Integer matchMode);

    ReplyTemplateBO.TemplateKeywordRuleBO ensureFallbackRule(Long templateId);

    void updateRule(Long ruleId, String keyword, Integer matchMode);

    void deleteRule(Long ruleId);

    ReplyTemplateBO.TemplateKeywordContentBO addContent(Long ruleId, String replyText, String replyImageUrl);

    void updateContent(Long contentId, String replyText, String replyImageUrl);

    void deleteContent(Long contentId);

    List<ReplyTemplateBindingBO> listBindings(Long accountId, String xyGoodsId);

    ReplyTemplateBindingBO bind(Long accountId, String xyGoodsId, Long templateId);

    void updateBinding(Long bindingId, Integer enabled, Integer sortOrder);

    void unbind(Long bindingId);

    List<EffectiveKeywordRuleBO> effectiveRules(Long accountId, String xyGoodsId);

    EffectiveReplyConfigBO effectiveConfig(Long accountId, String xyGoodsId);

    int batchBind(Long accountId, List<String> xyGoodsIds, Long templateId);

    ReplyTemplateBO createFromGoods(Long accountId, String xyGoodsId, String name, String description);
}
