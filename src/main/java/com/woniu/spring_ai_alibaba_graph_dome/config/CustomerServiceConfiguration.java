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

@Configuration
public class CustomerServiceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GraphAutoConfiguration.class);

    /**
     * 创建一个Graph 工作流
     * @param builder
     * @return
     */
    @Bean
    public StateGraph CustomerGraph(ChatClient.Builder builder) throws GraphStateException {

        ChatClient chatClient = builder.defaultAdvisors(new SimpleLoggerAdvisor()).build();


        /**
         * 每次执行工作流时创建初始的全局状态对象
         */
        OverAllStateFactory stateFactory = () -> {
            OverAllState state = new OverAllState();

            /*input: 存储用户输入的原始文本
                classifier_output: 存储分类器的输出结果
                solution: 存储最终的处理方案
                使用ReplaceStrategy策略，表示每次写入会替换旧值*/
            state.registerKeyAndStrategy("input", new ReplaceStrategy());
            state.registerKeyAndStrategy("classifier_output", new ReplaceStrategy());
            state.registerKeyAndStrategy("solution", new ReplaceStrategy());
            return state;
        };

        //一级节点用于判断用户输入的问题是否涵盖 “新增设备”“维修记录”“设备下架”“手册导入”“运维经验录入”等
        QuestionClassifierNode Level1Node = QuestionClassifierNode.builder()
                .chatClient(chatClient)
                .inputTextKey("input")
                .categories(List.of("Other","新增设备","维修记录","设备下架","手册导入","运维经验录入"))
                .classificationInstructions(
                        List.of(
                                "If the user input is a general AI chat, classify it as: Normal AI Chat",
                                "If the user input is '新增设备', '登记新设备', or related to adding new equipment, classify it as: 新增设备",
                                "If the user input is '维修记录' or related to maintenance records, classify it as: 维修记录",
                                "If the user input is '设备下架' or related to device decommissioning, classify it as: 设备下架",
                                "If the user input is '手册导入' or related to importing manuals, classify it as: 手册导入",
                                "If the user input is '运维经验录入' or related to entering O&M experience, classify it as: 运维经验录入",
                                "For any other input, classify it as: Other"))
                .build();


        //开始编排节点
        StateGraph stateGraph = new StateGraph("Customer Service Workflow Demo", stateFactory)
                .addNode("Level1Node", node_async(Level1Node))
                .addNode("recorder", node_async(new RecordingNode()))

                .addEdge(START,"Level1Node")
                .addConditionalEdges("Level1Node",
                        edge_async(new FeedbackQuestionDispatcher()),
                        Map.of("新增设备", "recorder", "维修记录", "recorder", "设备下架", "recorder", "手册导入", "recorder", "运维经验录入", "recorder", "Other", "recorder"))
                .addEdge("recorder", END);


        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "workflow graph");

        return stateGraph;
    }


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

            if (classifierOutput.contains("新增设备")) {
                return "新增设备";
            }else if(classifierOutput.contains("维修记录")) {
                return "维修记录";
            }else if(classifierOutput.contains("设备下架")) {
                return "设备下架";
            }else if(classifierOutput.contains("手册导入")) {
                return "手册导入";
            }else if(classifierOutput.contains("运维经验录入")) {
                return "运维经验录入";
            }
            return "Other";
        }

    }
}
