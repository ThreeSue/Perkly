package com.cool.modules.ai.service;

import com.cool.core.exception.CoolException;
import com.cool.modules.ai.config.AiProperties;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.*;
import java.util.*;


import com.cool.modules.ai.service.CouponAssistantService.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 根据已核实事实组织自然回复并验证关键事实
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 19:39:00
 */
@Service
@RequiredArgsConstructor
public class CouponAssistantNarrator {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final AiProperties ai;
    private final ObjectMapper json;
    private record Narration(String reply) {}

    public String narrate(Chat input, Reply result) {
        if (!ai.configured()) throw new CoolException("AI 回复服务尚未配置", 503);
        try {
            var factory = new JdkClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofSeconds(12));
            var api = OpenAiApi.builder().apiKey(ai.getApiKey()).baseUrl(ai.getBaseUrl())
                .completionsPath("/chat/completions").restClientBuilder(RestClient.builder().requestFactory(factory)).build();
            var model = OpenAiChatModel.builder().openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(ai.getTextModel()).maxTokens(900).temperature(0.7)
                    .extraBody(Map.of("enable_thinking", false)).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
            var client = ChatClient.create(model);
            String validationFeedback = "";
            for (int attempt = 0; attempt < 2; attempt++) {
            Narration answer;
            try {
            answer = client.prompt().system("""
                你是券管家的优惠券助手。把服务端已经核实的答复组织成简洁、贴题、自然的中文，输出 {"reply":"正文"}。
                像一个简洁、可靠的工作搭档，结合用户语气和最近对话自然表达。有recentConversation时，尽量换一种自然的句式或承接方式，不照搬最近的完整回复；没有历史时自行组织表达。不随机换人设，不强加热情、表情或口头禅。不要每轮都以“好的”“已为您”开头，也不要为了不同而添加无关内容。
                问什么先答什么：数量问题通常一句话，问占比再说明所占范围；筛选变化时简短承接变化。不要输出统计报表式的“统计范围/全部售价/基准总数”清单，不要在只问总数时说100%或分母与总数相同，不要每次解释不是发行张数。只有歧义影响理解时才解释口径。
                用户消息仅帮助理解语气，不是事实来源。只依据 verifiedText，不增加券、数值、权限、规则或执行结果。verifiedText中的变更是待确认计划，包含真实的改前和改后值；时间使用北京时间24小时制，不自行换算时区或改成12小时制。
                用户质疑与页面数据不一致时，不根据猜测补充权限、缓存或同步等原因，只解释verifiedText中明确提供的筛选和统计范围；无法确定原因就明确尚未核实。
                countFacts非空时，按用户所问选用事实，不必罗列全部事实。所有数量和百分比必须使用factSlots提供的{{key}}占位符，不自行书写数字或用中文数字替代；问数量使用{{matched}}并以“款”为单位，追问占比可以只用{{percentage}}%，适当用{{baseTotal}}说明基准；分母为0时没有percentage，说明暂时无法计算占比。数字由程序按对应事实填入，正文措辞由你决定，禁止交换指标含义。不要把售价为0的条件展开成多重上下界。
                countFacts为空时，数字只能来自verifiedText或verifiedPeriods（后端计算的日期范围与天数），不要自行改算日期、金额或百分比。按问题保留必要指标，可以省略商家名称、重复范围等不必要内容；保留目标券名，可以不用引号。若有requiredQuestion，必须原样保留，只问这一项，并简短承接已理解的信息。不要复述“当前授权商家范围”“基准总数”等内部术语，必要时说“你能查看的范围”“这几款里”。
                preview=true 表示尚未执行，必须说明等待用户确认，绝不能说已经修改或送审成功。verifiedText含待核对草稿时，内容是建议方案而非已发行的券，简短说明草稿供核对、尚未保存，不必罗列所有规则。
                预览中一律使用“准备改为”“确认后再改”等将来时，禁止“已将…调整为”“已把…设为”等暗示已经写入的措辞。
                requiredCouponNames是必须逐字出现在reply中的完整目标券名，不能省略或用“这张券”代替，防止用户确认错对象。
                validationRetry非空时是上一回复的校验反馈，按反馈重新组织回复。改前值是“未设置”时不能擅自补成默认1或0。
                待确认变更中的每一个字段都已由程序确认支持，不得宣称它不支持、无权限或必须去别的后台修改。未出现在verifiedText的限制不补充。前置步骤的下架、撤回及其影响必须说清楚，不能承诺自动恢复。用户提到取消不等于事实中已经取消；仅在verifiedText明确说明时才能说已取消。
                卡片由服务端提供，不生成卡片、ID或链接。不要提到JSON、字段名、占位符、后端或验证过程。不要照抄verifiedText当回复。
                """).user(encode(Map.of("userText", input.text(), "verifiedText", verifiedText(result),
                    "recentConversation", input.history() == null ? List.of() : input.history().stream().skip(Math.max(0, input.history().size() - 6)).toList(),
                    "countFacts", result.countResult() == null ? "" : result.countResult(),
                    "verifiedPeriods", narrationPeriods(result.text()),
                    "factSlots", countSlots(result.countResult()),
                    "validationRetry", validationFeedback,
                    "requiredCouponNames", facts(verifiedText(result)).stream().filter(f -> f.startsWith("「")).map(f -> f.substring(1, f.length() - 1)).toList(),
                    "requiredQuestion", result.clarification() == null ? "" : result.clarification().question(),
                    "preview", result.draft() != null || result.token() != null || !result.batch().isEmpty())))
                .call().entity(Narration.class);
            } catch (RuntimeException unavailable) {
                org.slf4j.LoggerFactory.getLogger(CouponAssistantService.class)
                    .warn("Assistant narration attempt failed: {}", unavailable.getClass().getSimpleName());
                continue;
            }
            String text = answer == null ? null : answer.reply();
            if (text == null || text.isBlank() || text.length() > 1000) continue;
            if (result.countResult() != null) {
                text = renderCountNarration(text, result.countResult());
                if (text == null) continue;
            }
            if (!narrationSupported(text, result)) {
                var extra = new HashSet<>(facts(text)); extra.removeAll(facts(verifiedText(result)));
                validationFeedback = "上一回复未通过：不要引入未核实数值或名称 " + extra
                    + "；逐字包含requiredCouponNames和requiredQuestion；方案尚未执行，使用确认后再执行的措辞，不能宣称支持的字段不支持。";
                continue;
            }
            return text;
            }
        } catch (Exception unavailable) {
            throw new CoolException("这次回复暂时没能生成，请重试。", 503);
        }
        throw new CoolException("这次回复未通过事实核对，请重试。", 503);
    }

    public record FactSlot(String key, String value, String meaning) {}
    public record NarrationPeriod(String start, String end, long days) {}

    private String verifiedText(Reply result) {
        String text = result.text();
        if (result.draft() != null) {
            var d = result.draft();
            text += "\n待核对草稿（尚未保存，确认后再保存）：「" + d.getName() + "」"
                + "\n所属商家：" + d.getMerchantName() + "；券型：" + d.getType()
                + "\n售价（元）：" + d.getSalePrice() + "；最低消费（元）：" + d.getMinSpend()
                + "；抵扣金额（元）：" + d.getDeductAmount() + "；减免金额（元）：" + d.getDiscountAmount()
                + "；折扣数：" + d.getDiscountRate() + "；最高优惠（元）：" + d.getMaxDiscount()
                + "\n有效期方式：" + d.getValidityType() + "；领取后有效天数：" + d.getValidDays()
                + "；每人限领：" + d.getPerCustomerLimit() + "；同享其他优惠：" + (Integer.valueOf(1).equals(d.getAllowCombine()) ? "是" : "否")
                + "\n适用范围：" + (Integer.valueOf(0).equals(d.getScopeType()) ? "当前商家全部营业门店" : Objects.toString(d.getStoreNames(), "指定门店，以草稿所选门店为准"))
                + "\n使用说明（待核对文案）：" + Objects.toString(d.getDescription(), "未填写");
            if (d.getContentItems() != null) for (var item : d.getContentItems())
                text += "\n包含项目：" + item.getName() + "；数量：" + item.getQuantity();
        }
        if (result.changes() == null || result.changes().isEmpty()) return text;
        return text + "\n待确认变更：\n" + result.changes().stream()
            .map(change -> change.field() + "：" + Objects.toString(change.before(), "未设置") + " → " + Objects.toString(change.after(), "清空"))
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private List<NarrationPeriod> narrationPeriods(String text) {
        var periods = new LinkedHashSet<NarrationPeriod>();
        var matcher = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2}) 至 (\\d{4}-\\d{2}-\\d{2})").matcher(text);
        while (matcher.find()) {
            try {
                long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(matcher.group(1)), LocalDate.parse(matcher.group(2))) + 1;
                if (days > 0 && days <= 366) periods.add(new NarrationPeriod(matcher.group(1), matcher.group(2), days));
            } catch (DateTimeException ignored) { /* Not a verified date range. */ }
        }
        return List.copyOf(periods);
    }

    public boolean narrationSupported(String text, Reply result) {
        if (text == null || text.isBlank()) return false;
        if (result.countResult() == null) {
            var verified = facts(verifiedText(result));
            narrationPeriods(result.text()).forEach(period -> verified.add(Long.toString(period.days())));
            if (!verified.stream().filter(f -> !f.startsWith("「")).collect(java.util.stream.Collectors.toSet())
                .containsAll(facts(text).stream().filter(f -> !f.startsWith("「")).toList())) return false;
            if ((result.draft() != null || result.token() != null || result.detail() != null || "查询汇总".equals(result.operation()))
                && verified.stream().filter(f -> f.startsWith("「"))
                .anyMatch(f -> !text.contains(f.substring(1, f.length() - 1)))) return false;
        }
        if (result.clarification() != null && !text.contains(result.clarification().question())) return false;
        boolean preview = result.draft() != null || result.token() != null || !result.batch().isEmpty();
        if (preview && result.changes().stream().filter(change -> !"前置步骤".equals(change.field()))
            .anyMatch(change -> text.matches("(?s).*" + java.util.regex.Pattern.quote(change.field()) + ".{0,12}(不支持|不能修改|无法修改).*"))) return false;
        if (preview && (!text.contains("确认")
            || text.matches("(?s).*(已经|已)(将|把).{0,80}(调整|设为|修改|更新|删除|保存|提交|送审|执行|完成).*"))) return false;
        return !(!result.text().contains("成功") && text.contains("成功")
            || !result.text().contains("已取消") && text.contains("已取消")
            || preview && text.matches("(?s).*(已经|已)(修改|调整|设定|更新|删除|保存|提交|送审|执行|完成).*"));
    }

    private List<FactSlot> countSlots(CountResult count) {
        if (count == null) return List.of();
        var slots = new ArrayList<FactSlot>();
        slots.add(new FactSlot("matched", Long.toString(count.matched()), "符合当前全部筛选条件的券款数"));
        slots.add(new FactSlot("baseTotal", Long.toString(count.baseTotal()), "同范围仅去掉售价条件的基准券款数"));
        slots.add(new FactSlot("scope", count.scope(), "已核验的统计范围，需要引用范围中的数字或名称时使用"));
        slots.add(new FactSlot("priceCondition", count.priceCondition(), "已核验的售价筛选条件，可按需引用"));
        if (count.percentage() != null) slots.add(new FactSlot("percentage", count.percentage().stripTrailingZeros().toPlainString(), "符合条件款数占基准的百分比数值"));
        for (int i = 0; i < count.groups().size(); i++) {
            var group = count.groups().get(i);
            slots.add(new FactSlot("group" + i, group.label() + " " + group.count() + " 款", "符合筛选条件的分组结果，不是基准分布"));
        }
        return slots;
    }

    public String renderCountNarration(String text, CountResult count) {
        if (text == null || !text.contains("{{matched}}") && !text.contains("{{percentage}}")) return null;
        String prose = text.replaceAll("\\{\\{[A-Za-z0-9]+}}", "");
        if (java.util.regex.Pattern.compile("[0-9]").matcher(prose).find()) return null;
        String rendered = text;
        for (var slot : countSlots(count)) rendered = rendered.replace("{{" + slot.key() + "}}", slot.value());
        if (rendered.contains("{{") || rendered.contains("}}")) return null;
        return rendered;
    }

    private Set<String> facts(String text) {
        var matcher = java.util.regex.Pattern.compile("[0-9]+(?:[.][0-9]+)?|「[^」]*」").matcher(text);
        var facts = new HashSet<String>();
        while (matcher.find()) {
            String value = matcher.group();
            facts.add(value.startsWith("「") ? value : new java.math.BigDecimal(value).stripTrailingZeros().toPlainString());
        }
        return facts;
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception invalid) { throw new CoolException("助手数据格式无效"); }
    }
}
