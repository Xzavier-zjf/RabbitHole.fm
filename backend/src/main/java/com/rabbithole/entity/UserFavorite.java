package com.rabbithole.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_favorite")
public class UserFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long songId;
    private String source;
    private String sourceSongId;
    private String songName;
    private String artists;
    private String coverUrl;
    private String songUrl;
    private String sourcePayload;
    private LocalDateTime createdAt;
}
