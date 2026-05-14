package com.rabbithole.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_song_request")
public class SongRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private Long channelId;
    private Long songId;
    private String songName;
    private String artists;
    private String message;
    private Integer status; // 0=pending, 1=played, 2=skipped
    private LocalDateTime createdAt;
    private LocalDateTime playedAt;
}
