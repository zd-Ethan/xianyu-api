package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveKeywordRuleBO;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveReplyConfigBO;
import com.feijimiao.xianyuassistant.entity.bo.ReplyTemplateBO;
import com.feijimiao.xianyuassistant.entity.bo.ReplyTemplateBindingBO;
import com.feijimiao.xianyuassistant.service.ReplyTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reply-template")
@CrossOrigin(origins = "*")
public class ReplyTemplateController {

    @Autowired
    private ReplyTemplateService replyTemplateService;

    @PostMapping("/list")
    public ResultObject<List<ReplyTemplateBO>> list(@RequestBody(required = false) Map<String, Object> params) {
        try {
            Long accountId = getOptionalLong(params, "xianyuAccountId");
            return ResultObject.success(replyTemplateService.list(accountId));
        } catch (Exception e) {
            log.error("获取回复模板列表失败", e);
            return ResultObject.failed("获取回复模板列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/detail")
    public ResultObject<ReplyTemplateBO> detail(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.detail(getRequiredLong(params, "templateId")));
        } catch (Exception e) {
            log.error("获取回复模板详情失败", e);
            return ResultObject.failed("获取回复模板详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResultObject<ReplyTemplateBO> create(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.create(
                    getOptionalLong(params, "xianyuAccountId"),
                    getString(params, "name"),
                    getString(params, "description")));
        } catch (Exception e) {
            log.error("创建回复模板失败", e);
            return ResultObject.failed("创建回复模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResultObject<?> update(@RequestBody Map<String, Object> params) {
        try {
            replyTemplateService.update(
                    getRequiredLong(params, "templateId"),
                    getString(params, "name"),
                    getString(params, "description"),
                    getOptionalInteger(params, "enabled"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("更新回复模板失败", e);
            return ResultObject.failed("更新回复模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/updateConfig")
    public ResultObject<?> updateConfig(@RequestBody Map<String, Object> params) {
        try {
            replyTemplateService.updateConfig(
                    getRequiredLong(params, "templateId"),
                    getOptionalInteger(params, "firstReplyOn"),
                    getOptionalInteger(params, "firstReplySkipManualOn"),
                    getString(params, "firstReplyText"),
                    getString(params, "firstReplyImageUrl"),
                    getOptionalInteger(params, "xianyuAutoReplyOn"),
                    getOptionalInteger(params, "xianyuAutoReplyContextOn"),
                    getOptionalInteger(params, "xianyuKeywordReplyOn"),
                    getOptionalInteger(params, "humanInterventionOn"),
                    getOptionalInteger(params, "humanInterventionMinutes"),
                    getOptionalInteger(params, "ragDelaySeconds"),
                    getString(params, "fixedMaterial"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("更新回复模板配置失败", e);
            return ResultObject.failed("更新回复模板配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResultObject<?> delete(@RequestBody Map<String, Object> params) {
        try {
            replyTemplateService.delete(getRequiredLong(params, "templateId"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("删除回复模板失败", e);
            return ResultObject.failed("删除回复模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/addRule")
    public ResultObject<ReplyTemplateBO.TemplateKeywordRuleBO> addRule(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.addRule(
                    getRequiredLong(params, "templateId"),
                    getString(params, "keyword"),
                    getOptionalInteger(params, "matchMode")));
        } catch (Exception e) {
            log.error("添加模板关键词失败", e);
            return ResultObject.failed("添加模板关键词失败: " + e.getMessage());
        }
    }

    @PostMapping("/ensureFallbackRule")
    public ResultObject<ReplyTemplateBO.TemplateKeywordRuleBO> ensureFallbackRule(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.ensureFallbackRule(getRequiredLong(params, "templateId")));
        } catch (Exception e) {
            log.error("获取模板兜底回复失败", e);
            return ResultObject.failed("获取模板兜底回复失败: " + e.getMessage());
        }
    }

    @PostMapping("/updateRule")
    public ResultObject<?> updateRule(@RequestBody Map<String, Object> params) {
        try {
            replyTemplateService.updateRule(
                    getRequiredLong(params, "ruleId"),
                    getString(params, "keyword"),
                    getOptionalInteger(params, "matchMode"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("更新模板关键词失败", e);
            return ResultObject.failed("更新模板关键词失败: " + e.getMessage());
        }
    }

    @PostMapping("/deleteRule")
    public ResultObject<?> deleteRule(@RequestBody Map<String, Object> params) {
        try {
            replyTemplateService.deleteRule(getRequiredLong(params, "ruleId"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("删除模板关键词失败", e);
            return ResultObject.failed("删除模板关键词失败: " + e.getMessage());
        }
    }

    @PostMapping("/addContent")
    public ResultObject<ReplyTemplateBO.TemplateKeywordContentBO> addContent(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.addContent(
                    getRequiredLong(params, "ruleId"),
                    getString(params, "replyText"),
                    getString(params, "replyImageUrl")));
        } catch (Exception e) {
            log.error("添加模板回复内容失败", e);
            return ResultObject.failed("添加模板回复内容失败: " + e.getMessage());
        }
    }

    @PostMapping("/updateContent")
    public ResultObject<?> updateContent(@RequestBody Map<String, Object> params) {
        try {
            replyTemplateService.updateContent(
                    getRequiredLong(params, "contentId"),
                    getString(params, "replyText"),
                    getString(params, "replyImageUrl"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("更新模板回复内容失败", e);
            return ResultObject.failed("更新模板回复内容失败: " + e.getMessage());
        }
    }

    @PostMapping("/deleteContent")
    public ResultObject<?> deleteContent(@RequestBody Map<String, Object> params) {
        try {
            replyTemplateService.deleteContent(getRequiredLong(params, "contentId"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("删除模板回复内容失败", e);
            return ResultObject.failed("删除模板回复内容失败: " + e.getMessage());
        }
    }

    @PostMapping("/bindings")
    public ResultObject<List<ReplyTemplateBindingBO>> bindings(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.listBindings(
                    getRequiredLong(params, "xianyuAccountId"),
                    getRequiredString(params, "xyGoodsId")));
        } catch (Exception e) {
            log.error("获取商品模板绑定失败", e);
            return ResultObject.failed("获取商品模板绑定失败: " + e.getMessage());
        }
    }

    @PostMapping("/bind")
    public ResultObject<ReplyTemplateBindingBO> bind(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.bind(
                    getRequiredLong(params, "xianyuAccountId"),
                    getRequiredString(params, "xyGoodsId"),
                    getRequiredLong(params, "templateId")));
        } catch (Exception e) {
            log.error("绑定回复模板失败", e);
            return ResultObject.failed("绑定回复模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/batchBind")
    public ResultObject<Map<String, Object>> batchBind(@RequestBody Map<String, Object> params) {
        try {
            int changed = replyTemplateService.batchBind(
                    getRequiredLong(params, "xianyuAccountId"),
                    getStringList(params, "xyGoodsIds"),
                    getRequiredLong(params, "templateId"));
            return ResultObject.success(Map.of("changed", changed));
        } catch (Exception e) {
            log.error("批量绑定回复模板失败", e);
            return ResultObject.failed("批量绑定回复模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/updateBinding")
    public ResultObject<?> updateBinding(@RequestBody Map<String, Object> params) {
        try {
            replyTemplateService.updateBinding(
                    getRequiredLong(params, "bindingId"),
                    getOptionalInteger(params, "enabled"),
                    getOptionalInteger(params, "sortOrder"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("更新模板绑定失败", e);
            return ResultObject.failed("更新模板绑定失败: " + e.getMessage());
        }
    }

    @PostMapping("/unbind")
    public ResultObject<?> unbind(@RequestBody Map<String, Object> params) {
        try {
            replyTemplateService.unbind(getRequiredLong(params, "bindingId"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("解绑回复模板失败", e);
            return ResultObject.failed("解绑回复模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/effective")
    public ResultObject<List<EffectiveKeywordRuleBO>> effective(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.effectiveRules(
                    getRequiredLong(params, "xianyuAccountId"),
                    getRequiredString(params, "xyGoodsId")));
        } catch (Exception e) {
            log.error("获取最终生效回复规则失败", e);
            return ResultObject.failed("获取最终生效回复规则失败: " + e.getMessage());
        }
    }

    @PostMapping("/effectiveConfig")
    public ResultObject<EffectiveReplyConfigBO> effectiveConfig(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.effectiveConfig(
                    getRequiredLong(params, "xianyuAccountId"),
                    getRequiredString(params, "xyGoodsId")));
        } catch (Exception e) {
            log.error("获取最终生效回复配置失败", e);
            return ResultObject.failed("获取最终生效回复配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/createFromGoods")
    public ResultObject<ReplyTemplateBO> createFromGoods(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(replyTemplateService.createFromGoods(
                    getRequiredLong(params, "xianyuAccountId"),
                    getRequiredString(params, "xyGoodsId"),
                    getString(params, "name"),
                    getString(params, "description")));
        } catch (Exception e) {
            log.error("从商品配置创建模板失败", e);
            return ResultObject.failed("从商品配置创建模板失败: " + e.getMessage());
        }
    }

    private Long getRequiredLong(Map<String, Object> params, String key) {
        Long value = getOptionalLong(params, key);
        if (value == null) {
            throw new RuntimeException("缺少参数: " + key);
        }
        return value;
    }

    private Long getOptionalLong(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    private Integer getOptionalInteger(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        return Integer.valueOf(value.toString());
    }

    private String getRequiredString(Map<String, Object> params, String key) {
        String value = getString(params, key);
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("缺少参数: " + key);
        }
        return value;
    }

    private String getString(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        return value == null ? null : value.toString();
    }

    private List<String> getStringList(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().trim().isEmpty()) {
                    result.add(item.toString().trim());
                }
            }
        } else if (!value.toString().trim().isEmpty()) {
            for (String item : value.toString().split(",")) {
                if (!item.trim().isEmpty()) {
                    result.add(item.trim());
                }
            }
        }
        return result;
    }
}
