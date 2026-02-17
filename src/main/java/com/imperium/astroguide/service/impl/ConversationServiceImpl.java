package com.imperium.astroguide.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.imperium.astroguide.mapper.ConversationMapper;
import com.imperium.astroguide.model.entity.Conversation;
import com.imperium.astroguide.service.ConversationService;
import org.springframework.stereotype.Service;

@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {

}
