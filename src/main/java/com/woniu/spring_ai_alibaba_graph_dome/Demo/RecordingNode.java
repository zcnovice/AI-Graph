package com.woniu.spring_ai_alibaba_graph_dome.Demo;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class RecordingNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(RecordingNode.class);

    @Override   //“接力棒”：接收一个OverAllState参数，用于获取全局状态。
    public Map<String, Object> apply(OverAllState state) {
        //获取输出内容
        String feedback = (String) state.value("classifier_output").get();

        Map<String, Object> updatedState = new HashMap<>();
        //根据这里的业务逻辑，积极的就返回  Praise, no action taken.
        if (feedback.contains("positive")) {
            logger.info("Received positive feedback: {}", feedback);
            updatedState.put("solution", "Praise, no action taken.");
        }
        //剩下的是消极的，那种消极就返回对应的消极语句
        else {
            logger.info("Received negative feedback: {}", feedback);
            updatedState.put("solution", feedback);
        }

        return updatedState;
    }

}
