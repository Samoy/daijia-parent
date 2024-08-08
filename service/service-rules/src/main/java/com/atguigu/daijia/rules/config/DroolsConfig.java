package com.atguigu.daijia.rules.config;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DroolsConfig
 *
 * @author Samoy
 * @date 2024/8/8
 */
@Configuration
public class DroolsConfig {
    // KieServices的实例，用于访问KieServices工厂方法
    private static final KieServices kieServices = KieServices.Factory.get();
    // 规则文件路径，指向DRL规则文件
    private static final String RULES_CUSTOMER_RULES_DRL = "rules/FeeRule.drl";

    /**
     * 配置KieContainer bean
     * 该方法负责加载规则文件并构建规则容器，是Drools规则引擎的核心配置方法
     *
     * @return KieContainer 实例，包含了编译后的规则库
     */
    @Bean
    public KieContainer kieContainer() {
        // 创建一个新的KieFileSystem实例，用于加载规则文件
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        // 将规则文件加载到KieFileSystem中
        kieFileSystem.write(ResourceFactory.newClassPathResource(RULES_CUSTOMER_RULES_DRL));
        // 创建一个新的KieBuilder实例，用于编译KieFileSystem中的规则
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        // 编译所有加载的规则
        kieBuilder.buildAll();
        // 获取编译后的KieModule实例
        KieModule kieModule = kieBuilder.getKieModule();
        // 根据编译后的KieModule创建一个新的KieContainer实例
        return kieServices.newKieContainer(kieModule.getReleaseId());
    }
}
