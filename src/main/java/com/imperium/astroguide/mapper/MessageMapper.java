package com.imperium.astroguide.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.imperium.astroguide.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
