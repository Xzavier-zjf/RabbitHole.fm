package com.rabbithole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rabbithole.entity.SongRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SongRequestMapper extends BaseMapper<SongRequest> {
}
