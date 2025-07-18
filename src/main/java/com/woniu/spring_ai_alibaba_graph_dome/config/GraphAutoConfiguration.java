package com.woniu.spring_ai_alibaba_graph_dome.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.node.QuestionClassifierNode;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import com.woniu.spring_ai_alibaba_graph_dome.Demo.RecordingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Graph Demo：首先判断评价正负，其次细分负面问题，最后输出处理方案。
 *
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */

@Configuration
public class GraphAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GraphAutoConfiguration.class);

    /**
     * 定义一个工作流 StateGraph Bean.
     */
    @Bean
    /* 接收一个ChatClient.Builder参数，用于构建AI聊天客户端 */
    public StateGraph workflowGraph(ChatClient.Builder builder) throws GraphStateException {

        // LLMs Bean
        /*                                   增强顾问                  */
        ChatClient chatClient = builder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
        /* SimpleLoggerAdvisor 是 Spring AI 提供的一个具体的 Advisor 实现。它的作用非常明确：自动记录（Log）与 AI 模型的每一次交互 */


        // 定义一个 OverAllStateFactory，用于在每次执行工作流时创建初始的全局状态对象。通过注册若干 Key 及其更新策略来管理上下文数据
        // 注册三个状态 key 分别为
        // 1. input：用户输入的文本
        // 2. classifier_output：分类器的输出结果
        // 3. solution：最终输出结论
        // 使用 ReplaceStrategy（每次写入替换旧值）策略处理上下文状态对象中的数据，用于在节点中传递数据

        /* 定义了一个状态工厂，用于管理工作流执行过程中的状态数据 */
        /* 在 alibaba-cloud-ai-graph 这个工作流框架中，“状态”（State）** 扮演着至关重要的角色，它就像是整个工作流的中央内存或共享数据中心。 */
        OverAllStateFactory stateFactory = () -> {
            /* 定义一个接力棒  ‘state’  工作流中的每个结点之间传递的 “接力棒” */
            /* 也可以理解为共享内存（state） */
            OverAllState state = new OverAllState();

            /* 下面的就是共享内存中的变量 */
            /*input: 存储用户输入的原始文本
                classifier_output: 存储分类器的输出结果
                solution: 存储最终的处理方案
                使用ReplaceStrategy策略，表示每次写入会替换旧值*/
            state.registerKeyAndStrategy("input", new ReplaceStrategy());
            state.registerKeyAndStrategy("classifier_output", new ReplaceStrategy());
            state.registerKeyAndStrategy("solution", new ReplaceStrategy());
            return state;
        };
        /*  */

        // 创建 workflows 节点
        // 使用 Graph 框架预定义的 QuestionClassifierNode 来处理文本分类任务

        // 评价正负分类节点
        /* 整个工作流中的第一个核心处理单元。 */
        /* 接收一段用户反馈文本，然后利用大语言模型（LLM）判断这段话的情感是积极的（Positive）还是消极的（Negative）。 */

        /* QuestionClassifierNode.builder()  预置节点类型 */
        QuestionClassifierNode feedbackClassifier = QuestionClassifierNode.builder()
                /* 指定要调用的模型 */
                .chatClient(chatClient)
                /* 指定输入源。这告诉节点：“你要分类的文本，请到全局状态（OverAllState）中去找一个键（Key）为 input 的值”。在工作流开始时，用户的原始输入会被存放在这个 "input" 键中。 */
                .inputTextKey("input")
                /* 指定输出键，必须重我给的里面选取 */
                .categories(List.of("positive feedback", "negative feedback"))
                /* 。这部分是给AI的“特别提示” */
                .classificationInstructions(
                        /* 尝试理解用户在提供反馈时的感受 */
                        List.of("Try to understand the user's feeling when he/she is giving the feedback."))
                .build();

        /* 与上面相似 */
        // 负面评价具体问题分类节点
        QuestionClassifierNode specificQuestionClassifier = QuestionClassifierNode.builder()
                .chatClient(chatClient)
                .inputTextKey("input")
                .categories(List.of("after-sale service", "transportation", "product quality", "others"))
                .classificationInstructions(List
                        /* 客户想从我们这里获得什么样的服务或帮助？请根据你的理解进行分类。 */
                        .of("What kind of service or help the customer is trying to get from us? Classify the question based on your understanding."))
                .build();



        // 编排 Node 节点，使用 StateGraph 的 API，将上述节点加入图中，并设置节点间的跳转关系
        // 首先将节点注册到图，并使用 node_async(...) 将每个 NodeAction 包装为异步节点执行（提高吞吐或防止阻塞，具体实现框架已封装）
        /*                                     给这个工作流起一个名字，便于识别和日志记录。     之前定义的接力棒   */
        StateGraph stateGraph = new StateGraph("Consumer Service Workflow Demo", stateFactory)

                // 定义节点
                .addNode("feedback_classifier", node_async(feedbackClassifier))
                .addNode("specific_question_classifier", node_async(specificQuestionClassifier))
                /* 注册“记录和输出方案”节点。 */
                .addNode("recorder", node_async(new RecordingNode()))

                // 定义边（流程顺序）
                /* 所有流程的起点（START）都必须连接到 feedback_classifier 这个节点 */
                /* 定义起始结点 */
                .addEdge(START, "feedback_classifier")

                /*  这是一个条件分支，相当于在流水线上安装了一个智能分拣机。
                    "feedback_classifier": 指定这个分拣机安装在哪个工作站后面。*/
                .addConditionalEdges("feedback_classifier",

                        /* edge_async(new FeedbackQuestionDispatcher()): 指定分拣机的决策逻辑。当 feedback_classifier 完成后，会调用 FeedbackQuestionDispatcher 里的代码。这个调度器会检查全局状态（OverAllState）并返回一个字符串（"positive" 或 "negative"）。*/
                        edge_async(new FeedbackQuestionDispatcher()),
                        /* 如果调度器返回 "positive"，就把流程导向 "recorder" 节点。
                        如果调度器返回 "negative"，就把流程导向 "specific_question_classifier" 节点。 */
                        Map.of("positive", "recorder", "negative", "specific_question_classifier"))

                .addConditionalEdges("specific_question_classifier",
                        edge_async(new SpecificQuestionDispatcher()),
                        Map.of("after-sale", "recorder", "transportation", "recorder", "quality", "recorder", "others",
                                "recorder"))

                // 图的结束节点
                .addEdge("recorder", END);

        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "workflow graph");

        System.out.println("\n\n");
        System.out.println(graphRepresentation.content());
        System.out.println("\n\n");

        return stateGraph;
    }


    /* FeedbackQuestionDispatcher 是一个决策逻辑单元，在你的工作流中，它扮演着第一个智能分拣机的角色。 */
    /* 在 alibaba-cloud-ai-graph 框架中，任何想要在“边”（Edge）上执行逻辑、决定下一跳去哪里的类，都必须实现这个接口。EdgeAction 直译过来就是“边的动作”。 */
    public static class FeedbackQuestionDispatcher implements EdgeAction {
        /* 它的唯一职责是：在第一个分类节点 (feedback_classifier) 完成工作后，检查分类结果，并决定工作流的下一个走向。它本身不进行AI计算，只做简单的逻辑判断。 */
        @Override
        /* 传入“接力棒” */
        public String apply(OverAllState state) {

            /* 从全局状态中获取（classifier_output）的值 */
            /* .orElse(""): 这是一个安全措施。如果 state 中因为某种原因没有 "classifier_output" 这个键，或者它的值为 null，那么就使用一个空字符串 "" 作为默认值，避免程序抛出 NullPointerException。 */
            String classifierOutput = (String) state.value("classifier_output").orElse("");
            /* 打印日志信息（调用最上面的日志定义） */
            logger.info("classifierOutput: {}", classifierOutput);

            if (classifierOutput.contains("positive")) {
                return "positive";
            }
            return "negative";
        }

    }

    public static class SpecificQuestionDispatcher implements EdgeAction {

        @Override
        public String apply(OverAllState state) {

            String classifierOutput = (String) state.value("classifier_output").orElse("");
            logger.info("classifierOutput: {}", classifierOutput);

            Map<String, String> classifierMap = new HashMap<>();
            classifierMap.put("after-sale", "after-sale");
            classifierMap.put("quality", "quality");
            classifierMap.put("transportation", "transportation");

            for (Map.Entry<String, String> entry : classifierMap.entrySet()) {
                if (classifierOutput.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }

            return "others";
        }

    }

}
