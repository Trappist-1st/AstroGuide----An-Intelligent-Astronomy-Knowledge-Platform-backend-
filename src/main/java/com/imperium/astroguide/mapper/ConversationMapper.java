package com.imperium.astroguide.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.imperium.astroguide.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
