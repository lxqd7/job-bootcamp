package com.jobbootcamp.user.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.user.domain.RoleCode;
import com.jobbootcamp.user.domain.UserAccount;
import com.jobbootcamp.user.infrastructure.RolePermissionMapper;
import com.jobbootcamp.user.infrastructure.UserAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.demo.seed-enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final List<String> ADMIN_PERMISSIONS = List.of(
            "course:manage",
            "route:manage",
            "assignment:manage",
            "teacher:assign",
            "community:moderate",
            "anonymous:trace",
            "marketing:manage",
            "points:revoke",
            "trade:operate",
            "user:manage");

    private final UserAccountMapper userMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoDataSeeder(UserAccountMapper userMapper,
                          RolePermissionMapper rolePermissionMapper,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.demo.password}") String demoPassword) {
        this.userMapper = userMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String permission : ADMIN_PERMISSIONS) {
            rolePermissionMapper.insertIgnore(RoleCode.ADMIN.name(), permission);
        }

        seed("student01", "学员一", RoleCode.STUDENT);
        seed("student02", "学员二", RoleCode.STUDENT);
        seed("student03", "学员三", RoleCode.STUDENT);
        seed("teacher01", "教师一", RoleCode.TEACHER);
        seed("teacher02", "教师二", RoleCode.TEACHER);
        seed("admin01", "管理员", RoleCode.ADMIN);

        log.info("Demo accounts ready: student01-03, teacher01-02, admin01. Password comes from DEMO_PASSWORD.");
    }

    private void seed(String username, String nickname, RoleCode roleCode) {
        Long existing = userMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username));
        if (existing != null && existing > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(demoPassword));
        user.setNickname(nickname);
        user.setRoleCode(roleCode.name());
        user.setEnabled(true);
        user.setTokenVersion(0L);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setRevision(0L);
        userMapper.insert(user);
    }
}