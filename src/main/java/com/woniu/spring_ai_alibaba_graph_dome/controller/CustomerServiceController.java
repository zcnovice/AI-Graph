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

@RestController
@RequestMapping("/graph/customerService")
public class CustomerServiceController {

    private final CompiledGraph compiledGraph;

    public CustomerServiceController(@Qualifier("CustomerGraph") StateGraph stateGraph) throws GraphStateException {

        this.compiledGraph = stateGraph.compile();
    }


    @GetMapping("/chat")
    public String simpleChat(@RequestParam("query") String query) {

        return compiledGraph.invoke(Map.of("input", query))
                .flatMap(input -> input.value("solution"))
                .get().toString();
    }
}
