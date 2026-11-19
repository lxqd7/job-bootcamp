package com.jobbootcamp.course.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时重建一次分类闭包，保证迁移前已有分类也具备完整祖先路径；分类变更时仍由服务重建。
 */
@Component
@Order(1)
public class CategoryClosureInitializer implements ApplicationRunner {

    private final CategoryClosureService closureService;

    public CategoryClosureInitializer(CategoryClosureService closureService) {
        this.closureService = closureService;
    }

    @Override
    public void run(ApplicationArguments args) {
        closureService.rebuildAll();
    }
}