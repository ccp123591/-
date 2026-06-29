package com.fitcoach.infra.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 默认 AI 教练实现 — 离线 / 无 API key 时启用，给出确定性的中文反馈。
 * matchIfMissing=true → 未配置 ai.coach.provider 时也启用。
 */
@Component
@ConditionalOnProperty(name = "ai.coach.provider", havingValue = "mock", matchIfMissing = true)
public class MockCoachProvider implements AiCoachProvider {

    private static final Pattern P_LOW      = Pattern.compile("(累|烦|不开心|难过|疲惫|压力|焦虑|想哭|心情不好|emo|沮丧|郁闷|崩溃|无力)");
    private static final Pattern P_HAPPY    = Pattern.compile("(开心|快乐|高兴|爽|超棒|太棒|完成|搞定|赞|nice)");
    private static final Pattern P_TRAIN    = Pattern.compile("(练|训练|动作|深蹲|俯卧|平板|计划|目标|增肌|减脂|减肥|强度)");
    private static final Pattern P_MEMORY   = Pattern.compile("(记得|记不记得|上次|之前|提过|说过|那天|那次)");
    private static final Pattern P_GREETING = Pattern.compile("(你好|嗨|hello|hi|在吗|在不在|早|晚安|午安)");
    private static final Pattern P_NAME_SET = Pattern.compile("(?:我叫|叫我|我的名字叫|我的名字是|名字叫)([\\u4e00-\\u9fa5A-Za-z]{1,8})");
    private static final Pattern P_NAME_ASK = Pattern.compile("(我叫什么|我是谁|我的名字|记得我.{0,3}名|知道我.{0,3}名)");
    private static final Pattern P_THANKS   = Pattern.compile("(谢谢|谢啦|多谢|感谢|辛苦了|thx|thank)");
    private static final Pattern P_BYE       = Pattern.compile("(再见|拜拜|拜了|不聊了|先下了|睡了|晚安|明天见|先这样|去忙了)");
    private static final Pattern P_QUESTION  = Pattern.compile("(为什么|为啥|怎么办|怎么样|咋办|咋样|能不能|可不可以|行不行|好不好|吗[?？]?$|[?？]$)");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public CoachAiResponse feedback(CoachContext ctx) {
        String label = labelOf(ctx);
        Integer reps = ctx.getReps() == null ? 0 : ctx.getReps();
        Integer score = ctx.getScore() == null ? 0 : ctx.getScore();

        String review;
        if (score >= 90) {
            review = String.format("本次 %s %d 次，得分 %d，动作非常标准，节奏稳定。", label, reps, score);
        } else if (score >= 75) {
            review = String.format("本次 %s %d 次，得分 %d，整体不错，节奏与深度可再提升。", label, reps, score);
        } else if (score >= 60) {
            review = String.format("本次 %s %d 次，得分 %d，动作合格，建议关注稳定性与发力均衡。", label, reps, score);
        } else {
            review = String.format("本次 %s %d 次，得分 %d，动作偏粗糙，建议放慢节奏、关注关键角度。", label, reps, score);
        }

        // 融合视觉点评（JoyAI-VL）— 把"看到的"接到"数据算到的"后面
        String formReview = ctx.getFormReview();
        if (formReview != null && !formReview.isBlank()) {
            review = review + " 画面观察：" + formReview.strip() + "。";
        }

        String suggestion;
        if (score >= 80) {
            suggestion = String.format("可适当加大幅度或提高频率，下次目标 %d 次。", reps + 3);
        } else {
            suggestion = "放慢节奏、控制底位 1 秒，注意左右发力均衡。";
        }

        String encouragement = score >= 75 ? "保持节奏，明天再战！" : "稳扎稳打，进步看得见！";
        String nextGoal = String.format("下次目标：%d 次 %s", Math.max(reps + 2, 8), label);

        return CoachAiResponse.builder()
                .review(review)
                .suggestion(suggestion)
                .encouragement(encouragement)
                .nextGoal(nextGoal)
                .provider(name())
                .tokensUsed(0)
                .build();
    }

    @Override
    public CoachAiResponse suggestion(CoachContext ctx) {
        Integer avg = ctx.getRecentAvgScore() == null ? 0 : ctx.getRecentAvgScore();
        long total = ctx.getRecentTotalReps() == null ? 0L : ctx.getRecentTotalReps();
        String review = String.format("近期平均得分 %d，累计完成 %d 次，整体训练状态稳定。", avg, total);
        String suggestion = "周训 3-4 次、加入有氧 10 分钟、保持核心训练频率。";
        String encouragement = "持续就有回报，继续保持！";
        String nextGoal = "本周目标：3 次有效训练 + 200 次累计动作";
        return CoachAiResponse.builder()
                .review(review).suggestion(suggestion)
                .encouragement(encouragement).nextGoal(nextGoal)
                .provider(name()).tokensUsed(0).build();
    }

    @Override
    public CoachAiResponse weeklyPlan(CoachContext ctx) {
        String review = "建议本周训练 3-4 次，覆盖下肢 / 上肢 / 核心三大块。";
        String suggestion = "周一 深蹲 15、周三 俯卧撑 12、周五 平板支撑 60s、周日 前屈伸展 20。";
        String encouragement = "规律训练才是王道，加油！";
        String nextGoal = "本周完成 4 次训练并积累 ≥ 200 reps。";
        return CoachAiResponse.builder()
                .review(review).suggestion(suggestion)
                .encouragement(encouragement).nextGoal(nextGoal)
                .provider(name()).tokensUsed(0).build();
    }

    private static String labelOf(CoachContext ctx) {
        if (ctx.getActionLabel() != null && !ctx.getActionLabel().isBlank()) return ctx.getActionLabel();
        if (ctx.getAction() != null) {
            return switch (ctx.getAction()) {
                case "squat" -> "深蹲";
                case "pushup" -> "俯卧撑";
                case "plank" -> "平板支撑";
                case "stretch" -> "前屈伸展";
                default -> ctx.getAction();
            };
        }
        return "训练";
    }

    @Override
    public ChatAiResponse chat(CoachContext ctx, String userMessage, List<ChatTurn> history) {
        String msg = userMessage == null ? "" : userMessage.trim();
        String lower = msg.toLowerCase();
        boolean hasMemory = ctx.getRelevantHistory() != null && !ctx.getRelevantHistory().isBlank();
        int seed = history == null ? 0 : history.size();   // 随对话推进轮换措辞，避免重复感
        String setName = extractName(msg);                  // 本轮是否在报名字
        String who = rememberedName(msg, history, ctx.getNickname());  // 跨轮记住的称呼
        String nc = (who != null && !who.isBlank()) ? "，" + who : "";

        StringBuilder sb = new StringBuilder();
        if (setName != null) {
            sb.append(pick(seed,
                    "好，记住啦——" + setName + "，以后就这么喊你。",
                    setName + "，名字我收下了，挺好记的。今天想聊点什么？",
                    "嗯，" + setName + "，记下了，咱们接着聊。"));
        } else if (P_NAME_ASK.matcher(msg).find()) {
            sb.append((who != null && !who.isBlank())
                    ? pick(seed, "当然记得，你是" + who + "呀。", "这还能忘？" + who + "。")
                    : "你还没正式告诉我名字呢，报个名，我这就记住。");
        } else if (msg.startsWith("（叙旧）") || msg.startsWith("(叙旧)")) {
            if (hasMemory) {
                sb.append("这些都是咱们最近聊过的，我都收着呢：\n")
                  .append(ctx.getRelevantHistory())
                  .append("\n现在回头看，是不是也挺有意思？");
            } else {
                sb.append("咱们最近聊得还不多，多说几句，下回我就能跟你好好叙叙了。");
            }
        } else if (P_LOW.matcher(lower).find()) {
            sb.append(pick(seed, "听你这么说，我心里也跟着沉了一下" + nc + "。", "辛苦了" + nc + "，这种感觉我懂。"));
            if (hasMemory) sb.append("我记得你之前也有过类似的时候，那会儿动一动反而松快了些。");
            sb.append(pick(seed, "不急着练，先喝口水、深呼吸三下，行吗？", "今天就当休息，陪我说说话也好。"));
        } else if (P_HAPPY.matcher(lower).find()) {
            sb.append(pick(seed, "你这状态我隔着屏幕都能感受到" + nc + "！", "真为你高兴" + nc + "！"));
            sb.append(pick(seed, "趁这股劲儿，加 3 个 " + labelOf(ctx) + " 再收？", "把这份劲儿留一点到下次训练里。"));
        } else if (P_MEMORY.matcher(lower).find()) {
            if (hasMemory) {
                sb.append("当然记得，你提过的这些我都收着：\n")
                  .append(ctx.getRelevantHistory()).append("\n现在再看，感觉怎么样？");
            } else {
                sb.append("嗯…这件事我好像没具体收到过，跟我说说？以后我帮你记着。");
            }
        } else if (P_TRAIN.matcher(lower).find()) {
            sb.append(pick(seed, "好呀，结合你最近的状态", "行，按你的节奏来"));
            Integer avg = ctx.getRecentAvgScore();
            if (avg != null && avg > 0) sb.append("（平均分 ").append(avg).append("）");
            sb.append("，下次比上回多做 2-3 个，节奏稳为先。");
            if (hasMemory) sb.append(" 之前你也是这样一点点上来的，相信自己。");
        } else if (P_THANKS.matcher(lower).find()) {
            sb.append(pick(seed, "客气啥" + nc + "，咱俩之间不用这套。", "不谢" + nc + "，能帮上就好。", "这是我该做的。"));
        } else if (P_BYE.matcher(lower).find()) {
            sb.append(pick(seed, "去吧" + nc + "，记得喝水、早点休息，我随时在。", "好，先这样" + nc + "，想聊了喊我一声。"));
        } else if (P_GREETING.matcher(lower).find()) {
            sb.append(pick(seed, "嗨" + nc + "，我在的。", "来啦" + nc + "！"));
            sb.append("今天想随便聊聊，还是练几个动作放松一下？");
        } else if (P_QUESTION.matcher(msg).find()) {
            sb.append(pick(seed, "这个问题问得好" + nc + "。", "嗯，我想想哈。"));
            sb.append("你具体是卡在哪一步？多讲两句，我帮你拆开看。");
        } else {
            sb.append(pick(seed, "嗯，听到你了" + nc + "。", "我在认真听呢" + nc + "。"));
            if (hasMemory) {
                String[] lines = ctx.getRelevantHistory().split("\n");
                if (lines.length > 0) sb.append("说到这个，我想起你提过 — ").append(lines[0].replace("- ", "").trim()).append("。");
            }
            sb.append("你接着说。");
        }

        return ChatAiResponse.builder()
                .reply(sb.toString())
                .provider(name())
                .tokensUsed(0)
                .build();
    }

    private static String pick(int seed, String... vs) {
        return vs[Math.floorMod(seed, vs.length)];
    }

    /** 本轮 / 历史里用户报过的名字（最近一次优先），没有则回退到 nickname。 */
    private static String rememberedName(String current, List<ChatTurn> history, String fallback) {
        String found = extractName(current);
        if (found == null && history != null) {
            for (ChatTurn t : history) {
                if (t != null && "user".equals(t.getRole())) {
                    String n = extractName(t.getContent());
                    if (n != null) found = n;
                }
            }
        }
        return found != null ? found : fallback;
    }

    /** 从「我叫X / 叫我X」里抽名字，过滤掉疑问词。 */
    private static String extractName(String s) {
        if (s == null) return null;
        java.util.regex.Matcher m = P_NAME_SET.matcher(s);
        if (!m.find()) return null;
        String n = m.group(1);
        return ("什么".equals(n) || "啥".equals(n) || "谁".equals(n)) ? null : n;
    }
}
