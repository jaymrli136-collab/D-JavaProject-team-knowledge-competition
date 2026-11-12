package com.jaymrli.teamknowledge.config;

import com.jaymrli.teamknowledge.api.ApiDtos.IngestDocumentRequest;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.ProjectDecision;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.service.DocumentIngestionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

@Component
@Order(1)
public class DemoDataLoader implements ApplicationRunner {
    private final KnowledgeStore store;
    private final DocumentIngestionService ingestion;

    public DemoDataLoader(KnowledgeStore store, DocumentIngestionService ingestion) {
        this.store = store;
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!store.allProjects().isEmpty()) return;
        store.addProject(new Project("p_tomato", "番茄小说增长实验", "提升免费小说新用户留存和阅读深度", "u_bob", Set.of("u_bob", "u_alice"), "active"));
        store.addProject(new Project("p_duanju", "红果短剧内容推荐", "优化短剧推荐体验与内容供给效率", "u_carol", Set.of("u_carol", "u_alice"), "active"));
        store.addProject(new Project("p_toutiao", "头条热点专题", "建设热点事件的专题聚合与分发能力", "u_bob", Set.of("u_bob"), "active"));

        KnowledgeDocument staleLaunch = ingest("p_tomato", "安卓新版上线计划 v1",
                "https://docs.example.com/tomato/launch-v1",
                LocalDate.of(2026, 10, 20), 1, Set.of("上线", "计划"),
                "# 上线计划\n原计划于12月15日发布安卓新版，并先覆盖10%的新用户。\n\n# 依赖项\n依赖客户端包体优化和内容侧推荐接口联调。");
        KnowledgeDocument launchDecision = ingest("p_tomato", "安卓新版上线调整决策",
                "https://docs.example.com/tomato/launch-decision",
                LocalDate.of(2026, 11, 8), 2, Set.of("决策", "上线", "安卓"),
                "# 决策\n经增长、客户端和运营共同确认，安卓新版上线时间由12月15日提前至12月8日。\n\n# 原因\n11月压测结果稳定，包体优化提前完成，春节前流量窗口需要更早验证留存收益。");
        KnowledgeDocument tomatoRisk = ingest("p_tomato", "番茄小说十一月风险清单",
                "https://docs.example.com/tomato/risk-2026-11",
                LocalDate.of(2026, 11, 10), 1, Set.of("风险", "成本", "内容供给"),
                "# 当前最大风险\n短剧内容采购成本连续两个月上涨，若继续按当前采买规模扩张，四季度内容成本将超过预算上限。\n\n# 应对\n优先续约高完播率内容，压低低效采买，并通过推荐实验提升单次阅读时长。\n\n# 其他风险\n部分老旧安卓机型存在包体兼容性隐患。");
        ingest("p_tomato", "番茄小说指标口径",
                "https://docs.example.com/tomato/metrics",
                LocalDate.of(2026, 11, 2), 1, Set.of("指标", "留存", "阅读"),
                "# 7日留存\n新用户首次启动后第7天仍产生一次有效阅读，记为7日留存。\n\n# 有效阅读\n单次连续阅读不少于60秒，且完成至少两个章节。\n\n# 北极星指标\n人均周有效阅读时长。");

        KnowledgeDocument diversityDecision = ingest("p_duanju", "短剧推荐探索利用策略",
                "https://docs.example.com/duanju/policy",
                LocalDate.of(2026, 11, 9), 3, Set.of("推荐", "探索", "多样性"),
                "# 策略\n推荐系统采用80%利用、20%探索的流量分配。利用阶段服务明确的观看偏好，探索阶段测试新题材和新作者。\n\n# 多样性约束\n同一题材连续推荐不超过2条，避免用户兴趣收窄和内容同质化。\n\n# 目标\n在不降低人均观看时长的前提下，提升新内容发现率和次日回访。");
        KnowledgeDocument duanjuRisk = ingest("p_duanju", "红果短剧内容供给风险",
                "https://docs.example.com/duanju/risk",
                LocalDate.of(2026, 11, 8), 1, Set.of("风险", "版权", "内容供给"),
                "# 当前最大风险\n版权到期与内容供给不确定是当前最大风险。头部短剧版权将在12月集中到期，续约价格存在明显上涨压力。\n\n# 影响\n若续约失败，推荐池会缺少高完播内容，可能导致用户观看时长下降。\n\n# 缓解\n提前锁定头部内容，扩大腰部供给，并建立版权到期预警。");
        ingest("p_duanju", "推荐实验复盘",
                "https://docs.example.com/duanju/experiment-review",
                LocalDate.of(2026, 11, 6), 2, Set.of("实验", "推荐"),
                "# 实验结果\n将探索流量从10%提升到20%后，新内容曝光提升18%，人均观看时长没有显著下降。\n\n# 结论\n当前阶段继续维持20%探索流量，并对低质探索内容增加质量门槛。");

        ingest("p_toutiao", "热点专题上线状态",
                "https://docs.example.com/toutiao/status",
                LocalDate.of(2026, 11, 9), 1, Set.of("热点", "上线"),
                "# 状态\n热点专题聚合页已完成灰度，计划11月20日全量上线。\n\n# 当前进度\n推荐接口已接通，专题编辑后台仍在验收。");
        ingest("p_toutiao", "热点专题风险",
                "https://docs.example.com/toutiao/risk",
                LocalDate.of(2026, 11, 10), 1, Set.of("风险", "审核"),
                "# 最大风险\n突发事件的事实核验和审核时延是当前最大风险，错误聚合可能放大不实信息。\n\n# 应对\n增加权威源白名单、双人复核和敏感事件降级策略。");
        KnowledgeDocument toutiaoDecision = ingest("p_toutiao", "热点编辑流程决策",
                "https://docs.example.com/toutiao/editorial-decision",
                LocalDate.of(2026, 11, 7), 1, Set.of("决策", "审核"),
                "# 决策\n热点专题采用机器聚合加人工复核流程，突发敏感事件必须经过双人复核后才能发布。\n\n# 责任\n专题编辑负责事实核验，审核负责人负责发布确认。");

        store.addDecision(new ProjectDecision("d_tomato_launch", "p_tomato", "安卓新版提前上线",
                "安卓新版上线时间调整为12月8日", "压测稳定且流量窗口提前", "u_bob",
                launchDecision.id(), LocalDate.of(2026, 11, 8)));
        store.addDecision(new ProjectDecision("d_duanju_diversity", "p_duanju", "维持20%探索流量",
                "推荐系统维持80%利用、20%探索，并限制同题材连续推荐", "提升新内容发现率且不损失观看时长", "u_carol",
                diversityDecision.id(), LocalDate.of(2026, 11, 9)));
        store.addDecision(new ProjectDecision("d_toutiao_review", "p_toutiao", "敏感热点双人复核",
                "敏感热点必须双人复核后发布", "降低事实错误和审核风险", "u_bob",
                toutiaoDecision.id(), LocalDate.of(2026, 11, 7)));
    }

    private KnowledgeDocument ingest(String projectId, String title, String sourceUri, LocalDate date,
                                       int version, Set<String> tags, String content) {
        return ingestion.ingest(projectOwner(projectId),
                projectId, new IngestDocumentRequest(title, content, sourceUri, date, version, tags));
    }

    private String projectOwner(String projectId) {
        return store.findProject(projectId).orElseThrow().ownerId();
    }
}
