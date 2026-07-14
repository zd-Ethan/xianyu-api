package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.bo.DeliveryTemplateBindingBO;
import com.feijimiao.xianyuassistant.entity.bo.DeliveryTemplateBO;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveDeliveryConfigBO;
import com.feijimiao.xianyuassistant.service.DeliveryTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/delivery-template")
@CrossOrigin(origins = "*")
public class DeliveryTemplateController {

    @Autowired
    private DeliveryTemplateService deliveryTemplateService;

    @PostMapping("/list")
    public ResultObject<List<DeliveryTemplateBO>> list(@RequestBody(required = false) Map<String, Object> params) {
        try {
            return ResultObject.success(deliveryTemplateService.list(getOptionalLong(params, "xianyuAccountId")));
        } catch (Exception e) {
            log.error("获取发货模板列表失败", e);
            return ResultObject.failed("获取发货模板列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/detail")
    public ResultObject<DeliveryTemplateBO> detail(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(deliveryTemplateService.detail(getRequiredLong(params, "templateId")));
        } catch (Exception e) {
            log.error("获取发货模板详情失败", e);
            return ResultObject.failed("获取发货模板详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResultObject<DeliveryTemplateBO> create(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(deliveryTemplateService.create(
                    getOptionalLong(params, "xianyuAccountId"),
                    getString(params, "name"),
                    getString(params, "description")));
        } catch (Exception e) {
            log.error("创建发货模板失败", e);
            return ResultObject.failed("创建发货模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResultObject<?> update(@RequestBody Map<String, Object> params) {
        try {
            deliveryTemplateService.update(
                    getRequiredLong(params, "templateId"),
                    getString(params, "name"),
                    getString(params, "description"),
                    getOptionalInteger(params, "enabled"),
                    getOptionalInteger(params, "deliveryMode"),
                    getString(params, "autoDeliveryContent"),
                    getString(params, "kamiConfigIds"),
                    getString(params, "kamiDeliveryTemplate"),
                    getString(params, "autoDeliveryImageUrl"),
                    getOptionalInteger(params, "autoConfirmShipment"),
                    getOptionalInteger(params, "multiQuantityDelivery"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("更新发货模板失败", e);
            return ResultObject.failed("更新发货模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResultObject<?> delete(@RequestBody Map<String, Object> params) {
        try {
            deliveryTemplateService.delete(getRequiredLong(params, "templateId"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("删除发货模板失败", e);
            return ResultObject.failed("删除发货模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/createFromGoods")
    public ResultObject<DeliveryTemplateBO> createFromGoods(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(deliveryTemplateService.createFromGoods(
                    getRequiredLong(params, "xianyuAccountId"),
                    getRequiredString(params, "xyGoodsId"),
                    getString(params, "name"),
                    getString(params, "description")));
        } catch (Exception e) {
            log.error("从商品配置生成发货模板失败", e);
            return ResultObject.failed("从商品配置生成发货模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/apply")
    public ResultObject<Map<String, Object>> apply(@RequestBody Map<String, Object> params) {
        try {
            int changed = deliveryTemplateService.applyToGoods(
                    getRequiredLong(params, "xianyuAccountId"),
                    getStringList(params, "xyGoodsIds"),
                    getRequiredLong(params, "templateId"),
                    getOptionalInteger(params, "enableAutoDelivery"));
            return ResultObject.success(Map.of("changed", changed));
        } catch (Exception e) {
            log.error("套用发货模板失败", e);
            return ResultObject.failed("套用发货模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/bindings")
    public ResultObject<List<DeliveryTemplateBindingBO>> bindings(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(deliveryTemplateService.bindings(
                    getRequiredLong(params, "xianyuAccountId"),
                    getRequiredString(params, "xyGoodsId")));
        } catch (Exception e) {
            log.error("获取发货模板绑定失败", e);
            return ResultObject.failed("获取发货模板绑定失败: " + e.getMessage());
        }
    }

    @PostMapping("/bind")
    public ResultObject<DeliveryTemplateBindingBO> bind(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(deliveryTemplateService.bind(
                    getRequiredLong(params, "xianyuAccountId"),
                    getRequiredString(params, "xyGoodsId"),
                    getRequiredLong(params, "templateId"),
                    getOptionalInteger(params, "enableAutoDelivery")));
        } catch (Exception e) {
            log.error("绑定发货模板失败", e);
            return ResultObject.failed("绑定发货模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/unbind")
    public ResultObject<?> unbind(@RequestBody Map<String, Object> params) {
        try {
            deliveryTemplateService.unbind(getRequiredLong(params, "bindingId"));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("解绑发货模板失败", e);
            return ResultObject.failed("解绑发货模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/effectiveConfig")
    public ResultObject<EffectiveDeliveryConfigBO> effectiveConfig(@RequestBody Map<String, Object> params) {
        try {
            return ResultObject.success(deliveryTemplateService.effectiveConfig(
                    getRequiredLong(params, "xianyuAccountId"),
                    getRequiredString(params, "xyGoodsId"),
                    getString(params, "skuId")));
        } catch (Exception e) {
            log.error("获取最终发货配置失败", e);
            return ResultObject.failed("获取最终发货配置失败: " + e.getMessage());
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
