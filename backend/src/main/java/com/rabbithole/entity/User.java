package com.rabbithole.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;

    @JsonIgnore
    private String passwordHash;

    private String nickname;
    private String avatarUrl;
    private String email;
    private Integer requestQuota;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
