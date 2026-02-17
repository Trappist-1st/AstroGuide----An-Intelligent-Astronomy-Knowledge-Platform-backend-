package com.imperium.astroguide.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.imperium.astroguide.mapper.TermCacheMapper;
import com.imperium.astroguide.model.entity.TermCache;
import com.imperium.astroguide.service.TermCacheService;
import org.springframework.stereotype.Service;

@Service
public class TermCacheServiceImpl extends ServiceImpl<TermCacheMapper, TermCache> implements TermCacheService {
}
