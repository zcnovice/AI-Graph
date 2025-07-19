package com.woniu.spring_ai_alibaba_graph_dome.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author zcnovice
 * @data 2025/7/19 上午10:26
 */
@RestController
@RequestMapping("/graph/recommendedPlaces")
public class RecommendedPlacesController {

    private final CompiledGraph compiledGraph;


    public RecommendedPlacesController(@Qualifier("workflowGraphR") StateGraph stateGraph) throws GraphStateException {
        this.compiledGraph  = stateGraph.compile();
    }

    @GetMapping("/places")
    public String recommendPlaces(@RequestParam("query") String query) {
        return compiledGraph.invoke(Map.of("input", query))
                .flatMap(state -> state.value("solution"))
                .orElse("No places recommended").toString();
    }

}
