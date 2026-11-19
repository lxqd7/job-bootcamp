package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class CourseVersionInitializer implements ApplicationRunner {

    private final CourseMapper courseMapper;
    private final CourseVersionService courseVersionService;

    public CourseVersionInitializer(CourseMapper courseMapper, CourseVersionService courseVersionService) {
        this.courseMapper = courseMapper;
        this.courseVersionService = courseVersionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Course course : courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getStatus, "ONLINE"))) {
            if (courseVersionService.latestVersionNo(course.getId()) == 0) {
                courseVersionService.publish(course);
            }
        }
    }
}