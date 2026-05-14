package com.rabbithole.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_channel")
public class UserChannel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long channelId;
    private String channelName;
}
