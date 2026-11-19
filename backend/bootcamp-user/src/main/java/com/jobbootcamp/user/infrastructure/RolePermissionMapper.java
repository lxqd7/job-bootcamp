package com.jobbootcamp.user.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionMapper {

    @Select("SELECT permission_code FROM role_permission WHERE role_code = #{roleCode} ORDER BY permission_code")
    List<String> selectCodesByRole(@Param("roleCode") String roleCode);

    @Insert("INSERT IGNORE INTO role_permission(role_code, permission_code) VALUES(#{roleCode}, #{permissionCode})")
    int insertIgnore(@Param("roleCode") String roleCode, @Param("permissionCode") String permissionCode);
}