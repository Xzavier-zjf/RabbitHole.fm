package com.rabbithole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rabbithole.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
