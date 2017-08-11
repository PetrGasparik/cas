package org.apereo.cas.config;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.interrupt.webflow.InquireInterruptAction;
import org.apereo.cas.interrupt.webflow.InterruptWebflowConfigurer;
import org.apereo.cas.interrupt.webflow.PrepareInterruptViewAction;
import org.apereo.cas.web.flow.CasWebflowConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry;
import org.springframework.webflow.engine.builder.support.FlowBuilderServices;
import org.springframework.webflow.execution.Action;

/**
 * This is {@link CasInterruptWebflowConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
@Configuration("casInterruptWebflowConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class CasInterruptWebflowConfiguration {
    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("loginFlowRegistry")
    private FlowDefinitionRegistry loginFlowDefinitionRegistry;

    @Autowired
    private FlowBuilderServices flowBuilderServices;
    
    @ConditionalOnMissingBean(name = "interruptWebflowConfigurer")
    @Bean
    public CasWebflowConfigurer interruptWebflowConfigurer() {
        return new InterruptWebflowConfigurer(flowBuilderServices, loginFlowDefinitionRegistry);
    }
    
    @Bean
    public Action inquireInterruptAction() {
        return new InquireInterruptAction();
    }

    @Bean
    public Action prepareInterruptViewAction() {
        return new PrepareInterruptViewAction();
    }
}