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

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * @author zcnovice
 * @data 2025/7/19 上午10:28
 */
@Configuration
public class RecommendedPlacesConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GraphAutoConfiguration.class);

    @Bean
    /* 接收一个ChatClient.Builder参数，用于构建AI聊天客户端 */
    public StateGraph workflowGraphR(ChatClient.Builder builder) throws GraphStateException {
        ChatClient chatClient = builder.defaultAdvisors(new SimpleLoggerAdvisor()).build();

        OverAllStateFactory stateFactory = () -> {
            OverAllState state = new OverAllState();

            state.registerKeyAndStrategy("input", new ReplaceStrategy());
            state.registerKeyAndStrategy("classifier_output", new ReplaceStrategy());
            state.registerKeyAndStrategy("solution", new ReplaceStrategy());
            return state;
        };


        /*  定义一个问题分类节点，用于将用户输入分类为有意图与无意图 */
        QuestionClassifierNode intentClassifier  = QuestionClassifierNode.builder()
                .chatClient(chatClient)
                .inputTextKey("input")
                .categories(List.of("with intent", "without intent"))
                .classificationInstructions(
                        List.of("\"Determine if the user's input specifies a **category of place** to visit (e.g., 'coffee shop', 'hospital').\n" +
                                "Inputs with a specific category are 'with intent'. General inquiries like 'where to go for fun?' are 'without intent'.\""))
                .build();


        StateGraph stateGraph = new StateGraph("Consumer Service Workflow Demo", stateFactory)

                // 定义节点
                .addNode("intentClassifier", node_async(intentClassifier ))
                /* 注册“记录和输出方案”节点。 */
                .addNode("recorder", node_async(new RecordingNode()))


                // 定义边（流程顺序）
                /* 所有流程的起点（START）都必须连接到 intentClassifier 这个节点 */
                /* 定义起始结点 */
                .addEdge(START, "intentClassifier")


                .addConditionalEdges("intentClassifier",
                        edge_async(new IntentClassifier()),
                        Map.of("with intent", "recorder", "without intent", "recorder"))

                // 图的结束节点
                .addEdge("recorder", END);


        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "workflow graph");



        System.out.println("\n\n");
        System.out.println(graphRepresentation.content());
        System.out.println("\n\n");

        return stateGraph;

    }

    public static class IntentClassifier implements EdgeAction {

        @Override
        public String apply(OverAllState state) {
            String classifierOutput = (String) state.value("classifier_output").orElse("");
            /* 打印日志信息（调用最上面的日志定义） */
            logger.info("classifierOutput: {}", classifierOutput);

            if (classifierOutput.contains("with intent")) {
                return "with intent";
            }
            return "without intent";
        }
    }

}
